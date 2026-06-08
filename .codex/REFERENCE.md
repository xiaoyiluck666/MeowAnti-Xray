# Meow Anti-Xray Reference

- Minecraft 服务端反矿透模组，维护 Fabric 与 NeoForge 双 loader。
- 共享源码：`src/main/java/com/meowantixray/`。
- Fabric 专属入口与平台适配：`src/fabric/java/com/meowantixray/fabric/`。
- NeoForge 子工程：`neoforge/`。
- NeoForge 专属入口与平台适配：`neoforge/src/main/java/com/meowantixray/neoforge/`。
- 反矿透核心：`src/main/java/com/meowantixray/antixray/`。
- 跨版本兼容层：`src/main/java/com/meowantixray/compat/MinecraftCompat.java`。
- Fabric mixin 清单：`src/main/resources/meowantixray.mixins.json`。
- NeoForge mixin 清单：`neoforge/src/main/resources/meowantixray.neoforge.mixins.json`。
- GitHub 私有仓库：`https://github.com/xiaoyiluck666/MeowAnti-Xray`，本地 `origin` 指向 `https://github.com/xiaoyiluck666/MeowAnti-Xray.git`。
- Modrinth 更新检测：`src/main/java/com/meowantixray/update/ModrinthUpdateChecker.java`，项目 slug `meowanti-xray`，公开页 `https://modrinth.com/mod/meowanti-xray`。
- 拆分来源项目：MeowConsole，Modrinth 公开页 `https://modrinth.com/mod/meowconsole`。
- 当前目标：Minecraft `26.1.2`，Java `25`；NeoForge 目标版本 `26.1.2.30-beta`。
- Paper parity 基准：2026-06-06 复查 PaperMC/Paper `main` HEAD `76d2ac758cb3abe75aceefa88207443768f585c6` 的 anti-xray 配置/行为要点；Paper 默认 hidden/replacement 字段未新增。本项目默认 hidden blocks 在 Paper 风格默认值外额外包含 `ancient_debris`、`nether_quartz_ore`、`nether_gold_ore`，属于独立反矿透模组的实用保护增强，不视为需要回退的 parity bug。
- 反矿透权限：配置项 `use-permission` 开启后优先检查 `bypass-permission`，默认 `paper.antixray.bypass`；Fabric/NeoForge 权限 API 均用反射可选桥接，缺失时回退到管理员/OP 权限等级，避免 loader 依赖硬绑定。
- 反矿透异步重写背压：`async-worker-threads` 默认 1，`async-queue-size` 默认 16；异步容量为两者之和，容量满时同步 fallback。该默认值优先防止模组层 section/packet 快照任务堆积导致 OOM；内存充足且希望更高跑图吞吐时可把 `async-queue-size` 调到 32 或 64。
- 线程边界：本项目线程只处理反矿透功能；不要把 Meow Console 的控制台、睡觉、玩家消息、飞行保护、Velocity、MCDR 等非反矿透记忆写入本项目。
- 源项目边界：`E:\Code2026\fabricmod\meowconsole` 继续维护 `Meow Console`。

## Commands

- 双 loader 构建：`.\gradlew.bat buildAllLoaders`
- Fabric 构建与测试：`.\gradlew.bat build`
- NeoForge 构建与测试：`.\gradlew.bat :neoforge:build`
- 全量单测：`.\gradlew.bat test :neoforge:test`
- 发布构建：`.\gradlew.bat clean test :neoforge:test releaseAllLoaders --console=plain`
- Modrinth 发布检查/上传：`.\tools\publish-modrinth.ps1` 默认 dry-run；确认无误后用 `.\tools\publish-modrinth.ps1 -Upload`。脚本读取 `MODRINTH_TOKEN` 或 `MODRINTH_API_TOKEN`，会拒绝上传已存在的 `<version>+fabric` / `<version>+neoforge`。
- Minecraft 26.1.2 网络假玩家压测：`node tools\mc-2612-load-runner.js --clients 8 --duration 90 --rcon-teleport --rcon-password meow-local-rcon-20260519 --gamemode spectator --teleport-y 180 --teleport-interval 3000 --teleport-step 256 --connect-delay 400`。该工具不依赖 Carpet / mineflayer / minecraft-protocol，直接实现 protocol `775` 的登录、configuration、play、keepalive、teleport ack、chunk batch ack 子集；需要服务端 `online-mode=false`，RCON 仅用于可选 `/tp` 跑图，`--gamemode spectator` 用于避免高空跑图触发原版飞行检测。
