package dansplugins.rpsystem.integrations.plan;

import dansplugins.rpsystem.api.CharacterService;
import dansplugins.rpsystem.api.event.CharacterEndedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dependency-free lifecycle facade for the optional Plan integration.
 *
 * <p>This class deliberately has no Plan type in its constant pool. It handles either plugin load
 * order and only asks the JVM to verify the Plan-typed bridge after Bukkit confirms Plan is enabled.
 * That keeps MedievalRoleplayEngine loadable when the provided Plan API is absent at runtime.
 */
public final class PlanIntegration implements Listener {

    private static final String PLAN_PLUGIN = "Plan";
    private static final String BRIDGE_CLASS =
            "dansplugins.rpsystem.integrations.plan.PlanBridge";

    private final Plugin plugin;
    private final CharacterService characters;
    private final Logger logger;

    private boolean started;
    private Object bridge;
    private BridgeMethods bridgeMethods;

    public PlanIntegration(Plugin plugin, CharacterService characters) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.logger = plugin.getLogger();
    }

    /** Registers the load-order listener and attaches immediately when Plan is already enabled. */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        attachIfAvailable();
    }

    /** Unregisters both the Bukkit listener and the exact Plan extension instance. */
    public void close() {
        synchronized (this) {
            if (!started) {
                return;
            }
            started = false;
        }
        HandlerList.unregisterAll(this);
        detach();
    }

    public boolean isRegistered() {
        Invocation target = invocation(BridgeMethods::isRegistered);
        if (target == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(target.method().invoke(target.bridge()));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException
                 | LinkageError failure) {
            logInvocationFailure("check Plan registration", failure);
            return false;
        }
    }

    public void refreshPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        invoke(BridgeMethods::refreshPlayer, playerId);
    }

    public void refreshServer() {
        invoke(BridgeMethods::refreshServer);
    }

    public void refreshAll() {
        invoke(BridgeMethods::refreshAll);
    }

    /** Refreshes one player's Plan values and the server's current-character table. */
    public void refreshCharacter(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        invoke(BridgeMethods::refreshCharacter, playerId);
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (PLAN_PLUGIN.equals(event.getPlugin().getName())) {
            attachIfAvailable();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (PLAN_PLUGIN.equals(event.getPlugin().getName())) {
            detach();
        }
    }

    /** A TrueDeath archive changes both the player's history and the current-character table. */
    @EventHandler
    public void onCharacterEnded(CharacterEndedEvent event) {
        refreshCharacter(event.ended().playerId());
    }

    private synchronized void attachIfAvailable() {
        if (!started || bridge != null) {
            return;
        }
        Plugin plan = plugin.getServer().getPluginManager().getPlugin(PLAN_PLUGIN);
        if (plan == null || !plan.isEnabled()) {
            return;
        }

        try {
            Class<?> bridgeType = Class.forName(
                    BRIDGE_CLASS, true, plugin.getClass().getClassLoader());
            Method open = bridgeType.getMethod("open", CharacterService.class, Logger.class);
            Object attached = open.invoke(null, characters, logger);
            if (attached == null) {
                return;
            }
            bridge = attached;
            bridgeMethods = new BridgeMethods(
                    bridgeType.getMethod("close"),
                    bridgeType.getMethod("isRegistered"),
                    bridgeType.getMethod("refreshPlayer", UUID.class),
                    bridgeType.getMethod("refreshServer"),
                    bridgeType.getMethod("refreshAll"),
                    bridgeType.getMethod("refreshCharacter", UUID.class));
        } catch (InvocationTargetException failure) {
            logInvocationFailure("register the Plan integration", failure.getCause());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING,
                    "Plan is enabled, but its character integration could not be loaded", failure);
        }
    }

    private void detach() {
        Object current;
        Method close;
        synchronized (this) {
            current = bridge;
            close = bridgeMethods == null ? null : bridgeMethods.close();
            bridge = null;
            bridgeMethods = null;
        }
        if (current == null || close == null) {
            return;
        }
        try {
            close.invoke(current);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException
                 | LinkageError failure) {
            logInvocationFailure("unregister the Plan integration", failure);
        }
    }

    private void invoke(java.util.function.Function<BridgeMethods, Method> method, Object... args) {
        Invocation target = invocation(method);
        if (target == null) {
            return;
        }
        try {
            target.method().invoke(target.bridge(), args);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException
                 | LinkageError failure) {
            logInvocationFailure("refresh Plan character data", failure);
        }
    }

    private synchronized Invocation invocation(
            java.util.function.Function<BridgeMethods, Method> method) {
        if (bridge == null || bridgeMethods == null) {
            return null;
        }
        return new Invocation(bridge, method.apply(bridgeMethods));
    }

    private void logInvocationFailure(String action, Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation
                && invocation.getCause() != null
                ? invocation.getCause()
                : failure;
        logger.log(Level.WARNING, "Could not " + action, cause);
    }

    private record Invocation(Object bridge, Method method) {
    }

    private record BridgeMethods(Method close, Method isRegistered, Method refreshPlayer,
                                 Method refreshServer, Method refreshAll,
                                 Method refreshCharacter) {
    }
}
