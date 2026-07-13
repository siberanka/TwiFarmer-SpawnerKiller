package xyz.geik.farmer.modules.spawnerkiller.configuration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.geik.glib.shades.okaeri.configs.ConfigManager;
import xyz.geik.glib.shades.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSchemaRepairTest {

    private static final Logger LOGGER = Logger.getLogger(ConfigSchemaRepairTest.class.getName());

    @TempDir
    Path temporaryDirectory;

    @Test
    void addsMissingConfigEntriesWithoutCreatingAnErrorBackup() throws Exception {
        Path config = temporaryDirectory.resolve("config.yml");
        Files.writeString(config, "status: true\n", StandardCharsets.UTF_8);

        ConfigSchemaRepair.repairConfig(config.toFile(), LOGGER);

        YamlConfiguration repaired = YamlConfiguration.loadConfiguration(config.toFile());
        assertTrue(repaired.getBoolean("status"));
        assertFalse(repaired.getBoolean("optimize-module.enable"));
        assertTrue(repaired.getBoolean("update-checker.enable"));
        assertEquals(6, repaired.getInt("update-checker.check-interval-hours"));
        assertEquals(512, repaired.getInt("optimize-module.max-queued-entities"));
        assertTrue(repaired.getBoolean("update-checker.enable"));
        assertEquals(0L, countBackups());
    }

    @Test
    void backsUpAndRepairsInvalidConfigValues() throws Exception {
        Path config = temporaryDirectory.resolve("config.yml");
        Files.writeString(config, "status: maybe\nmode: nonsense\nblacklist: [NOT_A_REAL_ENTITY]\n"
                + "optimize-module:\n  enable: true\n  max-queued-entities: -5\n", StandardCharsets.UTF_8);

        ConfigSchemaRepair.repairConfig(config.toFile(), LOGGER);

        YamlConfiguration repaired = YamlConfiguration.loadConfiguration(config.toFile());
        assertFalse(repaired.getBoolean("status"));
        assertEquals("blacklist", repaired.getString("mode"));
        assertEquals(512, repaired.getInt("optimize-module.max-queued-entities"));
        assertEquals(1L, countBackups());
    }

    @Test
    void backsUpAndRegeneratesMalformedYaml() throws Exception {
        Path config = temporaryDirectory.resolve("config.yml");
        Files.writeString(config, "status: [unterminated\n", StandardCharsets.UTF_8);

        ConfigSchemaRepair.repairConfig(config.toFile(), LOGGER);

        YamlConfiguration repaired = YamlConfiguration.loadConfiguration(config.toFile());
        assertFalse(repaired.getBoolean("status"));
        assertEquals(64, repaired.getInt("optimize-module.max-entities-per-run"));
        assertEquals(1L, countBackups());
    }

    @Test
    void repairsLanguageShapeAfterBackingUpInvalidContent() throws Exception {
        Path language = temporaryDirectory.resolve("en.yml");
        Files.writeString(language, "enabled: []\n", StandardCharsets.UTF_8);
        String defaults = "enabled: '&aEnabled'\ndisabled: '&cDisabled'\nmoduleGui:\n"
                + "  icon:\n    guiInterface: 'k'\n    name: '&eSpawner Killer'\n"
                + "    skull: 'texture'\n    lore: ['Status: {status}']\n";

        ConfigSchemaRepair.repairLanguage(language.toFile(),
                new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8)), LOGGER);

        YamlConfiguration repaired = YamlConfiguration.loadConfiguration(language.toFile());
        assertEquals("&aEnabled", repaired.getString("enabled"));
        assertEquals("&cDisabled", repaired.getString("disabled"));
        assertEquals(1L, countBackups());
    }

    @Test
    void okaeriSerializesTheRequiredKebabCaseOptimizationSection() throws Exception {
        ConfigFile loaded = ConfigManager.create(ConfigFile.class,
                setup -> setup.withConfigurer(new YamlBukkitConfigurer()));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(loaded.saveToString());
        assertFalse(loaded.getOptimizeModule().isEnable());
        assertTrue(yaml.isConfigurationSection("optimize-module"));
        assertTrue(yaml.contains("optimize-module.async-stack-drops"));
        assertTrue(yaml.isConfigurationSection("update-checker"));
        assertTrue(loaded.getUpdateChecker().isEnable());
        assertEquals(6, yaml.getInt("update-checker.check-interval-hours"));
        assertEquals(64, yaml.getInt("optimize-module.max-entities-per-run"));

        ConfigFile parsed = ConfigManager.create(ConfigFile.class,
                setup -> setup.withConfigurer(new YamlBukkitConfigurer()));
        parsed.load("optimize-module:\n  enable: true\n  processing-delay-ticks: 7\n");
        assertTrue(parsed.getOptimizeModule().isEnable());
        assertEquals(7, parsed.getOptimizeModule().getProcessingDelayTicks());
    }

    private long countBackups() throws Exception {
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            return files.filter(path -> path.getFileName().toString().contains(".bak-")).count();
        }
    }
}
