package dansplugins.rpsystem.config;

import dansplugins.rpsystem.storage.AtomicFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void unversionedConfigurationIsUpgradedWithoutOverwritingAdministratorValues()
            throws Exception {
        Path config = write("""
                version: old-build
                chatFeaturesEnabled: true
                changeNameCooldown: 900
                neurtalAlertColor: gold
                private-extension:
                  # retained comment
                  retained: secret
                  list: [one, two]
                test: 1
                """);
        byte[] original = Files.readAllBytes(config);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test");

        assertEquals(ConfigMigrator.State.UPGRADED, result.state());
        assertEquals(0, result.sourceVersion());
        assertNotNull(result.backup());
        assertArrayEquals(original, Files.readAllBytes(result.backup()));

        YamlConfiguration upgraded = strictLoad(config);
        assertEquals(ConfigMigrator.CURRENT_VERSION,
                upgraded.getInt(ConfigMigrator.VERSION_KEY));
        assertEquals("v2.0.0-test", upgraded.getString("version"));
        assertTrue(upgraded.getBoolean("chatFeaturesEnabled"));
        assertEquals(900, upgraded.getInt("changeNameCooldown"));
        assertEquals("gold", upgraded.getString("neutralAlertColor"));
        assertFalse(upgraded.contains("neurtalAlertColor", true));
        assertFalse(upgraded.contains("test", true));
        assertEquals("secret", upgraded.getString("private-extension.retained"));
        assertEquals(java.util.List.of("one", "two"),
                upgraded.getStringList("private-extension.list"));
        assertTrue(upgraded.getComments("private-extension.retained")
                .contains("retained comment"));
        assertTrue(upgraded.contains("planIntegrationEnabled", true));

        List<String> expectedOrder = new ArrayList<>(
                parse(bundledConfig()).getKeys(false));
        expectedOrder.add("private-extension");
        assertEquals(expectedOrder, new ArrayList<>(upgraded.getKeys(false)),
                "known keys must follow the bundled template and unknown roots come last");
        assertEquals(parse(bundledConfig()).getComments("planIntegrationEnabled"),
                upgraded.getComments("planIntegrationEnabled"),
                "a newly introduced key keeps its current bundled placement and comments");

        ConfigMigrator.Result second = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-newer");
        assertEquals(ConfigMigrator.State.CURRENT, second.state());
        assertEquals(2, fileCount());
    }

    @Test
    void canonicalColourWinsWhenBothHistoricalSpellingsExist() throws Exception {
        Path config = write("""
                neutralAlertColor: blue
                neurtalAlertColor: gold
                """);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test");

        assertEquals(ConfigMigrator.State.UPGRADED, result.state());
        YamlConfiguration upgraded = strictLoad(config);
        assertEquals("blue", upgraded.getString("neutralAlertColor"));
        assertFalse(upgraded.contains("neurtalAlertColor", true));
    }

    @Test
    void currentConfigurationIsNotRewrittenOrBackedUp() throws Exception {
        Path config = write("config-version: 1\nversion: existing\n");
        String original = Files.readString(config);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-newer");

        assertEquals(ConfigMigrator.State.CURRENT, result.state());
        assertNull(result.backup());
        assertEquals(original, Files.readString(config));
        assertEquals(1, fileCount());
    }

    @Test
    void aFlowRootCurrentMarkerIsAcceptedWithoutRewrite() throws Exception {
        Path config = write("{config-version: 1, version: existing}\n");
        byte[] original = Files.readAllBytes(config);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-newer");

        assertEquals(ConfigMigrator.State.CURRENT, result.state());
        assertArrayEquals(original, Files.readAllBytes(config));
        assertNull(result.backup());
    }

    @Test
    void secretBearingMigrationFilesAreOwnerOnly() throws Exception {
        Path config = write("private-extension:\n  api-key: keep-secret\n");

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test");

        assertEquals(ConfigMigrator.State.UPGRADED, result.state());
        assertOwnerOnly(config);
        assertOwnerOnly(result.backup());
    }

    @Test
    void currentConfigurationCanUseAndEditBundledDefaultsWithoutARewrite() throws Exception {
        Path config = write("config-version: 1\nversion: existing\n");

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-newer");

        assertEquals(ConfigMigrator.State.CURRENT, result.state());
        YamlConfiguration installed = strictLoad(config);
        installed.setDefaults(parse(bundledConfig()));
        installed.options().copyDefaults(true);
        assertTrue(installed.isSet("planIntegrationEnabled"));
        assertTrue(installed.getBoolean("planIntegrationEnabled"));
    }

    @Test
    void futureConfigurationFailsClosedAndRemainsUntouched() throws Exception {
        Path config = write("config-version: 2\napi-key: keep-me\n");
        String original = Files.readString(config);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test");

        assertEquals(ConfigMigrator.State.FUTURE, result.state());
        assertFalse(result.compatible());
        assertEquals(original, Files.readString(config));
        assertEquals(1, fileCount());
    }

    @Test
    void quotedFractionalNegativeOrBooleanSchemaVersionsAreRejected() throws Exception {
        int index = 0;
        for (String value : new String[]{"'1'", "1.0", "1.5", "-1", "true", "null",
                "", "~", "!!int \"1\""}) {
            Path config = temporaryDirectory.resolve("config-" + index++ + ".yml");
            Files.writeString(config, "config-version: " + value + "\n");
            String original = Files.readString(config);

            ConfigMigrator.Result result = ConfigMigrator.upgrade(
                    config, bundledConfig(), "v2.0.0-test");

            assertEquals(ConfigMigrator.State.INVALID, result.state(), value);
            assertEquals(original, Files.readString(config), value);
        }
    }

    @Test
    void duplicateSchemaDeclarationsAreRejectedWithoutAWrite() throws Exception {
        int index = 0;
        for (String document : List.of(
                "config-version: 2\nconfig-version: 0\n",
                "config-version: 2\n\"config-version\": 0\n",
                "config-version: 2\n\"config\\u002dversion\": 0\n",
                "\"config-version\": 1\n")) {
            Path config = temporaryDirectory.resolve("duplicate-" + index++ + ".yml");
            Files.writeString(config, document);
            String original = Files.readString(config);

            ConfigMigrator.Result result = ConfigMigrator.upgrade(
                    config, bundledConfig(), "v2.0.0-test");

            assertEquals(ConfigMigrator.State.INVALID, result.state(), document);
            assertEquals(original, Files.readString(config), document);
        }
        assertEquals(4, fileCount());
    }

    @Test
    void nullExtensionValueIsRejectedRatherThanSilentlyDeleted() throws Exception {
        Path config = write("extension: null\n");
        String original = Files.readString(config);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test");

        assertEquals(ConfigMigrator.State.INVALID, result.state());
        assertEquals(original, Files.readString(config));
        assertEquals(1, fileCount());
    }

    @Test
    void invalidKnownValueIsRejectedBeforeMigrationWritesAnything() throws Exception {
        Path config = write("""
                config-version: 1
                birdSpeed: 0
                planIntegrationEnabled: 'true'
                """);
        String original = Files.readString(config);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test");

        assertEquals(ConfigMigrator.State.INVALID, result.state());
        assertTrue(result.detail().contains("birdSpeed"));
        assertTrue(result.detail().contains("planIntegrationEnabled"));
        assertEquals(original, Files.readString(config));
        assertEquals(1, fileCount());
    }

    @Test
    void invalidBundledSchemaOrDefaultsBlockStartupWithoutTouchingInstalledFile()
            throws Exception {
        Path config = write("config-version: 1\nversion: existing\n");
        String original = Files.readString(config);
        String invalidDefaults = bundledConfig()
                .replace("config-version: 1", "config-version: 2")
                .replace("birdSpeed: 20", "birdSpeed: 'fast'");

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, invalidDefaults, "v2.0.0-test");

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertTrue(result.detail().contains("config-version"));
        assertTrue(result.detail().contains("birdSpeed"));
        assertEquals(original, Files.readString(config));
        assertEquals(1, fileCount());
    }

    @Test
    void backupNameCollisionNeverOverwritesAnEarlierBackup() throws Exception {
        Path config = write("version: legacy\n");
        Path firstBackup = temporaryDirectory.resolve("config.yml.v0.bak");
        Files.writeString(firstBackup, "earlier backup\n");

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test");

        assertEquals(ConfigMigrator.State.UPGRADED, result.state());
        assertEquals("config.yml.v0.bak.1", result.backup().getFileName().toString());
        assertEquals("earlier backup\n", Files.readString(firstBackup));
        assertEquals("version: legacy\n", Files.readString(result.backup()));
    }

    @Test
    void replacementFailureLeavesInstalledBytesUntouchedAndReportsBackup() throws Exception {
        Path config = write("version: legacy\n");
        String original = Files.readString(config);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test",
                (path, contents, expectedCurrent) -> {
                    throw new IOException("injected write failure");
                });

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertNotNull(result.backup());
        assertEquals(original, Files.readString(config));
        assertEquals(original, Files.readString(result.backup()));
    }

    @Test
    void editCompletedBeforeReplacementCheckLeavesTheNewOperatorBytesUntouched() throws Exception {
        Path config = write("version: legacy\n");
        String edited = "version: edited-during-migration\n";

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test",
                (path, contents, expectedCurrent) -> {
                    Files.writeString(path, edited);
                    AtomicFiles.writeUtf8AtomicRequired(path, contents, expectedCurrent);
                });

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertTrue(result.detail().contains("changed"));
        assertEquals(edited, Files.readString(config));
    }

    @Test
    void unsupportedAtomicReplacementHasAnActionableDiagnostic() throws Exception {
        Path config = write("version: legacy\n");
        String original = Files.readString(config);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test",
                (path, contents, expectedCurrent) -> {
                    throw new AtomicMoveNotSupportedException(
                            path.toString(), path.toString(), "injected unsupported move");
                });

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertTrue(result.detail().contains("cannot atomically replace"));
        assertEquals(original, Files.readString(config));
        assertEquals(original, Files.readString(result.backup()));
    }

    @Test
    void malformedUtf8FailsClosedWithoutCreatingABackup() throws Exception {
        Path config = temporaryDirectory.resolve("config.yml");
        byte[] malformed = new byte[]{(byte) 0xc3, (byte) 0x28};
        Files.write(config, malformed);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test");

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertTrue(result.detail().contains("UTF-8"));
        assertArrayEquals(malformed, Files.readAllBytes(config));
        assertEquals(1, fileCount());
    }

    @Test
    void attachedDefaultsCannotHideAMissingExplicitSchemaAfterReload() throws Exception {
        YamlConfiguration loaded = parse("version: edited-during-startup\n");
        loaded.setDefaults(parse(bundledConfig()));

        List<String> errors = ConfigMigrator.validateCurrentConfiguration(loaded);

        assertTrue(errors.stream().anyMatch(error -> error.contains("config-version")));
        assertFalse(ConfigMigrator.hasCurrentVersion(loaded));
    }

    @Test
    void invalidYamlFailsClosedAndRemainsUntouched() throws Exception {
        Path config = write("config-version: 1\nvalue: [broken\n");
        String original = Files.readString(config);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(
                config, bundledConfig(), "v2.0.0-test");

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertFalse(result.compatible());
        assertEquals(original, Files.readString(config));
    }

    private Path write(String contents) throws IOException {
        Path config = temporaryDirectory.resolve("config.yml");
        Files.writeString(config, contents);
        return config;
    }

    private long fileCount() throws IOException {
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            return files.count();
        }
    }

    private static void assertOwnerOnly(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file,
                PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file));
            return;
        }

        AclFileAttributeView acl = Files.getFileAttributeView(file,
                AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        assertNotNull(acl, "filesystem exposes neither POSIX permissions nor a Windows ACL");
        UserPrincipal owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
        List<AclEntry> entries = acl.getAcl();
        assertFalse(entries.isEmpty(), "owner-only ACL grants nobody access");
        assertTrue(entries.stream().filter(entry -> entry.type() == AclEntryType.ALLOW)
                        .allMatch(entry -> entry.principal().equals(owner)),
                () -> "a non-owner has an allow ACL on " + file + ": " + entries);
    }

    private static YamlConfiguration strictLoad(Path path) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.load(path.toFile());
        return yaml;
    }

    private static YamlConfiguration parse(String contents) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.loadFromString(contents);
        return yaml;
    }

    private static String bundledConfig() throws IOException {
        try (InputStream stream = ConfigMigratorTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
