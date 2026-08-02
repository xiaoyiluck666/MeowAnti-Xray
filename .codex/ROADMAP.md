# Meow Anti-Xray Roadmap

Last updated: 2026-08-02

## Current State

- Latest published mainline version: `1.5.0`.
- Latest published maintenance version: `1.2.3`.
- Current mainline target: Minecraft `26.2`, Java `25`.
- Mainline branch: `main` for Minecraft `26.2` / mod `1.5.x+`.
- Maintenance branch: `maintenance/26.1.x` for Minecraft `26.1`, `26.1.1`, `26.1.2` / mod `1.2.x`.
- Modrinth project: `meowanti-xray` / project id `8pl8obwY`.
- Latest Modrinth versions:
  - Fabric: `1.5.0+mc26.2.fabric`, version id `gnp1C7GA`.
  - NeoForge: `1.5.0+mc26.2.neoforge`, version id `Bkv4fcCA`.
- Latest 26.1.x versions:
  - Fabric: `1.2.3+fabric`, version id `xsDDkrUC`.
  - NeoForge: `1.2.3+neoforge`, version id `yCzFiKhF`.
- Current package name: `com.meowantixray`.

## Branch Policy

- Keep `main` on Minecraft `26.2` dependencies and metadata.
- Keep `maintenance/26.1.x` on Minecraft `26.1.x` dependencies and metadata.
- Do not merge `main` wholesale into `maintenance/26.1.x`.
- For shared bugfixes, Paper parity adjustments, or diagnostics fixes, cherry-pick the smallest commit and then recheck:
  - `gradle.properties`
  - `fabric.mod.json`
  - `neoforge.mods.toml`
  - `tools/publish-modrinth.ps1`
  - pressure-test command `--mc-version`
- New feature work should land on `main` first unless it is specifically a 26.1.x compatibility fix.

## Recently Completed

- `1.2.3`: latest Minecraft 26.1.x maintenance release; backported Paper parity runtime random seed and transparent override coverage.
- `1.4.0`: combined the planned 1.3.2 and 1.4.0 iterations; published read-only `/antixray config`, dry-run `/antixray validate`, two-phase config validation/activation, and last-known-good safe reload for Fabric and NeoForge.
- `1.3.1`: latest Minecraft 26.2 mainline release; shipped Paper parity runtime random seed and transparent override coverage.
- NeoForge 26.2 fake-client pressure path fixed after `1.3.1`: the runner now avoids stale local playerdata by defaulting to fresh session-suffixed usernames and enables spectator chunk generation for RCON spectator routes.
- NeoForge 26.2 full-duration pressure follow-up completed after the runner fix: 8 fake clients, 92s, 38264 chunks, 7234 batches, 0 errors, 415.8 chunks/s, `/antixray profile` rewriteTasks=38321, rewriteAvgMs=2.570, rewriteMaxMs=30.327, syncFallbackRatio=44.0%.
- Paper parity rechecked against Paper `main` `a313ca15477cf331c859f329d548eeef31ded16b`; anti-xray behavior/config defaults are unchanged, and no code change is required.
- `1.3.0`: Minecraft 26.2 compatibility release; updated Fabric Loader/API and NeoForge targets, updated pressure runner to protocol `776`, retained protocol `775` support for 26.1.2, completed dual-loader pressure tests, and published Fabric/NeoForge builds to Modrinth.
- `maintenance/26.1.x`: remote branch created from `e7b3966` to isolate 26.1.x support from the 26.2 mainline.
- Paper parity status report added at `reports/paper-parity-status.md`.
- `1.5.0` queue matrix completed: both loaders passed 8-client, 90-second routes with zero runner errors; default queue remains `16`.

## Product Direction

The product is mature enough that the priority is no longer to chase Paper source-level parity or add broad control features. The next releases should make three promises to server owners:

1. A configuration mistake cannot silently replace their file or unexpectedly weaken the active protection.
2. The same configuration behaves and is verifiable on Fabric and NeoForge.
3. Performance tuning is based on repeatable measurements, not a larger default queue or an isolated throughput number.

Do not add in-game configuration writes, a general server console, or features outside anti-xray protection. Keep Paper tracking as a release gate, not a standalone feature roadmap.

## Planned Iterations

### 1.5.0 - Dual-loader Performance Baseline (complete)

**Goal:** turn async/backpressure tuning into a measured compatibility contract.

- Baseline tooling is now defined by `tools/capture-pressure-sample.ps1`, structured runner JSON output, and `reports/1.5.0-pressure-matrix.md`; raw samples stay under ignored `build/pressure-results/`.
- The clean-worktree queue `16` smoke gate now passes on the same config SHA256: Fabric 2 clients / 25s / 7127 chunks / 0 errors, NeoForge 2 clients / 25s / 6823 chunks / 0 errors; both finish with inFlight=0 and visible rewrite activity.
- Completed the versioned pressure matrix for Fabric and NeoForge on the same Minecraft 26.2 target: 8-client, 90-second spectator routes at queue `0`, `16`, `32`, and `64`.
- Repeated queue `16` and queue `32` three times per loader. Queue `32` improved throughput but still produced saturated pressure and occasional in-flight work at the sample boundary.
- Kept the default at `16`; no scheduling or snapshot-allocation code change is justified by the matrix.
- Added repeatable lifecycle helpers and structured runtime pressure fields for future samples.

**Acceptance:** met. Both loaders completed the standard route with zero runner errors and visible rewrite activity; the report includes environment and configuration evidence; no default queue increase was made while pressure remained visible.

### Compatibility and Maintenance (continuous)

- Recheck Paper upstream before every minor or Minecraft compatibility release. Keep engine modes, natural replacement, replacement targets, neighbor reveal shapes, lava exposure, transparent overrides, randomness, and bit storage threshold tests green.
- For Minecraft/loader updates, create a short compatibility branch plan first: update mappings and metadata, restore both loader startup, then run the pressure matrix before publishing. Do not combine a game-version jump with a behavior redesign.
- Apply shared behavior fixes to `main` first. Cherry-pick only compatible, focused commits to `maintenance/26.1.x`, then recheck its metadata and run its branch-specific build/test and smoke gate.
- Break up `FakeOreService` only while changing a bounded behavior area, with characterization tests guarding the extracted behavior. A standalone cosmetic rewrite of the current large service is not planned.

## Watch List

- Minecraft `26.2` and NeoForge beta changes on `main`.
- NeoForge fake-player pressure path: fixed stale-name/spectator trap on 2026-07-02; full-duration profiling passed on 2026-07-09.
- Minecraft `26.1.x` support regressions on `maintenance/26.1.x`.
- Paper anti-xray implementation changes:
  - Last checked Paper `main`: `a313ca15477cf331c859f329d548eeef31ded16b` on 2026-07-09.
  - Exact implementation parity is not expected because Paper rewrites packet buffers in the native serialization path while this mod operates from the Fabric/NeoForge loader layer.
  - This project intentionally includes extra default hidden blocks for Nether protection: `ancient_debris`, `nether_quartz_ore`, `nether_gold_ore`.
- Network fake-player runner protocol support:
  - `26.2`: protocol `776`.
  - `26.2-neoforge`: protocol `776` with NeoForge 26.2 packet ID candidates observed during pressure testing.
  - `26.1.2`: protocol `775`.
  - Revalidate packet IDs before using it for newer Minecraft targets.

## Release Checklist

1. Confirm target branch.
   - `main`: release `1.5.x+` for Minecraft `26.2`.
   - `maintenance/26.1.x`: release `1.2.x` patches for Minecraft `26.1.x`.

2. Update local files.
   - `gradle.properties`: `mod_version`.
   - `CHANGELOG.md`: version index.
   - `changelogs/<version>.zh-CN.md`.
   - `changelogs/<version>.en-US.md`.
   - README and Modrinth intros if compatibility, commands, or user-visible behavior changed.

3. Verify locally.
   - `.\gradlew.bat clean test :neoforge:test releaseAllLoaders --console=plain`
   - `node --check tools\mc-2612-load-runner.js`
   - Check `build/release/` contains both loader jars.
   - Inspect jar metadata versions:
     - Fabric: `fabric.mod.json`.
     - NeoForge: `META-INF/neoforge.mods.toml`.

4. Upload to Modrinth.
   - Publish as separate versions:
     - `<version>+mc<game-version>.fabric`
     - `<version>+mc<game-version>.neoforge`
   - Pass exactly one Modrinth game-version support line to the release script, such as `-GameVersions 26.2` or `-GameVersions 26.1.x`; do not use version naming to imply cross-line compatibility.
   - Fabric version requires Fabric API dependency (`P7dR8mSH`).
   - NeoForge version currently has no extra Modrinth dependency.
   - Never store the Modrinth token in repo, project memory, or Serena memory.
