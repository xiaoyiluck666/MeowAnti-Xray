# Meow Anti-Xray

`Meow Anti-Xray` 是面向 Minecraft 服务端的反矿透模组，目标是在 Fabric 与 NeoForge 上提供接近 Paper Anti-Xray 的矿物隐藏体验。

## 主要能力

- 按维度配置隐藏矿物与伪装方块。
- 支持区块包重写，默认使用更贴近 Paper 的 engine mode 2。
- 玩家挖掘、开始破坏方块、爆炸影响方块时，会触发附近真实方块显露更新。
- 支持异步区块重写，并提供 `/antixray profile` 查看运行统计。
- 提供 `/antixray status`、`/antixray reload`、`/antixray debug` 等管理命令。

## 配置文件

```text
config/meowantixray.yml
```

## 项目地址

- GitHub：<https://github.com/xiaoyiluck666/MeowAnti-Xray>
- Issues：<https://github.com/xiaoyiluck666/MeowAnti-Xray/issues>
