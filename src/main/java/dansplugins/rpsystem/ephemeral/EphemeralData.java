package dansplugins.rpsystem.ephemeral;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EphemeralData {
    private final Set<UUID> playersWithBusyBirds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersSpeakingInLocalChat = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersOnNameChangeCooldown = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersWithRightClickCooldown = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersWhoHaveHiddenGlobalChat = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersWhoHaveHiddenLocalChat = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersWhoHaveHiddenLocalOOCChat = ConcurrentHashMap.newKeySet();

    public Set<UUID> getPlayersWithBusyBirds() {
        return playersWithBusyBirds;
    }

    public Set<UUID> getPlayersSpeakingInLocalChat() {
        return playersSpeakingInLocalChat;
    }

    public Set<UUID> getPlayersOnNameChangeCooldown() {
        return playersOnNameChangeCooldown;
    }

    public Set<UUID> getPlayersWithRightClickCooldown() {
        return playersWithRightClickCooldown;
    }

    public Set<UUID> getPlayersWhoHaveHiddenGlobalChat() {
        return playersWhoHaveHiddenGlobalChat;
    }

    public Set<UUID> getPlayersWhoHaveHiddenLocalChat() {
        return playersWhoHaveHiddenLocalChat;
    }

    public Set<UUID> getPlayersWhoHaveHiddenLocalOOCChat() {
        return playersWhoHaveHiddenLocalOOCChat;
    }

    /** Clears state that is meaningful only during one login session. */
    public void clearSession(UUID playerId) {
        playersWithBusyBirds.remove(playerId);
        playersSpeakingInLocalChat.remove(playerId);
        playersWithRightClickCooldown.remove(playerId);
        playersWhoHaveHiddenGlobalChat.remove(playerId);
        playersWhoHaveHiddenLocalChat.remove(playerId);
        playersWhoHaveHiddenLocalOOCChat.remove(playerId);
    }

}
