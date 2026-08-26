package dansplugins.rpsystem.storage;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterHistoryRepositoryTest {

    private static final UUID PLAYER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID APPROVER_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    @TempDir
    Path temporaryDirectory;

    @Test
    void archiveIsDurableOrderedAndIdempotentAcrossReload() throws Exception {
        CharacterHistoryRepository repository = repository();
        CharacterRecord older = deceased(
                UUID.fromString("50000000-0000-0000-0000-000000000001"), 100L, "Older");
        CharacterRecord newer = deceased(
                UUID.fromString("50000000-0000-0000-0000-000000000002"), 200L, "Newer");

        assertEquals(CharacterHistoryRepository.ArchiveResult.ARCHIVED,
                repository.archive(older));
        assertEquals(CharacterHistoryRepository.ArchiveResult.ALREADY_PRESENT,
                repository.archive(older));
        assertEquals(CharacterHistoryRepository.ArchiveResult.ARCHIVED,
                repository.archive(newer));

        assertEquals(List.of(newer, older), repository.history(PLAYER_ID));
        assertEquals(List.of(newer, older), List.copyOf(repository.all()));
        assertEquals(older, repository.character(older.characterId()).orElseThrow());
        assertTrue(repository.containsDeath(PLAYER_ID, 100L));
        assertEquals(older, repository.death(PLAYER_ID, 100L).orElseThrow());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("history")
                .resolve(PLAYER_ID.toString()).resolve("100.yml")));

        CharacterHistoryRepository reloaded = repository();
        reloaded.load();

        assertEquals(List.of(newer, older), reloaded.history(PLAYER_ID));
        assertEquals(CharacterHistoryRepository.ArchiveResult.ALREADY_PRESENT,
                reloaded.archive(older));
    }

    @Test
    void returnedHistoryCollectionsCannotMutateRepositoryMembership() throws Exception {
        CharacterHistoryRepository repository = repository();
        CharacterRecord record = deceased(UUID.randomUUID(), 100L, "Record");
        repository.archive(record);

        List<CharacterRecord> playerHistory = repository.history(PLAYER_ID);
        Collection<CharacterRecord> all = repository.all();

        assertThrows(UnsupportedOperationException.class, playerHistory::clear);
        assertThrows(UnsupportedOperationException.class, all::clear);
        assertEquals(record, repository.character(record.characterId()).orElseThrow());
    }

    @Test
    void conflictingIdentityOrTimestampIsRejectedWithoutReplacingTheArchive() throws Exception {
        CharacterHistoryRepository repository = repository();
        UUID characterId = UUID.randomUUID();
        CharacterRecord original = deceased(characterId, 100L, "Original");
        repository.archive(original);

        CharacterRecord sameIdDifferentDeath = deceased(characterId, 200L, "Changed");
        CharacterRecord sameDeathDifferentId = deceased(UUID.randomUUID(), 100L, "Changed");

        assertThrows(IOException.class, () -> repository.archive(sameIdDifferentDeath));
        assertThrows(IOException.class, () -> repository.archive(sameDeathDifferentId));
        assertEquals(List.of(original), repository.history(PLAYER_ID));
        assertEquals(original, repository.character(characterId).orElseThrow());
    }

    @Test
    void malformedFilesAreQuarantinedFromTheLoadedState() throws Exception {
        CharacterHistoryRepository writer = repository();
        CharacterRecord valid = deceased(UUID.randomUUID(), 100L, "Valid");
        writer.archive(valid);

        Path ownerDirectory = temporaryDirectory.resolve("history").resolve(PLAYER_ID.toString());
        Files.writeString(ownerDirectory.resolve("broken.yml"),
                "schema-version: 1\ncharacter: [not valid yaml\n");
        Path wrongOwner = temporaryDirectory.resolve("history")
                .resolve(UUID.randomUUID().toString());
        Files.createDirectories(wrongOwner);
        Files.copy(ownerDirectory.resolve("100.yml"), wrongOwner.resolve("wrong-owner.yml"));
        Path nonOwner = temporaryDirectory.resolve("history").resolve("not-a-uuid");
        Files.createDirectories(nonOwner);
        Files.writeString(nonOwner.resolve("ignored.yml"), "anything: true\n");

        CharacterHistoryRepository loaded = repository();
        loaded.load();

        assertEquals(List.of(valid), loaded.history(PLAYER_ID));
        assertEquals(List.of(valid), List.copyOf(loaded.all()));
        assertFalse(loaded.character(UUID.randomUUID()).isPresent());
    }

    @Test
    void duplicateCharacterArchiveDoesNotPartiallyEnterThePlayerIndex() throws Exception {
        CharacterHistoryRepository writer = repository();
        CharacterRecord valid = deceased(UUID.randomUUID(), 100L, "Valid");
        writer.archive(valid);

        Path ownerDirectory = temporaryDirectory.resolve("history").resolve(PLAYER_ID.toString());
        String duplicate = Files.readString(ownerDirectory.resolve("100.yml"))
                .replace("ended-at: 100", "ended-at: 200")
                .replace("declared-at: 90", "declared-at: 190")
                .replace("name: Valid", "name: Conflicting");
        Files.writeString(ownerDirectory.resolve("200.yml"), duplicate);

        CharacterHistoryRepository loaded = repository();
        loaded.load();

        assertEquals(1, loaded.history(PLAYER_ID).size());
        assertEquals(1, loaded.all().size());
        assertEquals(loaded.all(), loaded.history(PLAYER_ID));
        assertTrue(loaded.character(valid.characterId()).isPresent());
    }

    @Test
    void onlyDeceasedRecordsCanEnterHistory() {
        CharacterRecord active = new CharacterRecord(
                UUID.randomUUID(), PLAYER_ID, "Account", CharacterStatus.ACTIVE,
                1L, 0L, 0L, null, "", "Name", "Human", "Culture", 20,
                "Gender", "Religion");

        assertThrows(IllegalArgumentException.class, () -> repository().archive(active));
    }

    private CharacterHistoryRepository repository() {
        Logger logger = Logger.getLogger("character-history-test-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        return new CharacterHistoryRepository(temporaryDirectory, logger);
    }

    private static CharacterRecord deceased(UUID characterId, long endedAt, String name) {
        return new CharacterRecord(
                characterId,
                PLAYER_ID,
                "Account",
                CharacterStatus.DECEASED,
                1L,
                endedAt,
                Math.max(0L, endedAt - 10L),
                APPROVER_ID,
                "A public reason",
                name,
                "Human",
                "Northmarcher",
                30,
                "Unspecified",
                "None");
    }
}
