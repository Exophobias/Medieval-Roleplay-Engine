package dansplugins.rpsystem.cards;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterCardTest {

    private static final UUID PLAYER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CHARACTER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void sevenLineLegacyCardGainsMetadataAndIsMarkedForMigration() {
        List<String> legacy = List.of(
                PLAYER_ID.toString(),
                "Aldric of Bracken",
                "Human",
                "Northmarcher",
                "37",
                "Male",
                "The Kindled Flame");

        CharacterCard card = CharacterCard.fromLines(legacy, 12_345L);

        assertEquals(PLAYER_ID, card.getPlayerUUID());
        assertNotNull(card.getCharacterId());
        assertEquals(12_345L, card.getCreatedAt());
        assertEquals("", card.getLastKnownPlayerName());
        assertTrue(card.needsMigration());
        assertEquals(CharacterStatus.ACTIVE, card.snapshot().status());

        List<String> migrated = card.serializedLines();
        assertEquals(10, migrated.size());
        assertEquals(legacy, migrated.subList(0, 7));
        assertEquals(card.getCharacterId().toString(), migrated.get(7));
        assertEquals("12345", migrated.get(8));
        assertEquals("", migrated.get(9));

        card.markPersisted();
        assertFalse(card.needsMigration());
    }

    @Test
    void tenLineRoundTripPreservesStableIdentityAndSnapshot() {
        List<String> persisted = tenLines(
                PLAYER_ID, CHARACTER_ID, 42_000L, "AccountName", "Ysabet");

        CharacterCard first = CharacterCard.fromLines(persisted, 999L);
        CharacterCard second = CharacterCard.fromLines(first.serializedLines(), 123L);

        assertFalse(first.needsMigration());
        assertFalse(second.needsMigration());
        assertEquals(persisted, first.serializedLines());
        assertEquals(first.serializedLines(), second.serializedLines());
        assertEquals(first.snapshot(), second.snapshot());
        assertEquals(CHARACTER_ID, second.getCharacterId());
        assertEquals(42_000L, second.getCreatedAt());
        assertEquals("AccountName", second.getLastKnownPlayerName());
    }

    @Test
    void snapshotsDoNotChangeWhenTheMutableCardChanges() {
        CharacterCard card = CharacterCard.newDraft(PLAYER_ID, "AccountName", 100L);
        CharacterRecord draft = card.snapshot();

        assertEquals(CharacterStatus.DRAFT, draft.status());
        assertFalse(draft.isConfigured());

        card.setName("Elowen");
        card.setRace("Elf");
        card.setAge(81);
        CharacterRecord active = card.snapshot();

        assertEquals(CharacterCard.DEFAULT_NAME, draft.name());
        assertEquals(0, draft.age());
        assertEquals(CharacterStatus.ACTIVE, active.status());
        assertEquals("Elowen", active.name());
        assertEquals("Elf", active.race());
        assertEquals(81, active.age());
    }

    @Test
    void deceasedSnapshotKeepsItsIdentityWhenASeparateDraftIsCreated() {
        CharacterCard card = CharacterCard.fromLines(
                tenLines(PLAYER_ID, CHARACTER_ID, 50L, "AccountName", "The Old King"), 0L);
        UUID approver = UUID.fromString("33333333-3333-3333-3333-333333333333");

        CharacterRecord deceased = card.deceasedSnapshot(100L, 200L, approver,
                "  Fell at the ford\nwith witnesses  ");
        CharacterRecord replacement = CharacterCard.newDraft(
                PLAYER_ID, "AccountName", 200L).snapshot();

        assertEquals(CharacterStatus.DECEASED, deceased.status());
        assertEquals(CHARACTER_ID, deceased.characterId());
        assertEquals("The Old King", deceased.name());
        assertEquals(200L, deceased.endedAt());
        assertEquals(100L, deceased.deathDeclaredAt());
        assertEquals(approver, deceased.deathApprovedBy());
        assertEquals("Fell at the ford with witnesses", deceased.endReason());

        assertEquals(CharacterStatus.DRAFT, replacement.status());
        assertNotEquals(CHARACTER_ID, replacement.characterId());
        assertEquals(200L, replacement.createdAt());
        assertEquals(CharacterCard.DEFAULT_NAME, replacement.name());
    }

    @Test
    void invalidInputIsRejectedAndTextIsBoundedToOneLine() {
        CharacterCard card = CharacterCard.newDraft(PLAYER_ID, " Account\r\nName ", -10L);
        card.setName("  A\r\nB\0C  ");
        card.setRace("x".repeat(200));

        assertEquals(0L, card.getCreatedAt());
        assertEquals("Account  Name", card.getLastKnownPlayerName());
        assertEquals("A  B C", card.getName());
        assertEquals(128, card.getRace().length());
        assertThrows(IllegalArgumentException.class, () -> card.setAge(-1));
        assertThrows(IllegalArgumentException.class,
                () -> CharacterCard.fromLines(List.of("too", "short"), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> CharacterCard.fromLines(List.of(
                        "not-a-uuid", "n", "r", "s", "1", "g", "f"), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> card.deceasedSnapshot(201L, 200L, UUID.randomUUID(), "reason"));
        assertThrows(IllegalArgumentException.class,
                () -> card.deceasedSnapshot(0L, 0L, UUID.randomUUID(), "reason"));
    }

    static List<String> tenLines(UUID playerId, UUID characterId, long createdAt,
                                 String accountName, String characterName) {
        return List.of(
                playerId.toString(),
                characterName,
                "Human",
                "Northmarcher",
                "37",
                "Female",
                "The Kindled Flame",
                characterId.toString(),
                Long.toString(createdAt),
                accountName);
    }
}
