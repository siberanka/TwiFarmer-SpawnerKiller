package xyz.geik.farmer.modules.spawnerkiller.handlers;

import mc.rellox.spawnermeta.SpawnerMeta;
import mc.rellox.spawnermeta.api.APIInstance;
import mc.rellox.spawnermeta.api.events.SpawnerPostSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import xyz.geik.farmer.modules.spawnerkiller.SpawnerKiller;
import xyz.geik.farmer.modules.spawnerkiller.service.SpawnProcessor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SpawnerMeta 25.8 bridge (Minecraft 1.21.x and 26.x).
 *
 * SpawnerMeta does not expose an unregister method for API callbacks. The
 * active flag therefore makes old callbacks inert across module reloads and
 * prevents duplicate drop/kill commits.
 *
 * @author amownyy
 * @author siberanka
 */
public final class SpawnerMetaEvent {

    private final SpawnerKiller module;
    private volatile SpawnProcessor processor;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public SpawnerMetaEvent(SpawnerKiller module, SpawnProcessor processor) {
        this.module = module;
        this.processor = processor;

        Plugin plugin = Bukkit.getPluginManager().getPlugin("SpawnerMeta");
        if (!(plugin instanceof SpawnerMeta spawnerMeta) || !plugin.isEnabled()) {
            throw new IllegalStateException("SpawnerMeta was disabled while its integration was being registered");
        }
        APIInstance api = spawnerMeta.getAPI();
        if (api == null) {
            throw new IllegalStateException("SpawnerMeta returned a null API instance");
        }
        api.register(SpawnerPostSpawnEvent.class, this::onPostSpawn);
    }

    private void onPostSpawn(SpawnerPostSpawnEvent event) {
        SpawnProcessor processorSnapshot = processor;
        if (!active.get() || !module.isOperational() || event == null || event.entities == null
                || event.entities.isEmpty() || processorSnapshot == null) {
            return;
        }

        SpawnerKiller.OptimizationSettings optimization = module.getOptimizationSettings();
        int batchSize = optimization.enable() ? optimization.maxEntitiesPerRun() : Integer.MAX_VALUE;
        int index = 0;
        for (Object candidate : event.entities) {
            if (!active.get()) {
                return;
            }
            if (candidate instanceof Entity entity) {
                long extraDelay = optimization.enable() ? index / batchSize : 0L;
                processorSnapshot.submit(entity, extraDelay);
                index++;
            }
        }
    }

    public void deactivate() {
        active.set(false);
        processor = null;
    }

    public void activate(SpawnProcessor processor) {
        this.processor = processor;
        active.set(true);
    }
}
