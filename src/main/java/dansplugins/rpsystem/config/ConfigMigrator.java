package dansplugins.rpsystem.config;

import dansplugins.rpsystem.storage.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Upgrades {@code config.yml} without confusing a plugin release with a config schema.
 *
 * <p>An unversioned file is schema zero. Migrations are sequential so a future schema can
 * upgrade every supported predecessor without guessing which transformations already ran.
 * Explicit administrator values and unknown keys are retained.</p>
 */
public final class ConfigMigrator {
    public static final String VERSION_KEY = "config-version";
    public static final int CURRENT_VERSION = 1;

    private static final List<String> BOOLEAN_PATHS = List.of(
            "rightClickToViewCard",
            "trueDeathIntegrationEnabled",
            "planIntegrationEnabled",
            "legacyReligionFieldEnabled",
            "exposeReligionPlaceholder",
            "chatFeaturesEnabled",
            "debugMode",
            "logChat"
    );
    private static final List<String> NON_NEGATIVE_INTEGER_PATHS = List.of(
            "changeNameCooldown",
            "localChatRadius",
            "whisperChatRadius",
            "yellChatRadius",
            "emoteRadius",
            "localOOCChatRadius"
    );
    private static final List<String> STRING_PATHS = List.of(
            "version",
            "emoteColor",
            "positiveAlertColor",
            "neutralAlertColor",
            "negativeAlertColor",
            "localChatColor",
            "whisperChatColor",
            "yellChatColor",
            "localOOCChatColor"
    );

    private ConfigMigrator() {
    }

    @FunctionalInterface
    interface ConfigWriter {
        void write(Path path, String contents, byte[] expectedCurrent) throws IOException;
    }

    public enum State {
        CURRENT,
        UPGRADED,
        INVALID,
        FUTURE,
        ERROR
    }

    public record Result(State state, int sourceVersion, Path backup, String detail) {
        public boolean compatible() {
            return state == State.CURRENT || state == State.UPGRADED;
        }
    }

    /**
     * Inspects and, when necessary, atomically upgrades one on-disk configuration.
     *
     * @param configFile    installed configuration
     * @param bundledYaml   filtered configuration shipped inside this plugin jar
     * @param pluginVersion plugin release which performs the migration
     * @return a result which tells the caller whether startup may continue
     */
    public static Result upgrade(Path configFile, String bundledYaml, String pluginVersion) {
        return upgrade(configFile, bundledYaml, pluginVersion,
                AtomicFiles::writeUtf8AtomicRequired);
    }

    static Result upgrade(Path configFile, String bundledYaml, String pluginVersion,
                          ConfigWriter writer) {
        Objects.requireNonNull(configFile, "configFile");
        Objects.requireNonNull(bundledYaml, "bundledYaml");
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        Objects.requireNonNull(writer, "writer");

        final byte[] installedBytes;
        try {
            installedBytes = Files.readAllBytes(configFile);
        } catch (IOException e) {
            return new Result(State.ERROR, -1, null,
                    "config.yml could not be read");
        }

        final String installedYaml;
        try {
            installedYaml = decodeUtf8(installedBytes);
        } catch (CharacterCodingException e) {
            return new Result(State.ERROR, -1, null,
                    "the installed config.yml is not valid UTF-8");
        }

        final YamlConfiguration installed;
        try {
            installed = parse(installedYaml);
        } catch (InvalidConfigurationException e) {
            // Parser messages can repeat the offending line. Do not put arbitrary configuration
            // values into the server log, even though MRE itself currently stores no credentials.
            return new Result(State.ERROR, -1, null,
                    "the installed config.yml is not valid YAML");
        }

        final YamlConfiguration defaults;
        try {
            defaults = parse(bundledYaml);
        } catch (InvalidConfigurationException e) {
            return new Result(State.ERROR, -1, null,
                    "the plugin jar contains an invalid default config.yml");
        }

        List<String> bundledErrors = validateBundledDefaults(defaults);
        if (!bundledErrors.isEmpty()) {
            return new Result(State.ERROR, -1, null,
                    "the plugin jar contains invalid configuration defaults: "
                            + String.join("; ", bundledErrors));
        }

        Version configured = readVersion(installed);
        if (!configured.valid()) {
            return new Result(State.INVALID, -1, null,
                    VERSION_KEY + " must be an unquoted, non-negative integer");
        }
        if (configured.value() > CURRENT_VERSION) {
            return new Result(State.FUTURE, configured.value(), null,
                    "schema v" + configured.value() + " is newer than supported schema v"
                            + CURRENT_VERSION);
        }

        int sourceVersion = configured.value();
        if (sourceVersion < CURRENT_VERSION) {
            int workingVersion = sourceVersion;
            while (workingVersion < CURRENT_VERSION) {
                switch (workingVersion) {
                    case 0 -> migrateZeroToOne(installed, defaults, pluginVersion);
                    default -> {
                        return new Result(State.ERROR, sourceVersion, null,
                                "no migration exists from schema v" + workingVersion);
                    }
                }
                workingVersion++;
            }
            installed.set(VERSION_KEY, CURRENT_VERSION);
            installed.set("version", pluginVersion);
        }

        List<String> errors = validateExplicitValues(installed);
        if (!errors.isEmpty()) {
            return new Result(State.INVALID, sourceVersion, null,
                    "invalid configuration: " + String.join("; ", errors));
        }
        if (sourceVersion == CURRENT_VERSION) {
            return new Result(State.CURRENT, sourceVersion, null,
                    "schema v" + CURRENT_VERSION + " is current");
        }

        final Path backup;
        try {
            backup = createVerifiedBackup(configFile, installedBytes, sourceVersion);
        } catch (BackupSnapshotChangedException e) {
            return new Result(State.ERROR, sourceVersion, null,
                    "config.yml changed while its migration backup was being prepared; retry "
                            + "startup");
        } catch (IOException e) {
            return new Result(State.ERROR, sourceVersion, null,
                    "config.yml could not be backed up safely");
        }
        try {
            writer.write(configFile, installed.saveToString(), installedBytes);
        } catch (AtomicFiles.FileContentChangedException e) {
            return new Result(State.ERROR, sourceVersion, backup,
                    "config.yml changed while its migration replacement was prepared; stop the "
                            + "editor and retry startup. The original snapshot remains in "
                            + backup.getFileName());
        } catch (AtomicMoveNotSupportedException e) {
            return new Result(State.ERROR, sourceVersion, backup,
                    "the filesystem cannot atomically replace config.yml, so migration was "
                            + "refused. The original remains available in "
                            + backup.getFileName());
        } catch (IOException e) {
            return new Result(State.ERROR, sourceVersion, backup,
                    "the migrated config.yml could not replace the installed file; the original "
                            + "remains available in " + backup.getFileName());
        }

        return new Result(State.UPGRADED, sourceVersion, backup,
                "upgraded schema v" + sourceVersion + " to v" + CURRENT_VERSION);
    }

    static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    /** Validates the explicit values in the exact configuration snapshot Bukkit loaded. */
    public static List<String> validateCurrentConfiguration(ConfigurationSection installed) {
        Objects.requireNonNull(installed, "installed");
        List<String> errors = new ArrayList<>();
        Version version = readVersion(installed);
        if (!version.valid()) {
            errors.add(VERSION_KEY + " must be an unquoted, non-negative integer");
        } else if (version.value() != CURRENT_VERSION) {
            errors.add(VERSION_KEY + " must equal " + CURRENT_VERSION + " but is "
                    + version.value());
        }
        errors.addAll(validateExplicitValues(installed));
        return List.copyOf(errors);
    }

    public static boolean hasCurrentVersion(ConfigurationSection installed) {
        Objects.requireNonNull(installed, "installed");
        Version version = readVersion(installed);
        return version.valid() && version.value() == CURRENT_VERSION;
    }

    private static YamlConfiguration parse(String yaml) throws InvalidConfigurationException {
        YamlConfiguration parsed = new YamlConfiguration();
        parsed.options().parseComments(true);
        parsed.loadFromString(yaml);
        return parsed;
    }

    private static Version readVersion(ConfigurationSection installed) {
        // ignoreDefault=true is intentional. Bundled defaults must never make an unversioned
        // installed file look current if defaults are attached to this object in the future.
        if (!installed.contains(VERSION_KEY, true)) {
            return new Version(true, 0);
        }

        Object raw = installed.get(VERSION_KEY);
        if (!isIntegerNumber(raw)) {
            return new Version(false, -1);
        }
        Number number = (Number) raw;
        long whole = number.longValue();
        if (whole < 0 || whole > Integer.MAX_VALUE) {
            return new Version(false, -1);
        }
        return new Version(true, (int) whole);
    }

    private static void migrateZeroToOne(YamlConfiguration installed,
                                          YamlConfiguration defaults,
                                          String pluginVersion) {
        // Preserve the historical typo's value only when the canonical key is genuinely absent.
        if (!installed.contains("neutralAlertColor", true)
                && installed.get("neurtalAlertColor") instanceof String legacyColour) {
            installed.set("neutralAlertColor", legacyColour);
        }
        installed.set("neurtalAlertColor", null);
        installed.set("test", null);

        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)
                    || VERSION_KEY.equals(path)
                    || "version".equals(path)
                    || installed.contains(path, true)) {
                continue;
            }
            installed.set(path, defaults.get(path));
            installed.setComments(path, defaults.getComments(path));
            installed.setInlineComments(path, defaults.getInlineComments(path));
        }
        installed.set("version", pluginVersion);
    }

    private static List<String> validateExplicitValues(ConfigurationSection installed) {
        List<String> errors = new ArrayList<>();
        for (String path : BOOLEAN_PATHS) {
            if (installed.contains(path, true) && !(installed.get(path) instanceof Boolean)) {
                errors.add(path + " must be true or false without quotes");
            }
        }
        for (String path : NON_NEGATIVE_INTEGER_PATHS) {
            validateInteger(installed, path, 0, errors);
        }
        validateInteger(installed, "birdSpeed", 1, errors);
        for (String path : STRING_PATHS) {
            if (installed.contains(path, true) && !(installed.get(path) instanceof String)) {
                errors.add(path + " must be text");
            }
        }
        return errors;
    }

    private static List<String> validateBundledDefaults(YamlConfiguration defaults) {
        List<String> errors = new ArrayList<>();
        Version version = readVersion(defaults);
        if (!version.valid() || version.value() != CURRENT_VERSION) {
            errors.add(VERSION_KEY + " must equal " + CURRENT_VERSION);
        }
        requirePaths(defaults, BOOLEAN_PATHS, errors);
        requirePaths(defaults, NON_NEGATIVE_INTEGER_PATHS, errors);
        requirePaths(defaults, STRING_PATHS, errors);
        if (!defaults.contains("birdSpeed", true)) {
            errors.add("birdSpeed is missing");
        }
        errors.addAll(validateExplicitValues(defaults));
        return errors;
    }

    private static void requirePaths(YamlConfiguration defaults, List<String> paths,
                                     List<String> errors) {
        for (String path : paths) {
            if (!defaults.contains(path, true)) {
                errors.add(path + " is missing");
            }
        }
    }

    private static void validateInteger(ConfigurationSection installed, String path, int minimum,
                                        List<String> errors) {
        if (!installed.contains(path, true)) {
            return;
        }
        Object raw = installed.get(path);
        if (!isIntegerNumber(raw)
                || ((Number) raw).longValue() < minimum
                || ((Number) raw).longValue() > Integer.MAX_VALUE) {
            errors.add(path + " must be an integer of at least " + minimum);
        }
    }

    private static boolean isIntegerNumber(Object raw) {
        return raw instanceof Byte || raw instanceof Short
                || raw instanceof Integer || raw instanceof Long;
    }

    private static Path nextBackupPath(Path configFile, int sourceVersion) throws IOException {
        Path parent = configFile.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("config.yml has no parent directory");
        }
        String base = configFile.getFileName() + ".v" + sourceVersion + ".bak";
        Path candidate = parent.resolve(base);
        for (int suffix = 1; Files.exists(candidate); suffix++) {
            candidate = parent.resolve(base + "." + suffix);
        }
        return candidate;
    }

    private static Path createVerifiedBackup(Path configFile, byte[] installedBytes,
                                             int sourceVersion) throws IOException {
        Path parent = configFile.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("config.yml has no parent directory");
        }
        Path temporary = Files.createTempFile(parent,
                "." + configFile.getFileName() + ".v" + sourceVersion + ".bak-", ".tmp");
        boolean promoted = false;
        try {
            Files.copy(configFile, temporary, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            if (!Arrays.equals(installedBytes, Files.readAllBytes(temporary))) {
                throw new BackupSnapshotChangedException();
            }
            Path backup = nextBackupPath(configFile, sourceVersion);
            Files.move(temporary, backup, StandardCopyOption.ATOMIC_MOVE);
            promoted = true;
            return backup;
        } finally {
            if (!promoted) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static final class BackupSnapshotChangedException extends IOException {
        private BackupSnapshotChangedException() {
            super("config snapshot changed while backup was being prepared");
        }
    }

    private record Version(boolean valid, int value) {
    }
}
