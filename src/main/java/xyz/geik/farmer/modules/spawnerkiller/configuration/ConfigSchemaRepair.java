package xyz.geik.farmer.modules.spawnerkiller.configuration;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.geik.farmer.modules.spawnerkiller.compatibility.EntityTypeNames;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Repairs module YAML before the normal loaders see it. Invalid data is never
 * overwritten until a timestamped backup has been created successfully.
 *
 * @author siberanka
 */
public final class ConfigSchemaRepair {

    private static final long MAX_YAML_BYTES = 2L * 1024L * 1024L;
    static final int MAX_BACKUPS_PER_FILE = 20;
    private static final DateTimeFormatter BACKUP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private ConfigSchemaRepair() {
    }

    public static void repairConfig(File file, Logger logger) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(logger, "logger");
        ensureParent(file.toPath());

        LoadResult result = load(file.toPath(), logger);
        YamlConfiguration yaml = result.yaml;
        boolean invalid = result.invalid;
        boolean changed = result.invalid || !file.isFile();
        Map<String, Rule> schema = configSchema();
        Map<String, Object> replacements = new LinkedHashMap<>();

        if (yaml.contains("optimize-module") && !yaml.isConfigurationSection("optimize-module")) {
            replacements.put("optimize-module", null);
            invalid = true;
            changed = true;
        }
        if (yaml.contains("update-checker") && !yaml.isConfigurationSection("update-checker")) {
            replacements.put("update-checker", null);
            invalid = true;
            changed = true;
        }

        for (Map.Entry<String, Rule> entry : schema.entrySet()) {
            String path = entry.getKey();
            Rule rule = entry.getValue();
            if (!yaml.contains(path)) {
                replacements.put(path, rule.defaultValue);
                changed = true;
                continue;
            }

            Object current = yaml.get(path);
            Object normalized = rule.normalize(current);
            if (normalized == Rule.INVALID) {
                replacements.put(path, rule.defaultValue);
                invalid = true;
                changed = true;
            }
            else if (!equivalentValues(current, normalized)) {
                replacements.put(path, normalized);
                invalid = true;
                changed = true;
            }
        }

        if (invalid && file.isFile() && !result.backedUp) {
            backup(file.toPath(), logger);
        }
        replacements.forEach(yaml::set);
        if (changed) {
            saveAtomically(yaml, file.toPath());
        }
        pruneBackups(file.toPath(), logger);
    }

    public static void repairLanguage(File file, InputStream defaultsStream, Logger logger) throws IOException {
        Objects.requireNonNull(defaultsStream, "defaultsStream");
        ensureParent(file.toPath());

        YamlConfiguration defaults = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
            defaults.load(reader);
        }
        catch (InvalidConfigurationException exception) {
            throw new IOException("Bundled language template is invalid: " + file.getName(), exception);
        }

        LoadResult result = load(file.toPath(), logger);
        YamlConfiguration yaml = result.yaml;
        boolean invalid = result.invalid;
        boolean changed = result.invalid || !file.isFile();
        Map<String, Object> replacements = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : defaults.getValues(true).entrySet()) {
            String path = entry.getKey();
            Object expected = entry.getValue();
            if (expected instanceof org.bukkit.configuration.ConfigurationSection) {
                if (yaml.contains(path) && !yaml.isConfigurationSection(path)) {
                    replacements.put(path, null);
                    invalid = true;
                    changed = true;
                }
                continue;
            }
            if (!yaml.contains(path)) {
                replacements.put(path, expected);
                changed = true;
                continue;
            }
            Object current = yaml.get(path);
            if (!sameShape(expected, current) || !meaningfulLanguageValue(path, current)) {
                replacements.put(path, expected);
                invalid = true;
                changed = true;
            }
        }

        if (invalid && file.isFile() && !result.backedUp) {
            backup(file.toPath(), logger);
        }
        replacements.forEach(yaml::set);
        if (changed) {
            saveAtomically(yaml, file.toPath());
        }
        pruneBackups(file.toPath(), logger);
    }

    private static LoadResult load(Path file, Logger logger) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new LoadResult(new YamlConfiguration(), false, false);
        }
        if (Files.size(file) > MAX_YAML_BYTES) {
            backup(file, logger);
            logger.warning("Oversized YAML was replaced with safe defaults: " + file.getFileName());
            return new LoadResult(new YamlConfiguration(), true, true);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
            return new LoadResult(yaml, false, false);
        }
        catch (InvalidConfigurationException exception) {
            backup(file, logger);
            logger.warning("Malformed YAML was backed up and regenerated: " + file.getFileName());
            return new LoadResult(new YamlConfiguration(), true, true);
        }
    }

    private static Map<String, Rule> configSchema() {
        Map<String, Rule> schema = new LinkedHashMap<>();
        schema.put("status", Rule.bool(false));
        schema.put("requireFarmer", Rule.bool(true));
        schema.put("cookFoods", Rule.bool(true));
        schema.put("removeMob", Rule.bool(true));
        schema.put("defaultStatus", Rule.bool(true));
        schema.put("required-farmer-level", Rule.integer(1, 1, 1000));
        schema.put("customPerm", Rule.string("farmer.spawnerkiller",
                value -> value.matches("[A-Za-z0-9._-]{1,128}"), String::trim));
        schema.put("wildstacker-recovery-radius", Rule.integer(16, 1, 64));
        schema.put("mode", Rule.string("blacklist",
                value -> value.equals("blacklist") || value.equals("whitelist"),
                value -> value.trim().toLowerCase(Locale.ROOT)));
        schema.put("whitelist", Rule.entities(Arrays.asList("VILLAGER")));
        schema.put("blacklist", Rule.entities(Arrays.asList("VILLAGER")));
        schema.put("update-checker.enable", Rule.bool(true));
        schema.put("update-checker.check-interval-hours", Rule.integer(6, 1, 168));
        schema.put("update-checker.connect-timeout-seconds", Rule.integer(5, 2, 30));
        schema.put("update-checker.request-timeout-seconds", Rule.integer(8, 3, 60));
        schema.put("optimize-module.enable", Rule.bool(false));
        schema.put("optimize-module.async-precheck", Rule.bool(true));
        schema.put("optimize-module.async-stack-drops", Rule.bool(true));
        schema.put("optimize-module.processing-delay-ticks", Rule.integer(2, 0, 1200));
        schema.put("optimize-module.max-entities-per-run", Rule.integer(64, 1, 4096));
        schema.put("optimize-module.max-queued-entities", Rule.integer(512, 16, 100000));
        schema.put("optimize-module.max-pending-per-region", Rule.integer(64, 1, 10000));
        schema.put("optimize-module.collapse-duplicate-spawns", Rule.bool(true));
        schema.put("optimize-module.batch-drops", Rule.bool(true));
        schema.put("optimize-module.max-stack-process-amount", Rule.integer(100000, 1, 1000000));
        schema.put("optimize-module.audit-log-rate-limit-ms", Rule.longNumber(5000L, 250L, 3600000L));
        return schema;
    }

    private static boolean sameShape(Object expected, Object current) {
        if (expected instanceof Collection<?>) {
            if (!(current instanceof Collection<?> collection)) {
                return false;
            }
            return collection.size() <= 128 && collection.stream().allMatch(String.class::isInstance);
        }
        return expected != null && current != null && expected.getClass().isInstance(current);
    }

    private static boolean equivalentValues(Object current, Object normalized) {
        if (isIntegralNumber(current) && isIntegralNumber(normalized)) {
            return ((Number) current).longValue() == ((Number) normalized).longValue();
        }
        return Objects.equals(current, normalized);
    }

    private static boolean isIntegralNumber(Object value) {
        return value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long;
    }

    private static boolean meaningfulLanguageValue(String path, Object value) {
        if (value instanceof String string) {
            int limit = path.endsWith(".skull") ? 8192 : 1024;
            if (string.length() > limit) {
                return false;
            }
            if ("update.available".equals(path)) {
                return !string.isBlank() && string.contains("{module}") && string.contains("{current}")
                        && string.contains("{latest}") && string.contains("{url}");
            }
            if ("level-required".equals(path)) {
                return !string.isBlank() && string.contains("{required_level}")
                        && string.contains("{current_level}");
            }
            if ("moduleGui.upgrade-to-unlock".equals(path)) {
                return !string.isBlank() && string.contains("{required_level}");
            }
            return !string.isBlank() || path.contains(".lore");
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty() || collection.size() > 64) {
                return false;
            }
            for (Object item : collection) {
                if (!(item instanceof String string) || string.length() > 1024) {
                    return false;
                }
            }
            if ("moduleGui.icon.lore".equals(path)) {
                String lore = String.join("\n", collection.stream().map(String.class::cast).toList());
                return lore.contains("{status}") && lore.contains("{required_level}")
                        && lore.contains("{action}");
            }
        }
        return true;
    }

    private static void ensureParent(Path file) throws IOException {
        Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("YAML file has no parent directory: " + file);
        }
        Files.createDirectories(parent);
    }

    private static Path backup(Path file, Logger logger) throws IOException {
        String suffix = ".bak-" + BACKUP_TIME.format(Instant.now());
        Path backup = file.resolveSibling(file.getFileName() + suffix);
        int collision = 0;
        while (Files.exists(backup) && collision < 1000) {
            collision++;
            backup = file.resolveSibling(file.getFileName() + suffix + "-" + collision);
        }
        if (Files.exists(backup)) {
            throw new IOException("Could not allocate a unique YAML backup name for " + file.getFileName());
        }
        Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        logger.warning("Invalid YAML data backed up to " + backup.getFileName());
        return backup;
    }

    private static void pruneBackups(Path file, Logger logger) {
        Path normalized = file.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return;
        }
        String prefix = normalized.getFileName() + ".bak-";
        try (Stream<Path> entries = Files.list(parent)) {
            List<Path> backups = entries
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
            for (int index = MAX_BACKUPS_PER_FILE; index < backups.size(); index++) {
                Files.deleteIfExists(backups.get(index));
            }
        }
        catch (IOException exception) {
            logger.warning("Could not prune old YAML backups for " + normalized.getFileName()
                    + ": " + exception.getClass().getSimpleName());
        }
    }

    private static void saveAtomically(YamlConfiguration yaml, Path destination) throws IOException {
        Path parent = destination.toAbsolutePath().normalize().getParent();
        Path temporary = Files.createTempFile(parent, destination.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record LoadResult(YamlConfiguration yaml, boolean invalid, boolean backedUp) {
    }

    private static final class Rule {
        private static final Object INVALID = new Object();
        private final Object defaultValue;
        private final java.util.function.Function<Object, Object> normalizer;

        private Rule(Object defaultValue, java.util.function.Function<Object, Object> normalizer) {
            this.defaultValue = defaultValue;
            this.normalizer = normalizer;
        }

        private Object normalize(Object value) {
            return normalizer.apply(value);
        }

        private static Rule bool(boolean defaultValue) {
            return new Rule(defaultValue, value -> value instanceof Boolean ? value : INVALID);
        }

        private static Rule integer(int defaultValue, int minimum, int maximum) {
            return new Rule(defaultValue, value -> {
                if (!isIntegralNumber(value)) {
                    return INVALID;
                }
                Number number = (Number) value;
                long parsed = number.longValue();
                return parsed >= minimum && parsed <= maximum ? (int) parsed : INVALID;
            });
        }

        private static Rule longNumber(long defaultValue, long minimum, long maximum) {
            return new Rule(defaultValue, value -> {
                if (!isIntegralNumber(value)) {
                    return INVALID;
                }
                Number number = (Number) value;
                long parsed = number.longValue();
                return parsed >= minimum && parsed <= maximum ? parsed : INVALID;
            });
        }

        private static Rule string(String defaultValue, Predicate<String> validator,
                                   java.util.function.UnaryOperator<String> normalizer) {
            return new Rule(defaultValue, value -> {
                if (!(value instanceof String string) || string.length() > 8192) {
                    return INVALID;
                }
                String normalized = normalizer.apply(string);
                return validator.test(normalized) ? normalized : INVALID;
            });
        }

        private static Rule entities(List<String> defaultValue) {
            return new Rule(defaultValue, value -> {
                if (!(value instanceof Collection<?> collection) || collection.size() > 1024) {
                    return INVALID;
                }
                List<String> normalized = new ArrayList<>(collection.size());
                for (Object item : collection) {
                    if (!(item instanceof String string) || string.length() > 128) {
                        return INVALID;
                    }
                    String type = EntityTypeNames.normalizeConfigured(string);
                    if (type == null) {
                        return INVALID;
                    }
                    if (!normalized.contains(type)) {
                        normalized.add(type);
                    }
                }
                return normalized;
            });
        }
    }
}
