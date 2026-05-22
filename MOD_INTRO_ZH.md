# Meow Anti-Xray

`Meow Anti-Xray` 是一个专为 Minecraft 服务端打造的反矿透模组，支持 Fabric 与 NeoForge。它把接近 Paper Anti-Xray 的矿物隐藏体验带到 Mod 服务端：不改客户端、不要求玩家安装，只在服务端处理区块包，就能保护生存服的矿物资源。🛡️⛏️

这个项目从 `MeowConsole` 中拆分而来，现在只专注反矿透能力。控制台、睡眠、玩家消息等无关功能已经移除，目标是让服务器管理员得到一个更轻、更纯粹、更容易排查性能的反矿透模组。原项目地址：<https://modrinth.com/mod/meowconsole> 🐾

## 核心亮点

- 🪨 **按 Paper 思路移植的矿物伪装**：默认采用接近 Paper engine mode 2 的区块包重写逻辑，隐藏矿物并使用伪装方块填充客户端视野。
- 🧭 **持续对齐 Paper Anti-Xray 行为**：同步 Paper 风格的默认隐藏方块、替换方块、邻近刷新半径、挖掘显露逻辑与 `paper.antixray.bypass` 权限节点。
- 🚫 **避免假矿物破坏容器方块**：对齐 Paper 的处理思路，箱子、末影箱等方块实体不会被作为 mode 2 假矿候选，降低客户端显示异常风险。
- 🌍 **按维度细分配置**：主世界、下界、末地和自定义维度都可以分别配置隐藏方块、替换方块、高度范围与启用状态。
- ⚡ **异步区块包重写**：把重写工作从主线程移到后台线程，降低玩家跑图、传送和加载新区块时的主线程压力。
- 🧯 **异步队列背压优化**：新增 `async-queue-size`，在高压跑图时限制后台重写任务堆积，避免大量区块快照排队导致内存暴涨或 OOM。
- 📊 **可观测性能指标**：`/antixray profile` 可以查看重写次数、平均耗时、最大耗时、刷新包数量等指标，方便定位服务器性能瓶颈。
- 🔍 **真实方块及时显露**：玩家挖掘、开始破坏方块、爆炸改变方块时，会按 Paper 风格刷新附近真实方块，减少误隐藏。
- 🔐 **权限绕过支持**：开启 `use-permission` 后可使用 `bypass-permission` 配置权限节点；Fabric / NeoForge 会尽量桥接对应权限 API，缺失时回退 OP 判断。
- 🔌 **Fabric / NeoForge 双端维护**：同一套反矿透核心，两个 loader 共享逻辑，尽量保持行为、配置和性能策略一致。

## 性能优化说明

Meow Anti-Xray 的核心压力来自“玩家加载新区块时的区块包重写”。这类工作天然会消耗 CPU 和内存，尤其是在多玩家高速跑图、RCON 传送、预生成后集中加载新区块时。

最新版本参考 Paper 的实现方式重新审视了异步处理路径：Paper 能在原生区块包序列化阶段拿到更轻量的 buffer 和 palette 信息；而模组层实现为了兼容 Fabric / NeoForge，需要复制必要的区块快照。因此本项目加入了更保守的异步背压策略：

```yml
anti-xray:
  async-chunk-rewrite: true
  async-worker-threads: 1
  async-queue-size: 16
```

`async-queue-size` 可以在配置文件里调整。默认 `16` 优先防止内存堆积；如果服务器内存充足、希望提高跑图吞吐，可以尝试 `32` 或 `64`。实际测试中，8 个真实网络假玩家持续 RCON 跑图时，修复前会触发 Java heap OOM；加入背压后可稳定完成压测，且无客户端错误。

## 适合的服务器

- Fabric / NeoForge 生存服
- 公益服、好友服、半公开服
- 希望获得 Paper 风格反矿透，但又需要 Mod 服务端生态的服务器
- 使用预生成地图、预加载区块、较高视距或经常多人跑图的服务器

预生成或预加载地图不会绕过本模组。Meow Anti-Xray 在区块发送给玩家客户端时生效，而不是在服务端生成区块时生效。需要注意的是，网页地图渲染类工具如果直接读取服务端真实区块数据，则不属于玩家客户端区块包，本模组不会替它隐藏矿物。

## 配置文件

```text
config/meowantixray.yml
```

## 项目地址

- GitHub：<https://github.com/xiaoyiluck666/MeowAnti-Xray>
- Issues：<https://github.com/xiaoyiluck666/MeowAnti-Xray/issues>
- Modrinth：<https://modrinth.com/mod/meowanti-xray>
- MeowConsole 原项目：<https://modrinth.com/mod/meowconsole>
