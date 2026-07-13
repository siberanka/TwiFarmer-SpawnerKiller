package xyz.geik.farmer.modules.spawnerkiller.configuration;

/** Bounded GitHub release-check settings. */
public record UpdateSettings(
        boolean enabled,
        int checkIntervalHours,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds
) {
    public static UpdateSettings from(ConfigFile.UpdateCheckerSettings settings) {
        return new UpdateSettings(settings.isEnable(), settings.getCheckIntervalHours(),
                settings.getConnectTimeoutSeconds(), settings.getRequestTimeoutSeconds());
    }
}
