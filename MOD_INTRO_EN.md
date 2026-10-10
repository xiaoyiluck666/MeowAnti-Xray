# 🛡️ Meow Anti-Xray

💎 **Hide unexposed ores** before chunk data reaches X-ray clients.

🚀 **Install it on the server only** - players need no client mod.

🛡️ **Prevent ore-location leaks without false-positive punishments** - no behavior scoring or player accusations.

## 🚀 Quick Start

1. 📥 [Download the latest release](https://modrinth.com/mod/meowanti-xray/versions) for Fabric or NeoForge.
2. 📁 Put the jar in the server's `mods` directory and start the server.
3. ✅ Run `/antixray validate`, then `/antixray status` to confirm protection is active.

⚙️ Configuration is generated at `config/meowantixray.yml`. The defaults provide Paper-style engine mode 2 protection; per-dimension settings control hidden blocks, replacements, height ranges, permissions, and async capacity.

## 🛡️ Prevention, Not Player Profiling

- 📦 **Before delivery:** chunk packets are rewritten before they reach the player.
- 👁️ **Client view only:** unexposed ores look like ordinary blocks until the terrain is mined or changed; server world data remains untouched.
- ⚖️ **No player profiling:** protection does not score mining behavior or label players as cheaters.

## ✨ Highlights

- 🚀 **Server-side only:** vanilla and modded clients join normally.
- 🔌 **Dual loader:** one anti-xray core for Fabric and NeoForge dedicated servers.
- 🌍 **Per-dimension control:** configure the Overworld, Nether, End, and custom dimensions separately.
- ✅ **Safe reloads:** `/antixray validate` checks changes before atomic `/antixray reload`; invalid changes keep the last-known-good protection active.
- ⛏️ **Accurate reveals:** mining, block-breaking starts, explosions, and nearby changes reveal real blocks.
- ⚡ **Bounded async work:** `/antixray profile` exposes rewrite pressure and timing.
- 🔐 **Admin controls:** optional permission bypass and asynchronous Modrinth update checks.

## 🌐 Compatibility

| Minecraft | Release line | Fabric | NeoForge |
| --- | --- | --- | --- |
| `26.3` | `1.7.0` | ✅ | ✅ |
| `26.2` | `1.7.0` | ✅ | ✅ |
| `26.1`-`26.1.2` | `1.7.0` | ✅ | ✅ |

All `1.7.0` builds require Java `25`. Choose the file matching both Minecraft and loader.

- 🧵 **Fabric:** Loader `>=0.19.5`; Fabric API must match Minecraft (`26.3`: `>=0.161.0+26.3`; `26.2`: `>=0.158.0+26.2`; `26.1.x`: `>=0.144.0+26.1`).
- 🔥 **NeoForge:** use the matching Minecraft line (`26.3`: `>=26.3.0.16-beta`; `26.2`: `>=26.2.0.69`; `26.1.x`: `>=26.1.0.19-beta`).

## 📈 Performance Evidence

🧪 **Repeated 8-client, 90-second samples:** Fabric averaged `427.17 chunks/s`, NeoForge averaged `390.07 chunks/s`, and both recorded `0` client errors. [Read the full pressure matrix](https://github.com/xiaoyiluck666/MeowAnti-Xray/blob/main/reports/1.5.0-pressure-matrix.md).

## 🧰 Commands

```text
/antixray status
/antixray config
/antixray validate
/antixray reload
/antixray profile
/antixray debug <world> <x> <y> <z>
/antixray inspect <world> <x> <y> <z>
```

## 🔗 Support and Links

- 📖 [Documentation](https://github.com/xiaoyiluck666/MeowAnti-Xray#readme)
- 💻 [Source code](https://github.com/xiaoyiluck666/MeowAnti-Xray)
- 🐛 [Report a bug or request a feature](https://github.com/xiaoyiluck666/MeowAnti-Xray/issues/new/choose)
- 📥 [Download on Modrinth](https://modrinth.com/mod/meowanti-xray)

❤️ If the project saves your server administration time, you can support continued maintenance through [PayPal](https://www.paypal.com/paypalme/neworldcn) or [Afdian](https://afdian.com/a/xiaoyiluck).
