package dansplugins.rpsystem.utils;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.bukkit.Bukkit.getOfflinePlayers;
import static org.bukkit.Bukkit.getOnlinePlayers;

public class UUIDChecker {

    /** One-pass UUID index used to hydrate seven-line cards before their first migrated save. */
    public Map<UUID, String> knownPlayerNamesByUuid() {
        Map<UUID, String> known = new LinkedHashMap<>();
        for (OfflinePlayer player : getOfflinePlayers()) {
            String name = player.getName();
            if (name != null && !name.isBlank()) {
                known.put(player.getUniqueId(), name);
            }
        }
        for (Player player : getOnlinePlayers()) {
            known.put(player.getUniqueId(), player.getName());
        }
        return Map.copyOf(known);
    }

    /** One-pass index for bulk legacy migrations; avoids scanning every known player per card. */
    public Map<String, UUID> knownPlayersByName() {
        Map<String, UUID> known = new LinkedHashMap<>();
        for (OfflinePlayer player : getOfflinePlayers()) {
            String name = player.getName();
            if (name != null && !name.isBlank()) {
                known.put(name.toLowerCase(Locale.ROOT), player.getUniqueId());
            }
        }
        for (Player player : getOnlinePlayers()) {
            known.put(player.getName().toLowerCase(Locale.ROOT), player.getUniqueId());
        }
        return Map.copyOf(known);
    }
}
