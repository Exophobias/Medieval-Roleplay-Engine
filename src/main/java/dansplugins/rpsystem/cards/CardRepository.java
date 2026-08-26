package dansplugins.rpsystem.cards;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** UUID-indexed current-card store. Reads are safe for Plan and other asynchronous consumers. */
public final class CardRepository {

    private final ConcurrentMap<UUID, CharacterCard> cards = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CharacterCard> cardsByCharacter = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<UUID>> playersByLastKnownName = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> lastKnownNamesByPlayer = new ConcurrentHashMap<>();

    /** Returns a stable collection snapshot; callers cannot mutate repository membership. */
    public Collection<CharacterCard> getCards() {
        return List.copyOf(cards.values());
    }

    public CharacterCard getCard(UUID uuid) {
        return uuid == null ? null : cards.get(uuid);
    }

    public CharacterCard getCardByCharacterId(UUID characterId) {
        return characterId == null ? null : cardsByCharacter.get(characterId);
    }

    public CharacterCard getCardByLastKnownPlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        Set<UUID> matches = playersByLastKnownName.get(normalizeName(playerName));
        if (matches == null || matches.size() != 1) {
            return null;
        }
        UUID playerId = matches.iterator().next();
        return playerId == null ? null : cards.get(playerId);
    }

    public boolean hasCard(UUID uuid) {
        return uuid != null && cards.containsKey(uuid);
    }

    public synchronized CharacterCard put(CharacterCard card) {
        Objects.requireNonNull(card, "card");
        CharacterCard characterOwner = cardsByCharacter.get(card.getCharacterId());
        if (characterOwner != null
                && !characterOwner.getPlayerUUID().equals(card.getPlayerUUID())) {
            throw new IllegalArgumentException("character id " + card.getCharacterId()
                    + " is already assigned to " + characterOwner.getPlayerUUID());
        }
        CharacterCard replaced = cards.put(card.getPlayerUUID(), card);
        if (replaced != null) {
            cardsByCharacter.remove(replaced.getCharacterId(), replaced);
        }
        cardsByCharacter.put(card.getCharacterId(), card);
        String oldName = lastKnownNamesByPlayer.remove(card.getPlayerUUID());
        if (oldName != null) {
            removeNameOwner(oldName, card.getPlayerUUID());
        }
        String newName = normalizeName(card.getLastKnownPlayerName());
        if (!newName.isEmpty()) {
            lastKnownNamesByPlayer.put(card.getPlayerUUID(), newName);
            playersByLastKnownName.computeIfAbsent(newName,
                    ignored -> ConcurrentHashMap.newKeySet()).add(card.getPlayerUUID());
        }
        return replaced;
    }

    public synchronized CharacterCard remove(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        CharacterCard removed = cards.remove(playerId);
        if (removed != null) {
            cardsByCharacter.remove(removed.getCharacterId(), removed);
        }
        String oldName = lastKnownNamesByPlayer.remove(playerId);
        if (oldName != null) {
            removeNameOwner(oldName, playerId);
        }
        return removed;
    }

    public synchronized void clear() {
        cards.clear();
        cardsByCharacter.clear();
        playersByLastKnownName.clear();
        lastKnownNamesByPlayer.clear();
    }

    public int size() {
        return cards.size();
    }

    private static String normalizeName(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase(Locale.ROOT);
    }

    private void removeNameOwner(String normalizedName, UUID playerId) {
        Set<UUID> owners = playersByLastKnownName.get(normalizedName);
        if (owners == null) {
            return;
        }
        owners.remove(playerId);
        if (owners.isEmpty()) {
            playersByLastKnownName.remove(normalizedName, owners);
        }
    }
}
