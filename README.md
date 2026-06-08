# Meow Anti-Xray

Meow Anti-Xray is a server-side Minecraft mod focused on Paper-like anti-xray protection for Fabric and NeoForge dedicated servers.

## Features

- Paper-like ore obfuscation with engine mode 2 defaults.
- Per-dimension hidden ore and replacement block configuration.
- Reveal updates when players mine blocks, start breaking blocks, or explosions change nearby blocks.
- Optional async chunk packet rewrite with lightweight profiling counters.
- Async Modrinth update checks on server startup, with the latest result shown in `/antixray status`.
- Server commands: `/antixray`, `/antixray status`, `/antixray reload`, `/antixray profile`, `/antixray debug <world> <x> <y> <z>`.

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
