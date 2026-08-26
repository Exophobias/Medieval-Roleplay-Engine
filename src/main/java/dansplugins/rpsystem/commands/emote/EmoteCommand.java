package dansplugins.rpsystem.commands.emote;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import dansplugins.rpsystem.api.CharacterRecord;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EmoteCommand {
    private final MedievalRoleplayEngine medievalRoleplayEngine;

    public EmoteCommand(MedievalRoleplayEngine medievalRoleplayEngine) {
        this.medievalRoleplayEngine = medievalRoleplayEngine;
    }

    public boolean emoteAction(CommandSender sender, String[] args) {

        int emoteRadius = medievalRoleplayEngine.getConfig().getInt("emoteRadius");
        String emoteColor = medievalRoleplayEngine.getConfig().getString("emoteColor");

        if (sender instanceof Player player) {
            if (player.hasPermission("rp.emote") || player.hasPermission("rp.me") || player.hasPermission("rp.default")) {
                if (args.length > 0) {
                    String message = medievalRoleplayEngine.argumentParser.createStringFromFirstArgOnwards(args, 0);
                    CharacterRecord character = medievalRoleplayEngine.characterService
                            .currentCharacter(player.getUniqueId()).orElse(null);
                    if (character == null || !character.isConfigured()) {
                        player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor()
                                + "Set your character name with /card name before using emotes.");
                        return true;
                    }

                    medievalRoleplayEngine.messenger.sendRPMessageToPlayersWithinDistance(player,
                            medievalRoleplayEngine.colorChecker.getColorByName(emoteColor) + ""
                                    + ChatColor.ITALIC + character.name() + " " + message,
                            emoteRadius);
                    return true;
                }
                player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor()
                        + "Usage: /emote <action>");
                return true;
            }
            else {
                player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Sorry! In order to use this command, you need one the following permissions: 'rp.emote', 'rp.me'");
                return true;
            }

        }
        return false;
    }

}
