# Meow Anti-Xray

Meow Anti-Xray is a server-side Minecraft mod focused on Paper-like anti-xray protection for Fabric and NeoForge dedicated servers.

## Features

- Paper-like ore obfuscation with engine mode 2 defaults.
- Per-dimension hidden ore and replacement block configuration.
- Reveal updates when players mine blocks, start breaking blocks, or explosions change nearby blocks.
- Optional async chunk packet rewrite with lightweight profiling counters.
- Safe configuration workflow for server owners: read-only `/antixray config`, dry-run `/antixray validate`, and atomic `/antixray reload` that retains the last-known-good protection on failure.
- Cleaner console and RCON command output, with stable `minecraft:*` dimension ids in inspect results.
- Async Modrinth update checks on server startup, with the latest result shown in `/antixray status`.
- Optional anonymous bStats usage metrics for counting active servers and basic runtime versions.
- Server commands: `/antixray`, `/antixray status`, `/antixray config`, `/antixray validate`, `/antixray reload`, `/antixray profile`, `/antixray debug <world> <x> <y> <z>`, `/antixray inspect <world> <x> <y> <z>`.

## Compatibility

Meow Anti-Xray `1.7.0` supports Minecraft `26.1`, `26.1.1`, `26.1.2`, `26.2`, and `26.3` with separate builds for each Minecraft support line and loader. All builds require Java `25`.

| Minecraft | Mod version | Fabric Loader | Fabric API | NeoForge |
| --- | --- | --- | --- | --- |
| `26.3` | `1.7.0` | `>=0.19.5` | `>=0.161.0+26.3` | `>=26.3.0.16-beta` |
| `26.2` | `1.7.0` | `>=0.19.5` | `>=0.158.0+26.2` | `>=26.2.0.69` |
| `26.1` / `26.1.1` / `26.1.2` | `1.7.0` | `>=0.19.5` | `>=0.144.0+26.1` | `>=26.1.0.19-beta` |

Choose the file matching both your Minecraft support line and loader. Fabric API and NeoForge must also match the server's Minecraft version. Fabric Loader 0.19.5 includes the MixinExtras compatibility required by the new builds.

- The repeatable Minecraft 26.2 dual-loader pressure matrix is recorded at [reports/1.5.0-pressure-matrix.md](reports/1.5.0-pressure-matrix.md).
- Version-specific 1.7.0 release checks are recorded at [reports/1.7.0-multiversion-release.md](reports/1.7.0-multiversion-release.md).

## Configuration

The default config is generated at:

```text
config/meowantixray.yml
```

Example:

```yml
anti-xray:
  engine-mode: 2
  max-block-height: 64
  hidden-blocks: ["minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"]
  replacement-blocks:
    - minecraft:stone
    - minecraft:deepslate
  dimension-settings:
    nether:
      hidden-blocks:
        - minecraft:ancient_debris
      replacement-blocks:
        - minecraft:netherrack
```

Existing config files are supplemented with newly added missing keys during load without overwriting your values. Quoted YAML values, quoted list items, inline lists, and dimension aliases such as `nether` / `end` are supported.
Explicit empty inline lists such as `hidden-blocks: []` and `replacement-blocks: []` are also preserved as intentional user config.

bStats is configured separately at `config/meowantixray-bstats.properties`. It stores a random server UUID and sends only anonymous aggregate data. Set `enabled=false` to opt out. After the bStats service for this mod is registered, set its numeric `service-id` in this file; the default `0` keeps reporting disabled until that registration is complete.

## Commands

- `/antixray status`
  Shows loader/runtime info, async capacity, config path, and per-dimension counts.
- `/antixray config`
  Shows the effective read-only config summary, including source path, global settings, async capacity, permission bypass, and per-dimension overrides.
- `/antixray validate`
  Checks the file on disk without applying it. Reports syntax, value, dimension, and block-id errors and tells you whether reload is safe.
- `/antixray reload`
  Validates first, then applies the config atomically. Invalid input leaves the file and last-known-good active configuration unchanged.
- `/antixray profile`
  Shows rewrite/reveal counters, timings, async pressure, and sync fallback ratios.
- `/antixray debug <world> <x> <y> <z>`
- `/antixray inspect <world> <x> <y> <z>`
  In-game this shows multi-line inspect output for a specific block position, including real/fake block, target classification, exposure state, and active config context. Console and RCON receive the same information in a compact single-line form.

## Build

```powershell
.\gradlew.bat buildAllLoaders
```

Fabric only:

```powershell
.\gradlew.bat build
```

NeoForge only:

```powershell
.\gradlew.bat :neoforge:build
```

## Links

- Repository: `https://github.com/xiaoyiluck666/MeowAnti-Xray`
- Issues: `https://github.com/xiaoyiluck666/MeowAnti-Xray/issues`
- Modrinth: `https://modrinth.com/mod/meowanti-xray`
- Split from MeowConsole: `https://modrinth.com/mod/meowconsole`
