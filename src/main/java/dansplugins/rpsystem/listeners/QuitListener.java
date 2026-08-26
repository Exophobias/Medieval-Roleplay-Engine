package dansplugins.rpsystem.listeners;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Keeps session-only state bounded across a long server uptime. */
public final class QuitListener implements Listener {

    private final MedievalRoleplayEngine plugin;

    public QuitListener(MedievalRoleplayEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.ephemeralData.clearSession(event.getPlayer().getUniqueId());
    }
}
