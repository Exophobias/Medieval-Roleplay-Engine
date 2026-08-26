package dansplugins.rpsystem.cards;

import dansplugins.rpsystem.MedievalRoleplayEngine;

import java.util.UUID;

public class CardLookupServiceImpl implements CardLookupService {
    private final MedievalRoleplayEngine medievalRoleplayEngine;
    public CardLookupServiceImpl(MedievalRoleplayEngine medievalRoleplayEngine) {
        this.medievalRoleplayEngine = medievalRoleplayEngine;
    }

    @Override
    public CharacterCard lookup(UUID playerUUID) {
        CharacterCard card = medievalRoleplayEngine.cardRepository.getCard(playerUUID);
        medievalRoleplayEngine.logger.log(card == null
                ? "Character card not found for " + playerUUID
                : "Character card found for " + playerUUID);
        return card;
    }

}
