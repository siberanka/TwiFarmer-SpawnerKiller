package xyz.geik.farmer.modules.spawnerkiller.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubReleaseParserTest {
    @Test
    void selectsValidatedJar() {
        String body = """
                {"tag_name":"v1.1.1","html_url":"https://github.com/siberanka/TwiFarmer-SpawnerKiller/releases/tag/v1.1.1",
                "assets":[{"name":"Farmer-SpawnerKiller-1.1.1.jar","browser_download_url":"https://github.com/siberanka/TwiFarmer-SpawnerKiller/releases/download/v1.1.1/Farmer-SpawnerKiller-1.1.1.jar"}]}
                """;
        GitHubReleaseParser.ReleaseInfo release = GitHubReleaseParser.parse(body).orElseThrow();
        assertEquals("v1.1.1", release.tag());
        assertTrue(release.downloadUrl().endsWith("Farmer-SpawnerKiller-1.1.1.jar"));
    }

    @Test
    void rejectsMalformedOversizedAndForeignResponses() {
        assertTrue(GitHubReleaseParser.parse("not-json").isEmpty());
        assertTrue(GitHubReleaseParser.parse("x".repeat(GitHubReleaseParser.MAX_RESPONSE_LENGTH + 1)).isEmpty());
        assertTrue(GitHubReleaseParser.parse(
                "{\"tag_name\":\"v1.1.1\",\"html_url\":\"https://example.com/release\",\"assets\":[]}").isEmpty());
    }
}
