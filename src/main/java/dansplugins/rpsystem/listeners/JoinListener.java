package dansplugins.rpsystem.listeners;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.cards.CharacterCard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {
    private final MedievalRoleplayEngine medievalRoleplayEngine;

    public JoinListener(MedievalRoleplayEngine medievalRoleplayEngine) {
        this.medievalRoleplayEngine = medievalRoleplayEngine;
    }

    @EventHandler()
    public void handle(PlayerJoinEvent event) {
        CharacterCard card = medievalRoleplayEngine.cardRepository.getCard(
                event.getPlayer().getUniqueId());
        if (card == null) {
            CharacterCard draft = CharacterCard.newDraft(event.getPlayer().getUniqueId(),
                    event.getPlayer().getName(), System.currentTimeMillis());
            if (!medievalRoleplayEngine.storageService.saveCard(draft)) {
                return;
            }
            medievalRoleplayEngine.cardRepository.put(draft);
            medievalRoleplayEngine.storageService.saveCardFileNames();
            medievalRoleplayEngine.publishCharacterCreated(draft.snapshot());
            return;
        }

        CharacterRecord previous = card.snapshot();
        if (card.setLastKnownPlayerName(event.getPlayer().getName())) {
            medievalRoleplayEngine.persistCharacterUpdate(card, previous);
        }
    }

}
