# Meow Anti-Xray Reference

- Minecraft 服务端反矿透模组，维护 Fabric 与 NeoForge 双 loader。
- 共享源码：`src/main/java/com/meowconsole/`。
- Fabric 专属入口与平台适配：`src/fabric/java/com/meowconsole/fabric/`。
- NeoForge 子工程：`neoforge/`。
- 反矿透核心：`src/main/java/com/meowconsole/antixray/`。
- 跨版本兼容层：`src/main/java/com/meowconsole/compat/MinecraftCompat.java`。
- Fabric mixin 清单：`src/main/resources/meowantixray.mixins.json`。
- NeoForge mixin 清单：`neoforge/src/main/resources/meowantixray.neoforge.mixins.json`。
- GitHub 私有仓库：`https://github.com/xiaoyiluck666/MeowAnti-Xray`，本地 `origin` 指向 `https://github.com/xiaoyiluck666/MeowAnti-Xray.git`。
- Modrinth 更新检测：`src/main/java/com/meowconsole/update/ModrinthUpdateChecker.java`，项目 slug `meowanti-xray`，公开页 `https://modrinth.com/mod/meowanti-xray`。
- 拆分来源项目：MeowConsole，Modrinth 公开页 `https://modrinth.com/mod/meowconsole`。
- 当前目标：Minecraft `26.1.2`，Java `25`；NeoForge 目标版本 `26.1.2.30-beta`。
- Paper parity 基准：2026-05-19 对齐 PaperMC/Paper `main` HEAD `5c917dababca4087b9df4d44b415d85125479830` 的 anti-xray 配置/行为要点。
- 反矿透权限：配置项 `use-permission` 开启后优先检查 `bypass-permission`，默认 `paper.antixray.bypass`；Fabric/NeoForge 权限 API 均用反射可选桥接，缺失时回退到管理员/OP 权限等级，避免 loader 依赖硬绑定。
- 反矿透异步重写背压：`async-worker-threads` 默认 1，`async-queue-size` 默认 16；异步容量为两者之和，容量满时同步 fallback。该默认值优先防止模组层 section/packet 快照任务堆积导致 OOM；内存充足且希望更高跑图吞吐时可把 `async-queue-size` 调到 32 或 64。
- 线程边界：本项目线程只处理反矿透功能；不要把 Meow Console 的控制台、睡觉、玩家消息、飞行保护、Velocity、MCDR 等非反矿透记忆写入本项目。
- 源项目边界：`E:\Code2026\fabricmod\meowconsole` 继续维护 `Meow Console`。

## Commands

- 双 loader 构建：`.\gradlew.bat buildAllLoaders`
- Fabric 构建与测试：`.\gradlew.bat build`
- NeoForge 构建与测试：`.\gradlew.bat :neoforge:build`
- 全量单测：`.\gradlew.bat test :neoforge:test`
- Minecraft 26.1.2 网络假玩家压测：`node tools\mc-2612-load-runner.js --clients 8 --duration 90 --rcon-teleport --rcon-password meow-local-rcon-20260519 --gamemode spectator --teleport-y 180 --teleport-interval 3000 --teleport-step 256 --connect-delay 400`。该工具不依赖 Carpet / mineflayer / minecraft-protocol，直接实现 protocol `775` 的登录、configuration、play、keepalive、teleport ack、chunk batch ack 子集；需要服务端 `online-mode=false`，RCON 仅用于可选 `/tp` 跑图，`--gamemode spectator` 用于避免高空跑图触发原版飞行检测。
