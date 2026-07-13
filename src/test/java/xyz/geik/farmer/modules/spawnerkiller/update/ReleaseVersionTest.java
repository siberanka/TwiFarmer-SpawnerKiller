package xyz.geik.farmer.modules.spawnerkiller.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseVersionTest {
    @Test
    void comparesStableAndPrereleaseVersions() {
        assertTrue(ReleaseVersion.isNewer("1.1.0", "v1.1.1"));
        assertTrue(ReleaseVersion.isNewer("1.1.1-beta.2", "1.1.1"));
        assertFalse(ReleaseVersion.isNewer("1.1.1", "1.1.1-beta.2"));
        assertFalse(ReleaseVersion.isNewer("1.1.1", "1.1.1"));
    }

    @Test
    void rejectsMalformedVersions() {
        assertTrue(ReleaseVersion.parse("1..1").isEmpty());
        assertTrue(ReleaseVersion.parse("1.01.1").isEmpty());
        assertTrue(ReleaseVersion.parse("1.1.1-01").isEmpty());
        assertTrue(ReleaseVersion.parse("9".repeat(65)).isEmpty());
    }
}
