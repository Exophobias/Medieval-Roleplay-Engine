package dansplugins.rpsystem.storage;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import dansplugins.rpsystem.cards.CharacterCard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Stream;

/** Current-card persistence, including safe migration from both historical formats. */
public final class StorageService {

    private static final String MANIFEST = "cards.txt";

    private final MedievalRoleplayEngine plugin;
    private final Path dataDirectory;
    private final Path legacyDirectory;
    private final CurrentCardFiles currentFiles;

    public StorageService(MedievalRoleplayEngine plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        this.currentFiles = new CurrentCardFiles(dataDirectory);
        Path pluginsDirectory = dataDirectory.getParent();
        this.legacyDirectory = pluginsDirectory == null
                ? dataDirectory.resolveSibling("medieval-roleplay-engine")
                : pluginsDirectory.resolve("medieval-roleplay-engine");
    }

    public void saveCardFileNames() {
        try {
            List<String> names = plugin.cardRepository.getCards().stream()
                    .map(CharacterCard::getPlayerUUID)
                    .sorted()
                    .map(uuid -> uuid + ".txt")
                    .toList();
            String content = names.isEmpty() ? "" : String.join(System.lineSeparator(), names)
                    + System.lineSeparator();
            AtomicFiles.writeUtf8(dataDirectory.resolve(MANIFEST), content);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the character-card manifest", e);
        }
    }

    /** Saves one tiny current-card file atomically. */
    public boolean saveCard(CharacterCard card) {
        if (card == null) {
            return false;
        }
        try {
            currentFiles.write(card);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not save character card for " + card.getPlayerUUID(), e);
            return false;
        }
    }

    public void saveCards() {
        plugin.cardRepository.getCards().forEach(this::saveCard);
    }

    /**
     * Loads the manifest and also scans UUID files, so a crash between a card write and a manifest
     * write repairs itself at the next startup instead of orphaning the card.
     */
    public void loadCards() {
        plugin.cardRepository.clear();
        currentFiles.beginLoad();
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create the character data directory", e);
            return;
        }

        Set<Path> candidates = new LinkedHashSet<>();
        Path manifest = dataDirectory.resolve(MANIFEST);
        if (Files.isRegularFile(manifest)) {
            try {
                for (String line : Files.readAllLines(manifest)) {
                    String filename = line.trim();
                    if (!filename.isEmpty()) {
                        validatedCardPath(filename).ifPresent(candidates::add);
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not read cards.txt; UUID card files will still be scanned", e);
            }
        }

        try (Stream<Path> files = Files.list(dataDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase(MANIFEST))
                    .forEach(path -> validatedCardPath(path.getFileName().toString())
                            .ifPresent(candidates::add));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not scan current character cards", e);
        }

        Map<UUID, String> knownPlayerNames = plugin.uuidChecker.knownPlayerNamesByUuid();
        candidates.stream().sorted().forEach(path -> loadCard(path, knownPlayerNames));
        saveCardFileNames();
        plugin.getLogger().info("Loaded " + plugin.cardRepository.size() + " character cards.");
    }

    /**
     * Imports the pre-UUID-folder format and then renames that directory as a recoverable backup.
     * The old files are never deleted before every replacement file and the manifest are durable.
     */
    public boolean legacyLoadCards() {
        Path namesFile = legacyDirectory.resolve("card-player-names.txt");
        if (!Files.isRegularFile(namesFile)) {
            return false;
        }

        List<CharacterCard> imported = new ArrayList<>();
        int unresolvedEntries = 0;
        try {
            Map<String, UUID> knownPlayers = plugin.uuidChecker.knownPlayersByName();
            for (String rawName : Files.readAllLines(namesFile)) {
                String playerName = rawName.trim();
                if (playerName.isEmpty()) {
                    continue;
                }
                if (playerName.contains("/") || playerName.contains("\\")) {
                    unresolvedEntries++;
                    plugin.getLogger().warning("Ignoring unsafe legacy card owner entry "
                            + playerName);
                    continue;
                }
                UUID playerId = knownPlayers.get(playerName.toLowerCase(Locale.ROOT));
                if (playerId == null) {
                    unresolvedEntries++;
                    plugin.getLogger().warning("Could not resolve legacy card owner " + playerName);
                    continue;
                }
                if (plugin.cardRepository.hasCard(playerId)) {
                    plugin.getLogger().info("Keeping the existing current card for " + playerName
                            + " instead of overwriting it with a pre-UUID legacy copy.");
                    continue;
                }

                Path source = legacyDirectory.resolve(playerName + ".txt").normalize();
                if (!source.getParent().equals(legacyDirectory) || !Files.isRegularFile(source)) {
                    unresolvedEntries++;
                    plugin.getLogger().warning("Missing legacy card file for " + playerName);
                    continue;
                }
                List<String> lines = Files.readAllLines(source);
                if (lines.size() < 7) {
                    unresolvedEntries++;
                    plugin.getLogger().warning("Ignoring truncated legacy card " + source);
                    continue;
                }

                CharacterCard card = CharacterCard.newDraft(playerId, playerName,
                        Files.getLastModifiedTime(source).toMillis());
                card.setName(lines.get(1));
                card.setRace(lines.get(2));
                card.setSubculture(lines.get(3));
                card.setAge(Integer.parseInt(lines.get(4).trim()));
                card.setGender(lines.get(5));
                card.setReligion(lines.get(6));
                imported.add(card);
            }
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Legacy character migration stopped; the original directory was left intact", e);
            return false;
        }

        for (CharacterCard card : imported) {
            if (!saveCard(card)) {
                plugin.getLogger().severe(
                        "Legacy character migration was not finalized; original files remain intact.");
                return false;
            }
            plugin.cardRepository.put(card);
        }
        saveCardFileNames();

        if (unresolvedEntries > 0) {
            plugin.getLogger().severe("Imported every resolvable legacy character, but "
                    + unresolvedEntries + " source entr"
                    + (unresolvedEntries == 1 ? "y remains" : "ies remain")
                    + " unresolved. The original directory was left in place so startup can "
                    + "retry after those entries are repaired.");
            return false;
        }

        Path backup = legacyDirectory.resolveSibling(
                legacyDirectory.getFileName() + ".migrated-" + System.currentTimeMillis());
        try {
            Files.move(legacyDirectory, backup, StandardCopyOption.ATOMIC_MOVE);
            plugin.getLogger().info("Migrated " + imported.size()
                    + " legacy cards; original files were retained at " + backup);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Cards migrated, but the legacy directory could not be renamed; it was not deleted", e);
        }
        return true;
    }

    public boolean oldSaveFolderPresent() {
        return Files.isDirectory(legacyDirectory);
    }

    private void loadCard(Path path, Map<UUID, String> knownPlayerNames) {
        try {
            String filename = path.getFileName().toString();
            UUID filenameOwner = UUID.fromString(filename.substring(0, filename.length() - 4));
            CharacterCard card = CharacterCard.fromLines(
                    Files.readAllLines(path), Files.getLastModifiedTime(path).toMillis());
            if (!filenameOwner.equals(card.getPlayerUUID())) {
                throw new IOException("filename UUID does not match card owner UUID");
            }
            boolean hydratedName = false;
            if (card.getLastKnownPlayerName().isBlank()) {
                String knownName = knownPlayerNames.get(filenameOwner);
                hydratedName = knownName != null && card.setLastKnownPlayerName(knownName);
            }
            plugin.cardRepository.put(card);
            currentFiles.loaded(filenameOwner);
            if (card.needsMigration() || hydratedName) {
                saveCard(card);
            }
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Ignoring invalid character card " + path, e);
        }
    }

    private java.util.Optional<Path> validatedCardPath(String filename) {
        if (!filename.endsWith(".txt")) {
            plugin.getLogger().warning("Ignoring non-card entry in cards.txt: " + filename);
            return java.util.Optional.empty();
        }
        String uuidPart = filename.substring(0, filename.length() - 4);
        try {
            UUID owner = UUID.fromString(uuidPart);
            return java.util.Optional.of(cardPath(owner));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ignoring invalid card filename: " + filename);
            return java.util.Optional.empty();
        }
    }

    private Path cardPath(UUID playerId) {
        return dataDirectory.resolve(playerId + ".txt");
    }
}
