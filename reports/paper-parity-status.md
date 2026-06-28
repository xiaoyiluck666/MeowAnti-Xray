# Paper Anti-Xray Parity Status

Last checked: 2026-06-28

## Scope

Meow Anti-Xray tracks Paper-style anti-xray behavior for Fabric and NeoForge dedicated servers. Exact source-level parity is not the goal: Paper patches the native server serialization path, while this mod rewrites chunk packets from the loader layer using section and packet snapshots.

## Upstream Baseline

- Paper repository: `https://github.com/PaperMC/Paper`
- Paper `main`: `76d2ac758cb3abe75aceefa88207443768f585c6`
- Paper `ver/1.21.11`: `c5eb0790f199da6c38d0a650e1e5cd5415b28185`
- Key Paper files to recheck:
  - `paper-server/src/main/java/io/papermc/paper/antixray/ChunkPacketBlockControllerAntiXray.java`
  - `paper-server/src/main/java/io/papermc/paper/antixray/ChunkPacketInfoAntiXray.java`
  - `paper-server/src/main/java/io/papermc/paper/antixray/ChunkPacketInfo.java`
  - `WorldConfiguration.Anticheat.AntiXray`

## Current Progress

Practical behavior/config parity is high, roughly 85%. Internal implementation parity is intentionally lower because the loader mod layer cannot use Paper's native packet serialization hooks.

| Area | Status | Notes |
| --- | --- | --- |
| Config fields | Aligned | `enabled`, `engine-mode`, `max-block-height`, `update-radius`, `lava-obscures`, `use-permission`, `hidden-blocks`, `replacement-blocks` are represented in the mod config. |
| Permission bypass | Aligned | Default node is `paper.antixray.bypass`; Fabric/NeoForge permission APIs are optional bridges with OP fallback. |
| Defaults | Mostly aligned | Paper-style defaults are used, with intentional extra Nether protection: `ancient_debris`, `nether_quartz_ore`, `nether_gold_ore`. |
| Engine mode 1 | Aligned | Natural replacement follows dimension and height rules: stone/deepslate, netherrack, end_stone. |
| Engine mode 2/3 targets | Aligned | Replacement targets are included according to actual obfuscation rules. |
| EntityBlock decoys | Aligned | Block entities are excluded from fake block candidates. |
| Neighbor reveal/update radius | Aligned | Paper-style neighbor shapes are covered by tests. |
| Lava obscures | Aligned | `lava-obscures` participates in exposure checks. |
| Transparent override solid checks | Aligned | Paper-style transparent overrides for spawner, barrier, shulker boxes, slime block, and mangrove roots are covered by regression tests. |
| Packet bit storage thresholds | Aligned | Target bits are tested against Paper-style thresholds. |
| Async processing | Mod-layer equivalent | Uses bounded queue/backpressure to avoid snapshot task OOM. |
| Fake block randomness | Mostly aligned | Runtime rewrite paths now use a non-zero `ThreadLocalRandom` seed followed by xorshift and unsigned bounded scaling, matching Paper's seed model. The mod still creates random state at the loader snapshot section boundary rather than Paper's native chunk serialization lifecycle. |
| Native packet serialization lifecycle | Not applicable | Fabric/NeoForge mod code cannot directly port Paper's native serialization buffer rewrite path. |

## Branch Policy

- `main`: Minecraft `26.2`, mod `1.3.x+`.
- `maintenance/26.1.x`: Minecraft `26.1`, `26.1.1`, `26.1.2`, mod `1.2.x`.
- Do not merge `main` wholesale into `maintenance/26.1.x`.
- Shared bugfixes and Paper parity fixes should be cherry-picked, then version metadata and loader dependencies must be reviewed on the target branch before build or release.

## Next Alignment Work

1. Recheck Paper upstream before each minor release or compatibility release.
2. Keep parity tests for engine modes, replacement targets, neighbor reveal shapes, lava exposure, transparent overrides, and bit storage thresholds green on both supported branches.
3. If Paper changes anti-xray defaults, target selection, or random selection logic, update `main` first, then cherry-pick only the behavior fix to `maintenance/26.1.x` if it is compatible with 26.1.x mappings and loader APIs.
4. Keep implementation differences documented when they are caused by loader-layer constraints rather than missing work.
