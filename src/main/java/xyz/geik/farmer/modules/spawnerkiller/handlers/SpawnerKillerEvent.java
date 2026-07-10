package xyz.geik.farmer.modules.spawnerkiller.handlers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.jetbrains.annotations.NotNull;
import xyz.geik.farmer.modules.spawnerkiller.SpawnerKiller;
import xyz.geik.farmer.modules.spawnerkiller.service.SpawnProcessor;

/**
 * Handles vanilla/Paper spawner events when SpawnerMeta is not active.
 *
 * @author poyraz
 * @author siberanka
 */
public final class SpawnerKillerEvent implements Listener {

    private final SpawnerKiller module;
    private final SpawnProcessor processor;

    public SpawnerKillerEvent(SpawnerKiller module, SpawnProcessor processor) {
        this.module = module;
        this.processor = processor;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawnEvent(@NotNull SpawnerSpawnEvent event) {
        if (!module.isOperational()) {
            return;
        }
        processor.submit(event.getEntity());
    }
}
