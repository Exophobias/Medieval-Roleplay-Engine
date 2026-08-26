package dansplugins.rpsystem.config;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ConfigService {
    private final MedievalRoleplayEngine medievalRoleplayEngine;
    private boolean altered = false;

    public ConfigService(MedievalRoleplayEngine medievalRoleplayEngine) {
        this.medievalRoleplayEngine = medievalRoleplayEngine;
    }

    /**
     * Upgrades the physical file before Bukkit attaches bundled defaults to it.
     *
     * @return true when the schema is supported and plugin startup may continue
     */
    public boolean prepareConfiguration() {
        String bundled;
        try (InputStream stream = medievalRoleplayEngine.getResource("config.yml")) {
            if (stream == null) {
                medievalRoleplayEngine.getLogger().severe(
                        "The plugin jar has no config.yml; MedievalRoleplayEngine will be disabled.");
                return false;
            }
            bundled = ConfigMigrator.decodeUtf8(stream.readAllBytes());
        } catch (IOException e) {
            medievalRoleplayEngine.getLogger().severe(
                    "The bundled config.yml could not be read; MedievalRoleplayEngine will be "
                            + "disabled.");
            return false;
        }

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                medievalRoleplayEngine.getDataFolder().toPath().resolve("config.yml"),
                bundled, medievalRoleplayEngine.getVersion());
        String schemaIdentity = medievalRoleplayEngine.getVersion()
                + " supports config schema v" + ConfigMigrator.CURRENT_VERSION;

        switch (result.state()) {
            case CURRENT -> medievalRoleplayEngine.getLogger().info(
                    schemaIdentity + "; installed schema v" + result.sourceVersion()
                            + " is current.");
            case UPGRADED -> medievalRoleplayEngine.getLogger().warning(
                    schemaIdentity + "; installed schema v" + result.sourceVersion()
                            + " was migrated to v" + ConfigMigrator.CURRENT_VERSION
                            + ". Original saved as " + result.backup().getFileName() + '.');
            case FUTURE -> medievalRoleplayEngine.getLogger().severe(
                    schemaIdentity + "; installed schema v" + result.sourceVersion()
                            + " is blocked because it is newer. This jar will not rewrite a config "
                            + "created by a newer plugin; install the matching/newer jar or restore "
                            + "an older config backup.");
            case INVALID, ERROR -> medievalRoleplayEngine.getLogger().severe(
                    schemaIdentity + "; installed schema is blocked: " + result.detail()
                            + ". MedievalRoleplayEngine will be disabled without changing the "
                            + "installed configuration.");
        }

        if (!result.compatible()) {
            return false;
        }
        medievalRoleplayEngine.reloadConfig();
        List<String> reloadedErrors = ConfigMigrator.validateCurrentConfiguration(
                medievalRoleplayEngine.getConfig());
        if (!reloadedErrors.isEmpty()) {
            medievalRoleplayEngine.getLogger().severe(
                    schemaIdentity + "; the configuration snapshot loaded by Bukkit is blocked: "
                            + String.join("; ", reloadedErrors) + ". config.yml may have changed "
                            + "during startup. MedievalRoleplayEngine will be disabled without "
                            + "saving it.");
            return false;
        }
        // Migration decisions used only the physical file. Defaults are attached only afterward;
        // copying them into the in-memory view keeps /rpconfig set usable when an operator has
        // deliberately omitted a key and accepts its shipped default.
        medievalRoleplayEngine.getConfig().options().copyDefaults(true);
        return true;
    }

    public void setConfigOption(String option, String value, Player player) {

        if (getConfig().isSet(option)) {

            if (option.equalsIgnoreCase("version")
                    || option.equalsIgnoreCase(ConfigMigrator.VERSION_KEY)) {
                player.sendMessage(ChatColor.RED + "Cannot set configuration metadata!");
                return;
            }
            else if (option.equalsIgnoreCase("localChatRadius")
                    || option.equalsIgnoreCase("whisperChatRadius")
                    || option.equalsIgnoreCase("yellChatRadius")
                    || option.equalsIgnoreCase("changeNameCooldown")
                    || option.equalsIgnoreCase("emoteRadius")
                    || option.equalsIgnoreCase("localOOCChatRadius")
                    || option.equalsIgnoreCase("birdSpeed")) {
                int parsed;
                try {
                    parsed = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "That value must be an integer.");
                    return;
                }
                if (parsed < 0 || (option.equalsIgnoreCase("birdSpeed") && parsed == 0)) {
                    player.sendMessage(ChatColor.RED + "That value must be positive.");
                    return;
                }
                getConfig().set(option, parsed);
                player.sendMessage(medievalRoleplayEngine.colorChecker.getColorByName(getString("positiveAlertColor")) + "Integer set!");
            }
            else if (option.equalsIgnoreCase("rightClickToViewCard")
                    || option.equalsIgnoreCase("chatFeaturesEnabled")
                    || option.equalsIgnoreCase("debugMode")
                    || option.equalsIgnoreCase("logChat")
                    || option.equalsIgnoreCase("trueDeathIntegrationEnabled")
                    || option.equalsIgnoreCase("planIntegrationEnabled")
                    || option.equalsIgnoreCase("legacyReligionFieldEnabled")
                    || option.equalsIgnoreCase("exposeReligionPlaceholder")) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    player.sendMessage(ChatColor.RED + "That value must be true or false.");
                    return;
                }
                getConfig().set(option, Boolean.parseBoolean(value));
                player.sendMessage(medievalRoleplayEngine.colorChecker.getColorByName(getString("positiveAlertColor")) + "Boolean set!");
            }
            else if (option.equalsIgnoreCase("doubletest")) {
                getConfig().set(option, Double.parseDouble(value));
                player.sendMessage(medievalRoleplayEngine.colorChecker.getColorByName(getString("positiveAlertColor")) + "Double set!");
            }
            else {
                getConfig().set(option, value);
                player.sendMessage(medievalRoleplayEngine.colorChecker.getColorByName(getString("positiveAlertColor")) + "String set!");
            }

            medievalRoleplayEngine.saveConfig();
            altered = true;
        }
        else {
            player.sendMessage(ChatColor.RED + String.format("The option '%s' wasn't found.", option));
        }

    }

    /**
     * Retained for source compatibility with older callers. New code must use the startup
     * migrator; this helper never attempts to upgrade or downgrade a schema.
     */
    @Deprecated
    public void saveConfigDefaults() {
        // Kept only for binary/source compatibility. Appending Bukkit defaults would put newly
        // shipped keys at the end of the administrator's file and bypass the schema transaction.
        medievalRoleplayEngine.getLogger().warning(
                "saveConfigDefaults() is retired; config schema migration owns default placement"
                        + " and no configuration changes were written.");
    }

    public void sendPlayerConfigList(Player player) {
        player.sendMessage(medievalRoleplayEngine.colorChecker.getColorByName(getString("neutralAlertColor"))
                + "plugin-version: " + medievalRoleplayEngine.getVersion()
                + ", supported-config-version: " + ConfigMigrator.CURRENT_VERSION
                + ", config-version: " + getConfig().getInt(ConfigMigrator.VERSION_KEY)
                + ", config-state: current"
                + ", config-written-by: " + getConfig().getString("version")
                + ", debugMode: " + getConfig().getBoolean("debugMode")
                + ", chatFeaturesEnabled: " + getConfig().getBoolean("chatFeaturesEnabled")
                + ", trueDeathIntegrationEnabled: " + getConfig().getBoolean("trueDeathIntegrationEnabled")
                + ", planIntegrationEnabled: " + getConfig().getBoolean("planIntegrationEnabled")
                + ", legacyReligionFieldEnabled: " + getConfig().getBoolean("legacyReligionFieldEnabled")
                + ", exposeReligionPlaceholder: " + getConfig().getBoolean("exposeReligionPlaceholder")
                + ", localChatRadius: " + getConfig().getInt("localChatRadius")
                + ", whisperChatRadius: " + getConfig().getInt("whisperChatRadius")
                + ", yellChatRadius: " + getConfig().getInt("yellChatRadius")
                + ", emoteRadius: " + getConfig().getInt("emoteRadius")
                + ", changeNameCooldown: " + getConfig().getInt("changeNameCooldown")
                + ", localChatColor: " + getConfig().getString("localChatColor")
                + ", whisperChatColor: " + getConfig().getString("whisperChatColor")
                + ", yellChatColor: " + getConfig().getString("yellChatColor")
                + ", emoteColor: " + getConfig().getString("emoteColor")
                + ", rightClickToViewCard: " + getConfig().getBoolean("rightClickToViewCard")
                + ", localOOCChatRadius: " + getConfig().getInt("localOOCChatRadius")
                + ", localOOCChatColor: " + getConfig().getString("localOOCChatColor")
                + ", positiveAlertColor: " + getConfig().getString("positiveAlertColor")
                + ", neutralAlertColor: " + getConfig().getString("neutralAlertColor")
                + ", negativeAlertColor: " + getConfig().getString("negativeAlertColor")
                + ", birdSpeed: " + getConfig().getString("birdSpeed")
                + ", logChat:" + getConfig().getBoolean("logChat"));
    }

    public boolean hasBeenAltered() {
        return altered;
    }

    public FileConfiguration getConfig() {
        return medievalRoleplayEngine.getConfig();
    }

    public int getInt(String option) {
        return getConfig().getInt(option);
    }

    public boolean getBoolean(String option) {
        return getConfig().getBoolean(option);
    }

    public double getDouble(String option) {
        return getConfig().getDouble(option);
    }

    public String getString(String option) {
        return getConfig().getString(option);
    }

}
