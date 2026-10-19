package dansplugins.rpsystem.welcome;

import com.github.exophobias.patriamwelcome.api.HubFeature;
import com.github.exophobias.patriamwelcome.api.HubFeatureProvider;
import com.github.exophobias.patriamwelcome.api.HubFeatureView;
import dansplugins.rpsystem.MedievalRoleplayEngine;
import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.commands.help.HelpCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import java.util.ArrayList;
import java.util.List;

/** Self-only current card; no lookups of another player's biography or history. */
public final class WelcomeFeatureProvider implements HubFeatureProvider {
    private final MedievalRoleplayEngine owner;
    public WelcomeFeatureProvider(MedievalRoleplayEngine owner) { this.owner = owner; }
    public static AutoCloseable register(Plugin plugin) {
        var provider = new WelcomeFeatureProvider((MedievalRoleplayEngine) plugin);
        plugin.getServer().getServicesManager().register(HubFeatureProvider.class, provider, plugin, ServicePriority.Normal);
        return () -> plugin.getServer().getServicesManager().unregister(HubFeatureProvider.class, provider);
    }
    @Override public String key() { return "character"; }
    @Override public boolean availableTo(Player player) {
        return Bukkit.isPrimaryThread() && owner.featureReady() && player.isOnline()
                && owner.getServer().getPlayer(player.getUniqueId()) == player
                && !new HelpCommand(owner).permitted(player, true).isEmpty();
    }
    @Override public void open(Player player, HubFeatureView view) {
        if (!availableTo(player) || !view.active()) { view.unavailable("Character pages are unavailable."); return; }
        // currentCharacter is an immutable in-memory owner snapshot; this never creates a card.
        CharacterRecord card = HelpCommand.allows(player, "rp.card.show")
                ? owner.characterService.currentCharacter(player.getUniqueId()).orElse(null) : null;
        view.show(project(player, card, new HelpCommand(owner).permitted(player, true),
                owner.getConfig().getBoolean("legacyReligionFieldEnabled", false)));
    }
    static HubFeature project(Player player, CharacterRecord card, List<HelpCommand.Entry> help, boolean legacyReligion) {
        var rows = new ArrayList<HubFeature.Row>();
        var sections = new ArrayList<HubFeature.Section>();
        boolean readable = HelpCommand.allows(player, "rp.card.show");
        if (card != null && (!card.playerId().equals(player.getUniqueId()) || !card.isCurrent()))
            throw new IllegalArgumentException("A self current card is required");
        if (readable && card != null) {
            rows.add(new HubFeature.Row("Character", set(card.name())));
            rows.add(new HubFeature.Row("Status", card.status().name()));
            rows.add(new HubFeature.Row("Nationality", set(card.nationality())));
            rows.add(new HubFeature.Row("Subculture", set(card.subculture())));
            rows.add(new HubFeature.Row("Age", Integer.toString(card.age())));
            rows.add(new HubFeature.Row("Sex / gender", set(card.gender())));
            rows.add(new HubFeature.Row("Story visibility", card.showStoryPublicly() ? "Public" : "Private"));
            if (legacyReligion) rows.add(new HubFeature.Row("Legacy biography religion", set(card.religion())));
            story(sections, "Appearance", card.appearance()); story(sections, "Calling", card.calling());
            story(sections, "Origin", card.originDescription()); story(sections, "Mannerisms", card.mannerisms());
            story(sections, "Current goal", card.currentGoal()); story(sections, "Your backstory", card.backstory());
        } else rows.add(new HubFeature.Row("Current card", readable ? "No current character card" : "Card viewing is not permitted"));
        List<HubFeature.Help> entries = help.stream().filter(entry -> HelpCommand.allows(player, entry.permission()))
                .map(entry -> new HubFeature.Help(entry.form().substring(1), entry.description(),
                        actualPermission(player, entry.permission()), entry.staff())).toList();
        var actions = new ArrayList<HubFeature.Action>();
        if (readable) actions.add(new HubFeature.Action("View card", "Read your current card through its owner command", "card", actualPermission(player, "rp.card.show")));
        return new HubFeature("Character", "Your current character and permitted card commands. Parameterized commands below require their shown arguments.",
                rows, sections, entries, actions);
    }
    private static String actualPermission(Player player, String node) {
        if (player.hasPermission(node)) return node;
        if (node.startsWith("rp.card.") && player.hasPermission("rp.card.*")) return "rp.card.*";
        if (node.startsWith("rp.card.") && !node.equals("rp.card.forcesave") && !node.equals("rp.card.forceload") && player.hasPermission("rp.default")) return "rp.default";
        return "rp.admin";
    }
    private static String set(String value) { return value.isBlank() || value.startsWith("default") ? "Not set" : value; }
    private static void story(List<HubFeature.Section> sections, String title, String value) {
        if (!value.isBlank()) sections.add(new HubFeature.Section(title, List.of(value)));
    }
}
