package dansplugins.rpsystem.integrations.truedeath;

import dansplugins.rpsystem.cards.CharacterServiceImpl.TrueDeathContext;
import dansplugins.rpsystem.storage.AtomicFiles;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Durable composite identities for approved true deaths consumed by this plugin. */
final class TrueDeathJournal {

    private static final int SCHEMA_VERSION = 1;

    private final Path file;
    private final Set<String> processedDeaths = new HashSet<>();
    private boolean initialized;

    TrueDeathJournal(Path dataDirectory) {
        this.file = dataDirectory.toAbsolutePath().normalize().resolve("true-death-state.yml");
    }

    LoadResult load() {
        processedDeaths.clear();
        initialized = false;
        if (!Files.exists(file)) {
            return LoadResult.NEW;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file));
            if (yaml.getInt("schema-version") != SCHEMA_VERSION
                    || !yaml.getBoolean("initialized")) {
                return LoadResult.INVALID;
            }
            if (!yaml.isList("processed-deaths")) {
                return LoadResult.INVALID;
            }
            List<?> saved = yaml.getList("processed-deaths");
            if (saved == null) {
                return LoadResult.INVALID;
            }
            for (Object value : saved) {
                if (!(value instanceof String identity) || !validIdentity(identity)) {
                    return LoadResult.INVALID;
                }
                processedDeaths.add(identity);
            }
            initialized = true;
            return LoadResult.READY;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
            return LoadResult.INVALID;
        }
    }

    synchronized void establishBaseline(Collection<TrueDeathContext> deaths) throws IOException {
        processedDeaths.clear();
        for (TrueDeathContext death : deaths) {
            processedDeaths.add(identity(death));
        }
        initialized = true;
        save();
    }

    synchronized boolean processed(TrueDeathContext death) {
        return processedDeaths.contains(identity(death));
    }

    synchronized void markProcessed(TrueDeathContext death) throws IOException {
        if (!initialized) {
            throw new IllegalStateException("true-death journal has not been initialized");
        }
        processedDeaths.add(identity(death));
        save();
    }

    private void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        yaml.set("initialized", initialized);
        yaml.set("processed-deaths", processedDeaths.stream().sorted().toList());
        AtomicFiles.writeUtf8(file, yaml.saveToString());
    }

    static String identity(TrueDeathContext death) {
        return death.playerId() + "|" + death.declaredAt() + "|" + death.approvedAt()
                + "|" + death.approvedBy();
    }

    private static boolean validIdentity(String identity) {
        if (identity == null) {
            return false;
        }
        String[] parts = identity.split("\\|", -1);
        if (parts.length != 4) {
            return false;
        }
        try {
            java.util.UUID.fromString(parts[0]);
            long declaredAt = Long.parseLong(parts[1]);
            long approvedAt = Long.parseLong(parts[2]);
            java.util.UUID.fromString(parts[3]);
            return declaredAt >= 0 && approvedAt > 0 && declaredAt <= approvedAt;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    enum LoadResult {
        NEW,
        READY,
        INVALID
    }
}
