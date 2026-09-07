package dansplugins.rpsystem.storage;

import dansplugins.rpsystem.cards.CharacterCard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Only successfully loaded or written current cards may replace an existing owner file. */
final class CurrentCardFiles {
    private final Path directory;
    private final Set<UUID> readableOwners = new HashSet<>();

    CurrentCardFiles(Path directory) {
        this.directory = directory;
    }

    void beginLoad() {
        readableOwners.clear();
    }

    void loaded(UUID owner) {
        readableOwners.add(owner);
    }

    void write(CharacterCard card) throws IOException {
        UUID owner = card.getPlayerUUID();
        Path target = directory.resolve(owner + ".txt");
        // isRegularFile/exists alone also treat inaccessible paths as absent. Only a positive
        // notExists result establishes that a new player has no current file to preserve.
        if (!readableOwners.contains(owner)
                && !Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to replace an unreadable or unverified character card: "
                    + target);
        }
        String content = String.join(System.lineSeparator(), card.serializedLines())
                + System.lineSeparator();
        AtomicFiles.writeUtf8(target, content);
        readableOwners.add(owner);
        card.markPersisted();
    }
}
