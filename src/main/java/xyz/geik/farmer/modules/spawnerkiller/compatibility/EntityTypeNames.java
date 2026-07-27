package xyz.geik.farmer.modules.spawnerkiller.compatibility;

import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keeps configured entity identifiers stable across Bukkit enum aliases and
 * the Paper 1.21.x to 26.x API transition.
 *
 * @author siberanka
 */
public final class EntityTypeNames {

    private static final Map<String, String> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("DROPPED_ITEM", "ITEM"),
            Map.entry("ENDER_SIGNAL", "EYE_OF_ENDER"),
            Map.entry("FIREWORK", "FIREWORK_ROCKET"),
            Map.entry("LEASH_HITCH", "LEASH_KNOT"),
            Map.entry("MUSHROOM_COW", "MOOSHROOM"),
            Map.entry("PIG_ZOMBIE", "ZOMBIFIED_PIGLIN"),
            Map.entry("PRIMED_TNT", "TNT"),
            Map.entry("SNOWMAN", "SNOW_GOLEM"),
            Map.entry("SPLASH_POTION", "POTION"),
            Map.entry("THROWN_EXP_BOTTLE", "EXPERIENCE_BOTTLE"),
            Map.entry("TROPICALFISH", "TROPICAL_FISH"),
            Map.entry("SKELETONHORSE", "SKELETON_HORSE"),
            Map.entry("ZOMBIEHORSE", "ZOMBIE_HORSE"),
            Map.entry("WANDERINGTRADER", "WANDERING_TRADER")
    );

    private static final Set<String> PAPER_26_ADDITIONS = Set.of(
            "CAMEL_HUSK",
            "COPPER_GOLEM",
            "MANNEQUIN",
            "NAUTILUS",
            "PARCHED",
            "ZOMBIE_NAUTILUS"
    );

    private EntityTypeNames() {
    }

    public static String normalizeConfigured(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        int separator = normalized.indexOf(':');
        if (separator >= 0) {
            if (!"minecraft".equalsIgnoreCase(normalized.substring(0, separator))) {
                return null;
            }
            normalized = normalized.substring(separator + 1);
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replace('-', '_');
        normalized = LEGACY_ALIASES.getOrDefault(normalized, normalized);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,127}")) {
            return null;
        }
        try {
            EntityType.valueOf(normalized);
            return normalized;
        }
        catch (IllegalArgumentException ignored) {
            return PAPER_26_ADDITIONS.contains(normalized) ? normalized : null;
        }
    }

    public static String stableName(EntityType type) {
        if (type == null) {
            return null;
        }
        try {
            String key = type.getKey().getKey().toUpperCase(Locale.ROOT);
            return LEGACY_ALIASES.getOrDefault(key, key);
        }
        catch (RuntimeException | LinkageError ignored) {
            String name = type.name();
            return LEGACY_ALIASES.getOrDefault(name, name);
        }
    }
}
