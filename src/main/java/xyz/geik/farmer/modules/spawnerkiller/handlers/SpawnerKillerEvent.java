package xyz.geik.farmer.modules.spawnerkiller.handlers;

import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.jetbrains.annotations.NotNull;
import xyz.geik.farmer.modules.spawnerkiller.SpawnerKiller;
import xyz.geik.farmer.modules.spawnerkiller.service.SpawnProcessor;

/**
 * Handles the vanilla/Paper spawn paths. Notifications are deferred until the
 * event chain and optional stacking integrations have finalized the entity.
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSpawnerSpawnEvent(@NotNull SpawnerSpawnEvent event) {
        if (!module.isOperational()) {
            return;
        }
        processor.submitAfterSpawn(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCreatureSpawnEvent(@NotNull CreatureSpawnEvent event) {
        if (!module.isOperational() || !isSpawnerReason(event.getSpawnReason())) {
            return;
        }
        processor.submitAfterSpawn(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPreSpawnerSpawnEvent(@NotNull PreSpawnerSpawnEvent event) {
        if (!module.isOperational() || !event.isCancelled() || !event.shouldAbortSpawn()) {
            return;
        }
        processor.recoverCancelledPreSpawn(event.getSpawnerLocation(), event.getType());
    }

    static boolean isSpawnerReason(CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.SPAWNER;
    }
}
