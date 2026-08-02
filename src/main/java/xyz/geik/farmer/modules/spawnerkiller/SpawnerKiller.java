package xyz.geik.farmer.modules.spawnerkiller;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.HandlerList;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.modules.FarmerModule;
import xyz.geik.farmer.modules.spawnerkiller.configuration.ConfigFile;
import xyz.geik.farmer.modules.spawnerkiller.configuration.ConfigSchemaRepair;
import xyz.geik.farmer.modules.spawnerkiller.configuration.UpdateSettings;
import xyz.geik.farmer.modules.spawnerkiller.compatibility.EntityTypeNames;
import xyz.geik.farmer.modules.spawnerkiller.handlers.SpawnerKillerEvent;
import xyz.geik.farmer.modules.spawnerkiller.handlers.SpawnerKillerGuiCreateEvent;
import xyz.geik.farmer.modules.spawnerkiller.handlers.SpawnerMetaEvent;
import xyz.geik.farmer.modules.spawnerkiller.platform.PaperPlatform;
import xyz.geik.farmer.modules.spawnerkiller.service.SpawnProcessor;
import xyz.geik.farmer.modules.spawnerkiller.update.UpdateChecker;
import xyz.geik.farmer.shades.storage.Config;
import xyz.geik.glib.GLib;
import xyz.geik.glib.chat.ChatUtils;
import xyz.geik.glib.shades.okaeri.configs.ConfigManager;
import xyz.geik.glib.shades.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Farmer's Paper-only SpawnerKiller module.
 *
 * @author geik
 * @author siberanka
 */
public class SpawnerKiller extends FarmerModule {

    private static final List<String> BUNDLED_LANGUAGES = List.of("en", "tr", "de");

    @Getter
    private static volatile SpawnerKiller instance;

    private SpawnerKillerEvent spawnerKillerEvent;
    private SpawnerKillerGuiCreateEvent spawnerKillerGuiCreateEvent;
    private SpawnerMetaEvent spawnerMetaEvent;
    private SpawnProcessor spawnProcessor;
    private UpdateChecker updateChecker;
    private final AtomicLong lifecycleEpoch = new AtomicLong();

    private volatile RuntimeSettings settings = RuntimeSettings.defaults();
    private volatile boolean operational;

    @Getter
    private ConfigFile configFile;

    public SpawnerKiller() {
    }

    @Override
    public void onEnable() {
        lifecycleEpoch.incrementAndGet();
        instance = this;
        setHasGui(true);

        if (!PaperPlatform.isSupported()) {
            Main.getInstance().getLogger().severe(
                    "SpawnerKiller requires Paper, Folia or Leaf. Plain Bukkit/Spigot is not supported.");
            operational = false;
            return;
        }

        try {
            loadFilesAndSettings();
            startUpdateChecker();
            if (configFile.isStatus()) {
                registerHandlers();
                operational = true;
                consoleMessage("&a" + getName() + " enabled (Paper/Folia/Leaf scheduler mode).");
            }
            else {
                operational = false;
                consoleMessage("&c" + getName() + " is not loaded.");
            }
        }
        catch (Exception exception) {
            operational = false;
            unregisterHandlers(true);
            Main.getInstance().getLogger().log(Level.SEVERE,
                    "SpawnerKiller failed closed while loading its configuration", exception);
        }
    }

    @Override
    public void onReload() {
        if (!PaperPlatform.isSupported()) {
            return;
        }

        lifecycleEpoch.incrementAndGet();
        stopUpdateChecker();
        unregisterHandlers(false);
        operational = false;
        try {
            loadFilesAndSettings();
            startUpdateChecker();
            if (configFile.isStatus()) {
                registerHandlers();
                operational = true;
            }
        }
        catch (Exception exception) {
            Main.getInstance().getLogger().log(Level.SEVERE,
                    "SpawnerKiller reload was rejected; the module remains inactive", exception);
        }
    }

    @Override
    public void onDisable() {
        operational = false;
        lifecycleEpoch.incrementAndGet();
        stopUpdateChecker();
        unregisterHandlers(true);
        settings = RuntimeSettings.defaults();
        instance = null;
    }

    public long getLifecycleGeneration() {
        return lifecycleEpoch.get();
    }

    void loadFilesAndSettings() throws IOException {
        setupFile();
        Config language = loadAndRepairLanguage();
        if (language == null) {
            throw new IOException("SpawnerKiller language initialization returned no configuration");
        }
        applySettings(language);
    }

    public void setupFile() throws IOException {
        File config = new File(Main.getInstance().getDataFolder(),
                "modules/" + getName().toLowerCase(Locale.ROOT) + "/config.yml");
        ConfigSchemaRepair.repairConfig(config, Main.getInstance().getLogger());
        try {
            configFile = ConfigManager.create(ConfigFile.class, it -> {
                it.withConfigurer(new YamlBukkitConfigurer());
                it.withBindFile(config);
                // Okaeri's shaded file overload does not close its stream on this Farmer build.
                // Own the stream here so repeated production reloads cannot leak file handles.
                try (InputStream input = Files.newInputStream(config.toPath())) {
                    it.load(input);
                }
                catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        }
        catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    Config loadAndRepairLanguage() throws IOException {
        String requested = Main.getConfigFile().getSettings().getLang();
        String language = sanitizeLanguage(requested);
        if (!BUNDLED_LANGUAGES.contains(language)) {
            language = "en";
            Main.getInstance().getLogger().warning(
                    "Unsupported SpawnerKiller language '" + sanitizeForLog(requested) + "'; using en.yml.");
        }

        for (String bundledLanguage : BUNDLED_LANGUAGES) {
            String resourcePath = "spawnerkiller/lang/" + bundledLanguage + ".yml";
            InputStream defaults = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (defaults == null) {
                throw new IOException("Bundled language resource is missing: " + resourcePath);
            }
            File languageFile = new File(Main.getInstance().getDataFolder(),
                    "modules/" + getName().toLowerCase(Locale.ROOT) + "/lang/" + bundledLanguage + ".yml");
            ConfigSchemaRepair.repairLanguage(languageFile, defaults, Main.getInstance().getLogger());
        }
        setLang(language, getClass());
        Config loadedLanguage = getLang();
        if (loadedLanguage == null) {
            throw new IOException("SpawnerKiller could not initialize language '" + language + "'");
        }
        return loadedLanguage;
    }

    void applySettings(Config language) {
        String mode = "whitelist".equalsIgnoreCase(configFile.getMode()) ? "whitelist" : "blacklist";
        Set<String> whitelist = normalizeEntityNames(configFile.getWhitelist());
        Set<String> blacklist = normalizeEntityNames(configFile.getBlacklist());
        ConfigFile.OptimizeModule optimize = configFile.getOptimizeModule();
        OptimizationSettings optimization = optimize == null
                ? OptimizationSettings.defaults()
                : new OptimizationSettings(
                        optimize.isEnable(),
                        optimize.isAsyncPrecheck(),
                        optimize.isAsyncStackDrops(),
                        clamp(optimize.getProcessingDelayTicks(), 0, 1200),
                        clamp(optimize.getMaxEntitiesPerRun(), 1, 4096),
                        clamp(optimize.getMaxQueuedEntities(), 16, 100000),
                        clamp(optimize.getMaxPendingPerRegion(), 1, 10000),
                        optimize.isCollapseDuplicateSpawns(),
                        optimize.isBatchDrops(),
                        clamp(optimize.getMaxStackProcessAmount(), 1, 1000000),
                        clamp(optimize.getAuditLogRateLimitMs(), 250L, 3600000L));

        settings = new RuntimeSettings(
                configFile.isRequireFarmer(),
                configFile.isCookFoods(),
                configFile.isRemoveMob(),
                configFile.isDefaultStatus(),
                configFile.getCustomPerm(),
                mode,
                whitelist,
                blacklist,
                optimization);
        setDefaultState(configFile.isDefaultStatus());
        setRequiredFarmerLevel(configFile.getRequiredFarmerLevel());
        setDisplayName(language.getString("module-name"));
    }

    private void registerHandlers() {
        spawnProcessor = new SpawnProcessor(this, Main.getInstance());
        spawnerKillerGuiCreateEvent = new SpawnerKillerGuiCreateEvent();
        Bukkit.getPluginManager().registerEvents(spawnerKillerGuiCreateEvent, Main.getInstance());

        if (Bukkit.getPluginManager().isPluginEnabled("SpawnerMeta")) {
            if (spawnerMetaEvent == null) {
                spawnerMetaEvent = new SpawnerMetaEvent(this, spawnProcessor);
            }
            else {
                spawnerMetaEvent.activate(spawnProcessor);
            }
        }
        else {
            spawnerKillerEvent = new SpawnerKillerEvent(this, spawnProcessor);
            Bukkit.getPluginManager().registerEvents(spawnerKillerEvent, Main.getInstance());
        }
    }

    private void startUpdateChecker() {
        stopUpdateChecker();
        updateChecker = new UpdateChecker(this, UpdateSettings.from(configFile.getUpdateChecker()));
        updateChecker.start();
    }

    private void stopUpdateChecker() {
        if (updateChecker != null) {
            updateChecker.stop();
            updateChecker = null;
        }
    }

    private void unregisterHandlers(boolean finalDisable) {
        if (spawnerKillerEvent != null) {
            HandlerList.unregisterAll(spawnerKillerEvent);
            spawnerKillerEvent = null;
        }
        if (spawnerKillerGuiCreateEvent != null) {
            spawnerKillerGuiCreateEvent.closeOpenMenus();
            HandlerList.unregisterAll(spawnerKillerGuiCreateEvent);
            spawnerKillerGuiCreateEvent = null;
        }
        if (spawnerMetaEvent != null) {
            spawnerMetaEvent.deactivate();
            if (finalDisable) {
                spawnerMetaEvent = null;
            }
        }
        if (spawnProcessor != null) {
            spawnProcessor.close();
            spawnProcessor = null;
        }
    }

    public boolean shouldProcess(EntityType type) {
        if (!operational || type == null) {
            return false;
        }
        RuntimeSettings snapshot = settings;
        String name = EntityTypeNames.stableName(type);
        if ("whitelist".equals(snapshot.mode())) {
            return snapshot.whitelist().contains(name);
        }
        return !snapshot.blacklist().contains(name);
    }

    public boolean isOperational() {
        return operational;
    }

    public boolean isRequireFarmer() {
        return settings.requireFarmer();
    }

    public boolean isCookFoods() {
        return settings.cookFoods();
    }

    public boolean isRemoveMob() {
        return settings.removeMob();
    }

    public boolean isDefaultStatus() {
        return settings.defaultStatus();
    }

    public String getCustomPerm() {
        return settings.customPerm();
    }

    public Set<String> getWhitelist() {
        return settings.whitelist();
    }

    public Set<String> getBlacklist() {
        return settings.blacklist();
    }

    public OptimizationSettings getOptimizationSettings() {
        return settings.optimization();
    }

    public Config getLangFile() {
        return getLang();
    }

    private static Set<String> normalizeEntityNames(List<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String entry : configured) {
            String name = EntityTypeNames.normalizeConfigured(entry);
            if (name != null) {
                normalized.add(name);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String sanitizeLanguage(String language) {
        if (language == null || !language.matches("[A-Za-z0-9_-]{1,16}")) {
            return "en";
        }
        return language.toLowerCase(Locale.ROOT);
    }

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        return value.replaceAll("[\\r\\n\\t]", "_").substring(0, Math.min(value.length(), 32));
    }

    private void consoleMessage(String message) {
        ChatUtils.sendMessage(Bukkit.getConsoleSender(),
                "&3[" + GLib.getInstance().getName() + "] " + message);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record RuntimeSettings(
            boolean requireFarmer,
            boolean cookFoods,
            boolean removeMob,
            boolean defaultStatus,
            String customPerm,
            String mode,
            Set<String> whitelist,
            Set<String> blacklist,
            OptimizationSettings optimization) {

        private static RuntimeSettings defaults() {
            return new RuntimeSettings(true, true, true, true,
                    "farmer.spawnerkiller", "blacklist",
                    Collections.emptySet(), Set.of("VILLAGER"), OptimizationSettings.defaults());
        }
    }

    public record OptimizationSettings(
            boolean enable,
            boolean asyncPrecheck,
            boolean asyncStackDrops,
            int processingDelayTicks,
            int maxEntitiesPerRun,
            int maxQueuedEntities,
            int maxPendingPerRegion,
            boolean collapseDuplicateSpawns,
            boolean batchDrops,
            int maxStackProcessAmount,
            long auditLogRateLimitMs) {

        public static OptimizationSettings defaults() {
            return new OptimizationSettings(false, true, true, 2, 64, 512, 64,
                    true, true, 100000, 5000L);
        }
    }
}
