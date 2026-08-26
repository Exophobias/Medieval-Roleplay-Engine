package dansplugins.rpsystem.integrations.truedeath;

import dansplugins.rpsystem.cards.CharacterServiceImpl.TrueDeathContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrueDeathIntegrationTest {

    private static final UUID PLAYER = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID APPROVER = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");

    @Test
    void twoUnarchivedDurableDeathsAreBlockedInsteadOfGuessed() {
        TrueDeathContext older = death(2_000L);
        TrueDeathContext newer = death(3_000L);

        TrueDeathIntegration.ReconciliationPlan plan =
                TrueDeathIntegration.planForPlayer(List.of(newer, older), ignored -> false);

        assertTrue(plan.blocked());
        assertEquals(2L, plan.unresolved());
        assertEquals(List.of(older, newer), plan.deaths());
    }

    @Test
    void anArchivedOlderDeathLeavesOneProvableTransition() {
        TrueDeathContext older = death(2_000L);
        TrueDeathContext newer = death(3_000L);

        TrueDeathIntegration.ReconciliationPlan plan =
                TrueDeathIntegration.planForPlayer(
                        List.of(newer, older), candidate -> candidate.equals(older));

        assertFalse(plan.blocked());
        assertEquals(1L, plan.unresolved());
        assertEquals(List.of(older, newer), plan.deaths());
    }

    private static TrueDeathContext death(long approvedAt) {
        return new TrueDeathContext(
                PLAYER, approvedAt - 100L, approvedAt, APPROVER, "Reason");
    }
}
