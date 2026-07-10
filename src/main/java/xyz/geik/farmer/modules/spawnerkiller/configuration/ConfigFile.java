package xyz.geik.farmer.modules.spawnerkiller.configuration;

import lombok.Getter;
import lombok.Setter;
import xyz.geik.glib.shades.okaeri.configs.OkaeriConfig;
import xyz.geik.glib.shades.okaeri.configs.annotation.Comment;
import xyz.geik.glib.shades.okaeri.configs.annotation.CustomKey;
import xyz.geik.glib.shades.okaeri.configs.annotation.NameStrategy;
import xyz.geik.glib.shades.okaeri.configs.annotation.Names;

import java.util.Arrays;
import java.util.List;

/**
 * Modules file
 *
 * @author geik
 * @author siberanka
 * @since 2.0
 */
@Getter
@Setter
@Names(strategy = NameStrategy.IDENTITY)
public class ConfigFile extends OkaeriConfig {

    @Comment({"if you want to use spawner killer system",
            "set feature to true.",
            "and players with farmer.admin permission can give spawner killer.",
            "you can disable buy feature and give farmer with command"})
    private boolean status = false;

    @Comment({"if you want to kill mobs without farmer set it false"})
    private boolean requireFarmer = true;

    @Comment({"cook foods on spawner drop"})
    private boolean cookFoods = true;

    @Comment({"remove mob can't see mob only spawn item."})
    private boolean removeMob = true;

    @Comment({"default status for spawner killer when farmer place",
            "if set true, farmer will be enable spawner killer by default",
            "if set false, farmer will be disable spawner killer by default"})
    private boolean defaultStatus = true;

    @Comment({"custom perm for spawner killer status changer"})
    private String customPerm = "farmer.spawnerkiller";

    @Comment({"set whitelist mobs for spawner killer",
            "if you want to kill only whitelist mobs, set mode to whitelist",
            "if you want to kill all mobs except blacklist mobs, set mode to blacklist"})
    private String mode = "blacklist";

    @Comment({"You can add remove blacklist section",
            "if you want to remove mobs from blacklist"})
    private List<String> whitelist = Arrays.asList("VILLAGER");

    @Comment({"You can add remove blacklist section",
            "if you want to remove mobs from blacklist"})
    private List<String> blacklist = Arrays.asList("VILLAGER");

    @CustomKey("optimize-module")
    @Comment({"Production optimization controls.",
            "All child settings are ignored while enable is false.",
            "Bukkit world/entity operations always remain on the owning Paper/Folia region thread."})
    private OptimizeModule optimizeModule = new OptimizeModule();

    @Getter
    @Setter
    @Names(strategy = NameStrategy.IDENTITY)
    public static class OptimizeModule extends OkaeriConfig {

        @Comment("Master switch. Disabled by default to preserve the legacy timing behavior.")
        private boolean enable = false;

        @CustomKey("async-precheck")
        @Comment("Moves immutable queue/filter preparation to Paper's bounded async scheduler path.")
        private boolean asyncPrecheck = true;

        @CustomKey("async-stack-drops")
        @Comment("Calculates WildStacker loot off-thread, then revalidates and commits on the entity scheduler.")
        private boolean asyncStackDrops = true;

        @CustomKey("processing-delay-ticks")
        @Comment("Defers entity work on its owning EntityScheduler; 0 means no configured delay.")
        private int processingDelayTicks = 2;

        @CustomKey("max-entities-per-run")
        @Comment("SpawnerMeta batches larger than this are spread over following ticks.")
        private int maxEntitiesPerRun = 64;

        @CustomKey("max-queued-entities")
        @Comment("Global back-pressure limit. Overflow falls back to immediate region-safe processing.")
        private int maxQueuedEntities = 512;

        @CustomKey("max-pending-per-region")
        @Comment("Back-pressure limit per 8x8 chunk group.")
        private int maxPendingPerRegion = 64;

        @CustomKey("collapse-duplicate-spawns")
        @Comment("Coalesces duplicate Bukkit/SpawnerMeta notifications for the same entity.")
        private boolean collapseDuplicateSpawns = true;

        @CustomKey("batch-drops")
        @Comment("Combines similar manually generated drops into legal item stacks.")
        private boolean batchDrops = true;

        @CustomKey("max-stack-process-amount")
        @Comment("Hard safety ceiling for corrupt or hostile stack sizes.")
        private int maxStackProcessAmount = 100000;

        @CustomKey("audit-log-rate-limit-ms")
        @Comment("Minimum interval between repeated operational warnings of the same category.")
        private long auditLogRateLimitMs = 5000L;
    }

}
