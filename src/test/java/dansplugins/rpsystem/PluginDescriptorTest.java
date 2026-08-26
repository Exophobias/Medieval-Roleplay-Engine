package dansplugins.rpsystem;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDescriptorTest {

    @Test
    void descriptorOwnsOnlyThePatriamCharacterCommandSurface() throws IOException {
        YamlConfiguration descriptor = resource("plugin.yml");

        assertEquals("26.2", descriptor.getString("api-version"));
        assertEquals("dansplugins.rpsystem.MedievalRoleplayEngine",
                descriptor.getString("main"));
        assertEquals(Set.of("card", "emote", "me", "roll", "dice", "rphelp", "rpconfig"),
                keys(descriptor, "commands"));
        assertTrue(descriptor.getStringList("softdepend")
                .containsAll(Set.of("PlaceholderAPI", "Plan", "PatriamUtils")));

        for (String legacy : Set.of("bird", "local", "rp", "global", "ooc", "title",
                "yell", "whisper", "lo")) {
            assertFalse(keys(descriptor, "commands").contains(legacy));
        }
        assertFalse(descriptor.getString("version", "").contains("${"));
    }

    @Test
    void shippedConfigurationFailsClosedForOverlappingOrPrivateFeatures() throws IOException {
        YamlConfiguration config = resource("config.yml");

        assertFalse(config.getBoolean("chatFeaturesEnabled", true));
        assertFalse(config.getBoolean("legacyReligionFieldEnabled", true));
        assertFalse(config.getBoolean("exposeReligionPlaceholder", true));
        assertTrue(config.getBoolean("trueDeathIntegrationEnabled", false));
        assertTrue(config.getBoolean("planIntegrationEnabled", false));
    }

    private static Set<String> keys(YamlConfiguration yaml, String path) {
        var section = yaml.getConfigurationSection(path);
        assertNotNull(section);
        return section.getKeys(false);
    }

    private static YamlConfiguration resource(String name) throws IOException {
        try (InputStream stream = PluginDescriptorTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            assertNotNull(stream, name);
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            YamlConfiguration parsed = new YamlConfiguration();
            try {
                parsed.loadFromString(yaml);
            } catch (org.bukkit.configuration.InvalidConfigurationException invalid) {
                throw new IOException(name + " is invalid YAML", invalid);
            }
            return parsed;
        }
    }
}
