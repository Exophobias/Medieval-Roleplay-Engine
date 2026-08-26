package dansplugins.rpsystem.api.event;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterEventTest {

    private static final UUID PLAYER = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PLAYER = UUID.fromString(
            "10000000-0000-0000-0000-000000000002");
    private static final UUID FIRST_CHARACTER = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_CHARACTER = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");
    private static final UUID APPROVER = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");

    @Test
    void createAndUpdateEventsRejectImpossibleIdentityChanges() {
        CharacterRecord draft = current(FIRST_CHARACTER, PLAYER, CharacterStatus.DRAFT, 100L);
        CharacterRecord active = current(FIRST_CHARACTER, PLAYER, CharacterStatus.ACTIVE, 100L);

        assertDoesNotThrow(() -> new CharacterCreatedEvent(draft));
        assertDoesNotThrow(() -> new CharacterUpdatedEvent(draft, active));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterUpdatedEvent(draft,
                        current(SECOND_CHARACTER, PLAYER, CharacterStatus.ACTIVE, 100L)));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterUpdatedEvent(draft,
                        current(FIRST_CHARACTER, OTHER_PLAYER, CharacterStatus.ACTIVE, 100L)));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterCreatedEvent(ended(FIRST_CHARACTER, PLAYER, 100L, 200L)));
    }

    @Test
    void endedEventRequiresASeparateSameOwnerReplacementDraft() {
        CharacterRecord ended = ended(FIRST_CHARACTER, PLAYER, 100L, 200L);
        CharacterRecord replacement = current(
                SECOND_CHARACTER, PLAYER, CharacterStatus.DRAFT, 200L);

        assertDoesNotThrow(() -> new CharacterEndedEvent(ended, replacement));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterEndedEvent(ended,
                        current(FIRST_CHARACTER, PLAYER, CharacterStatus.DRAFT, 200L)));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterEndedEvent(ended,
                        current(SECOND_CHARACTER, OTHER_PLAYER, CharacterStatus.DRAFT, 200L)));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterEndedEvent(ended,
                        current(SECOND_CHARACTER, PLAYER, CharacterStatus.ACTIVE, 200L)));
    }

    private static CharacterRecord current(UUID character, UUID player, CharacterStatus status,
                                           long createdAt) {
        return new CharacterRecord(character, player, "Account", status, createdAt,
                0L, 0L, null, "", status == CharacterStatus.DRAFT ? "defaultName" : "Alys",
                "Human", "Valorian", 30, "Woman", "");
    }

    private static CharacterRecord ended(UUID character, UUID player, long createdAt,
                                         long endedAt) {
        return new CharacterRecord(character, player, "Account", CharacterStatus.DECEASED,
                createdAt, endedAt, endedAt, APPROVER, "", "Alys", "Human", "Valorian",
                30, "Woman", "");
    }
}
