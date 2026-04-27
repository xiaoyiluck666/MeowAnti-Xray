# Meow Anti-Xray Reference

- Minecraft 服务端反矿透模组，维护 Fabric 与 NeoForge 双 loader。
- 共享源码：`src/main/java/com/meowconsole/`。
- Fabric 专属入口与平台适配：`src/fabric/java/com/meowconsole/fabric/`。
- NeoForge 子工程：`neoforge/`。
- 反矿透核心：`src/main/java/com/meowconsole/antixray/`。
- 跨版本兼容层：`src/main/java/com/meowconsole/compat/MinecraftCompat.java`。
- Fabric mixin 清单：`src/main/resources/meowantixray.mixins.json`。
- NeoForge mixin 清单：`neoforge/src/main/resources/meowantixray.neoforge.mixins.json`。

## Commands

- 双 loader 构建：`.\gradlew.bat buildAllLoaders`
- Fabric 构建与测试：`.\gradlew.bat build`
- NeoForge 构建与测试：`.\gradlew.bat :neoforge:build`
- 全量单测：`.\gradlew.bat test :neoforge:test`
