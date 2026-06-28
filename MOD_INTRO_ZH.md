# 🛡️ Meow Anti-Xray

> 为 Fabric 与 NeoForge 生存服准备的服务端反矿透模组。<br>
> ⛏️ Paper 风格矿物伪装，🚀 客户端零安装，🔌 双 Loader 共用同一套核心逻辑。

`Meow Anti-Xray` 专注解决一个问题：让 Mod 服务端也能拥有接近 Paper Anti-Xray 的矿物隐藏体验。玩家不需要安装客户端模组，服务端会在区块发送给客户端之前重写区块包，把未暴露的矿物伪装成普通方块，从源头降低矿透收益。

本项目从 `MeowConsole` 拆分而来，现在只保留反矿透相关能力。没有控制台工具、睡眠功能、玩家消息等额外模块，目标是更轻、更纯粹，也更容易排查性能。

## ✨ 为什么选择 Meow Anti-Xray

- 🚀 **服务端安装即可生效**：玩家无需安装任何客户端内容，适合公开服、好友服和整合包服务端。
- 🧭 **Paper 风格体验**：默认按接近 Paper engine mode 2 的方式隐藏矿物，并持续对齐 Paper 风格的默认隐藏方块、替换方块、显露更新和邻近刷新行为。
- 🔌 **专为 Mod 服务端维护**：Fabric 与 NeoForge 共用核心逻辑，尽量保持配置、行为和性能策略一致。
- 🧱 **更安全的假矿处理**：箱子、末影箱等方块实体不会被用作假矿候选，减少客户端显示异常和误导性画面。
- ⚡ **高压跑图更稳**：异步区块包重写配合队列背压，避免大量区块快照无限堆积导致内存暴涨。
- 📊 **服主更容易看懂当前状态**：`/antixray status` 会显示运行状态、async 容量、配置路径和每个维度的 hidden/replacement 计数；`/antixray reload` 会返回配置变化 diff；`/antixray inspect` 可以直接解释某个方块为什么会或不会被伪装，并稳定显示标准维度 ID。控制台和 RCON 里也更容易读。

## 🧩 核心功能

- 💎 隐藏钻石、远古残骸、红石、青金石、金矿等高价值方块。
- 🪨 使用可配置的替换方块填充客户端视野，降低矿透可用信息。
- 🔍 玩家挖掘、开始破坏方块、爆炸改变地形时，会刷新附近真实方块。
- 🌍 支持主世界、下界、末地和自定义维度分别配置。
- ⚙️ 支持隐藏高度范围、隐藏方块列表、替换方块列表和维度开关。
- 🔐 支持权限绕过：可配置 `paper.antixray.bypass` 或自定义权限节点。
- 📣 启动时异步检查 Modrinth 新版本，并在 `/antixray status` 中显示当前状态。

## 🌐 版本兼容

当前 `1.3.0` 版本面向 Minecraft `26.2` 与 Java `25`：

- Fabric：Fabric Loader `>=0.19.3`，Fabric API `>=0.153.0+26.2`，Minecraft `>=26.2 <26.3`。
- NeoForge：NeoForge `>=26.2.0.7-beta`，Minecraft `[26.2,26.3)`。

## ⚙️ 性能设计

反矿透最重的工作发生在玩家加载新区块时。Meow Anti-Xray 会尽量把区块包重写放到后台线程，并通过 `async-queue-size` 限制待处理任务数量：

```yml
anti-xray:
  async-chunk-rewrite: true
  async-worker-threads: 1
  async-queue-size: 16
```

默认值优先保证内存安全。服务器内存充足、玩家经常高速跑图或传送时，可以尝试把 `async-queue-size` 调到 `32` 或 `64`，再通过 `/antixray profile` 观察是否出现明显同步 fallback。

需要说明的是：Paper 可以在原生区块包序列化阶段直接接入；Fabric / NeoForge 模组层需要保留必要快照才能异步处理。因此本项目追求的是“Paper 风格效果 + 适合 Mod Loader 的高性能实现”，不是机械照搬 Paper 源码。

## 📈 压测验证

Meow Anti-Xray 不只停留在功能实现，也做过真实跑图和高压区块加载测试。下面数据来自本地 Fabric / NeoForge dev server、spark profiler 与项目内网络假玩家压测工具，重点验证的是“区块包重写在真实加载压力下是否稳定”。

| 场景 | 结果 |
| --- | --- |
| 🧪 Fabric 26.2 / 1.3.0，8 个网络假玩家 | `92s` 内处理 `32,893` 个 chunks，约 `357.49 chunks/s`，客户端错误 `0` |
| 🔥 NeoForge 26.2 / 1.3.0，8 个网络假玩家 | `92s` 内处理 `35,593` 个 chunks，约 `386.86 chunks/s`，客户端错误 `0` |
| 🧪 Fabric 真实单人跑图 | TPS 稳定 `20.00`，MSPT 中位数 `3.31ms`，`meowantixray` 在 server-thread mods 视图占比约 `3.55%` |
| 🔥 NeoForge 8 个网络假玩家高压跑图 | `91s` 内处理 `36,233` 个 chunks，约 `398.06 chunks/s`，客户端错误 `0` |
| 🚀 更高吞吐配置测试 | `async-queue-size=64` 时 `91s` 内处理 `38,037` 个 chunks，约 `417.92 chunks/s`，客户端错误 `0` |

这些测试说明：默认配置优先保证内存稳定，高压跑图时不会让异步重写任务无限堆积；如果服务器内存更充足，也可以通过调大 `async-queue-size` 换取更高区块吞吐。完整性能报告可通过 spark 链接查看：

- 🧪 Fabric 真实跑图：<https://spark.lucko.me/btQdhJh1gH>
- 🔥 NeoForge 8 假玩家，默认保守队列：<https://spark.lucko.me/yFi0jlgOf4>
- 🚀 NeoForge 8 假玩家，更高吞吐队列：<https://spark.lucko.me/FyVDZTMgHb>

## 🌍 适合哪些服务器

- 🧱 Fabric / NeoForge 生存服
- 🤝 公益服、半公开服、好友服
- 🛡️ 希望使用 Mod 生态，但仍然需要强反矿透的服务器
- 🗺️ 使用预生成地图、高视距、频繁传送或多人跑图的服务器
- 🐾 想从 MeowConsole 内置反矿透迁移到独立反矿透模组的服务器

预生成或预加载地图不会绕过本模组。它在区块发送给玩家客户端时生效，而不是在区块生成时修改世界数据。网页地图、离线渲染器等直接读取服务端真实区块数据的工具，需要使用它们自己的隐藏或渲染配置。

## 🚀 快速开始

1. 从 Modrinth 下载与你的 Loader 匹配的 jar：Fabric 下载 Fabric 文件，NeoForge 下载 NeoForge 文件。
2. 把 jar 放入服务端 `mods` 目录。
3. 启动服务端，生成配置文件：

```text
config/meowantixray.yml
```

4. 根据服务器类型调整隐藏方块、替换方块、高度范围和异步参数。
5. 使用 `/antixray reload` 重载配置，使用 `/antixray status` 与 `/antixray profile` 检查运行状态。

常用配置示例：

```yml
anti-xray:
  engine-mode: 2
  max-block-height: 64
  hidden-blocks: ["minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"]
  replacement-blocks:
    - minecraft:stone
    - minecraft:deepslate
  dimension-settings:
    nether:
      hidden-blocks:
        - minecraft:ancient_debris
      replacement-blocks:
        - minecraft:netherrack
```

旧配置加载时会自动补齐新增但缺失的键，不会覆盖你已经写好的值。配置支持带引号的值、带引号的列表项、内联列表，以及 `nether` / `end` 这类维度别名；像 `hidden-blocks: []` 这样的显式空列表也会按你的配置原样保留。

## 🧰 常用命令

```text
/antixray status
/antixray reload
/antixray profile
/antixray debug <world> <x> <y> <z>
/antixray inspect <world> <x> <y> <z>
```

## 🔗 链接

- GitHub：<https://github.com/xiaoyiluck666/MeowAnti-Xray>
- 问题反馈：<https://github.com/xiaoyiluck666/MeowAnti-Xray/issues>
- Modrinth：<https://modrinth.com/mod/meowanti-xray>
- MeowConsole 原项目：<https://modrinth.com/mod/meowconsole>
