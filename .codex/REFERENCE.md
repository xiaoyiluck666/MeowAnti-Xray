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
- GitHub issue 表单：`.github/ISSUE_TEMPLATE/bug-report.yml` 强制收集 loader/版本、复现步骤、完整日志、模组列表和反矿透配置；`.github/ISSUE_TEMPLATE/feature-request.yml` 收集功能需求的使用场景与预期行为；`.github/ISSUE_TEMPLATE/config.yml` 禁用空白 issue。
- Modrinth 更新检测：`src/main/java/com/meowantixray/update/ModrinthUpdateChecker.java`，项目 slug `meowanti-xray`，公开页 `https://modrinth.com/mod/meowanti-xray`。
- 拆分来源项目：MeowConsole，Modrinth 公开页 `https://modrinth.com/mod/meowconsole`。
- 当前主线目标：Minecraft `26.3`，Java `25`；当前发布版本 `1.7.0`；Fabric Loader `0.19.5` / Fabric API `0.161.0+26.3`；NeoForge `26.3.0.16-beta`。旧版支持与本轮验证见 `reports/1.7.0-multiversion-release.md`。
- 分支隔离策略（2026-10-10 用户授权统一版本）：`main` 维护 Minecraft `26.3`；`maintenance/26.2.x` 维护 `26.2`；`maintenance/26.1.x` 维护 `26.1`、`26.1.1`、`26.1.2`。三条线的模组版本统一为 `1.7.0`，不再把旧 Minecraft 线限制在 `1.2.x` / `1.6.x`。各线分别构建 Fabric/NeoForge jar，保留目标 Minecraft 的依赖、packet accessor、metadata 和协议；共享功能/修复按范围回移，不整分支合并。发布展示名包含支持线和 loader：`1.7.0+mc26.1.x.fabric` 等；`26.1.x` 上传时展开为三个实际 game_versions 标签。
- Paper parity 基准：2026-06-06 复查 PaperMC/Paper `main` HEAD `76d2ac758cb3abe75aceefa88207443768f585c6` 的 anti-xray 配置/行为要点；2026-07-09 复查 Paper `main` HEAD `a313ca15477cf331c859f329d548eeef31ded16b`，anti-xray 配置字段与默认 hidden/replacement 未变，唯一相关源码差异是 Paper 内部 `onPlayerLeftClickBlock` 改为直接接收 `Level`，本项目无需代码改动；2026-08-03 复查 Paper `main` HEAD `0a993450f129c4942c2a9ed45ba047412b4667cf`，自上一基线以来无 anti-xray 实现文件或 `WorldConfiguration` 变更；2026-08-27 复查 Paper `main` HEAD `bb09b431d24f586da72ee7c4aa2e8cff2b32a478`，再前进 33 个提交仍无 anti-xray 实现文件或 `WorldConfiguration` 变更。本项目默认 hidden blocks 在 Paper 风格默认值外额外包含 `ancient_debris`、`nether_quartz_ore`、`nether_gold_ore`，属于独立反矿透模组的实用保护增强，不视为需要回退的 parity bug。当前 parity 状态报告见 `reports/paper-parity-status.md`。
- 反矿透权限：配置项 `use-permission` 开启后优先检查 `bypass-permission`，默认 `paper.antixray.bypass`；Fabric/NeoForge 权限 API 均用反射可选桥接，缺失时回退到管理员/OP 权限等级，避免 loader 依赖硬绑定。
- 反矿透异步重写背压：`async-worker-threads` 默认 1，`async-queue-size` 默认 16；异步容量为两者之和，容量满时同步 fallback。该默认值优先防止模组层 section/packet 快照任务堆积导致 OOM；内存充足且希望更高跑图吞吐时可把 `async-queue-size` 调到 32 或 64。
- 更新日志口径：面向最终使用者，只写用户可感知的功能、兼容性、配置变化、命令变化、修复和验证结论；不要写 AI 术语、内部实现过程、开发侧排障细节、重构过程或与使用者无关的工作流信息。
- 线程边界：本项目线程只处理反矿透功能；不要把 Meow Console 的控制台、睡觉、玩家消息、飞行保护、Velocity、MCDR 等非反矿透记忆写入本项目。
- 源项目边界：`E:\Code2026\fabricmod\meowconsole` 继续维护 `Meow Console`。

## Commands

- 双 loader 构建：`.\gradlew.bat buildAllLoaders`
- Fabric 构建与测试：`.\gradlew.bat build`
- NeoForge 构建与测试：`.\gradlew.bat :neoforge:build`
- 全量单测：`.\gradlew.bat test :neoforge:test`
- 发布构建：`.\gradlew.bat clean test :neoforge:test releaseAllLoaders --console=plain`
- Modrinth 发布检查/上传：`.\tools\publish-modrinth.ps1` 默认 dry-run；确认无误后用 `.\tools\publish-modrinth.ps1 -Upload`。脚本自动生成展示版本 `<version>+mc<game-version>.<loader>`，如 `1.7.0+mc26.3.fabric` 或 `1.2.4+mc26.1.x.neoforge`；每次发布必须传单一 `-GameVersions` 支持线，避免展示名含混。脚本读取 `MODRINTH_TOKEN` 或 `MODRINTH_API_TOKEN`，会拒绝上传已有展示版本，并输出 jar metadata、SHA512/SHA1、重复版本详情和发布后的 `.codex` 记录片段。当前本机 User 级环境变量已配置 `MODRINTH_TOKEN`，后续发布可直接执行上传；不要把明文 token 写入 repo、`.codex` 或 Serena memory。
- Modrinth 项目页维护：`.\tools\update-modrinth-listing.ps1` 默认 dry-run，使用 `-Apply` 后同步 `MOD_INTRO_EN.md` / `MOD_INTRO_ZH.md`、短描述、README 文档 URL、`marketing/modrinth/` 图标与缺失图库图，并维护 26.1/26.2/26.3 最新双 loader featured 状态；脚本只添加缺失图库图，不删除已有图片。项目页视觉规范见 `marketing/modrinth/DESIGN_PHILOSOPHY.md`。
- 只读配置摘要命令：`/antixray config`，OP-only，RCON/控制台输出单行紧凑摘要，游戏内输出多行摘要；只显示当前生效配置，不写入配置文件。
- 配置预检与安全重载：`/antixray validate` 为 OP-only 只读预检，检查磁盘候选配置但不应用；`/antixray reload` 仅在语法、值域和方块 ID 验证通过后切换运行态，失败时不覆盖文件并保留 last-known-good。现有 UTF-8 BOM 配置受支持。
- Minecraft 26.x 网络假玩家压测：Fabric `node tools\mc-2612-load-runner.js --mc-version 26.3 --clients 8 --duration 91 --connect-delay 100 --rcon-teleport --rcon-password meow-local-rcon-20260519 --gamemode spectator --teleport-interval 2500 --teleport-step 192`；NeoForge 26.3 使用 `node tools\mc-2612-load-runner.js --mc-version 26.3-neoforge --clients 8 --duration 91 --connect-delay 100 --port 25566 --rcon-teleport --rcon-port 25576 --rcon-password meow-local-rcon-20260519 --gamemode spectator --teleport-interval 2500 --teleport-step 192 --teleport-y 180`。该工具不依赖 Carpet / mineflayer / minecraft-protocol，直接实现 `26.3` protocol `777`、NeoForge 26.3 packet ID 候选与 `26.1.2` protocol `775` 的登录、configuration、play、keepalive、teleport ack、chunk batch ack 子集；默认给用户名追加 session suffix，避免复用本地旧 playerdata，只有需要验证持久玩家状态时才传 `--reuse-names`；需要服务端 `online-mode=false`，RCON 仅用于可选 `/tp` 跑图，`--gamemode spectator` 会先设置 `spectatorsGenerateChunks true` 并避免高空跑图触发原版飞行检测。测试 26.1.x 时先切到 `maintenance/26.1.x`，并显式传 `--mc-version 26.1.2`。
- 标准压力样本采集：先启动对应 loader 的本地测试服，再执行 `.\tools\capture-pressure-sample.ps1 -Loader fabric -Clients 2 -DurationSeconds 25 -ExpectedQueueSize 16` 或把 loader 改为 `neoforge`。脚本默认要求干净工作区，从本地 `server.properties` 读取端口与 RCON 密码，校验磁盘和 RCON 生效队列，并把环境、配置 SHA256、runner 结果及最终 `status/config/profile` 写入忽略目录 `build/pressure-results/`；开发期工具冒烟可显式传 `-AllowDirty`，正式基线不得使用。矩阵方法和记录表见 `reports/1.5.0-pressure-matrix.md`。
- 压力服生命周期辅助命令：`.\tools\start-pressure-server.ps1 -Loader fabric -QueueSize 16` / `.\tools\start-pressure-server.ps1 -Loader neoforge -QueueSize 16` 启动单一 loader；`.\tools\stop-pressure-server.ps1 -Loader fabric` 或 `neoforge` 回收对应 server worker 与 Gradle owner。完整矩阵入口为 `.\tools\run-pressure-matrix.ps1`。
- bStats：共享客户端位于 `src/main/java/com/meowantixray/metrics/BStatsMetrics.java`，Fabric/NeoForge 在服务器启动时注册匿名 30 分钟心跳；配置为 `config/meowantixray-bstats.properties`，`enabled=false` 可退出，`service-id=0` 会在 bStats 服务登记完成前保持不上报。当前 bStats 官方平台列表没有 Fabric/NeoForge 专属 Java 客户端，接入使用 `other` API 平台，登记后需将对应服务 ID 写入配置。

## 1.7.0 cross-version compatibility

- Fabric builds require Loader `>=0.19.5` on every support line. Its bundled MixinExtras `0.5.5` handles the array-valued `@Redirect.at` emitted by the current Fabric compile classpath; older Loader 0.19.4 / MixinExtras 0.5.4 can start the server but fail when `PlayerChunkSender` is first loaded during login. A Gradle/dev-server-only check does not cover this failure; test the actual packaged jar with a real server launcher.
- 26.2 retains Fabric API `0.158.0+26.2` / NeoForge `26.2.0.69`; 26.1.x retains Fabric API `0.145.4+26.1.2` / NeoForge build target `26.1.2.30-beta`. The 26.1.x NeoForge minimum is `26.1.0.19-beta` so matching 26.1 and 26.1.1 loader builds are not rejected by metadata.
- 26.1 / 26.1.1 / 26.1.2 all use protocol `775`; 26.2 uses `776`; 26.3 uses `777`. Old support lines use `getChunkData()`, while 26.3 uses `chunkData()`.
- Local isolated source worktrees are `.worktrees/mc26.1` and `.worktrees/mc26.2`; standalone validation worlds/logs are `.worktrees/validation-170`. These paths are locally ignored through `.git/info/exclude`; do not place worktrees beneath `build/`, where `clean` removes them.

- Paper release gate on 2026-10-10: compared `46d4834e8e2ec4af9dcfa028076fb16dfb8a3911` with `bb09b431d24f586da72ee7c4aa2e8cff2b32a478`; only native packet-construction changes affect anti-xray, with no algorithm/default changes requiring a mod backport. See `reports/paper-parity-status.md`.

- 26.1.x NeoForge entrypoint resolves either `event.level.block.BreakBlockEvent` (26.1.2) or `event.level.BlockEvent$BreakEvent` (26.1 / 26.1.1), registers the concrete event class, and caches its `getPlayer` method. This prevents a static reference to the newer event from rejecting older advertised versions.
