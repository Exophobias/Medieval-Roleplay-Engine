package dansplugins.rpsystem.cards;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;
import dansplugins.rpsystem.api.ForumCharacterEdit;
import dansplugins.rpsystem.api.ForumCharacterEditResult;
import dansplugins.rpsystem.api.ForumCharacterEditResult.Status;
import dansplugins.rpsystem.storage.CharacterHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ForumCharacterEditTest {

    @TempDir Path directory;

    @Test
    void createsInitialCharacterOnceAndPreservesIdentityOnRetryAndEdit() {
        Fixture fixture = fixture(ignored -> true);
        ForumCharacterEdit first = edit(fixture.player, null, null, "Aldric", "Human");

        ForumCharacterEditResult created = fixture.service.applyForumEdit(first);
        assertEquals(Status.APPLIED, created.status());
        assertEquals(CharacterStatus.ACTIVE, created.current().status());
        assertEquals("Player", created.current().lastKnownPlayerName());
        assertEquals(1, fixture.created.size());
        assertEquals(1, fixture.manifestWrites.get());
        assertEquals(1, fixture.startedCooldown.size());

        ForumCharacterEditResult retry = fixture.service.applyForumEdit(first);
        assertEquals(Status.UNCHANGED, retry.status());
        assertEquals(created.current(), retry.current());
        assertEquals(1, fixture.created.size());
        assertEquals(1, fixture.manifestWrites.get());

        ForumCharacterEdit update = edit(fixture.player, created.current().characterId(),
                created.current().editFingerprint(), "Aldric", "Dwarf");
        ForumCharacterEditResult updated = fixture.service.applyForumEdit(update);
        assertEquals(Status.APPLIED, updated.status());
        assertEquals(created.current().characterId(), updated.current().characterId());
        assertEquals("Dwarf", updated.current().race());
        assertEquals(1, fixture.updated.size());
        assertEquals(1, fixture.manifestWrites.get());
        assertEquals(Status.STALE, fixture.service.applyForumEdit(
                edit(fixture.player, created.current().characterId(),
                        created.current().editFingerprint(), "Aldric", "Elf")).status());
        assertEquals(Status.UNCHANGED, fixture.service.applyForumEdit(update).status());
        assertEquals(1, fixture.updated.size());
    }

    @Test
    void existingDraftNeedsItsExactIdAndFingerprintAndCannotBeReplaced() {
        Fixture fixture = fixture(ignored -> true);
        CharacterCard draft = CharacterCard.newDraft(fixture.player, "Player", 123L);
        fixture.cards.put(draft);
        CharacterRecord before = draft.snapshot();

        assertEquals(Status.STALE, fixture.service.applyForumEdit(
                edit(fixture.player, null, null, "Elowen", "Elf")).status());
        assertEquals(Status.STALE, fixture.service.applyForumEdit(
                edit(fixture.player, UUID.randomUUID(), before.editFingerprint(),
                        "Elowen", "Elf")).status());
        assertEquals(Status.STALE, fixture.service.applyForumEdit(
                edit(fixture.player, before.characterId(), "0".repeat(64),
                        "Elowen", "Elf")).status());
        assertEquals(before, fixture.service.currentCharacter(fixture.player).orElseThrow());

        ForumCharacterEditResult applied = fixture.service.applyForumEdit(
                edit(fixture.player, before.characterId(), before.editFingerprint(),
                        "Elowen", "Elf"));
        assertEquals(Status.APPLIED, applied.status());
        assertEquals(before.characterId(), applied.current().characterId());
        assertEquals(CharacterStatus.ACTIVE, applied.current().status());
    }

    @Test
    void draftCanSaveOtherFieldsBeforeChoosingAName() {
        Fixture fixture = fixture(ignored -> true);
        CharacterCard draft = CharacterCard.newDraft(fixture.player, "Player", 123L);
        fixture.cards.put(draft);
        CharacterRecord before = draft.snapshot();

        ForumCharacterEditResult saved = fixture.service.applyForumEdit(new ForumCharacterEdit(
                fixture.player, before.characterId(), before.editFingerprint(),
                "", "Elf", "Northmarcher", 37, "Female"));
        assertEquals(Status.APPLIED, saved.status());
        assertEquals(CharacterStatus.DRAFT, saved.current().status());
        assertEquals(before.characterId(), saved.current().characterId());
        assertTrue(fixture.startedCooldown.isEmpty());
        assertEquals(Status.UNCHANGED, fixture.service.applyForumEdit(new ForumCharacterEdit(
                fixture.player, before.characterId(), saved.current().editFingerprint(),
                CharacterCard.DEFAULT_NAME, "Elf", "Northmarcher", 37, "Female")).status());
    }

    @Test
    void archivedCharacterCannotBeEditedOrReplaceNewDraft() {
        Fixture fixture = fixture(ignored -> true);
        CharacterCard active = CharacterCard.newDraft(fixture.player, "Player", 100L);
        active.setName("Aldric");
        active.setRace("Human");
        fixture.cards.put(active);
        CharacterRecord before = active.snapshot();
        UUID approver = UUID.randomUUID();
        assertEquals(CharacterServiceImpl.EndResult.ENDED,
                fixture.service.endForTrueDeath(new CharacterServiceImpl.TrueDeathContext(
                        fixture.player, 150L, 200L, approver, "Retired")));

        ForumCharacterEditResult result = fixture.service.applyForumEdit(
                edit(fixture.player, before.characterId(), before.editFingerprint(),
                        "Aldric", "Dwarf"));
        assertEquals(Status.STALE, result.status());
        assertEquals(CharacterStatus.DRAFT, result.current().status());
        assertNotEquals(before.characterId(), result.current().characterId());
        assertEquals(1, fixture.history.history(fixture.player).size());
        assertEquals("Human", fixture.history.history(fixture.player).getFirst().race());
    }

    @Test
    void nameCooldownAppliesOnlyToDurableNameChangesAndActiveCannotReturnToDraft() {
        Fixture fixture = fixture(ignored -> true);
        CharacterCard active = CharacterCard.newDraft(fixture.player, "Player", 100L);
        active.setName("Aldric");
        active.setReligion("Old Shrine");
        fixture.cards.put(active);
        CharacterRecord before = active.snapshot();
        fixture.cooldowns.add(fixture.player);

        assertEquals(Status.NAME_COOLDOWN, fixture.service.applyForumEdit(
                edit(fixture.player, before.characterId(), before.editFingerprint(),
                        "Borin", "Human")).status());
        assertEquals(Status.INVALID, fixture.service.applyForumEdit(
                edit(fixture.player, before.characterId(), before.editFingerprint(),
                        "", "Human")).status());
        ForumCharacterEditResult raceOnly = fixture.service.applyForumEdit(
                edit(fixture.player, before.characterId(), before.editFingerprint(),
                        "Aldric", "Elf"));
        assertEquals(Status.APPLIED, raceOnly.status());
        assertEquals("Old Shrine", raceOnly.current().religion());
        assertTrue(fixture.startedCooldown.isEmpty());
        fixture.cooldowns.clear();

        assertEquals(Status.APPLIED, fixture.service.applyForumEdit(
                edit(fixture.player, before.characterId(),
                        raceOnly.current().editFingerprint(), "Borin", "Elf")).status());
        assertEquals(List.of(fixture.player), fixture.startedCooldown);
    }

    @Test
    void invalidOrFailedWritesLeaveCurrentCardAndEventsUntouched() {
        AtomicBoolean allowWrite = new AtomicBoolean(false);
        Fixture fixture = fixture(ignored -> allowWrite.get());
        ForumCharacterEdit first = edit(fixture.player, null, null, "Aldric", "Human");
        assertEquals(Status.STORAGE_FAILED, fixture.service.applyForumEdit(first).status());
        assertTrue(fixture.service.currentCharacter(fixture.player).isEmpty());
        assertTrue(fixture.created.isEmpty());
        assertTrue(fixture.startedCooldown.isEmpty());

        allowWrite.set(true);
        CharacterRecord created = fixture.service.applyForumEdit(first).current();
        allowWrite.set(false);
        ForumCharacterEdit update = edit(fixture.player, created.characterId(),
                created.editFingerprint(), "Aldric", "Dwarf");
        assertEquals(Status.STORAGE_FAILED, fixture.service.applyForumEdit(update).status());
        assertEquals(created, fixture.service.currentCharacter(fixture.player).orElseThrow());
        assertTrue(fixture.updated.isEmpty());
        assertEquals(Status.INVALID, fixture.service.applyForumEdit(
                edit(fixture.player, created.characterId(), created.editFingerprint(),
                        "Bad\nName", "Human")).status());
        assertEquals(Status.INVALID, fixture.service.applyForumEdit(
                edit(fixture.player, created.characterId(), created.editFingerprint(),
                        "Aldric", "x".repeat(129))).status());
        assertEquals(Status.INVALID, fixture.service.applyForumEdit(new ForumCharacterEdit(
                fixture.player, created.characterId(), created.editFingerprint(),
                "Aldric", "Human", "Culture", 1_000_001, "Female")).status());
        assertEquals(created, fixture.service.currentCharacter(fixture.player).orElseThrow());
    }

    @Test
    void offThreadCallFailsBeforeCheckingOrWritingRequest() {
        Fixture fixture = fixture(ignored -> true, false);
        assertThrows(IllegalStateException.class,
                () -> fixture.service.applyForumEdit(edit(fixture.player, null, null,
                        "Aldric", "Human")));
        assertTrue(fixture.cards.getCards().isEmpty());
    }

    private Fixture fixture(Predicate<CharacterCard> writer) {
        return fixture(writer, true);
    }

    private Fixture fixture(Predicate<CharacterCard> writer, boolean primaryThread) {
        Fixture f = new Fixture();
        f.player = UUID.randomUUID();
        f.cards = new CardRepository();
        f.history = new CharacterHistoryRepository(directory,
                Logger.getLogger("ForumCharacterEditTest"));
        f.created = new ArrayList<>();
        f.updated = new ArrayList<>();
        f.startedCooldown = new ArrayList<>();
        f.cooldowns = new HashSet<>();
        f.manifestWrites = new AtomicInteger();
        f.service = new CharacterServiceImpl(f.cards, f.history, writer,
                ignored -> { }, f.created::add, (previous, current) -> f.updated.add(current),
                f.cooldowns::contains, f.startedCooldown::add, () -> primaryThread,
                ignored -> "Player", f.manifestWrites::incrementAndGet,
                Logger.getLogger("ForumCharacterEditTest"));
        return f;
    }

    private static ForumCharacterEdit edit(UUID player, UUID character, String fingerprint,
                                            String name, String race) {
        return new ForumCharacterEdit(player, character, fingerprint, name, race,
                "Northmarcher", 37, "Female");
    }

    private static final class Fixture {
        UUID player;
        CardRepository cards;
        CharacterHistoryRepository history;
        CharacterServiceImpl service;
        List<CharacterRecord> created;
        List<CharacterRecord> updated;
        List<UUID> startedCooldown;
        Set<UUID> cooldowns;
        AtomicInteger manifestWrites;
    }
}
