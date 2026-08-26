package dansplugins.rpsystem;

import dansplugins.rpsystem.bstats.Metrics;
import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterService;
import dansplugins.rpsystem.api.event.CharacterCreatedEvent;
import dansplugins.rpsystem.api.event.CharacterEndedEvent;
import dansplugins.rpsystem.api.event.CharacterUpdatedEvent;
import dansplugins.rpsystem.cards.CardLookupService;
import dansplugins.rpsystem.cards.CardLookupServiceImpl;
import dansplugins.rpsystem.cards.CardRepository;
import dansplugins.rpsystem.cards.CharacterCard;
import dansplugins.rpsystem.cards.CharacterServiceImpl;
import dansplugins.rpsystem.commands.CommandService;
import dansplugins.rpsystem.config.ConfigMigrator;
import dansplugins.rpsystem.config.ConfigService;
import dansplugins.rpsystem.ephemeral.EphemeralData;
import dansplugins.rpsystem.listeners.InteractionListener;
import dansplugins.rpsystem.listeners.JoinListener;
import dansplugins.rpsystem.listeners.QuitListener;
import dansplugins.rpsystem.integrations.plan.PlanIntegration;
import dansplugins.rpsystem.integrations.truedeath.TrueDeathIntegration;
import dansplugins.rpsystem.placeholders.PlaceholderAPI;
import dansplugins.rpsystem.storage.CharacterHistoryRepository;
import dansplugins.rpsystem.storage.StorageService;
import dansplugins.rpsystem.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class MedievalRoleplayEngine extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 8996;

    private final String pluginVersion = "v" + getDescription().getVersion();

    public final CardRepository cardRepository = new CardRepository();
    public final CardLookupService cardLookupService = new CardLookupServiceImpl(this);
    public final CommandService commandService = new CommandService(this);
    public final ConfigService configService = new ConfigService(this);
    public final EphemeralData ephemeralData = new EphemeralData();
    public final Logger logger = new Logger(this);
    public final ArgumentParser argumentParser = new ArgumentParser();
    public final ColorChecker colorChecker = new ColorChecker(this);
    public final Messenger messenger = new Messenger(this);
    public final UUIDChecker uuidChecker = new UUIDChecker();
    public final StorageService storageService = new StorageService(this);
    public final CharacterHistoryRepository characterHistoryRepository =
            new CharacterHistoryRepository(getDataFolder().toPath(), getLogger());
    public final CharacterServiceImpl characterService = new CharacterServiceImpl(
            cardRepository, characterHistoryRepository, storageService,
            this::publishCharacterEnded, getLogger());

    private TrueDeathIntegration trueDeathIntegration;
    private PlanIntegration planIntegration;
    private PlaceholderAPI placeholderExpansion;
    private boolean dataLoaded;

    @Override
    public void onEnable() {
        dataLoaded = false;
        saveDefaultConfig();
        if (!configService.prepareConfiguration()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        storageService.loadCards();
        if (storageService.oldSaveFolderPresent()) {
            storageService.legacyLoadCards();
        }
        characterHistoryRepository.load();
        dataLoaded = true;

        getServer().getServicesManager().register(
                CharacterService.class, characterService, this, ServicePriority.Normal);

        registerListeners();

        new Metrics(this, BSTATS_PLUGIN_ID);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new PlaceholderAPI(this);
            placeholderExpansion.register();
        } else {
            getLogger().info("PlaceholderAPI is absent; character placeholders are idle.");
        }

        if (getConfig().getBoolean("planIntegrationEnabled", true)) {
            planIntegration = new PlanIntegration(this, characterService);
            planIntegration.start();
        }

        if (getConfig().getBoolean("trueDeathIntegrationEnabled", true)) {
            trueDeathIntegration = new TrueDeathIntegration(this);
            trueDeathIntegration.registerIfAvailable();
        }
    }

    @Override
    public void onDisable() {
        if (trueDeathIntegration != null) {
            trueDeathIntegration.close();
            trueDeathIntegration = null;
        }
        if (planIntegration != null) {
            planIntegration.close();
            planIntegration = null;
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        if (!dataLoaded) {
            return;
        }
        storageService.saveCardFileNames();
        storageService.saveCards();
        if (configService.hasBeenAltered()) {
            saveConfig();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return commandService.interpretCommand(sender, label, args);
    }

    public String getVersion() {
        return pluginVersion;
    }

    public boolean isDebugEnabled() {
        return getConfig().getBoolean("debugMode");
    }

    public boolean isVersionMismatched() {
        String configuredVersion = getConfig().getString("version");
        return configuredVersion == null || !configuredVersion.equalsIgnoreCase(getVersion());
    }

    public boolean isConfigVersionMismatched() {
        return !ConfigMigrator.hasCurrentVersion(getConfig());
    }

    /** Saves an edit before publishing it to optional analytics or web consumers. */
    public boolean persistCharacterUpdate(CharacterCard card, CharacterRecord previous) {
        CharacterRecord current = card.snapshot();
        if (current.equals(previous)) {
            return true;
        }
        if (!storageService.saveCard(card)) {
            card.restore(previous);
            return false;
        }
        cardRepository.put(card); // refreshes secondary indexes, including the cached account name
        getServer().getPluginManager().callEvent(new CharacterUpdatedEvent(previous, current));
        refreshCharacterViews(current.playerId());
        return true;
    }

    public void publishCharacterCreated(CharacterRecord character) {
        getServer().getPluginManager().callEvent(new CharacterCreatedEvent(character));
        refreshCharacterViews(character.playerId());
    }

    /** Refreshes optional read-only projections after the durable mutation has completed. */
    public void refreshCharacterViews(UUID playerId) {
        if (planIntegration != null) {
            planIntegration.refreshCharacter(playerId);
        }
    }

    public void refreshAllCharacterViews() {
        if (planIntegration != null) {
            planIntegration.refreshAll();
        }
    }

    private void publishCharacterEnded(CharacterEndedEvent event) {
        getServer().getPluginManager().callEvent(event);
    }

    private void registerListeners() {
        PluginManager manager = getServer().getPluginManager();
        manager.registerEvents(new InteractionListener(this), this);
        manager.registerEvents(new JoinListener(this), this);
        manager.registerEvents(new QuitListener(this), this);
        if (configService.getBoolean("chatFeaturesEnabled")) {
            getLogger().warning("Legacy MRE chat is disabled in the Patriam fork; use PatriamChat.");
        }
    }
}
