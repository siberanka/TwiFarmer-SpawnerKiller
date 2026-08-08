package xyz.geik.farmer.modules.spawnerkiller.handlers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerKillerEventTest {

    @Test
    void recognizesOnlySpawnerCreatureReason() {
        assertTrue(SpawnerKillerEvent.isSpawnerReason(CreatureSpawnEvent.SpawnReason.SPAWNER));
        assertFalse(SpawnerKillerEvent.isSpawnerReason(CreatureSpawnEvent.SpawnReason.NATURAL));
        assertFalse(SpawnerKillerEvent.isSpawnerReason(CreatureSpawnEvent.SpawnReason.CUSTOM));
        assertFalse(SpawnerKillerEvent.isSpawnerReason(null));
    }

    @Test
    void observesFinalSpawnerEventStateIncludingCancelledCompatibilityEvents() throws Exception {
        Method listener = SpawnerKillerEvent.class.getMethod(
                "onSpawnerSpawnEvent", org.bukkit.event.entity.SpawnerSpawnEvent.class);
        EventHandler handler = listener.getAnnotation(EventHandler.class);

        assertEquals(EventPriority.MONITOR, handler.priority());
        assertFalse(handler.ignoreCancelled());
    }

    @Test
    void observesCancelledPaperPreSpawnEventsForStackRecovery() throws Exception {
        Method listener = SpawnerKillerEvent.class.getMethod(
                "onPreSpawnerSpawnEvent", com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent.class);
        EventHandler handler = listener.getAnnotation(EventHandler.class);

        assertEquals(EventPriority.MONITOR, handler.priority());
        assertFalse(handler.ignoreCancelled());
    }
}
