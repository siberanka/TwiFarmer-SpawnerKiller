package xyz.geik.farmer.modules.spawnerkiller.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPlatformTest {

    @Test
    void requiredPaperAndFarmerApisAreOnTheCompileClasspath() {
        assertTrue(PaperPlatform.isSupported());
    }
}
