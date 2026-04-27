# Meow Anti-Xray 项目规则

## 项目定位
- 这是从 MeowConsole 拆分出的 Minecraft 服务端反矿透模组。
- 当前维护 Fabric 与 NeoForge 双 loader 构建。
- 共享核心源码在 `src/main/java/com/meowconsole/`，现阶段保留旧包名以降低拆分风险。
- Fabric 专属入口与适配在 `src/fabric/`。
- NeoForge 子工程在 `neoforge/`。

## 常用命令
- 双 loader 构建：`.\gradlew.bat buildAllLoaders`
- Fabric 构建与测试：`.\gradlew.bat build`
- NeoForge 构建与测试：`.\gradlew.bat :neoforge:build`
- 全量单测：`.\gradlew.bat test :neoforge:test`
- Fabric 服务端冒烟：`.\gradlew.bat runServer`
- NeoForge 服务端冒烟：`.\gradlew.bat :neoforge:runServer`

## 环境约束
- 项目 Java 语言级别为 25，优先使用 `C:\Program Files\Java\jdk-25.0.2`。
- 如当前 `JAVA_HOME` 指向旧 JDK，本轮命令可临时设置：`$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'; $env:Path="$env:JAVA_HOME\bin;$env:Path"`。
- 不要把本机临时代理、IDE 缓存路径或 Gradle 用户目录写成通用仓库配置。

## 改动约束
- 该项目只保留反矿透功能，不再加入 MeowConsole 的控制台、睡眠、玩家消息、飞行保护等功能。
- 多 loader 改动必须保持 Fabric 与 NeoForge 行为对齐。
- 涉及入口、平台抽象、初始化链或跨 loader 行为时，优先用 Serena 做结构化定位。

## 收尾要求
- 构建、测试或运行状态变化后，更新 `.codex/PROJECT_CONTEXT.md`。
- 新增稳定命令、目录或长期约束时，更新 `.codex/REFERENCE.md`。
- 临时排障细节写入 `.codex/AGENT_NOTES.md`，不要污染长期参考。
