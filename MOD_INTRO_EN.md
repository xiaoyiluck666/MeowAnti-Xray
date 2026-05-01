# Meow Anti-Xray

`Meow Anti-Xray` is a server-side Minecraft anti-xray mod for Fabric and NeoForge. It brings Paper-like ore hiding to modded dedicated servers with no client-side install required, so you can protect survival worlds without asking players to change anything. 🛡️⛏️

This project was split from `MeowConsole` and now focuses purely on anti-xray protection. Console utilities, sleep features, player-message helpers, and other unrelated tools were removed so this mod stays lightweight and purpose-built. Original project: <https://modrinth.com/mod/meowconsole> 🐾

## Why Use It

- 🪨 **Paper-like ore obfuscation**: Uses engine mode 2 style defaults to hide valuable blocks inside chunk packets.
- 🌍 **Per-dimension control**: Configure hidden ores and replacement blocks for the Overworld, Nether, and custom dimensions.
- ⚡ **Server-side deployment**: Players do not need to install anything on their clients.
- 🔍 **Smart reveal updates**: Mining, block-break starts, and explosions refresh nearby real blocks when needed.
- 🚀 **Optional async rewriting**: Move chunk packet rewrite work away from the main thread for busier servers.
- 📊 **Built-in profiling**: Use `/antixray profile` to inspect rewrite counts and timing metrics.
- 🔔 **Modrinth update notices**: Checks for compatible updates asynchronously after server startup, and `/antixray status` shows the latest result.
- 🧰 **Practical admin commands**: Manage status, reload config, profile performance, and debug exact block positions.
- 🔌 **Fabric / NeoForge support**: One anti-xray core maintained across both loaders.

## Config

```text
config/meowantixray.yml
```

## Links

- GitHub: <https://github.com/xiaoyiluck666/MeowAnti-Xray>
- Issues: <https://github.com/xiaoyiluck666/MeowAnti-Xray/issues>
- Modrinth: <https://modrinth.com/mod/meowanti-xray>
- Original MeowConsole project: <https://modrinth.com/mod/meowconsole>
