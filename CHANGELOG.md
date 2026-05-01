# Changelog

## 1.2.4

### 中文

🎉 **Meow Anti-Xray 正式作为独立服务端反矿透 Mod 发布！**

`Meow Anti-Xray` 从 [MeowConsole](https://modrinth.com/mod/meowconsole) 拆分而来，专注保留并强化原插件中的反矿透能力。控制台工具、睡眠、玩家消息、飞行保护等与反矿透无关的功能已移除，让服务器部署更轻、更清晰、更容易维护。🐾

#### 更新亮点

- 🛡️ **独立反矿透定位**：只做服务端矿物隐藏与真实方块显露，功能边界更清楚。
- 🪨 **接近 Paper Anti-Xray 的体验**：默认采用 engine mode 2 思路，对区块包中的高价值矿物进行伪装。
- ⚡ **纯服务端部署**：玩家无需安装客户端 Mod，适合公开服、生存服、好友服快速启用。
- 🌍 **按维度配置**：主世界、下界和自定义维度都可以单独配置隐藏矿物与替换方块。
- 🔍 **智能显露更新**：玩家挖掘、开始破坏方块、爆炸改变方块时，会刷新附近真实方块显示。
- 🚀 **可选异步区块包重写**：降低主线程压力，更适合高负载服务器。
- 📊 **内置性能统计**：`/antixray profile` 可查看重写次数、耗时和运行状态。
- 🧰 **实用管理命令**：保留 `/antixray status`、`/antixray reload`、`/antixray profile`、`/antixray debug`。
- 🔔 **新增 Modrinth 更新检测提示**：服务端启动后会异步检查 [Meow Anti-Xray](https://modrinth.com/mod/meowanti-xray) 是否有兼容当前 loader 和 Minecraft 版本的新版本；`/antixray status` 也会显示检测结果。
- 🔌 **Fabric / NeoForge 双 loader 维护**：共享同一套反矿透核心，保持行为对齐。
- 🧩 **NeoForge 目标更新**：构建目标对齐到 `26.1.2.30-beta` / Minecraft `26.1.2` / Java `25`。
- 🧯 **Fabric 启动修复**：修复爆炸相关 mixin 选择过早加载目标类的问题，避免新版 `Explosion` interface 下的 mixin 冲突。

#### 给 MeowConsole 用户

- 🐾 如果你只需要反矿透功能，推荐迁移到 `Meow Anti-Xray`。
- 🧩 如果你还需要 MeowConsole 的控制台或其他服务器辅助能力，可以继续使用 [MeowConsole](https://modrinth.com/mod/meowconsole)。

---

### English

🎉 **Meow Anti-Xray is now released as a standalone server-side anti-xray mod!**

`Meow Anti-Xray` was split from [MeowConsole](https://modrinth.com/mod/meowconsole) and now focuses purely on anti-xray protection. Console utilities, sleep features, player-message helpers, flight protection, and other unrelated tools were removed so the mod stays lighter, clearer, and easier to maintain. 🐾

#### Highlights

- 🛡️ **Dedicated anti-xray scope**: Focuses on server-side ore hiding and real-block reveal updates.
- 🪨 **Paper-like protection**: Uses engine mode 2 style defaults to obfuscate valuable ores in chunk packets.
- ⚡ **Server-side only**: Players do not need to install anything on their clients.
- 🌍 **Per-dimension configuration**: Configure hidden ores and replacement blocks for the Overworld, Nether, and custom dimensions.
- 🔍 **Smart reveal updates**: Mining, block-break starts, and explosions refresh nearby real blocks when needed.
- 🚀 **Optional async chunk packet rewriting**: Reduces main-thread pressure for busier servers.
- 📊 **Built-in profiling**: `/antixray profile` reports rewrite counts, timings, and runtime status.
- 🧰 **Practical admin commands**: Includes `/antixray status`, `/antixray reload`, `/antixray profile`, and `/antixray debug`.
- 🔔 **New Modrinth update checks**: On server startup, the mod asynchronously checks [Meow Anti-Xray](https://modrinth.com/mod/meowanti-xray) for newer versions compatible with the current loader and Minecraft version; `/antixray status` also shows the latest check result.
- 🔌 **Fabric / NeoForge support**: Both loaders share one anti-xray core and are kept behaviorally aligned.
- 🧩 **NeoForge target update**: Build target is aligned to `26.1.2.30-beta` / Minecraft `26.1.2` / Java `25`.
- 🧯 **Fabric startup fix**: Fixed explosion mixin selection loading target classes too early, avoiding mixin conflicts with the newer `Explosion` interface.

#### For MeowConsole Users

- 🐾 If you only need anti-xray protection, `Meow Anti-Xray` is the recommended lightweight choice.
- 🧩 If you still need MeowConsole console utilities or other server helper features, keep using [MeowConsole](https://modrinth.com/mod/meowconsole).
