package dansplugins.rpsystem.welcome;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;
import dansplugins.rpsystem.commands.help.HelpCommand;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WelcomeFeatureProviderTest {
    private final UUID self = UUID.randomUUID();
    private Player player(Set<String> grants) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
            if (method.getName().equals("getUniqueId")) return self;
            if (method.getName().equals("hasPermission")) return grants.contains(args[0].toString());
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
    }
    private CharacterRecord card(UUID player) {
        return new CharacterRecord(UUID.randomUUID(), player, "Player", CharacterStatus.ACTIVE, 1, 0, 0, null, "",
                "Current name", "", "Nationality", "Subculture", 22, "", "",
                "Appearance", "Calling", "Origin", "Mannerisms", "Goal", "Private self story", false);
    }
    @Test void selfStoryAndCurrentFieldsFollowActualCardPermissionOnEachProjection() {
        var grants = new HashSet<>(Set.of("rp.card.show", "rp.card.name"));
        Player viewer = player(grants);
        var helper = new HelpCommand(null);
        var first = WelcomeFeatureProvider.project(viewer, card(self), helper.permitted(viewer, true), false);
        assertTrue(first.sections().stream().anyMatch(section -> section.paragraphs().contains("Private self story")));
        assertTrue(first.help().stream().anyMatch(entry -> entry.command().startsWith("card name")));
        assertFalse(first.help().stream().anyMatch(entry -> entry.staff() || entry.command().startsWith("card lookup")));
        grants.remove("rp.card.show");
        var revoked = WelcomeFeatureProvider.project(viewer, card(self), first.help().stream().map(entry ->
                new HelpCommand.Entry("/" + entry.command(), entry.description(), "rp.card.show", false)).toList(), false);
        assertTrue(revoked.sections().isEmpty());
        assertTrue(revoked.actions().isEmpty());
        assertTrue(revoked.help().isEmpty());
    }
    @Test void foreignCardsCannotBeProjectedAndMissingCardsAreHonest() {
        Player viewer = player(Set.of("rp.card.*"));
        assertThrows(IllegalArgumentException.class, () -> WelcomeFeatureProvider.project(viewer, card(UUID.randomUUID()), List.of(), false));
        var missing = WelcomeFeatureProvider.project(viewer, null, new HelpCommand(null).permitted(viewer, true), false);
        assertEquals("No current character card", missing.rows().getFirst().value());
        assertTrue(missing.sections().isEmpty());
        assertTrue(missing.help().stream().allMatch(entry -> entry.permission().equals("rp.card.*")));
    }
}
