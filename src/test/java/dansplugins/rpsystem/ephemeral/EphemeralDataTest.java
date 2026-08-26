package dansplugins.rpsystem.ephemeral;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EphemeralDataTest {

    @Test
    void quitCleanupRetainsTimedNameCooldownButClearsSessionState() {
        UUID player = UUID.randomUUID();
        EphemeralData data = new EphemeralData();
        data.getPlayersOnNameChangeCooldown().add(player);
        data.getPlayersWithRightClickCooldown().add(player);
        data.getPlayersSpeakingInLocalChat().add(player);
        data.getPlayersWhoHaveHiddenLocalOOCChat().add(player);

        data.clearSession(player);

        assertTrue(data.getPlayersOnNameChangeCooldown().contains(player));
        assertFalse(data.getPlayersWithRightClickCooldown().contains(player));
        assertFalse(data.getPlayersSpeakingInLocalChat().contains(player));
        assertFalse(data.getPlayersWhoHaveHiddenLocalOOCChat().contains(player));
    }
}
