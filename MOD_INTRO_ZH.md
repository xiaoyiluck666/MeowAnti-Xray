# Meow Anti-Xray

`Meow Anti-Xray` 是一个专为 Minecraft 服务端打造的反矿透模组，支持 Fabric 与 NeoForge。它把接近 Paper Anti-Xray 的矿物隐藏体验带到 Mod 服务端：不改客户端、不要求玩家安装、开服端就能保护矿物资源。🛡️⛏️

这个项目从 `MeowConsole` 中拆分而来，保留并专注强化其中的反矿透能力，移除了控制台、睡眠、玩家消息等无关功能，让服务器管理更轻、更纯粹。原项目地址：<https://modrinth.com/mod/meowconsole> 🐾

## 为什么选择它

- 🪨 **接近 Paper 的矿物伪装体验**：默认使用 engine mode 2 思路，对区块包进行矿物隐藏与伪装。
- 🌍 **按维度细分配置**：主世界、下界、自定义维度都可以配置隐藏矿物和替换方块。
- ⚡ **服务端即插即用**：玩家无需安装客户端 Mod，适合公开服、生存服、好友服快速部署。
- 🔍 **真实方块及时显露**：玩家挖掘、开始破坏方块、爆炸改变方块时，会触发附近矿物刷新显示。
- 🚀 **可选异步重写**：降低区块包处理对主线程的影响，适合更高并发的服务器环境。
- 📊 **内置运行统计**：通过 `/antixray profile` 查看重写次数、耗时等关键指标，方便排查性能。
- 🔔 **Modrinth 更新提示**：服务端启动后异步检测兼容当前 loader 和 Minecraft 版本的新版本，`/antixray status` 也会显示检测结果。
- 🧰 **简洁管理命令**：提供 `/antixray status`、`/antixray reload`、`/antixray debug` 等实用命令。
- 🔌 **Fabric / NeoForge 双端维护**：同一套反矿透核心，面向两个 loader 保持行为对齐。

## 配置文件

```text
config/meowantixray.yml
```

## 项目地址

- GitHub：<https://github.com/xiaoyiluck666/MeowAnti-Xray>
- Issues：<https://github.com/xiaoyiluck666/MeowAnti-Xray/issues>
- Modrinth：<https://modrinth.com/mod/meowanti-xray>
- MeowConsole 原项目：<https://modrinth.com/mod/meowconsole>
