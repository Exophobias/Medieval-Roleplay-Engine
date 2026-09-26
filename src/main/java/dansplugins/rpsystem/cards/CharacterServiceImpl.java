package dansplugins.rpsystem.cards;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterService;
import dansplugins.rpsystem.api.CharacterStatus;
import dansplugins.rpsystem.api.ForumCharacterEdit;
import dansplugins.rpsystem.api.ForumCharacterEditResult;
import dansplugins.rpsystem.api.ForumCharacterEditResult.Status;
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
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Character reads, guarded forum edits, and the internal TrueDeath transition. */
public final class CharacterServiceImpl implements CharacterService {

    private final CardRepository current;
    private final CharacterHistoryRepository history;
    private final Predicate<CharacterCard> cardWriter;
    private final Consumer<CharacterEndedEvent> eventSink;
    private final Consumer<CharacterRecord> createdSink;
    private final BiConsumer<CharacterRecord, CharacterRecord> updatedSink;
    private final Predicate<UUID> nameOnCooldown;
    private final Consumer<UUID> beginNameCooldown;
    private final BooleanSupplier mainThread;
    private final Function<UUID, String> knownPlayerName;
    private final Runnable manifestWriter;
    private final Logger logger;

    public CharacterServiceImpl(CardRepository current,
                                CharacterHistoryRepository history,
                                StorageService storage,
                                Consumer<CharacterEndedEvent> eventSink,
                                Consumer<CharacterRecord> createdSink,
                                BiConsumer<CharacterRecord, CharacterRecord> updatedSink,
                                Predicate<UUID> nameOnCooldown,
                                Consumer<UUID> beginNameCooldown,
                                BooleanSupplier mainThread,
                                Function<UUID, String> knownPlayerName,
                                Logger logger) {
        this(current, history, storage::saveCard, eventSink, createdSink, updatedSink,
                nameOnCooldown, beginNameCooldown, mainThread, knownPlayerName,
                storage::saveCardFileNames, logger);
    }

    CharacterServiceImpl(CardRepository current,
                         CharacterHistoryRepository history,
                         Predicate<CharacterCard> cardWriter,
                         Consumer<CharacterEndedEvent> eventSink,
                         Logger logger) {
        this(current, history, cardWriter, eventSink, ignored -> { },
                (ignoredPrevious, ignoredCurrent) -> { }, ignored -> false,
                ignored -> { }, () -> true, ignored -> "", () -> { }, logger);
    }

    CharacterServiceImpl(CardRepository current,
                         CharacterHistoryRepository history,
                         Predicate<CharacterCard> cardWriter,
                         Consumer<CharacterEndedEvent> eventSink,
                         Consumer<CharacterRecord> createdSink,
                         BiConsumer<CharacterRecord, CharacterRecord> updatedSink,
                         Predicate<UUID> nameOnCooldown,
                         Consumer<UUID> beginNameCooldown,
                         BooleanSupplier mainThread,
                         Function<UUID, String> knownPlayerName,
                         Runnable manifestWriter,
                         Logger logger) {
        this.current = Objects.requireNonNull(current, "current");
        this.history = Objects.requireNonNull(history, "history");
        this.cardWriter = Objects.requireNonNull(cardWriter, "cardWriter");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
        this.createdSink = Objects.requireNonNull(createdSink, "createdSink");
        this.updatedSink = Objects.requireNonNull(updatedSink, "updatedSink");
        this.nameOnCooldown = Objects.requireNonNull(nameOnCooldown, "nameOnCooldown");
        this.beginNameCooldown = Objects.requireNonNull(beginNameCooldown, "beginNameCooldown");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.knownPlayerName = Objects.requireNonNull(knownPlayerName, "knownPlayerName");
        this.manifestWriter = Objects.requireNonNull(manifestWriter, "manifestWriter");
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

    @Override
    public synchronized ForumCharacterEditResult applyForumEdit(ForumCharacterEdit edit) {
        if (!mainThread.getAsBoolean()) {
            throw new IllegalStateException("Forum character edits must run on the Bukkit main thread");
        }
        if (edit == null || edit.playerId() == null || edit.age() < 0
                || edit.age() > CharacterRecord.MAX_PUBLIC_AGE
                || (edit.expectedCharacterId() == null) != (edit.expectedFingerprint() == null)
                || (edit.expectedFingerprint() != null
                && !edit.expectedFingerprint().matches("[0-9a-f]{64}"))) {
            return new ForumCharacterEditResult(Status.INVALID, null);
        }

        String name = cleanForumField(edit.name());
        String race = cleanForumField(edit.race());
        String subculture = cleanForumField(edit.subculture());
        String gender = cleanForumField(edit.gender());
        CharacterCard existing = current.getCard(edit.playerId());
        CharacterRecord previous = existing == null ? null : existing.snapshot();
        if (name == null || race == null || subculture == null || gender == null
                || (name.equalsIgnoreCase(CharacterCard.DEFAULT_NAME)
                && !name.equals(CharacterCard.DEFAULT_NAME))) {
            return new ForumCharacterEditResult(Status.INVALID, previous);
        }
        if (name.isBlank()) {
            name = CharacterCard.DEFAULT_NAME;
        }

        boolean sameFields = previous != null && sameForumFields(previous, name, race,
                subculture, edit.age(), gender);
        if (existing == null) {
            if (edit.expectedCharacterId() != null) {
                return new ForumCharacterEditResult(Status.STALE, null);
            }
        } else if (edit.expectedCharacterId() == null) {
            return new ForumCharacterEditResult(
                    sameFields ? Status.UNCHANGED : Status.STALE, previous);
        } else if (!existing.getCharacterId().equals(edit.expectedCharacterId())) {
            return new ForumCharacterEditResult(Status.STALE, previous);
        } else if (!previous.editFingerprint().equals(edit.expectedFingerprint())) {
            return new ForumCharacterEditResult(
                    sameFields ? Status.UNCHANGED : Status.STALE, previous);
        } else if (sameFields) {
            return new ForumCharacterEditResult(Status.UNCHANGED, previous);
        }

        if (previous != null && previous.status() == CharacterStatus.ACTIVE
                && CharacterCard.DEFAULT_NAME.equals(name)) {
            return new ForumCharacterEditResult(Status.INVALID, previous);
        }
        boolean nameChanged = previous == null
                ? !CharacterCard.DEFAULT_NAME.equals(name)
                : !previous.name().equals(name);
        if (nameChanged && nameOnCooldown.test(edit.playerId())) {
            return new ForumCharacterEditResult(Status.NAME_COOLDOWN, previous);
        }

        CharacterCard candidate = existing == null
                ? CharacterCard.newDraft(edit.playerId(), knownPlayerName.apply(edit.playerId()),
                        Math.max(1L, System.currentTimeMillis()))
                : CharacterCard.fromLines(existing.serializedLines(), existing.getCreatedAt());
        candidate.setName(name);
        candidate.setRace(race);
        candidate.setSubculture(subculture);
        candidate.setAge(edit.age());
        candidate.setGender(gender);
        if (!cardWriter.test(candidate)) {
            return new ForumCharacterEditResult(Status.STORAGE_FAILED, previous);
        }

        current.put(candidate);
        if (existing == null) {
            manifestWriter.run();
        }
        CharacterRecord saved = candidate.snapshot();
        if (nameChanged) {
            try {
                beginNameCooldown.accept(edit.playerId());
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not start the name-change cooldown", e);
            }
        }
        try {
            if (previous == null) {
                createdSink.accept(saved);
            } else {
                updatedSink.accept(previous, saved);
            }
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "A forum character edit consumer failed", e);
        }
        return new ForumCharacterEditResult(Status.APPLIED, saved);
    }

    private static boolean sameForumFields(CharacterRecord current, String name, String race,
                                           String subculture, int age, String gender) {
        return current.name().equals(name) && current.race().equals(race)
                && current.subculture().equals(subculture) && current.age() == age
                && current.gender().equals(gender);
    }

    private static String cleanForumField(String value) {
        if (value == null || value.codePoints().anyMatch(Character::isISOControl)) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.length() > 128) {
            return null;
        }
        return cleaned;
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
