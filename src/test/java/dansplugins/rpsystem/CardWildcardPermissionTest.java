package dansplugins.rpsystem;

import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Upstream permission-resolution regression, adapted to Patriam's existing descriptor policy. */
class CardWildcardPermissionTest {
    private static final String[] PLAYER_CARD_NODES = {
            "rp.card.show", "rp.card.lookup", "rp.card.help", "rp.card.name", "rp.card.nationality",
            "rp.card.subculture", "rp.card.religion", "rp.card.age", "rp.card.gender"
    };
    private static Plugin plugin;
    private boolean op;
    private PermissibleBase permissible;
    @BeforeAll static void registerPluginPermissions() {
        BukkitTestServer.installWithPluginYmlPermissions();
        plugin = BukkitTestServer.enabledPlugin();
    }
    @BeforeEach void createNonOperator() {
        op = false;
        permissible = new PermissibleBase(new ServerOperator() {
            public boolean isOp() { return op; }
            public void setOp(boolean value) { op = value; }
        });
    }
    @Test void ordinaryDefaultsDoNotGrantAdministrativeCardOperations() {
        for (String node : PLAYER_CARD_NODES) assertTrue(permissible.hasPermission(node), node);
        assertFalse(permissible.hasPermission("rp.card.*"));
        assertFalse(permissible.hasPermission("rp.card.forcesave"));
        assertFalse(permissible.hasPermission("rp.card.forceload"));
    }
    @Test void operatorsHaveAdministrativeDefaultsWithoutAnImplicitWildcard() {
        permissible.setOp(true);
        assertFalse(permissible.hasPermission("rp.card.*"));
        assertTrue(permissible.hasPermission("rp.card.forcesave"));
        assertTrue(permissible.hasPermission("rp.card.forceload"));
    }
    @Test void explicitlyGrantedWildcardRestoresARevokedPlayerCardNode() {
        permissible.addAttachment(plugin).setPermission("rp.card.name", false);
        assertFalse(permissible.hasPermission("rp.card.name"));
        permissible.addAttachment(plugin).setPermission("rp.card.*", true);
        for (String node : PLAYER_CARD_NODES) assertTrue(permissible.hasPermission(node), node);
    }
    @Test void revokingWildcardRevokesAllOrdinaryCardNodes() {
        permissible.addAttachment(plugin).setPermission("rp.card.*", false);
        for (String node : PLAYER_CARD_NODES) assertFalse(permissible.hasPermission(node), node);
    }
    @Test void grantedWildcardCannotReachAdministrativeCardNodes() {
        permissible.addAttachment(plugin).setPermission("rp.card.*", true);
        assertFalse(permissible.hasPermission("rp.card.forcesave"));
        assertFalse(permissible.hasPermission("rp.card.forceload"));
    }
}
