# Farmer SpawnerKiller Module

A production-focused Farmer module that removes mobs created by spawners while preserving drops and Farmer-region controls.

Maintainers/authors: Geik contributors and **siberanka**.

## Platform support

- Minecraft **1.21.x** and **26.x**
- **Paper**, **Folia**, and **Leaf**
- Farmer **v6-b123+**
- Optional SpawnerMeta **25.8** and WildStacker API **2025.2** integrations
- Java 21 for the 1.21.x server line; Paper 26.x server runtime requires Java 25

Plain Bukkit and Spigot servers are intentionally unsupported. The module verifies the Paper scheduler API during startup and fails closed on an unsupported platform.

## Installation

1. Stop the server.
2. Place `Farmer-SpawnerKiller-1.1.3.jar` in `plugins/Farmer/modules/`.
3. Start the server once to generate `plugins/Farmer/modules/spawnerkiller/config.yml` and the selected language file.
4. Set `status: true`, then restart or reload Farmer.

## Features

- Kills vanilla/Paper spawner entities, or consumes SpawnerMeta post-spawn batches.
- Honors Farmer region state and the per-Farmer `spawnerkiller` attribute.
- Supports whitelist/blacklist entity filtering and optional cooked drops.
- Coalesces duplicate Bukkit/SpawnerMeta notifications before committing drops.
- Uses Paper's entity scheduler for every delayed entity/world mutation, including Folia region ownership changes.
- Uses bounded async work only for immutable admission checks and WildStacker drop calculation; results are revalidated on the owning region before commit.
- Applies queue back-pressure, regional limits, legal drop batching, stack-size ceilings, overflow-safe XP calculations, and rate-limited operational logging.
- Uses each mob's runtime Paper XP reward for WildStacker batches, so new 26.x entity types do not fall through a hard-coded reward table.
- Automatically adds missing config/language entries. Malformed, wrongly typed, meaningless, oversized, or invalid entries are backed up to `*.bak-<UTC timestamp>` before repair.

## Update checker

`update-checker.enable` defaults to `true`. The module checks only the fixed `siberanka/TwiFarmer-SpawnerKiller` GitHub repository using asynchronous HTTPS at startup and every six hours by default. Connection/request timeouts, response size, SemVer tags, and release URLs are strictly bounded and validated.

When a newer release exists, the console and each operator or player with `farmer.admin` receive one localized message per release containing the SpawnerKiller module name, installed/latest versions, and validated download link. Reload/disable cancels or invalidates pending work.

```yaml
update-checker:
  enable: true
  check-interval-hours: 6
  connect-timeout-seconds: 5
  request-timeout-seconds: 8
```

## Production optimization

The optimization module is disabled by default, so every child setting is inert until `optimize-module.enable` is set to `true`.

```yaml
optimize-module:
  enable: false
  async-precheck: true
  async-stack-drops: true
  processing-delay-ticks: 2
  max-entities-per-run: 64
  max-queued-entities: 512
  max-pending-per-region: 64
  collapse-duplicate-spawns: true
  batch-drops: true
  max-stack-process-amount: 100000
  audit-log-rate-limit-ms: 5000
```

`processing-delay-ticks` defers work without touching Bukkit state asynchronously. Large SpawnerMeta batches are spread across ticks with `max-entities-per-run`. Queue limits prevent task flooding; overflow falls back to immediate work on the correct region thread. `async-stack-drops` follows WildStacker's recommendation for large loot calculations, then validates entity identity and stack amount again before removing the stack and spawning rewards.

## Configuration recovery

At enable/reload the module validates:

- booleans, ranges, permission syntax, and filter mode;
- entity names against the runtime registry, while retaining canonical 26.x names on a 1.21.x host;
- every optimization key and safety bound;
- selected language key types, list sizes, and meaningful text values;
- YAML size and parse validity.

Unknown custom keys are preserved. Missing keys are merged without creating an error backup. Invalid content is copied first and only then replaced with a safe default. If a selected bundled language is unavailable, English is used safely.

## Building

```bash
mvn -Ppaper-1.21 clean verify
mvn -Ppaper-26 clean verify
```

The release artifact is written to `target/Farmer-SpawnerKiller-1.1.3.jar`. Dependencies are provided by the server/Farmer module loader and are not shaded into the module.

## Security and lifecycle notes

- Farmer state is checked immediately before committing a kill.
- A persistent entity transaction marker prevents double processing and duplicate rewards.
- Stale async WildStacker plans are recalculated at most twice; continuously changing or corrupt state is rejected fail-closed.
- GUI toggles are permission checked, rate limited, and serialized per Farmer.
- Listeners, processor state, and queues are released on reload/disable. SpawnerMeta's API has no unregister operation, so one reusable inactive bridge is retained instead of registering another callback on every Farmer reload.

## Contributing

Open pull requests against `main`. Keep Paper/Folia region ownership, fail-closed validation, bounded queues, and the config backup guarantee intact when changing the hot path.
