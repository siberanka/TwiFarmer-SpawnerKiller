package xyz.geik.farmer.modules.spawnerkiller.compatibility;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityTypeNamesTest {

    @Test
    void acceptsPaper26NamesOnEveryCompileProfile() {
        assertEquals("CAMEL_HUSK", EntityTypeNames.normalizeConfigured("minecraft:camel_husk"));
        assertEquals("COPPER_GOLEM", EntityTypeNames.normalizeConfigured("copper-golem"));
        assertEquals("ZOMBIE_NAUTILUS", EntityTypeNames.normalizeConfigured("ZOMBIE_NAUTILUS"));
    }

    @Test
    void canonicalizesHistoricalBukkitAliases() {
        assertEquals("MOOSHROOM", EntityTypeNames.normalizeConfigured("MUSHROOM_COW"));
        assertEquals("ZOMBIFIED_PIGLIN", EntityTypeNames.normalizeConfigured("pig_zombie"));
        assertEquals("TROPICAL_FISH", EntityTypeNames.normalizeConfigured("tropicalfish"));
    }

    @Test
    void rejectsMalformedUnknownAndNonMinecraftIdentifiers() {
        assertNull(EntityTypeNames.normalizeConfigured("NOT_A_REAL_ENTITY"));
        assertNull(EntityTypeNames.normalizeConfigured("custom:camel_husk"));
        assertNull(EntityTypeNames.normalizeConfigured("../zombie"));
    }

    @Test
    void derivesStableNamespacedKeyFromRuntimeEntityType() {
        assertEquals("MOOSHROOM", EntityTypeNames.stableName(EntityType.MOOSHROOM));
    }
}
