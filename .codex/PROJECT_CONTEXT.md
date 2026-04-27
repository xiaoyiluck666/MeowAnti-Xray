# Meow Anti-Xray Project Context

- 本目录是从 MeowConsole 拆分出的反矿透独立项目。
- 目标 mod id / 构建产物名：`meowantixray`。
- 当前仍保留 Java 包名 `com.meowconsole`，用于降低拆分时对 mixin、测试与跨 loader 代码的震动。
- Fabric 入口：`src/fabric/java/com/meowconsole/fabric/MeowConsoleFabricMod.java`。
- NeoForge 入口：`neoforge/src/main/java/com/meowconsole/neoforge/MeowConsoleNeoForgeMod.java`。
- 反矿透配置文件：`config/meowantixray.yml`。
- 已移除控制台、睡眠加速、玩家消息、飞行保护、Velocity、MCDR 兼容、Modrinth 更新检查等非反矿透源码。

## Verification

- 2026-04-27：`.\gradlew.bat test :neoforge:test --console=plain` 通过。
- 2026-04-27：`.\gradlew.bat buildAllLoaders --console=plain` 通过。
