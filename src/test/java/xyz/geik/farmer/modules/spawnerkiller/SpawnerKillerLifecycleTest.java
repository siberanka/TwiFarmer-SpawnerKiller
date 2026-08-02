package xyz.geik.farmer.modules.spawnerkiller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.geik.farmer.shades.storage.Config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpawnerKillerLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsLanguageBeforeApplyingSettings() throws Exception {
        List<String> order = new ArrayList<>();
        Config language = new Config(temporaryDirectory.resolve("lang.yml").toFile());
        SpawnerKiller module = new TestModule(order, language);

        module.loadFilesAndSettings();

        assertEquals(List.of("config", "language", "settings"), order);
    }

    @Test
    void rejectsMissingLanguageBeforeApplyingSettings() {
        List<String> order = new ArrayList<>();
        SpawnerKiller module = new TestModule(order, null);

        assertThrows(IOException.class, module::loadFilesAndSettings);
        assertEquals(List.of("config", "language"), order);
    }

    private static final class TestModule extends SpawnerKiller {
        private final List<String> order;
        private final Config language;

        private TestModule(List<String> order, Config language) {
            this.order = order;
            this.language = language;
        }

        @Override
        public void setupFile() {
            order.add("config");
        }

        @Override
        Config loadAndRepairLanguage() {
            order.add("language");
            return language;
        }

        @Override
        void applySettings(Config loadedLanguage) {
            order.add("settings");
        }
    }
}
