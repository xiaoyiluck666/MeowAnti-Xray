# Meow Anti-Xray Project Context

- 本目录是从 MeowConsole 拆分出的反矿透独立项目。
- 目标 mod id / 构建产物名：`meowantixray`。
- 当前仍保留 Java 包名 `com.meowconsole`，用于降低拆分时对 mixin、测试与跨 loader 代码的震动。
- Fabric 入口：`src/fabric/java/com/meowconsole/fabric/MeowConsoleFabricMod.java`。
- NeoForge 入口：`neoforge/src/main/java/com/meowconsole/neoforge/MeowConsoleNeoForgeMod.java`。
- 反矿透配置文件：`config/meowantixray.yml`。
- 已移除控制台、睡眠加速、玩家消息、飞行保护、Velocity、MCDR 兼容等非反矿透源码；保留本项目独立的 Modrinth 更新检测提示。

## Thread Boundary

- 后续 `MeowAnti-Xray` 相关任务单独开线程接管本目录。
- 本项目只维护反矿透配置、混入、区块包重写、reveals、性能统计和 Fabric/NeoForge 反矿透对齐。
- 控制台彩色、多人生睡觉、玩家消息、飞行保护、Velocity、MCDR、更新检查等任务回到 `E:\Code2026\fabricmod\meowconsole`。

## Verification

- 2026-04-27：`.\gradlew.bat :neoforge:test --console=plain` 通过。
- 2026-04-27：`.\gradlew.bat test :neoforge:test --console=plain` 通过。
- 2026-04-27：`.\gradlew.bat buildAllLoaders --console=plain` 通过。
- 2026-04-27：1.2.4 发布准备已清理 NeoForge `-test` 后缀，并给 Fabric 产物名加入 `fabric` 标志；`.\gradlew.bat clean buildAllLoaders --console=plain` 通过，产物包含 `meowantixray-fabric-1.2.4.jar` 与 `meowantixray-neoforge-1.2.4.jar`。
- 2026-04-27：修复 Fabric 爆炸 mixin 动态选择，`.\gradlew.bat runServer --console=plain` 可初始化到 `Meow Anti-Xray initialized`，随后因测试目录未同意 EULA 正常停止。
- 2026-04-27：NeoForge 端已升级到 `26.1.2.30-beta` / Minecraft `26.1.2` / Java `25`；`.\gradlew.bat :neoforge:runServer --console=plain` 成功启动到 `Done` 与 `Meow Anti-Xray startup complete`，随后手动停止测试进程。
- 2026-04-27：补测真实 NeoForge installer 流程：`neoforge-26.1.2.30-beta-installer.jar --install-server` 成功；复制 `meowantixray-neoforge-1.2.4.jar` 到安装服 `mods/` 后用 `run.bat nogui` 成功启动到 `Done` 与 `Meow Anti-Xray startup complete`。
- 2026-04-27：清理 VS Code 问题面板中的 JDT null/dead-code 诊断后，`.\gradlew.bat test --console=plain` 通过。
- 2026-04-27：新增独立 Modrinth 更新检测提示并补全中英更新日志后，`.\gradlew.bat test --console=plain` 通过。
- 2026-05-01：修复 `MeowServerCommands` 的 VS Code/JDT null type safety 告警后，`.\gradlew.bat compileJava` 与 `.\gradlew.bat :neoforge:compileJava` 通过。
