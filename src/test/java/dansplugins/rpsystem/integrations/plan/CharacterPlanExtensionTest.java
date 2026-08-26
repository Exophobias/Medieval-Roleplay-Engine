package dansplugins.rpsystem.integrations.plan;

import com.djrapitops.plan.extension.extractor.ExtensionExtractor;
import com.djrapitops.plan.extension.table.Table;
import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterService;
import dansplugins.rpsystem.api.CharacterStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterPlanExtensionTest {

    private static final UUID ACTIVE_PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DRAFT_PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID APPROVER = UUID.fromString("10000000-0000-0000-0000-000000000099");

    private static final String PRIVATE_RELIGION = "THE HIDDEN RELIGION";
    private static final String PRIVATE_END_REASON = "PRIVATE STAFF DEATH REASON";

    @Test
    void extensionSatisfiesPlan57AnnotationContract() {
        CharacterPlanExtension extension = extension(records());

        assertDoesNotThrow(() -> new ExtensionExtractor(extension).validateAnnotations());
    }

    @Test
    void draftsAndPrivateFieldsNeverReachAnyPlanTable() {
        CharacterPlanExtension extension = extension(records());

        assertEquals("None", extension.currentCharacterName(DRAFT_PLAYER));
        assertTrue(extension.characterHistory(DRAFT_PLAYER).getRows().isEmpty());

        String serverData = flattened(extension.currentCharacterOverview());
        String playerHistory = flattened(extension.characterHistory(ACTIVE_PLAYER));
        String rendered = serverData + "\n" + playerHistory;

        assertTrue(serverData.contains("PlayerOne"));
        assertTrue(serverData.contains("Aldric"));
        assertFalse(serverData.contains("defaultName"));
        assertFalse(serverData.contains("DraftOwner"));

        assertTrue(playerHistory.contains("Edric"));
        assertTrue(playerHistory.contains("Deceased"));
        assertFalse(rendered.contains(PRIVATE_RELIGION));
        assertFalse(rendered.contains(PRIVATE_END_REASON));
        assertFalse(rendered.contains(APPROVER.toString()));
        for (CharacterRecord record : records()) {
            assertFalse(rendered.contains(record.characterId().toString()));
        }
    }

    @Test
    void currentAndHistorySurfacesContainOnlyConfiguredPublicCharacters() {
        CharacterPlanExtension extension = extension(records());

        assertTrue(extension.hasActiveCharacter(ACTIVE_PLAYER));
        assertFalse(extension.hasActiveCharacter(DRAFT_PLAYER));
        assertEquals("Aldric", extension.currentCharacterName(ACTIVE_PLAYER));
        assertEquals("Human", extension.currentRace(ACTIVE_PLAYER));
        assertEquals("Northman", extension.currentSubculture(ACTIVE_PLAYER));
        assertEquals(34L, extension.currentAge(ACTIVE_PLAYER));
        assertEquals("Man", extension.currentGender(ACTIVE_PLAYER));
        assertEquals(1L, extension.activeCharacterCount());

        List<Object[]> historyRows = extension.characterHistory(ACTIVE_PLAYER).getRows();
        assertEquals(1, historyRows.size());
        Object[] row = historyRows.getFirst();
        assertEquals("Edric", row[0]);
        assertEquals("Human / Northman", row[1]);
        assertEquals("Deceased", row[2]);
        assertEquals(2_000L, row[3]);
        assertNull(row[4]);
    }

    private static CharacterPlanExtension extension(List<CharacterRecord> records) {
        return new CharacterPlanExtension(new PlanCharacterView(new FakeCharacterService(records)));
    }

    private static List<CharacterRecord> records() {
        CharacterRecord active = new CharacterRecord(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                ACTIVE_PLAYER,
                "PlayerOne",
                CharacterStatus.ACTIVE,
                1_000L,
                0L,
                0L,
                null,
                "",
                "Aldric",
                "Human",
                "Northman",
                34,
                "Man",
                PRIVATE_RELIGION);
        CharacterRecord deceased = new CharacterRecord(
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                ACTIVE_PLAYER,
                "PlayerOne",
                CharacterStatus.DECEASED,
                100L,
                2_000L,
                1_900L,
                APPROVER,
                PRIVATE_END_REASON,
                "Edric",
                "Human",
                "Northman",
                62,
                "Man",
                PRIVATE_RELIGION);
        CharacterRecord draft = new CharacterRecord(
                UUID.fromString("20000000-0000-0000-0000-000000000003"),
                DRAFT_PLAYER,
                "DraftOwner",
                CharacterStatus.DRAFT,
                3_000L,
                0L,
                0L,
                null,
                "",
                "defaultName",
                "defaultRace",
                "defaultSubculture",
                0,
                "defaultGender",
                PRIVATE_RELIGION);
        return List.of(active, deceased, draft);
    }

    private static String flattened(Table table) {
        StringBuilder flattened = new StringBuilder();
        for (Object[] row : table.getRows()) {
            for (Object value : row) {
                flattened.append(value).append('\n');
            }
        }
        return flattened.toString();
    }

    private static final class FakeCharacterService implements CharacterService {

        private final List<CharacterRecord> records;

        private FakeCharacterService(List<CharacterRecord> records) {
            this.records = List.copyOf(records);
        }

        @Override
        public Optional<CharacterRecord> currentCharacter(UUID playerId) {
            return records.stream()
                    .filter(record -> record.playerId().equals(playerId) && record.isCurrent())
                    .findFirst();
        }

        @Override
        public Optional<CharacterRecord> character(UUID characterId) {
            return records.stream()
                    .filter(record -> record.characterId().equals(characterId))
                    .findFirst();
        }

        @Override
        public List<CharacterRecord> characters(UUID playerId) {
            ArrayList<CharacterRecord> playerRecords = new ArrayList<>();
            currentCharacter(playerId).ifPresent(playerRecords::add);
            records.stream()
                    .filter(record -> record.playerId().equals(playerId) && !record.isCurrent())
                    .forEach(playerRecords::add);
            return List.copyOf(playerRecords);
        }

        @Override
        public Collection<CharacterRecord> currentCharacters() {
            return records.stream().filter(CharacterRecord::isCurrent).toList();
        }

        @Override
        public Collection<CharacterRecord> endedCharacters() {
            return records.stream().filter(record -> !record.isCurrent()).toList();
        }
    }
}
