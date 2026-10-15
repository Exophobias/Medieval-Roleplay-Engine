package dansplugins.rpsystem.commands;
import dansplugins.rpsystem.commands.help.HelpCommand;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HelpCommandTest {
    private static CommandSender sender(Set<String> grants, List<String> output) {
        return (CommandSender) java.lang.reflect.Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return grants.contains(args[0].toString());
                    if (method.getName().equals("sendMessage") && args[0] instanceof String line) { output.add(line); return null; }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
    }

    @Test void actualRootHandlerFiltersPartialPlayerPermissionsAndDeniesStaffHelp() {
        var output = new ArrayList<String>();
        var sender = sender(Set.of("rp.help", "rp.card.name"), output);
        new CommandService(null).interpretCommand(sender, "rphelp", new String[0]);
        assertTrue(String.join("\n", output).contains("/card name"));
        assertFalse(String.join("\n", output).contains("lookup"));
        assertFalse(String.join("\n", output).contains("rpconfig"));
        output.clear();
        new HelpCommand(null).show(sender, new String[]{"staff"}, false);
        assertEquals(1, output.size());
        assertTrue(output.getFirst().contains("permission"));
    }
    @Test void ordinaryAndPartialStaffPagesStaySeparateWithEightRowsAndClamping() {
        var output = new ArrayList<String>();
        var sender = sender(Set.of("rp.default", "rp.card.forcesave"), output);
        var help = new HelpCommand(null);
        help.show(sender, new String[]{"0"}, false);
        assertEquals(10, output.size());
        assertFalse(output.getFirst().contains("Staff"));
        assertFalse(String.join("\n", output).contains("forcesave"));
        output.clear();
        help.show(sender, new String[]{"99"}, false);
        assertTrue(output.getFirst().contains("Staff"));
        assertTrue(String.join("\n", output).contains("forcesave"));
        assertFalse(String.join("\n", output).contains("forceload"));
        assertFalse(output.getLast().contains("for more"));
        output.clear();
        help.show(sender, new String[]{"banana"}, false);
        assertEquals(10, output.size());
    }
    @Test void staffOnlyAndConsoleDocumentationRetainPermissionBoundaries() {
        var output = new ArrayList<String>();
        var sender = sender(Set.of("rp.card.forceload"), output);
        new HelpCommand(null).show(sender, new String[0], true);
        assertTrue(output.getFirst().contains("Staff"));
        assertEquals(3, output.size());
        assertEquals(List.of("forceload"), new HelpCommand(null).suggestions(sender, true, ""));
        assertFalse(HelpCommand.allows(sender, "rp.card.forcesave"));
    }
}
