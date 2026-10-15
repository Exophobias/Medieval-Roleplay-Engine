package dansplugins.rpsystem.commands.help;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.List;

public class HelpCommand {
    private final MedievalRoleplayEngine plugin;
    public HelpCommand(MedievalRoleplayEngine plugin) { this.plugin = plugin; }
    private record Entry(String form, String description, String permission, boolean staff) {}

    private List<Entry> permitted(CommandSender sender, boolean cards) {
        List<Entry> entries = new ArrayList<>();
        if (!cards) {
            entries.add(new Entry("/rphelp [page]", "show permitted roleplay commands", "rp.help", false));
            entries.add(new Entry("/roll [dice]", "roll dice, for example 2d6+3", "rp.roll", false));
            entries.add(new Entry("/emote <action>", "describe a nearby character action", "rp.emote", false));
        }
        entries.add(new Entry("/card", "view your character card", "rp.card.show", false));
        entries.add(new Entry("/card lookup <player>", "view another character card", "rp.card.lookup", false));
        entries.add(new Entry("/card name <name>", "change your character name", "rp.card.name", false));
        entries.add(new Entry("/card nationality <nationality|clear>", "set or clear nationality", "rp.card.nationality", false));
        entries.add(new Entry("/card subculture <subculture>", "change subculture", "rp.card.subculture", false));
        entries.add(new Entry("/card age <age>", "change age", "rp.card.age", false));
        entries.add(new Entry("/card gender <sex/gender|clear>", "set or clear sex/gender", "rp.card.gender", false));
        if (plugin != null && plugin.getConfig().getBoolean("legacyReligionFieldEnabled", false))
            entries.add(new Entry("/card religion <religion>", "change the legacy biography religion field", "rp.card.religion", false));
        entries.add(new Entry("/card forcesave", "save current cards", "rp.card.forcesave", true));
        entries.add(new Entry("/card forceload", "reload current cards", "rp.card.forceload", true));
        if (!cards) {
            entries.add(new Entry("/rpconfig show", "view operator configuration", "rp.config", true));
            entries.add(new Entry("/rpconfig set <option> <value>", "change operator configuration", "rp.config", true));
        }
        return entries.stream().filter(entry -> allows(sender, entry.permission())).toList();
    }

    public static boolean allows(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        if (permission.equals("rp.help")) return sender.hasPermission("rp.rphelp") || sender.hasPermission("rp.default");
        if (permission.equals("rp.roll")) return sender.hasPermission("rp.dice") || sender.hasPermission("rp.default");
        if (permission.equals("rp.emote")) return sender.hasPermission("rp.me") || sender.hasPermission("rp.default");
        if (permission.equals("rp.config") || permission.equals("rp.card.forcesave") || permission.equals("rp.card.forceload"))
            return sender.hasPermission("rp.admin");
        return permission.startsWith("rp.card.") && (sender.hasPermission("rp.card.*") || sender.hasPermission("rp.default"));
    }

    public void showListOfCommands(CommandSender sender) { show(sender, new String[0], false); }
    public boolean hasStaff(CommandSender sender, boolean cards) {
        return permitted(sender, cards).stream().anyMatch(Entry::staff);
    }
    public List<String> suggestions(CommandSender sender, boolean cards, String prefix) {
        return permitted(sender, cards).stream().map(entry -> entry.form().split(" ").length > (cards ? 1 : 0) ? entry.form().split(" ")[cards ? 1 : 0] : "help")
                .filter(form -> !cards || !form.startsWith("/"))
                .map(form -> cards ? form : form.substring(1))
                .filter(form -> form.startsWith(prefix.toLowerCase(java.util.Locale.ROOT))).distinct().toList();
    }
    public void show(CommandSender sender, String[] args, boolean cards) {
        List<Entry> visible = permitted(sender, cards);
        List<Entry> ordinary = visible.stream().filter(entry -> !entry.staff()).toList();
        List<Entry> staff = visible.stream().filter(Entry::staff).toList();
        boolean directStaff = args.length > 0 && args[0].equalsIgnoreCase("staff");
        if (visible.isEmpty() || (directStaff && staff.isEmpty())) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use these commands.");
            return;
        }
        int ordinaryPages = (ordinary.size() + 7) / 8;
        int total = ordinaryPages + (staff.size() + 7) / 8;
        int requested = 1;
        try { if (args.length > (directStaff ? 1 : 0)) requested = Integer.parseInt(args[directStaff ? 1 : 0]); }
        catch (NumberFormatException ignored) { }
        int page = Math.max(1, Math.min(total, directStaff ? ordinaryPages + Math.max(1, requested) : requested));
        boolean staffPage = page > ordinaryPages;
        List<Entry> section = staffPage ? staff : ordinary;
        int offset = ((staffPage ? page - ordinaryPages : page) - 1) * 8;
        String root = cards ? "/card help" : "/rphelp";
        sender.sendMessage(ChatColor.DARK_GRAY + "  ---------- " + ChatColor.GREEN + "MedievalRoleplayEngine"
                + (staffPage ? " Staff" : "") + (total > 1 ? ChatColor.DARK_GRAY + " (" + ChatColor.WHITE
                + page + ChatColor.DARK_GRAY + "/" + ChatColor.WHITE + total + ChatColor.DARK_GRAY + ")" : "")
                + ChatColor.DARK_GRAY + " ----------");
        for (Entry entry : section.subList(offset, Math.min(section.size(), offset + 8)))
            sender.sendMessage(ChatColor.GREEN + entry.form() + ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + entry.description());
        sender.sendMessage(ChatColor.DARK_GRAY + (page < total ? "  ---------- " + ChatColor.GREEN + root + " "
                + (page + 1) + ChatColor.GRAY + " for more" + ChatColor.DARK_GRAY + " ----------"
                : "  ----------------------------------------"));
    }
}
