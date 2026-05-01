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
- 线程边界：本项目线程只处理反矿透功能；不要把 Meow Console 的控制台、睡觉、玩家消息、飞行保护、Velocity、MCDR 等非反矿透记忆写入本项目。
- 源项目边界：`E:\Code2026\fabricmod\meowconsole` 继续维护 `Meow Console`。

## Commands

- 双 loader 构建：`.\gradlew.bat buildAllLoaders`
- Fabric 构建与测试：`.\gradlew.bat build`
- NeoForge 构建与测试：`.\gradlew.bat :neoforge:build`
- 全量单测：`.\gradlew.bat test :neoforge:test`
