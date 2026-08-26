package dansplugins.rpsystem.commands.card;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.cards.CharacterCard;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

import static org.bukkit.Bukkit.getServer;

public class CardCommand {
    private final MedievalRoleplayEngine medievalRoleplayEngine;

    public CardCommand(MedievalRoleplayEngine medievalRoleplayEngine) {
        this.medievalRoleplayEngine = medievalRoleplayEngine;
    }

    public void showCard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return;
        }
        Player player = (Player) sender;

        if (player.hasPermission("rp.card.show") || player.hasPermission("rp.card.*") || player.hasPermission("rp.default")) {
            CharacterCard card = medievalRoleplayEngine.cardLookupService.lookup(player.getUniqueId());
            if (card == null) {
                player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "You do not have a character card to view.");
                return;
            }
            medievalRoleplayEngine.messenger.sendCardInfoToPlayer(card, player);
        }
        else {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Sorry! In order to use this command, you need the following permission: 'rp.card.show'");
        }
    }

    public void showHelpMessage(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return;
        }
        Player player = (Player) sender;

        if (player.hasPermission("rp.card.help") || player.hasPermission("rp.card.*") || player.hasPermission("rp.default")) {
            sender.sendMessage(ChatColor.BOLD + "" + medievalRoleplayEngine.colorChecker.getNeutralAlertColor() + " == " + "Character Card Commands" + " == ");
            sender.sendMessage(medievalRoleplayEngine.colorChecker.getNeutralAlertColor() + "/card - View your character card.");
            sender.sendMessage(medievalRoleplayEngine.colorChecker.getNeutralAlertColor() + "/card lookup (player) - View the character card of a specific player.");
            sender.sendMessage(medievalRoleplayEngine.colorChecker.getNeutralAlertColor() + "/card name (name) - Change your character's name.");
            sender.sendMessage(medievalRoleplayEngine.colorChecker.getNeutralAlertColor() + "/card race (race) - Change your character's race.");
            sender.sendMessage(medievalRoleplayEngine.colorChecker.getNeutralAlertColor() + "/card subculture (subculture) - Change your character's subculture.");
            sender.sendMessage(medievalRoleplayEngine.colorChecker.getNeutralAlertColor() + "/card age (age) - Change your character's age.");
            sender.sendMessage(medievalRoleplayEngine.colorChecker.getNeutralAlertColor() + "/card gender (gender) - Change your character's gender.");
            if (medievalRoleplayEngine.getConfig().getBoolean("legacyReligionFieldEnabled", false)) {
                sender.sendMessage(medievalRoleplayEngine.colorChecker.getNeutralAlertColor() + "/card religion (religion) - Change the legacy biography religion field.");
            }
        }
        else {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Sorry! In order to use this command, you need the following permission: 'rp.card.help'");
        }
    }

    public void changeName(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return;
        }
        Player player = (Player) sender;

        if (!(player.hasPermission("rp.card.name") || player.hasPermission("rp.card.*") || player.hasPermission("rp.default"))) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Sorry! In order to use this command, you need the following permission: 'rp.card.name'");
            return;
        }

        CharacterCard card = findCardForPlayer(player.getUniqueId());
        if (card == null) {
            return;
        }

        if (medievalRoleplayEngine.ephemeralData.getPlayersOnNameChangeCooldown().contains(player.getUniqueId())) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "You must wait before changing your name again!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Usage: /card name (character-name)");
            return;
        }

        CharacterRecord previous = card.snapshot();
        card.setName(medievalRoleplayEngine.argumentParser.createStringFromFirstArgOnwards(args, 1));
        if (!medievalRoleplayEngine.persistCharacterUpdate(card, previous)) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor()
                    + "Your character could not be saved, so the change was not applied.");
            return;
        }
        player.sendMessage(medievalRoleplayEngine.colorChecker.getPositiveAlertColor() + "Name set! Type /card to see changes.");

        int changeNameCooldown = Math.max(0,
                medievalRoleplayEngine.configService.getInt("changeNameCooldown"));
        if (changeNameCooldown != 0) {
            medievalRoleplayEngine.ephemeralData.getPlayersOnNameChangeCooldown().add(player.getUniqueId());
            UUID playerId = player.getUniqueId();
            getServer().getScheduler().runTaskLater(medievalRoleplayEngine, () -> {
                medievalRoleplayEngine.ephemeralData.getPlayersOnNameChangeCooldown().remove(playerId);
                Player online = getServer().getPlayer(playerId);
                if (online != null) {
                    online.sendMessage(medievalRoleplayEngine.colorChecker.getPositiveAlertColor()
                            + "You can now change your character's name again.");
                }
            }, changeNameCooldown * 20L);
        }
    }

    public void changeRace(CommandSender sender, String[] args) {
        applyStringCardChange(sender, args, "rp.card.race", "race",
                (card, value) -> card.setRace(value));
    }

    public void changeSubculture(CommandSender sender, String[] args) {
        applyStringCardChange(sender, args, "rp.card.subculture", "subculture",
                (card, value) -> card.setSubculture(value));
    }

    public void changeReligion(CommandSender sender, String[] args) {
        if (!medievalRoleplayEngine.getConfig().getBoolean("legacyReligionFieldEnabled", false)) {
            sender.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor()
                    + "Religion is managed by PatriamReligion, not the character biography.");
            return;
        }
        applyStringCardChange(sender, args, "rp.card.religion", "religion",
                (card, value) -> card.setReligion(value));
    }

    public void changeGender(CommandSender sender, String[] args) {
        applyStringCardChange(sender, args, "rp.card.gender", "gender",
                (card, value) -> card.setGender(value));
    }

    public void changeAge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return;
        }
        Player player = (Player) sender;

        if (!(player.hasPermission("rp.card.age") || player.hasPermission("rp.card.*") || player.hasPermission("rp.default"))) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Sorry! In order to use this command, you need the following permission: 'rp.card.age'");
            return;
        }

        CharacterCard card = findCardForPlayer(player.getUniqueId());
        if (card == null) {
            return;
        }

        if (args.length < 2) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Usage: /card age (character-age)");
            return;
        }

        int newAge;
        try {
            newAge = Integer.parseInt(medievalRoleplayEngine.argumentParser.createStringFromFirstArgOnwards(args, 1));
        } catch (NumberFormatException e) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Must be a number.");
            return;
        }
        if (newAge < 0 || newAge > 1_000_000) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor()
                    + "Age must be between 0 and 1000000.");
            return;
        }
        CharacterRecord previous = card.snapshot();
        card.setAge(newAge);
        if (!medievalRoleplayEngine.persistCharacterUpdate(card, previous)) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor()
                    + "Your character could not be saved, so the change was not applied.");
            return;
        }
        player.sendMessage(medievalRoleplayEngine.colorChecker.getPositiveAlertColor() + "Age set! Type /card to see changes.");
    }

    public void showPlayerInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return;
        }
        Player player = (Player) sender;

        if (!(player.hasPermission("rp.card.lookup") || player.hasPermission("rp.card.*") || player.hasPermission("rp.default"))) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Sorry! In order to use this command, you need the following permission: 'rp.card.lookup'");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Usage: /card lookup (playerName)");
            return;
        }

        String targetName = medievalRoleplayEngine.argumentParser.createStringFromFirstArgOnwards(args, 1);
        CharacterCard card = medievalRoleplayEngine.cardRepository
                .getCardByLastKnownPlayerName(targetName);
        if (card == null || !card.snapshot().isPubliclyVisible()) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "That player wasn't found.");
            return;
        }

        medievalRoleplayEngine.messenger.sendCardInfoToPlayer(card, player);
    }

    public boolean forceSave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return false;
        }
        Player player = (Player) sender;

        if (player.hasPermission("rp.card.forcesave") || player.hasPermission("rp.admin")) {
            medievalRoleplayEngine.storageService.saveCardFileNames();
            medievalRoleplayEngine.storageService.saveCards();
            player.sendMessage(medievalRoleplayEngine.colorChecker.getPositiveAlertColor()
                    + "Character cards saved.");
            return true;
        }
        else {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Sorry! In order to use this command, you need the following permission: 'rp.card.forcesave'");
            return false;
        }
    }

    public boolean forceLoad(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return false;
        }
        Player player = (Player) sender;

        if (player.hasPermission("rp.card.forceload") || player.hasPermission("rp.admin")) {
            medievalRoleplayEngine.storageService.loadCards();
            medievalRoleplayEngine.characterHistoryRepository.load();
            medievalRoleplayEngine.refreshAllCharacterViews();
            player.sendMessage(medievalRoleplayEngine.colorChecker.getPositiveAlertColor()
                    + "Current cards and character history reloaded.");
            return true;
        }
        else {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Sorry! In order to use this command, you need the following permission: 'rp.card.forceload'");
            return false;
        }
    }

    private void applyStringCardChange(CommandSender sender, String[] args, String permissionNode,
                                        String fieldName, CardFieldSetter setter) {
        if (!(sender instanceof Player)) {
            return;
        }
        Player player = (Player) sender;

        if (!(player.hasPermission(permissionNode) || player.hasPermission("rp.card.*") || player.hasPermission("rp.default"))) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor()
                    + "Sorry! In order to use this command, you need the following permission: '" + permissionNode + "'");
            return;
        }

        CharacterCard card = findCardForPlayer(player.getUniqueId());
        if (card == null) {
            return;
        }

        if (args.length < 2) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor()
                    + "Usage: /card " + fieldName + " (character-" + fieldName + ")");
            return;
        }

        CharacterRecord previous = card.snapshot();
        setter.apply(card, medievalRoleplayEngine.argumentParser.createStringFromFirstArgOnwards(args, 1));
        if (!medievalRoleplayEngine.persistCharacterUpdate(card, previous)) {
            player.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor()
                    + "Your character could not be saved, so the change was not applied.");
            return;
        }
        player.sendMessage(medievalRoleplayEngine.colorChecker.getPositiveAlertColor()
                + capitalize(fieldName) + " set! Type /card to see changes.");
    }

    private CharacterCard findCardForPlayer(UUID playerUUID) {
        return medievalRoleplayEngine.cardRepository.getCard(playerUUID);
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    @FunctionalInterface
    private interface CardFieldSetter {
        void apply(CharacterCard card, String value);
    }
}
