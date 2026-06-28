# Meow Anti-Xray

Meow Anti-Xray is a server-side Minecraft mod focused on Paper-like anti-xray protection for Fabric and NeoForge dedicated servers.

## Features

- Paper-like ore obfuscation with engine mode 2 defaults.
- Per-dimension hidden ore and replacement block configuration.
- Reveal updates when players mine blocks, start breaking blocks, or explosions change nearby blocks.
- Optional async chunk packet rewrite with lightweight profiling counters.
- Expanded diagnostics for server owners: richer `/antixray status`, reload change summaries, and `/antixray inspect`.
- Cleaner console and RCON command output, with stable `minecraft:*` dimension ids in inspect results.
- Async Modrinth update checks on server startup, with the latest result shown in `/antixray status`.
- Server commands: `/antixray`, `/antixray status`, `/antixray reload`, `/antixray profile`, `/antixray debug <world> <x> <y> <z>`, `/antixray inspect <world> <x> <y> <z>`.

## Compatibility

Meow Anti-Xray `1.3.0` targets Minecraft `26.2` and Java `25`.

- Fabric: Fabric Loader `>=0.19.3`, Fabric API `>=0.153.0+26.2`, Minecraft `>=26.2 <26.3`.
- NeoForge: NeoForge `>=26.2.0.7-beta`, Minecraft `[26.2,26.3)`.
- The Minecraft 26.2 pressure-test report is available at [reports/1.3.0-minecraft-26.2-pressure-report.md](reports/1.3.0-minecraft-26.2-pressure-report.md).

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

## Commands

- `/antixray status`
  Shows loader/runtime info, async capacity, config path, and per-dimension counts.
- `/antixray reload`
  Reloads the config and reports the post-reload state plus any effective runtime diffs.
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
