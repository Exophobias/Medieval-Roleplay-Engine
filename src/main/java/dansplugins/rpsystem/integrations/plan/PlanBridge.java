package dansplugins.rpsystem.integrations.plan;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.extension.Caller;
import com.djrapitops.plan.extension.ExtensionService;
import dansplugins.rpsystem.api.CharacterService;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Plan-typed half of the optional integration.
 *
 * <p>This class is loaded reflectively only after the dependency-free facade has confirmed that
 * Plan is enabled. Keeping every Plan descriptor here allows the main plugin and the facade to be
 * verified normally on servers where Plan is absent.
 */
public final class PlanBridge {

    private static final String[] REQUIRED_CAPABILITIES = {
            "DATA_EXTENSION_VALUES",
            "DATA_EXTENSION_TABLES",
            "DATA_EXTENSION_SHOW_IN_PLAYER_TABLE"
    };

    private final PlanCharacterView view;
    private final CharacterPlanExtension extension;
    private final Logger logger;

    private Caller caller;
    private boolean extensionRegistered;
    private boolean closed;

    private PlanBridge(CharacterService characters, Logger logger) {
        this.view = new PlanCharacterView(characters);
        this.extension = new CharacterPlanExtension(view);
        this.logger = logger;
    }

    /**
     * Creates the bridge, registers the extension, and follows Plan's internal reload lifecycle.
     *
     * @return a managed bridge, or {@code null} when the installed Plan API lacks required features
     */
    public static PlanBridge open(CharacterService characters, Logger logger) {
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(logger, "logger");

        PlanBridge bridge = new PlanBridge(characters, logger);
        if (!bridge.hasRequiredCapabilities()) {
            return null;
        }
        bridge.listenForPlanReloads();
        bridge.registerNow();
        return bridge;
    }

    public synchronized boolean isRegistered() {
        return !closed && caller != null;
    }

    /** Refreshes player providers. Passing the UUID is sufficient even if no public name remains. */
    public void refreshPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Caller current = activeCaller();
        if (current == null) {
            return;
        }
        String playerName = view.publicPlayerName(playerId).orElse(null);
        updatePlayer(current, playerId, playerName);
    }

    public void refreshServer() {
        Caller current = activeCaller();
        if (current != null) {
            updateServer(current);
        }
    }

    /** Refreshes the server overview and every player with public current or historical data. */
    public void refreshAll() {
        Caller current = activeCaller();
        if (current == null) {
            return;
        }
        updateServer(current);
        for (PlanCharacterView.PlayerIdentity player : view.publicPlayers()) {
            updatePlayer(current, player.playerId(), player.playerName());
        }
    }

    /** Convenience hook for one character mutation: update its owner and the server table. */
    public void refreshCharacter(UUID playerId) {
        refreshPlayer(playerId);
        refreshServer();
    }

    /** Unregisters the exact extension instance Plan accepted. Idempotent for shutdown paths. */
    public void close() {
        boolean shouldUnregister;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            caller = null;
            shouldUnregister = extensionRegistered;
            extensionRegistered = false;
        }
        if (!shouldUnregister) {
            return;
        }
        try {
            ExtensionService.getInstance().unregister(extension);
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.FINE,
                    "Plan was already unavailable while its character extension was closing",
                    failure);
        }
    }

    private boolean hasRequiredCapabilities() {
        try {
            CapabilityService capabilities = CapabilityService.getInstance();
            for (String required : REQUIRED_CAPABILITIES) {
                if (!capabilities.hasCapability(required)) {
                    logger.warning("Plan lacks " + required
                            + "; character analytics require Plan API 5.7 or newer.");
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING,
                    "Plan's capability API is unavailable; character analytics are disabled",
                    failure);
            return false;
        }
    }

    private void listenForPlanReloads() {
        WeakReference<PlanBridge> reference = new WeakReference<>(this);
        CapabilityService.getInstance().registerEnableListener(enabled -> {
            PlanBridge bridge = reference.get();
            if (bridge != null) {
                bridge.onPlanEnableState(Boolean.TRUE.equals(enabled));
            }
        });
    }

    private void onPlanEnableState(boolean enabled) {
        synchronized (this) {
            if (closed) {
                return;
            }
            if (!enabled) {
                caller = null;
                extensionRegistered = false;
                return;
            }
        }
        registerNow();
    }

    private void registerNow() {
        boolean accepted = false;
        synchronized (this) {
            if (closed) {
                return;
            }
            if (extensionRegistered) {
                try {
                    ExtensionService.getInstance().unregister(extension);
                } catch (RuntimeException | LinkageError ignored) {
                    // A reload may already have replaced Plan's old extension registry.
                }
                extensionRegistered = false;
                caller = null;
            }

            try {
                Optional<Caller> registration = ExtensionService.getInstance().register(extension);
                extensionRegistered = true;
                caller = registration.orElse(null);
                accepted = caller != null;
            } catch (IllegalArgumentException invalidExtension) {
                logger.log(Level.WARNING,
                        "Plan rejected the character extension as invalid", invalidExtension);
            } catch (IllegalStateException | LinkageError unavailable) {
                logger.log(Level.WARNING,
                        "Plan is present but its extension service is unavailable", unavailable);
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING,
                        "Could not register character analytics with Plan", failure);
            }
        }

        if (accepted) {
            logger.info("Registered current and historical character analytics with Plan.");
            refreshAll();
        } else if (extensionRegistered) {
            logger.info("Plan has disabled MedievalRoleplayEngine analytics in its configuration.");
        }
    }

    private synchronized Caller activeCaller() {
        return closed ? null : caller;
    }

    private void updatePlayer(Caller current, UUID playerId, String playerName) {
        try {
            current.updatePlayerData(playerId, playerName);
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING,
                    "Plan could not refresh character data for " + playerId, failure);
        }
    }

    private void updateServer(Caller current) {
        try {
            current.updateServerData();
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING,
                    "Plan could not refresh the current-character overview", failure);
        }
    }
}
