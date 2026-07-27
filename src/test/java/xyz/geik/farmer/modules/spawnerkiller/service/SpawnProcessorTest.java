package xyz.geik.farmer.modules.spawnerkiller.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnProcessorTest {

    @Test
    void stackExperienceUsesRuntimeRewardAndClampsOverflow() {
        assertEquals(120, SpawnProcessor.safeStackExperience(5, 24));
        assertEquals(0, SpawnProcessor.safeStackExperience(-1, 24));
        assertEquals(0, SpawnProcessor.safeStackExperience(5, -1));
        assertEquals(Integer.MAX_VALUE,
                SpawnProcessor.safeStackExperience(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
