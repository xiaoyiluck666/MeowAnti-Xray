# 🛡️ Meow Anti-Xray

> Server-side anti-xray for Fabric and NeoForge survival servers.<br>
> ⛏️ Paper-style ore obfuscation, 🚀 no client install, 🔌 one shared core across both loaders.

`Meow Anti-Xray` focuses on one job: bringing a Paper-like anti-xray experience to modded dedicated servers. Players join normally with a vanilla or modded client, while the server rewrites chunk packets before they are sent, hiding unexposed valuable blocks from xray clients.

This project was split from `MeowConsole` and now contains only anti-xray functionality. Console utilities, sleep features, player-message helpers, and unrelated tools were removed so the mod stays lightweight, focused, and easier to profile.

## ✨ Why Use Meow Anti-Xray

- 🚀 **Server-side only**: Install it on the server; players do not need to install anything.
- 🧭 **Paper-style behavior**: Uses an engine mode 2 inspired default setup and tracks Paper-style hidden blocks, replacement blocks, reveal updates, and neighbor refresh behavior.
- 🔌 **Built for modded servers**: Fabric and NeoForge share the same anti-xray core, keeping configuration and behavior aligned across loaders.
- 🧱 **Safer decoys**: Block entities such as chests and ender chests are excluded from fake ore candidates, reducing visual glitches and confusing client-side states.
- ⚡ **Stable under exploration load**: Async chunk packet rewriting uses bounded backpressure to avoid unbounded snapshot queues and memory spikes.
- 📊 **Clearer server-owner diagnostics**: `/antixray status` now reports runtime state, async capacity, config path, and per-dimension hidden/replacement counts; `/antixray reload` reports effective runtime diffs; `/antixray inspect` explains why a specific block is or is not being obfuscated, now with stable standard dimension ids and cleaner console / RCON output.

## 🧩 Features

- 💎 Hides diamonds, ancient debris, redstone, lapis, gold, and other valuable blocks.
- 🪨 Replaces hidden blocks client-side with configurable decoy states.
- 🔍 Reveals nearby real blocks when players mine, start breaking blocks, or explosions modify terrain.
- 🌍 Supports separate settings for the Overworld, Nether, End, and custom dimensions.
- ⚙️ Supports per-dimension height ranges, hidden block lists, replacement block lists, and enable switches.
- 🔐 Supports permission bypass through `paper.antixray.bypass` or a custom permission node.
- 📣 Checks Modrinth for updates asynchronously and shows the latest status in `/antixray status`.

## 🌐 Compatibility

Version `1.3.1` targets Minecraft `26.2` and Java `25`:

- Fabric: Fabric Loader `>=0.19.3`, Fabric API `>=0.153.0+26.2`, Minecraft `>=26.2 <26.3`.
- NeoForge: NeoForge `>=26.2.0.7-beta`, Minecraft `[26.2,26.3)`.

## ⚙️ Performance Model

The expensive part of anti-xray protection happens when players load new chunks. Meow Anti-Xray moves chunk packet rewriting away from the main thread when possible and limits queued async work with `async-queue-size`:

```yml
anti-xray:
  async-chunk-rewrite: true
  async-worker-threads: 1
  async-queue-size: 16
```

The default favors memory safety. Servers with more memory or heavy exploration traffic can try `32` or `64`, then use `/antixray profile` to watch for sync fallback and rewrite timings.

Paper can hook directly into vanilla chunk packet serialization. A Fabric / NeoForge mod has to keep enough snapshot data to rewrite packets safely across loaders. This project aims for Paper-style protection with an implementation tuned for the mod loader environment, not a line-by-line copy of Paper internals.

## 📈 Stress-Tested

Meow Anti-Xray has been tested with real exploration and heavy chunk-loading workloads. The numbers below come from local Fabric / NeoForge dev servers, spark profiler reports, and the project's network fake-player load runner. The goal was to verify stable chunk packet rewriting under real loading pressure.

| Scenario | Result |
| --- | --- |
| 🧪 Fabric 26.2 / 1.3.0 baseline with 8 network fake players | `32,893` chunks processed in `92s`, about `357.49 chunks/s`, `0` client errors |
| 🔥 NeoForge 26.2 / 1.3.0 baseline with 8 network fake players | `35,593` chunks processed in `92s`, about `386.86 chunks/s`, `0` client errors |
| 🧪 Fabric real single-player exploration | Stable `20.00 TPS`, median MSPT `3.31ms`, with `meowantixray` taking about `3.55%` in the server-thread mods view |
| 🔥 NeoForge stress test with 8 network fake players | `36,233` chunks processed in `91s`, about `398.06 chunks/s`, `0` client errors |
| 🚀 Higher-throughput async queue test | With `async-queue-size=64`, `38,037` chunks processed in `91s`, about `417.92 chunks/s`, `0` client errors |

These tests show that the default configuration favors memory stability and prevents async rewrite work from growing into an unbounded queue during heavy exploration. Servers with more memory can raise `async-queue-size` to trade memory headroom for higher chunk throughput. Full spark reports:

- 🧪 Fabric real exploration: <https://spark.lucko.me/btQdhJh1gH>
- 🔥 NeoForge 8 fake players, conservative default queue: <https://spark.lucko.me/yFi0jlgOf4>
- 🚀 NeoForge 8 fake players, higher-throughput queue: <https://spark.lucko.me/FyVDZTMgHb>

## 🌍 Good Fit For

- 🧱 Fabric / NeoForge survival servers
- 🤝 Public, semi-public, and friend-group servers
- 🛡️ Servers that want strong anti-xray protection while staying in the modded ecosystem
- 🗺️ Pregenerated worlds, high-view-distance servers, frequent teleport routes, and active exploration
- 🐾 Servers migrating from the old MeowConsole built-in anti-xray module

Pregeneration or chunk preloading does not bypass this mod. It works when chunk packets are sent to player clients; it does not modify stored world data. Web maps and offline renderers that read real chunk data directly need their own hiding or rendering configuration.

## 🚀 Quick Start

1. Download the jar for your loader from Modrinth: use the Fabric file for Fabric servers and the NeoForge file for NeoForge servers.
2. Put the jar in the server `mods` folder.
3. Start the server once to generate:

```text
config/meowantixray.yml
```

4. Tune hidden blocks, replacement blocks, height ranges, and async settings for your server.
5. Use `/antixray reload` to reload the config, then check `/antixray status` and `/antixray profile`.

Common config example:

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

Existing config files are supplemented with newly added missing keys during load without overwriting your values. Quoted values, quoted list items, inline lists, and dimension aliases such as `nether` / `end` are supported. Explicit empty inline lists such as `hidden-blocks: []` are also preserved as intentional configuration.

## 🧰 Commands

```text
/antixray status
/antixray reload
/antixray profile
/antixray debug <world> <x> <y> <z>
/antixray inspect <world> <x> <y> <z>
```

## 🔗 Links

- GitHub: <https://github.com/xiaoyiluck666/MeowAnti-Xray>
- Issues: <https://github.com/xiaoyiluck666/MeowAnti-Xray/issues>
- Modrinth: <https://modrinth.com/mod/meowanti-xray>
- Original MeowConsole project: <https://modrinth.com/mod/meowconsole>
