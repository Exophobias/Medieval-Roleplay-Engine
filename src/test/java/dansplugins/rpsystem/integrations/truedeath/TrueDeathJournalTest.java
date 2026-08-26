package dansplugins.rpsystem.integrations.truedeath;

import dansplugins.rpsystem.cards.CharacterServiceImpl.TrueDeathContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrueDeathJournalTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void compositeIdentityDoesNotCollapseDeathsAtTheSameApprovalTimestamp() throws Exception {
        UUID player = UUID.randomUUID();
        long approvedAt = 10_000L;
        TrueDeathContext first = new TrueDeathContext(player, 9_000L, approvedAt,
                UUID.randomUUID(), "first");
        TrueDeathContext second = new TrueDeathContext(player, 9_500L, approvedAt,
                UUID.randomUUID(), "second");

        TrueDeathJournal journal = new TrueDeathJournal(temporaryDirectory);
        assertEquals(TrueDeathJournal.LoadResult.NEW, journal.load());
        journal.establishBaseline(List.of(first));
        assertTrue(journal.processed(first));
        assertFalse(journal.processed(second));

        journal.markProcessed(second);
        TrueDeathJournal reloaded = new TrueDeathJournal(temporaryDirectory);
        assertEquals(TrueDeathJournal.LoadResult.READY, reloaded.load());
        assertTrue(reloaded.processed(first));
        assertTrue(reloaded.processed(second));
    }

    @Test
    void invalidStateFailsClosed() throws Exception {
        Files.writeString(temporaryDirectory.resolve("true-death-state.yml"), """
                schema-version: 1
                initialized: true
                processed-deaths:
                  - not-a-death
                """);

        assertEquals(TrueDeathJournal.LoadResult.INVALID,
                new TrueDeathJournal(temporaryDirectory).load());
    }

    @Test
    void missingOrWronglyTypedIdentityListFailsClosed() throws Exception {
        Path state = temporaryDirectory.resolve("true-death-state.yml");
        Files.writeString(state, "schema-version: 1\ninitialized: true\n");
        assertEquals(TrueDeathJournal.LoadResult.INVALID,
                new TrueDeathJournal(temporaryDirectory).load());

        Files.writeString(state, """
                schema-version: 1
                initialized: true
                processed-deaths: not-a-list
                """);
        assertEquals(TrueDeathJournal.LoadResult.INVALID,
                new TrueDeathJournal(temporaryDirectory).load());
    }
}
