# 🛡️ Meow Anti-Xray

💎 **在矿透客户端看见之前隐藏未暴露矿物。**

🚀 **只安装在 Fabric 或 NeoForge 服务端**，玩家无需客户端模组。

🛡️ **隐藏矿物位置，不误判处罚玩家**：不做行为评分，不指控玩家作弊。

## 🚀 三步安装

1. 📥 [下载最新版本](https://modrinth.com/mod/meowanti-xray/versions)，选择 Fabric 或 NeoForge。
2. 📁 把 jar 放入服务端 `mods` 目录，然后启动服务器。
3. ✅ 执行 `/antixray validate`，再用 `/antixray status` 确认保护已启用。

⚙️ 配置文件位于 `config/meowantixray.yml`。默认即提供 Paper 风格的 engine mode 2 保护；也可按维度调整隐藏方块、替换方块、高度范围、权限和异步容量。

## 🛡️ 事前防护，而不是行为画像

- 📦 **发送之前：**区块数据在到达玩家前就会被重写。
- 👁️ **只改客户端视图：**未暴露矿物显示为普通方块，挖掘或地形变化时再正常显露；服务端世界数据不变。
- ⚖️ **不做玩家画像：**不根据挖矿行为评分，也不给玩家贴作弊标签。

## ✨ 主要能力

- 🚀 **纯服务端：**原版和模组客户端都能正常加入。
- 🔌 **双 Loader：**Fabric 与 NeoForge 专用服务器共用同一套反矿透核心。
- 🌍 **分维度配置：**主世界、下界、末地和自定义维度可分别调整。
- ✅ **安全重载：**`/antixray validate` 先预检，再用 `/antixray reload` 原子应用；无效修改不会覆盖最后一次有效保护。
- ⛏️ **准确显露：**挖掘、开始破坏、爆炸和邻近变化后会刷新真实方块。
- ⚡ **有界异步：**通过 `/antixray profile` 查看区块重写压力与耗时。
- 🔐 **管理员控制：**支持可选权限绕过和异步 Modrinth 更新检查。

## 🌐 版本兼容

| Minecraft | 维护线 | Fabric | NeoForge |
| --- | --- | --- | --- |
| `26.3` | `1.7.0` | ✅ | ✅ |
| `26.2` | `1.7.0` | ✅ | ✅ |
| `26.1`-`26.1.2` | `1.7.0` | ✅ | ✅ |

所有 `1.7.0` 构建均要求 Java `25`。请同时按 Minecraft 版本与 loader 选择文件。

- 🧵 **Fabric：**Loader `>=0.19.5`；Fabric API 必须匹配 Minecraft（`26.3`：`>=0.161.0+26.3`；`26.2`：`>=0.158.0+26.2`；`26.1.x`：`>=0.144.0+26.1`）。
- 🔥 **NeoForge：**选择匹配 Minecraft 的版本（`26.3`：`>=26.3.0.16-beta`；`26.2`：`>=26.2.0.69`；`26.1.x`：`>=26.1.0.19-beta`）。

## 📈 性能验证

🧪 **8 客户端、90 秒重复样本：**Fabric 平均 `427.17 chunks/s`，NeoForge 平均 `390.07 chunks/s`，两者客户端错误均为 `0`。[查看完整压力矩阵](https://github.com/xiaoyiluck666/MeowAnti-Xray/blob/main/reports/1.5.0-pressure-matrix.md)。

## 🧰 常用命令

```text
/antixray status
/antixray config
/antixray validate
/antixray reload
/antixray profile
/antixray debug <world> <x> <y> <z>
/antixray inspect <world> <x> <y> <z>
```

## 🔗 支持与链接

- 📖 [使用文档](https://github.com/xiaoyiluck666/MeowAnti-Xray#readme)
- 💻 [源代码](https://github.com/xiaoyiluck666/MeowAnti-Xray)
- 🐛 [提交问题或功能建议](https://github.com/xiaoyiluck666/MeowAnti-Xray/issues/new/choose)
- 📥 [Modrinth 下载](https://modrinth.com/mod/meowanti-xray)

❤️ 如果本项目减少了你的服务器管理工作，可以通过 [PayPal](https://www.paypal.com/paypalme/neworldcn) 或[爱发电](https://afdian.com/a/xiaoyiluck)支持后续维护。
