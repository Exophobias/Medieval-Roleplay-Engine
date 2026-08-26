package dansplugins.rpsystem.commands;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import dansplugins.rpsystem.commands.card.CardCommand;
import dansplugins.rpsystem.commands.config.ConfigCommand;
import dansplugins.rpsystem.commands.emote.EmoteCommand;
import dansplugins.rpsystem.commands.help.HelpCommand;
import dansplugins.rpsystem.commands.roll.RollCommand;
import org.bukkit.command.CommandSender;

public class CommandService {
    private final MedievalRoleplayEngine medievalRoleplayEngine;

    public CommandService(MedievalRoleplayEngine medievalRoleplayEngine) {
        this.medievalRoleplayEngine = medievalRoleplayEngine;
    }

    public boolean interpretCommand(CommandSender sender, String label, String[] args) {

        if (label.equalsIgnoreCase("rphelp")) {
            HelpCommand command = new HelpCommand(medievalRoleplayEngine);
            command.showListOfCommands(sender);
            return true;
        }

        if (label.equalsIgnoreCase("card")) {
            CardCommand command = new CardCommand(medievalRoleplayEngine);
            if (args.length == 0) {
                command.showCard(sender, args);
                return true;
            }
            else {

                if (args[0].equalsIgnoreCase("help")) {
                    command.showHelpMessage(sender);
                    return true;
                }
                else if (args[0].equalsIgnoreCase("lookup")) {
                    command.showPlayerInfo(sender, args);
                    return true;
                }
                else if (args[0].equalsIgnoreCase("name")) {
                    command.changeName(sender, args);
                    return true;
                }
                else if (args[0].equalsIgnoreCase("race")) {
                    command.changeRace(sender, args);
                    return true;
                }
                else if (args[0].equalsIgnoreCase("subculture")) {
                    command.changeSubculture(sender, args);
                    return true;
                }
                else if (args[0].equalsIgnoreCase("religion")) {
                    command.changeReligion(sender, args);
                    return true;
                }
                else if (args[0].equalsIgnoreCase("age")) {
                    command.changeAge(sender, args);
                    return true;
                }
                else if (args[0].equalsIgnoreCase("gender")) {
                    command.changeGender(sender, args);
                    return true;
                }
                else if (args[0].equalsIgnoreCase("forcesave")) {
                    return command.forceSave(sender);
                }
                else if (args[0].equalsIgnoreCase("forceload")) {
                    return command.forceLoad(sender);
                }

                sender.sendMessage(medievalRoleplayEngine.colorChecker.getNegativeAlertColor() + "Sub-commands: help, lookup, name, race, subculture, religion, age, gender, forcesave, forceload");
            }
        }

        if (label.equalsIgnoreCase("roll") || label.equalsIgnoreCase("dice")) {
            RollCommand command = new RollCommand(medievalRoleplayEngine);
            return command.rollDice(sender, args);
        }

        if (label.equalsIgnoreCase("rpconfig")) {
            ConfigCommand command = new ConfigCommand(medievalRoleplayEngine);
            command.handleConfigAccess(sender, args);
            return true;
        }

        if (label.equalsIgnoreCase("emote") || label.equalsIgnoreCase("me")) {
            EmoteCommand command = new EmoteCommand(medievalRoleplayEngine);
            return command.emoteAction(sender, args);
        }

        return false;
    }

}
