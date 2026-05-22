# Meow Anti-Xray

`Meow Anti-Xray` is a server-side Minecraft anti-xray mod for Fabric and NeoForge. It brings a Paper-like ore hiding experience to modded dedicated servers without requiring any client-side install. Players can join normally, while the server protects valuable blocks by rewriting chunk packets before they reach the client. 🛡️⛏️

This project was split from `MeowConsole` and now focuses purely on anti-xray protection. Console utilities, sleep features, player-message helpers, and other unrelated tools were removed so the mod stays lightweight, purpose-built, and easier to profile. Original project: <https://modrinth.com/mod/meowconsole> 🐾

## Highlights

- 🪨 **Paper-style ore obfuscation**: Uses engine mode 2 style chunk packet rewriting by default, hiding valuable blocks and replacing them with decoy states client-side.
- 🧭 **Paper Anti-Xray parity work**: Tracks Paper-style defaults for hidden blocks, replacement blocks, neighbor refresh behavior, reveal updates, and the `paper.antixray.bypass` permission node.
- 🚫 **Safer decoy selection**: Block entities such as chests and ender chests are excluded from mode 2 decoys, matching Paper's safer behavior and reducing client-side display issues.
- 🌍 **Per-dimension configuration**: Configure hidden blocks, replacement blocks, max height, and enabled state separately for the Overworld, Nether, End, and custom dimensions.
- ⚡ **Async chunk packet rewriting**: Moves rewrite work away from the main thread to reduce pressure while players explore, teleport, or load new terrain.
- 🧯 **Async backpressure**: Adds `async-queue-size` to limit queued rewrite work under heavy chunk loading, preventing large snapshot queues from growing into memory spikes or OOM crashes.
- 📊 **Built-in profiling**: Use `/antixray profile` to inspect rewrite counts, average rewrite time, max rewrite time, reveal packets, and other useful diagnostics.
- 🔍 **Smart reveal updates**: Mining, block-break starts, and explosions refresh nearby real blocks using a Paper-style neighbor update shape.
- 🔐 **Permission bypass support**: When `use-permission` is enabled, `bypass-permission` controls who can bypass anti-xray. Fabric and NeoForge permission APIs are bridged when available, with OP fallback.
- 🔌 **Fabric / NeoForge support**: One shared anti-xray core is maintained across both loaders for consistent behavior and configuration.

## Performance Notes

The main cost of anti-xray protection is chunk packet rewriting when players load new chunks. This is most visible during fast exploration, RCON teleport routes, high view distance, pregenerated worlds being explored for the first time, or many players loading different areas at once.

Recent optimization work reviewed Paper's implementation path. Paper can hook directly into vanilla chunk packet serialization and work with lighter packet metadata. A Fabric / NeoForge mod has to stay loader-compatible and therefore needs to snapshot enough section and packet data before rewriting asynchronously. To keep that safe under load, Meow Anti-Xray now applies bounded async backpressure:

```yml
anti-xray:
  async-chunk-rewrite: true
  async-worker-threads: 1
  async-queue-size: 16
```

`async-queue-size` is configurable. The default `16` favors memory safety and prevents queued snapshot work from growing without bounds. Servers with more memory can try `32` or `64` for higher exploration throughput. In local stress testing with 8 real network fake players repeatedly teleporting and loading chunks, the old unbounded path could trigger Java heap OOM; the bounded path completed the test cleanly with no client errors.

## Good Fit For

- Fabric / NeoForge survival servers
- Public, semi-public, and friend-group servers
- Servers that want Paper-like anti-xray while staying in the modded server ecosystem
- Pregenerated worlds, high-view-distance servers, and communities with frequent exploration

Pregeneration or chunk preloading does not bypass this mod. Meow Anti-Xray applies when chunk packets are sent to player clients, not when chunks are generated or loaded on the server. Web map renderers that read real server chunk data directly are different: those are not player chunk packets, so they need their own hiding or rendering configuration.

## Config

```text
config/meowantixray.yml
```

## Links

- GitHub: <https://github.com/xiaoyiluck666/MeowAnti-Xray>
- Issues: <https://github.com/xiaoyiluck666/MeowAnti-Xray/issues>
- Modrinth: <https://modrinth.com/mod/meowanti-xray>
- Original MeowConsole project: <https://modrinth.com/mod/meowconsole>
