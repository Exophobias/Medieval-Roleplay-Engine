package dansplugins.rpsystem.cards;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterService;
import dansplugins.rpsystem.api.CharacterStatus;
import dansplugins.rpsystem.api.event.CharacterEndedEvent;
import dansplugins.rpsystem.storage.CharacterHistoryRepository;
import dansplugins.rpsystem.storage.StorageService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Character read API plus the single internal lifecycle transition used by TrueDeath. */
public final class CharacterServiceImpl implements CharacterService {

    private final CardRepository current;
    private final CharacterHistoryRepository history;
    private final Predicate<CharacterCard> cardWriter;
    private final Consumer<CharacterEndedEvent> eventSink;
    private final Logger logger;

    public CharacterServiceImpl(CardRepository current,
                                CharacterHistoryRepository history,
                                StorageService storage,
                                Consumer<CharacterEndedEvent> eventSink,
                                Logger logger) {
        this(current, history, storage::saveCard, eventSink, logger);
    }

    CharacterServiceImpl(CardRepository current,
                         CharacterHistoryRepository history,
                         Predicate<CharacterCard> cardWriter,
                         Consumer<CharacterEndedEvent> eventSink,
                         Logger logger) {
        this.current = Objects.requireNonNull(current, "current");
        this.history = Objects.requireNonNull(history, "history");
        this.cardWriter = Objects.requireNonNull(cardWriter, "cardWriter");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Optional<CharacterRecord> currentCharacter(UUID playerId) {
        CharacterCard card = current.getCard(playerId);
        return card == null ? Optional.empty() : Optional.of(card.snapshot());
    }

    @Override
    public Optional<CharacterRecord> character(UUID characterId) {
        CharacterCard card = current.getCardByCharacterId(characterId);
        return card == null ? history.character(characterId) : Optional.of(card.snapshot());
    }

    @Override
    public List<CharacterRecord> characters(UUID playerId) {
        ArrayList<CharacterRecord> records = new ArrayList<>();
        currentCharacter(playerId).ifPresent(records::add);
        records.addAll(history.history(playerId));
        return List.copyOf(records);
    }

    @Override
    public Collection<CharacterRecord> currentCharacters() {
        return current.getCards().stream().map(CharacterCard::snapshot).toList();
    }

    @Override
    public Collection<CharacterRecord> endedCharacters() {
        return history.all();
    }

    public boolean hasArchivedDeath(TrueDeathContext death) {
        Objects.requireNonNull(death, "death");
        return history.death(death.playerId(), death.approvedAt())
                .map(ended -> matchesDeath(ended, death))
                .orElse(false);
    }

    /**
     * Archives the exact current card and replaces it with a fresh draft.
     *
     * <p>The archive is written first. If the process stops before the replacement card lands, a
     * reconciliation call sees the same archived character id and completes the second half.
     */
    public synchronized EndResult endForTrueDeath(TrueDeathContext death) {
        Objects.requireNonNull(death, "death");
        Optional<CharacterRecord> existing = history.death(death.playerId(), death.approvedAt());
        CharacterCard card = current.getCard(death.playerId());

        if (existing.isPresent()) {
            CharacterRecord ended = existing.get();
            if (!matchesDeath(ended, death)) {
                logger.severe("Archived death at " + death.approvedAt() + " for "
                        + death.playerId() + " has different declaration or approver metadata; "
                        + "the new TrueDeath was not marked complete.");
                return EndResult.FAILED;
            }
            if (card == null || !card.getCharacterId().equals(ended.characterId())) {
                return EndResult.ALREADY_ENDED;
            }
            return replaceAfterArchive(card, ended, death.approvedAt(), EndResult.RECOVERED);
        }

        if (card == null) {
            return EndResult.NO_CURRENT_CARD;
        }

        if (card.getCreatedAt() > death.approvedAt()) {
            // This is already the post-death character (normally seen only after journal recovery).
            return EndResult.DEATH_PREDATES_CURRENT;
        }

        if (!card.isConfigured()) {
            return replaceDraft(card, death.approvedAt());
        }

        CharacterRecord ended;
        try {
            ended = card.deceasedSnapshot(death.declaredAt(), death.approvedAt(),
                    death.approvedBy(), death.reason());
            history.archive(ended);
        } catch (IOException | RuntimeException e) {
            logger.log(Level.SEVERE,
                    "Could not archive character " + card.getCharacterId()
                            + " for true death " + death.approvedAt(), e);
            return EndResult.FAILED;
        }
        return replaceAfterArchive(card, ended, death.approvedAt(), EndResult.ENDED);
    }

    private EndResult replaceAfterArchive(CharacterCard previous, CharacterRecord ended,
                                          long createdAt, EndResult success) {
        CharacterCard replacement = CharacterCard.newDraft(previous.getPlayerUUID(),
                previous.getLastKnownPlayerName(), createdAt);
        if (!cardWriter.test(replacement)) {
            logger.severe("Character " + ended.characterId()
                    + " is archived, but its replacement draft could not be saved; "
                    + "TrueDeath reconciliation will retry it.");
            return EndResult.FAILED;
        }
        current.put(replacement);
        CharacterEndedEvent event = new CharacterEndedEvent(ended, replacement.snapshot());
        try {
            eventSink.accept(event);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "A CharacterEndedEvent consumer failed", e);
        }
        return success;
    }

    private EndResult replaceDraft(CharacterCard previous, long createdAt) {
        CharacterCard replacement = CharacterCard.newDraft(previous.getPlayerUUID(),
                previous.getLastKnownPlayerName(), createdAt);
        if (!cardWriter.test(replacement)) {
            logger.severe("Unconfigured character draft for " + previous.getPlayerUUID()
                    + " could not be reset after TrueDeath; reconciliation will retry it.");
            return EndResult.FAILED;
        }
        current.put(replacement);
        return EndResult.DRAFT_RESET;
    }

    private static boolean matchesDeath(CharacterRecord ended, TrueDeathContext death) {
        return ended.status() == CharacterStatus.DECEASED
                && ended.playerId().equals(death.playerId())
                && ended.endedAt() == death.approvedAt()
                && ended.deathDeclaredAt() == death.declaredAt()
                && death.approvedBy().equals(ended.deathApprovedBy());
    }

    public record TrueDeathContext(UUID playerId, long declaredAt, long approvedAt,
                                   UUID approvedBy, String reason) {
        public TrueDeathContext {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(approvedBy, "approvedBy");
            reason = reason == null ? "" : reason;
            if (approvedAt <= 0 || declaredAt < 0 || declaredAt > approvedAt) {
                throw new IllegalArgumentException("invalid true-death timestamps");
            }
        }
    }

    public enum EndResult {
        ENDED,
        RECOVERED,
        ALREADY_ENDED,
        NO_CURRENT_CARD,
        DEATH_PREDATES_CURRENT,
        DRAFT_RESET,
        FAILED;

        public boolean complete() {
            return this != FAILED;
        }
    }
}
