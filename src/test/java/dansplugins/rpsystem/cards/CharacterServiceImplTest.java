package dansplugins.rpsystem.cards;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;
import dansplugins.rpsystem.api.event.CharacterEndedEvent;
import dansplugins.rpsystem.storage.CharacterHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CharacterServiceImplTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void trueDeathArchivesConfiguredCharacterAndInstallsDraftExactlyOnce() {
        Fixture fixture = fixture();
        CharacterCard current = configuredCard(fixture.playerId, 1_000L);
        fixture.cards.put(current);

        CharacterServiceImpl.TrueDeathContext death = death(fixture.playerId, 2_000L);
        assertEquals(CharacterServiceImpl.EndResult.ENDED,
                fixture.service.endForTrueDeath(death));

        List<CharacterRecord> history = fixture.history.history(fixture.playerId);
        assertEquals(1, history.size());
        assertEquals(CharacterStatus.DECEASED, history.getFirst().status());
        assertEquals(current.getCharacterId(), history.getFirst().characterId());

        CharacterRecord replacement = fixture.service.currentCharacter(fixture.playerId).orElseThrow();
        assertEquals(CharacterStatus.DRAFT, replacement.status());
        assertNotEquals(current.getCharacterId(), replacement.characterId());
        assertEquals(1, fixture.events.size());

        assertEquals(CharacterServiceImpl.EndResult.ALREADY_ENDED,
                fixture.service.endForTrueDeath(death));
        assertEquals(1, fixture.history.history(fixture.playerId).size());
        assertEquals(1, fixture.events.size());
    }

    @Test
    void retryCompletesReplacementAfterArchiveWasAlreadyWritten() {
        Fixture fixture = fixture();
        CharacterCard current = configuredCard(fixture.playerId, 1_000L);
        fixture.cards.put(current);
        AtomicBoolean first = new AtomicBoolean(true);
        fixture.service = service(fixture, ignored -> !first.getAndSet(false));

        CharacterServiceImpl.TrueDeathContext death = death(fixture.playerId, 2_000L);
        assertEquals(CharacterServiceImpl.EndResult.FAILED,
                fixture.service.endForTrueDeath(death));
        assertEquals(current.getCharacterId(), fixture.cards.getCard(fixture.playerId).getCharacterId());
        assertEquals(1, fixture.history.history(fixture.playerId).size());

        assertEquals(CharacterServiceImpl.EndResult.RECOVERED,
                fixture.service.endForTrueDeath(death));
        assertNotEquals(current.getCharacterId(), fixture.cards.getCard(fixture.playerId).getCharacterId());
        assertEquals(1, fixture.history.history(fixture.playerId).size());
    }

    @Test
    void unconfiguredDraftIsResetButNeverFabricatedAsHistory() {
        Fixture fixture = fixture();
        CharacterCard draft = CharacterCard.newDraft(fixture.playerId, "Player", 1_000L);
        fixture.cards.put(draft);

        assertEquals(CharacterServiceImpl.EndResult.DRAFT_RESET,
                fixture.service.endForTrueDeath(death(fixture.playerId, 2_000L)));
        assertTrue(fixture.history.history(fixture.playerId).isEmpty());
        assertNotEquals(draft.getCharacterId(),
                fixture.cards.getCard(fixture.playerId).getCharacterId());
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void oldDeathCannotEndACharacterCreatedAfterIt() {
        Fixture fixture = fixture();
        CharacterCard current = configuredCard(fixture.playerId, 3_000L);
        fixture.cards.put(current);

        assertEquals(CharacterServiceImpl.EndResult.DEATH_PREDATES_CURRENT,
                fixture.service.endForTrueDeath(death(fixture.playerId, 2_000L)));
        assertEquals(current.getCharacterId(), fixture.cards.getCard(fixture.playerId).getCharacterId());
        assertTrue(fixture.history.history(fixture.playerId).isEmpty());
    }

    @Test
    void sameApprovalTimestampWithDifferentIdentityFailsClosed() {
        Fixture fixture = fixture();
        fixture.cards.put(configuredCard(fixture.playerId, 1_000L));
        CharacterServiceImpl.TrueDeathContext original = death(fixture.playerId, 2_000L);
        assertEquals(CharacterServiceImpl.EndResult.ENDED,
                fixture.service.endForTrueDeath(original));

        CharacterServiceImpl.TrueDeathContext conflicting =
                new CharacterServiceImpl.TrueDeathContext(
                        fixture.playerId,
                        original.declaredAt() - 1,
                        original.approvedAt(),
                        UUID.randomUUID(),
                        "Different record");

        assertFalse(fixture.service.hasArchivedDeath(conflicting));
        assertEquals(CharacterServiceImpl.EndResult.FAILED,
                fixture.service.endForTrueDeath(conflicting));
        assertEquals(1, fixture.history.history(fixture.playerId).size());
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.playerId = UUID.randomUUID();
        fixture.cards = new CardRepository();
        fixture.history = new CharacterHistoryRepository(temporaryDirectory,
                Logger.getLogger("CharacterServiceImplTest"));
        fixture.events = new ArrayList<>();
        fixture.service = service(fixture, ignored -> true);
        return fixture;
    }

    private CharacterServiceImpl service(Fixture fixture,
                                         java.util.function.Predicate<CharacterCard> writer) {
        return new CharacterServiceImpl(fixture.cards, fixture.history, writer,
                fixture.events::add, Logger.getLogger("CharacterServiceImplTest"));
    }

    private static CharacterCard configuredCard(UUID playerId, long createdAt) {
        CharacterCard card = CharacterCard.newDraft(playerId, "Player", createdAt);
        card.setName("Aldric");
        card.setRace("Human");
        return card;
    }

    private static CharacterServiceImpl.TrueDeathContext death(UUID playerId, long approvedAt) {
        return new CharacterServiceImpl.TrueDeathContext(playerId, approvedAt - 100,
                approvedAt, UUID.randomUUID(), "Fell in battle");
    }

    private static final class Fixture {
        private UUID playerId;
        private CardRepository cards;
        private CharacterHistoryRepository history;
        private List<CharacterEndedEvent> events;
        private CharacterServiceImpl service;
    }
}
