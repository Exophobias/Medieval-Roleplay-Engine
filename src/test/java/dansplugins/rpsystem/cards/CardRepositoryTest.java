package dansplugins.rpsystem.cards;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardRepositoryTest {

    private static final UUID PLAYER_ONE =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID CHARACTER_ONE =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CHARACTER_TWO =
            UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void replacingAPlayersCardAlsoReplacesTheCharacterIdIndex() {
        CardRepository repository = new CardRepository();
        CharacterCard first = card(PLAYER_ONE, CHARACTER_ONE, "First");
        CharacterCard replacement = card(PLAYER_ONE, CHARACTER_TWO, "Second");

        assertNull(repository.put(first));
        assertSame(first, repository.getCard(PLAYER_ONE));
        assertSame(first, repository.getCardByCharacterId(CHARACTER_ONE));

        assertSame(first, repository.put(replacement));
        assertEquals(1, repository.size());
        assertSame(replacement, repository.getCard(PLAYER_ONE));
        assertNull(repository.getCardByCharacterId(CHARACTER_ONE));
        assertSame(replacement, repository.getCardByCharacterId(CHARACTER_TWO));

        assertSame(replacement, repository.remove(PLAYER_ONE));
        assertEquals(0, repository.size());
        assertNull(repository.getCardByCharacterId(CHARACTER_TWO));
    }

    @Test
    void cardsViewIsAnImmutableMembershipSnapshot() {
        CardRepository repository = new CardRepository();
        CharacterCard first = card(PLAYER_ONE, CHARACTER_ONE, "First");
        CharacterCard second = card(PLAYER_TWO, CHARACTER_TWO, "Second");
        repository.put(first);

        Collection<CharacterCard> snapshot = repository.getCards();
        assertEquals(1, snapshot.size());
        assertTrue(snapshot.contains(first));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());

        repository.put(second);
        assertEquals(1, snapshot.size());
        assertEquals(2, repository.getCards().size());
        assertFalse(snapshot.contains(second));

        repository.clear();
        assertEquals(0, repository.size());
        assertEquals(1, snapshot.size());
    }

    @Test
    void lastKnownPlayerNameIndexIsCaseInsensitiveAndTracksReplacement() {
        CardRepository repository = new CardRepository();
        CharacterCard first = CharacterCard.fromLines(
                CharacterCardTest.tenLines(
                        PLAYER_ONE, CHARACTER_ONE, 1L, "FirstAccount", "First"), 0L);
        CharacterCard replacement = CharacterCard.fromLines(
                CharacterCardTest.tenLines(
                        PLAYER_ONE, CHARACTER_TWO, 2L, "RenamedAccount", "Second"), 0L);

        repository.put(first);
        assertSame(first, repository.getCardByLastKnownPlayerName("  firstaccount "));

        repository.put(replacement);
        assertNull(repository.getCardByLastKnownPlayerName("FirstAccount"));
        assertSame(replacement, repository.getCardByLastKnownPlayerName("RENAMEDACCOUNT"));

        repository.remove(PLAYER_ONE);
        assertNull(repository.getCardByLastKnownPlayerName("RenamedAccount"));
    }

    @Test
    void oneCharacterIdCannotAliasTwoCurrentPlayers() {
        CardRepository repository = new CardRepository();
        CharacterCard first = card(PLAYER_ONE, CHARACTER_ONE, "First");
        CharacterCard conflicting = card(PLAYER_TWO, CHARACTER_ONE, "Conflicting");
        repository.put(first);

        assertThrows(IllegalArgumentException.class, () -> repository.put(conflicting));
        assertEquals(1, repository.size());
        assertSame(first, repository.getCard(PLAYER_ONE));
        assertSame(first, repository.getCardByCharacterId(CHARACTER_ONE));
        assertNull(repository.getCard(PLAYER_TWO));
    }

    @Test
    void reusedAccountNameIsAmbiguousInsteadOfReturningTheWrongPlayer() {
        CardRepository repository = new CardRepository();
        CharacterCard first = CharacterCard.fromLines(
                CharacterCardTest.tenLines(
                        PLAYER_ONE, CHARACTER_ONE, 1L, "SharedName", "First"), 0L);
        CharacterCard second = CharacterCard.fromLines(
                CharacterCardTest.tenLines(
                        PLAYER_TWO, CHARACTER_TWO, 2L, "SharedName", "Second"), 0L);

        repository.put(first);
        repository.put(second);
        assertNull(repository.getCardByLastKnownPlayerName("SharedName"));

        second.setLastKnownPlayerName("CurrentName");
        repository.put(second);
        assertSame(first, repository.getCardByLastKnownPlayerName("SharedName"));
        assertSame(second, repository.getCardByLastKnownPlayerName("CurrentName"));
    }

    @Test
    void nullLookupsAreSafe() {
        CardRepository repository = new CardRepository();

        assertNull(repository.getCard(null));
        assertNull(repository.getCardByCharacterId(null));
        assertNull(repository.getCardByLastKnownPlayerName(null));
        assertNull(repository.getCardByLastKnownPlayerName("  "));
        assertNull(repository.remove(null));
        assertFalse(repository.hasCard(null));
    }

    private static CharacterCard card(UUID playerId, UUID characterId, String name) {
        return CharacterCard.fromLines(
                CharacterCardTest.tenLines(playerId, characterId, 1L, "Account", name), 0L);
    }
}
