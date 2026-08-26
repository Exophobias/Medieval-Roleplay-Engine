package dansplugins.rpsystem.storage;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Append-only, UUID-indexed repository for characters ended by approved true death. */
public final class CharacterHistoryRepository {

    private static final int SCHEMA_VERSION = 1;

    private final Path historyDirectory;
    private final Logger logger;
    private final ConcurrentMap<UUID, ConcurrentSkipListMap<Long, CharacterRecord>> byPlayer =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CharacterRecord> byCharacter = new ConcurrentHashMap<>();

    public CharacterHistoryRepository(Path dataDirectory, Logger logger) {
        this.historyDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath().normalize().resolve("history");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void load() {
        byPlayer.clear();
        byCharacter.clear();
        if (!Files.isDirectory(historyDirectory)) {
            return;
        }

        try (Stream<Path> players = Files.list(historyDirectory)) {
            players.filter(Files::isDirectory).forEach(this::loadPlayerDirectory);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not enumerate character history", e);
        }
    }

    public synchronized ArchiveResult archive(CharacterRecord ended) throws IOException {
        requireEnded(ended);
        CharacterRecord existingById = byCharacter.get(ended.characterId());
        if (existingById != null) {
            if (existingById.equals(ended)) {
                return ArchiveResult.ALREADY_PRESENT;
            }
            throw new IOException("character id " + ended.characterId()
                    + " already belongs to a different archived record");
        }

        ConcurrentSkipListMap<Long, CharacterRecord> playerHistory = byPlayer.computeIfAbsent(
                ended.playerId(), ignored -> new ConcurrentSkipListMap<>());
        CharacterRecord existingAtDeath = playerHistory.get(ended.endedAt());
        if (existingAtDeath != null) {
            if (existingAtDeath.equals(ended)) {
                return ArchiveResult.ALREADY_PRESENT;
            }
            throw new IOException("player " + ended.playerId()
                    + " already has a different character ending at " + ended.endedAt());
        }

        Path target = fileFor(ended.playerId(), ended.endedAt());
        if (Files.exists(target)) {
            CharacterRecord onDisk = read(target);
            if (!onDisk.equals(ended)) {
                throw new IOException("archive file already contains a different record: " + target);
            }
        } else {
            AtomicFiles.writeUtf8(target, encode(ended));
        }

        playerHistory.put(ended.endedAt(), ended);
        byCharacter.put(ended.characterId(), ended);
        return ArchiveResult.ARCHIVED;
    }

    public List<CharacterRecord> history(UUID playerId) {
        ConcurrentSkipListMap<Long, CharacterRecord> history = byPlayer.get(playerId);
        return history == null ? List.of() : List.copyOf(history.descendingMap().values());
    }

    public Collection<CharacterRecord> all() {
        return byCharacter.values().stream()
                .sorted(Comparator.comparingLong(CharacterRecord::endedAt).reversed())
                .toList();
    }

    public Optional<CharacterRecord> character(UUID characterId) {
        return Optional.ofNullable(byCharacter.get(characterId));
    }

    public boolean containsDeath(UUID playerId, long endedAt) {
        Map<Long, CharacterRecord> history = byPlayer.get(playerId);
        return history != null && history.containsKey(endedAt);
    }

    public Optional<CharacterRecord> death(UUID playerId, long endedAt) {
        Map<Long, CharacterRecord> history = byPlayer.get(playerId);
        return history == null ? Optional.empty() : Optional.ofNullable(history.get(endedAt));
    }

    private void loadPlayerDirectory(Path playerDirectory) {
        UUID directoryPlayer;
        try {
            directoryPlayer = UUID.fromString(playerDirectory.getFileName().toString());
        } catch (IllegalArgumentException e) {
            logger.warning("Ignoring non-UUID character history directory " + playerDirectory);
            return;
        }

        try (Stream<Path> files = Files.list(playerDirectory)) {
            files.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".yml"))
                    .forEach(path -> loadOne(directoryPlayer, path));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not enumerate " + playerDirectory, e);
        }
    }

    private void loadOne(UUID directoryPlayer, Path file) {
        try {
            CharacterRecord record = read(file);
            if (!record.playerId().equals(directoryPlayer)) {
                throw new IOException("owner UUID does not match its directory");
            }
            if (!file.getFileName().toString().equals(record.endedAt() + ".yml")) {
                throw new IOException("archive filename does not match its end timestamp");
            }
            requireEnded(record);
            ConcurrentSkipListMap<Long, CharacterRecord> history = byPlayer.computeIfAbsent(
                    record.playerId(), ignored -> new ConcurrentSkipListMap<>());
            CharacterRecord atTimestamp = history.get(record.endedAt());
            CharacterRecord withId = byCharacter.get(record.characterId());
            if ((atTimestamp != null && !atTimestamp.equals(record))
                    || (withId != null && !withId.equals(record))) {
                throw new IOException("duplicate character id or end timestamp");
            }
            history.putIfAbsent(record.endedAt(), record);
            byCharacter.putIfAbsent(record.characterId(), record);
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Ignoring invalid character history file " + file, e);
        }
    }

    private CharacterRecord read(Path file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file));
        } catch (InvalidConfigurationException e) {
            throw new IOException("invalid YAML", e);
        }
        if (yaml.getInt("schema-version") != SCHEMA_VERSION) {
            throw new IOException("unsupported schema version " + yaml.get("schema-version"));
        }

        try {
            return new CharacterRecord(
                    UUID.fromString(required(yaml, "character.id")),
                    UUID.fromString(required(yaml, "owner.uuid")),
                    yaml.getString("owner.last-known-name", ""),
                    CharacterStatus.valueOf(required(yaml, "status")),
                    yaml.getLong("created-at"),
                    yaml.getLong("ended-at"),
                    yaml.getLong("death.declared-at"),
                    UUID.fromString(required(yaml, "death.approved-by")),
                    yaml.getString("death.reason", ""),
                    yaml.getString("fields.name", ""),
                    yaml.getString("fields.race", ""),
                    yaml.getString("fields.subculture", ""),
                    yaml.getInt("fields.age"),
                    yaml.getString("fields.gender", ""),
                    yaml.getString("fields.religion", ""));
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid character value", e);
        }
    }

    private String encode(CharacterRecord record) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        yaml.set("character.id", record.characterId().toString());
        yaml.set("owner.uuid", record.playerId().toString());
        yaml.set("owner.last-known-name", record.lastKnownPlayerName());
        yaml.set("status", record.status().name());
        yaml.set("created-at", record.createdAt());
        yaml.set("ended-at", record.endedAt());
        yaml.set("death.declared-at", record.deathDeclaredAt());
        yaml.set("death.approved-by", record.deathApprovedBy().toString());
        yaml.set("death.reason", record.endReason());
        yaml.set("fields.name", record.name());
        yaml.set("fields.race", record.race());
        yaml.set("fields.subculture", record.subculture());
        yaml.set("fields.age", record.age());
        yaml.set("fields.gender", record.gender());
        yaml.set("fields.religion", record.religion());
        return yaml.saveToString();
    }

    private Path fileFor(UUID playerId, long endedAt) {
        return historyDirectory.resolve(playerId.toString()).resolve(endedAt + ".yml");
    }

    private static String required(YamlConfiguration yaml, String path) throws IOException {
        String value = yaml.getString(path);
        if (value == null || value.isBlank()) {
            throw new IOException("missing required value " + path);
        }
        return value;
    }

    private static void requireEnded(CharacterRecord record) {
        if (record.status() != CharacterStatus.DECEASED) {
            throw new IllegalArgumentException("only deceased characters can be archived here");
        }
    }

    public enum ArchiveResult {
        ARCHIVED,
        ALREADY_PRESENT
    }
}
