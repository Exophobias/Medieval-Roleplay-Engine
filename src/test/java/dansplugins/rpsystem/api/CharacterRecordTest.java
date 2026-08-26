package dansplugins.rpsystem.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterRecordTest {

    private static final UUID CHARACTER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID APPROVER_ID =
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void currentAndEndedConvenienceViewsReflectTheLifecycleState() {
        CharacterRecord draft = record(CharacterStatus.DRAFT, 0L, 0L, null, "");
        CharacterRecord deceased = record(
                CharacterStatus.DECEASED, 200L, 100L, APPROVER_ID, "Old age");

        assertTrue(draft.isCurrent());
        assertFalse(draft.isConfigured());
        assertTrue(draft.endedAtOptional().isEmpty());
        assertTrue(draft.deathDeclaredAtOptional().isEmpty());
        assertTrue(draft.deathApprovedByOptional().isEmpty());

        assertFalse(deceased.isCurrent());
        assertTrue(deceased.isConfigured());
        assertTrue(deceased.isPubliclyVisible());
        assertEquals(200L, deceased.endedAtOptional().orElseThrow());
        assertEquals(100L, deceased.deathDeclaredAtOptional().orElseThrow());
        assertEquals(APPROVER_ID, deceased.deathApprovedByOptional().orElseThrow());
        assertEquals("Old age", deceased.endReasonOptional().orElseThrow());
    }

    @Test
    void lifecycleTimestampShapeIsValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> record(CharacterStatus.ACTIVE, 1L, 0L, null, ""));
        assertThrows(IllegalArgumentException.class,
                () -> record(CharacterStatus.DECEASED, 0L, 0L, APPROVER_ID, "reason"));
        assertThrows(IllegalArgumentException.class,
                () -> record(CharacterStatus.DECEASED, 100L, 101L, APPROVER_ID, "reason"));
    }

    @Test
    void deceasedRecordRequiresAnApprover() {
        assertThrows(NullPointerException.class,
                () -> record(CharacterStatus.DECEASED, 100L, 50L, null, "reason"));
    }

    @Test
    void recordRejectsNegativeAgeForCurrentAndEndedCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterRecord(
                        CHARACTER_ID, PLAYER_ID, "Account", CharacterStatus.ACTIVE,
                        1L, 0L, 0L, null, "", "Name", "Race", "Culture",
                        -1, "Gender", "Religion"));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterRecord(
                        CHARACTER_ID, PLAYER_ID, "Account", CharacterStatus.DECEASED,
                        1L, 2L, 1L, APPROVER_ID, "reason", "Name", "Race", "Culture",
                        -1, "Gender", "Religion"));
    }

    @Test
    void publicViewRequiresCompleteBoundedAndChronologicalData() {
        CharacterRecord complete = record(CharacterStatus.ACTIVE, 0L, 0L, null, "");
        assertTrue(complete.isPubliclyVisible());

        assertFalse(withPublicFields(complete, "defaultRace", "Culture", 30, "Gender")
                .isPubliclyVisible());
        assertFalse(withPublicFields(complete, "Race", "defaultSubculture", 30, "Gender")
                .isPubliclyVisible());
        assertFalse(withPublicFields(complete, "Race", "Culture", 30, "defaultGender")
                .isPubliclyVisible());
        assertFalse(withPublicFields(complete, "Race", "Culture", 1_000_001, "Gender")
                .isPubliclyVisible());

        CharacterRecord impossibleHistory = new CharacterRecord(
                CHARACTER_ID, PLAYER_ID, "Account", CharacterStatus.RETIRED,
                200L, 100L, 0L, null, "", "Character", "Race", "Culture",
                30, "Gender", "Religion");
        assertFalse(impossibleHistory.isPubliclyVisible());
    }

    private static CharacterRecord withPublicFields(CharacterRecord source, String race,
                                                     String subculture, int age, String gender) {
        return new CharacterRecord(
                source.characterId(), source.playerId(), source.lastKnownPlayerName(),
                source.status(), source.createdAt(), source.endedAt(), source.deathDeclaredAt(),
                source.deathApprovedBy(), source.endReason(), source.name(), race, subculture,
                age, gender, source.religion());
    }

    private static CharacterRecord record(CharacterStatus status, long endedAt, long declaredAt,
                                          UUID approvedBy, String reason) {
        return new CharacterRecord(
                CHARACTER_ID,
                PLAYER_ID,
                "Account",
                status,
                1L,
                endedAt,
                declaredAt,
                approvedBy,
                reason,
                status == CharacterStatus.DRAFT ? "defaultName" : "Character",
                "Human",
                "Northmarcher",
                30,
                "Unspecified",
                "None");
    }
}
