package dansplugins.rpsystem.cards;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import dansplugins.rpsystem.utils.Logger;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Characterizes how {@link CardLookupServiceImpl} finds a player's card. {@code /card} (with or without a player name)
 * looks cards up through this service rather than through {@link CardRepository} directly.
 */
public class CardLookupServiceImplTest {
    private final CardRepository cardRepository = new CardRepository();
    private final List<String> logged = new ArrayList<>();
    private CardLookupServiceImpl lookupService;

    @Before
    public void setUp() throws Exception {
        lookupService = new CardLookupServiceImpl(plugin());
    }

    @Test
    public void aCardInTheRepositoryIsReturnedAndReportedAsFoundInStorage() {
        UUID uuid = UUID.randomUUID();
        CharacterCard card = new CharacterCard(uuid);
        cardRepository.put(new CharacterCard(UUID.randomUUID()));
        cardRepository.put(card);

        assertSame(card, lookupService.lookup(uuid));
        assertEquals(Collections.singletonList("Character card found for " + uuid), logged);
    }

    @Test
    public void aSecondLookupReturnsTheCurrentRecord() {
        UUID uuid = UUID.randomUUID();
        CharacterCard card = new CharacterCard(uuid);
        cardRepository.put(card);
        lookupService.lookup(uuid);
        logged.clear();

        assertSame(card, lookupService.lookup(uuid));
        assertEquals(Collections.singletonList("Character card found for " + uuid), logged);
    }

    @Test
    public void anEqualButDistinctUUIDInstanceIsFoundInTheIndex() {
        UUID uuid = UUID.randomUUID();
        CharacterCard card = new CharacterCard(uuid);
        cardRepository.put(card);
        lookupService.lookup(uuid);
        logged.clear();

        assertSame(card, lookupService.lookup(UUID.fromString(uuid.toString())));
        assertEquals("Character card found for " + uuid, logged.get(0));
    }

    @Test
    public void anUnknownPlayerGetsNullAndIsReportedAsNotFound() {
        cardRepository.put(new CharacterCard(UUID.randomUUID()));
        UUID unknown = UUID.randomUUID();

        assertNull(lookupService.lookup(unknown));
        assertEquals(Collections.singletonList("Character card not found for " + unknown), logged);
    }

    @Test
    public void aMissIsNotCachedSoACardAddedLaterIsFound() {
        UUID uuid = UUID.randomUUID();
        assertNull(lookupService.lookup(uuid));

        CharacterCard card = new CharacterCard(uuid);
        cardRepository.put(card);
        logged.clear();

        assertSame(card, lookupService.lookup(uuid));
        assertEquals("Character card found for " + uuid, logged.get(0));
    }

    @Test
    public void aReplacedCardIsReturnedOnTheNextLookup() {
        UUID uuid = UUID.randomUUID();
        CharacterCard original = new CharacterCard(uuid);
        cardRepository.put(original);
        lookupService.lookup(uuid);

        cardRepository.clear();
        cardRepository.put(new CharacterCard(uuid));

        assertSame(cardRepository.getCard(uuid), lookupService.lookup(uuid));
    }

    @Test
    public void changesToACachedCardAreVisibleThroughLaterLookups() {
        UUID uuid = UUID.randomUUID();
        CharacterCard card = new CharacterCard(uuid);
        cardRepository.put(card);
        lookupService.lookup(uuid);

        card.setName("Aldric");

        assertEquals("Aldric", lookupService.lookup(uuid).getName());
    }

    @Test
    public void eachPlayersCardIsCachedSeparately() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CharacterCard firstCard = new CharacterCard(first);
        CharacterCard secondCard = new CharacterCard(second);
        cardRepository.put(firstCard);
        cardRepository.put(secondCard);
        lookupService.lookup(first);
        lookupService.lookup(second);
        logged.clear();

        assertSame(firstCard, lookupService.lookup(first));
        assertSame(secondCard, lookupService.lookup(second));
        assertEquals(java.util.List.of("Character card found for " + first, "Character card found for " + second), logged);
    }

    /** Allocates the plugin without running {@code JavaPlugin}'s constructor, then sets what the service reads. */
    private MedievalRoleplayEngine plugin() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        MedievalRoleplayEngine plugin =
                (MedievalRoleplayEngine) allocateInstance.invoke(unsafe, MedievalRoleplayEngine.class);
        setField(plugin, "cardRepository", cardRepository);
        setField(plugin, "logger", new RecordingLogger(logged));
        return plugin;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = MedievalRoleplayEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Records each message instead of consulting {@code debugMode}, which would need a {@code config.yml}. */
    private static final class RecordingLogger extends Logger {
        private final List<String> messages;

        RecordingLogger(List<String> messages) {
            super(null);
            this.messages = messages;
        }

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}
