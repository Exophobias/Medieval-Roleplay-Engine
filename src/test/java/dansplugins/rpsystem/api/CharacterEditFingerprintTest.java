package dansplugins.rpsystem.api;

import dansplugins.rpsystem.cards.CharacterCard;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterEditFingerprintTest {

    @Test
    void fingerprintTracksEditableFieldsAndIdentityButNotOtherMetadata() {
        UUID player = UUID.randomUUID();
        CharacterCard card = CharacterCard.newDraft(player, "Account", 100L);
        CharacterRecord first = card.snapshot();
        String fingerprint = first.editFingerprint();
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
        assertEquals(fingerprint, card.snapshot().editFingerprint());

        card.setReligion("Old Shrine");
        card.setLastKnownPlayerName("RenamedAccount");
        card.setRace("Legacy race is no longer editable");
        assertEquals(fingerprint, card.snapshot().editFingerprint());

        card.setNationality("Western March");
        String withNationality = card.snapshot().editFingerprint();
        assertNotEquals(fingerprint, withNationality);

        card.setOriginDescription("Born at sea.\nRaised ashore.");
        String withOrigin = card.snapshot().editFingerprint();
        assertNotEquals(withNationality, withOrigin);
        card.setShowStoryPublicly(true);
        assertNotEquals(withOrigin, card.snapshot().editFingerprint());

        card.setName("Aldric");
        assertNotEquals(fingerprint, card.snapshot().editFingerprint());
        assertNotEquals(card.snapshot().editFingerprint(),
                CharacterCard.newDraft(player, "Account", 100L).snapshot().editFingerprint());
    }
}
