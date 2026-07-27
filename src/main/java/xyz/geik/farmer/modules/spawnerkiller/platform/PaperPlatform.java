package xyz.geik.farmer.modules.spawnerkiller.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import xyz.geik.farmer.api.FarmerCompatibilityAPI;

/**
 * Verifies the Paper and Farmer APIs used by SpawnerKiller before activation.
 */
public final class PaperPlatform {

    private static final String[] REQUIRED_CLASSES = {
            "io.papermc.paper.threadedregions.scheduler.RegionScheduler",
            "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler",
            "io.papermc.paper.threadedregions.scheduler.AsyncScheduler",
            "io.papermc.paper.threadedregions.scheduler.EntityScheduler",
            "org.bukkit.event.entity.SpawnerSpawnEvent"
    };

    private PaperPlatform() {}

    public static boolean isSupported() {
        try {
            FarmerCompatibilityAPI.requireModuleApi(2);
            for (String className : REQUIRED_CLASSES)
                Class.forName(className, false, PaperPlatform.class.getClassLoader());
            Bukkit.class.getMethod("getRegionScheduler");
            Bukkit.class.getMethod("getGlobalRegionScheduler");
            Bukkit.class.getMethod("getAsyncScheduler");
            Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
            Entity.class.getMethod("getScheduler");
            Mob.class.getMethod("getPossibleExperienceReward");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
