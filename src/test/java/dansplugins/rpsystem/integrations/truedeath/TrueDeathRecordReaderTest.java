package dansplugins.rpsystem.integrations.truedeath;

import dansplugins.rpsystem.cards.CharacterServiceImpl.TrueDeathContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrueDeathRecordReaderTest {

    private static final UUID PLAYER_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID APPROVER_ID =
            UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Test
    void readsTheActualRecordShapedContractWithoutAUtilsDependency() throws Exception {
        FakeDeath death = new FakeDeath(PLAYER_ID, 100L, 200L, APPROVER_ID, "A public reason");

        TrueDeathContext context = TrueDeathRecordReader.fromRecord(death);

        assertEquals(PLAYER_ID, context.playerId());
        assertEquals(100L, context.declaredAt());
        assertEquals(200L, context.approvedAt());
        assertEquals(APPROVER_ID, context.approvedBy());
        assertEquals("A public reason", context.reason());
    }

    @Test
    void readsAnEventAndOptionalReasonCompatibilityShape() throws Exception {
        OptionalReasonDeath death = new OptionalReasonDeath(
                PLAYER_ID, 100, 200, APPROVER_ID, Optional.of("Optional reason"));

        TrueDeathContext context = TrueDeathRecordReader.fromEvent(new FakeEvent(death));

        assertEquals("Optional reason", context.reason());
        assertEquals(100L, context.declaredAt());
        assertEquals(200L, context.approvedAt());

        OptionalReasonDeath noReason = new OptionalReasonDeath(
                PLAYER_ID, 100, 200, APPROVER_ID, Optional.empty());
        assertEquals("", TrueDeathRecordReader.fromRecord(noReason).reason());
    }

    @Test
    void malformedOrThrowingShapesFailClosed() {
        assertThrows(ReflectiveOperationException.class,
                () -> TrueDeathRecordReader.fromRecord(new BadTimestampDeath()));
        assertThrows(ReflectiveOperationException.class,
                () -> TrueDeathRecordReader.fromRecord(new MissingReasonDeath()));

        ReflectiveOperationException failure = assertThrows(ReflectiveOperationException.class,
                () -> TrueDeathRecordReader.fromRecord(new ThrowingReasonDeath()));
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertThrows(ReflectiveOperationException.class,
                () -> TrueDeathRecordReader.fromEvent(null));
    }

    public record FakeDeath(UUID player, long declaredAt, long approvedAt, UUID approvedBy,
                            String reason) {
    }

    public record OptionalReasonDeath(UUID player, int declaredAt, int approvedAt, UUID approvedBy,
                                      Optional<String> reason) {
    }

    public record FakeEvent(Object death) {
    }

    public static final class BadTimestampDeath {
        public UUID player() {
            return PLAYER_ID;
        }

        public String declaredAt() {
            return "yesterday";
        }

        public long approvedAt() {
            return 200L;
        }

        public UUID approvedBy() {
            return APPROVER_ID;
        }

        public String reason() {
            return "reason";
        }
    }

    public static final class MissingReasonDeath {
        public UUID player() {
            return PLAYER_ID;
        }

        public long declaredAt() {
            return 100L;
        }

        public long approvedAt() {
            return 200L;
        }

        public UUID approvedBy() {
            return APPROVER_ID;
        }
    }

    public static final class ThrowingReasonDeath {
        public UUID player() {
            return PLAYER_ID;
        }

        public long declaredAt() {
            return 100L;
        }

        public long approvedAt() {
            return 200L;
        }

        public UUID approvedBy() {
            return APPROVER_ID;
        }

        public String reason() {
            throw new IllegalStateException("broken record");
        }
    }
}
