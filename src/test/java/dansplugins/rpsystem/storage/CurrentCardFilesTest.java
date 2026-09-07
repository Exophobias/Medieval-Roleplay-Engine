package dansplugins.rpsystem.storage;

import dansplugins.rpsystem.cards.CharacterCard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CurrentCardFilesTest {
    @TempDir Path directory;

    @Test void failedLoadCannotBeReplacedByLoginDraftOrLegacyImport() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = directory.resolve(owner + ".txt");
        Files.writeString(file, "truncated current character\n");
        CurrentCardFiles files = new CurrentCardFiles(directory);
        files.beginLoad();
        assertThrows(IllegalArgumentException.class,
                () -> CharacterCard.fromLines(Files.readAllLines(file), 1));
        assertThrows(IOException.class, () -> files.write(CharacterCard.newDraft(owner, "Player", 2)));
        assertEquals("truncated current character\n", Files.readString(file));
    }

    @Test void freshAndSuccessfullyLoadedCardsStillSaveAndReplace() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = directory.resolve(owner + ".txt");
        CurrentCardFiles files = new CurrentCardFiles(directory);
        CharacterCard card = CharacterCard.newDraft(owner, "Player", 1);
        files.write(card);
        files.beginLoad();
        CharacterCard loaded = CharacterCard.fromLines(Files.readAllLines(file), 1);
        files.loaded(loaded.getPlayerUUID());
        loaded.setName("Updated Character");
        files.write(loaded);
        assertEquals("Updated Character", CharacterCard.fromLines(Files.readAllLines(file), 1).getName());
    }

    @Test void reloadForgetsPreviouslyReadableOwnerAndPreservesNewCorruption() throws Exception {
        UUID owner = UUID.randomUUID();
        Path file = directory.resolve(owner + ".txt");
        CurrentCardFiles files = new CurrentCardFiles(directory);
        CharacterCard card = CharacterCard.newDraft(owner, "Player", 1);
        files.write(card);
        Files.writeString(file, "broken");
        files.beginLoad();
        assertThrows(IOException.class, () -> files.write(card));
        assertEquals("broken", Files.readString(file));
    }

    @Test void directoryAtOwnerPathIsNeverTreatedAsANewPlayer() throws Exception {
        UUID owner = UUID.randomUUID();
        Files.createDirectory(directory.resolve(owner + ".txt"));
        assertThrows(IOException.class, () -> new CurrentCardFiles(directory)
                .write(CharacterCard.newDraft(owner, "Player", 1)));
    }
}
