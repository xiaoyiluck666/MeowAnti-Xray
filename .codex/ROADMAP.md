# Meow Anti-Xray Roadmap

Last updated: 2026-06-28

## Current State

- Latest published version: `1.3.0`.
- Current mainline target: Minecraft `26.2`, Java `25`.
- Mainline branch: `main` for Minecraft `26.2` / mod `1.3.x+`.
- Maintenance branch: `maintenance/26.1.x` for Minecraft `26.1`, `26.1.1`, `26.1.2` / mod `1.2.x`.
- Modrinth project: `meowanti-xray` / project id `8pl8obwY`.
- Latest Modrinth versions:
  - Fabric: `1.3.0+fabric`, version id `rj3Ex0cy`.
  - NeoForge: `1.3.0+neoforge`, version id `OquEDb5X`.
- Latest 26.1.x versions:
  - Fabric: `1.2.2+fabric`, version id `Nvxt3UCV`.
  - NeoForge: `1.2.2+neoforge`, version id `lzz6pViR`.
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

- `1.2.2`: latest Minecraft 26.1.x maintenance release; fixed command output polish for RCON/console and normalized inspect dimension IDs.
- `1.3.0`: Minecraft 26.2 compatibility release; updated Fabric Loader/API and NeoForge targets, updated pressure runner to protocol `776`, retained protocol `775` support for 26.1.2, completed dual-loader pressure tests, and published Fabric/NeoForge builds to Modrinth.
- `maintenance/26.1.x`: remote branch created from `e7b3966` to isolate 26.1.x support from the 26.2 mainline.
- Paper parity status report added at `reports/paper-parity-status.md`.

## Next Iteration Candidates

1. Paper parity maintenance.
   - Recheck Paper upstream before each minor or compatibility release.
   - Keep tests covering engine modes, natural replacement, replacement targets, neighbor reveal shapes, lava exposure, and bit storage thresholds.
   - If Paper changes defaults or target selection, update `main` first and cherry-pick compatible behavior fixes to `maintenance/26.1.x`.

2. 26.1.x maintenance readiness.
   - On `maintenance/26.1.x`, verify the latest 1.2.x build still compiles and runs after any cherry-picked fix.
   - Keep Modrinth game versions at `26.1`, `26.1.1`, `26.1.2`.
   - Do not pull in 26.2 loader metadata or dependency versions.

3. Config UX polish.
   - Consider an OP-only `/antixray config` read-only summary command.
   - Keep edits/reload behavior conservative; avoid in-game config writes unless there is a clear user need.

4. Profiling and pressure tuning.
   - Keep `async-queue-size=16` as the documented default for memory safety.
   - Keep `32` / `64` as optional tuning for memory-rich servers watching `/antixray profile`.
   - Re-run dual-loader pressure tests after packet rewrite or async queue changes.

## Watch List

- Minecraft `26.2` and NeoForge beta changes on `main`.
- Minecraft `26.1.x` support regressions on `maintenance/26.1.x`.
- Paper anti-xray implementation changes:
  - Last checked Paper `main`: `76d2ac758cb3abe75aceefa88207443768f585c6` on 2026-06-28.
  - Exact implementation parity is not expected because Paper rewrites packet buffers in the native serialization path while this mod operates from the Fabric/NeoForge loader layer.
  - This project intentionally includes extra default hidden blocks for Nether protection: `ancient_debris`, `nether_quartz_ore`, `nether_gold_ore`.
- Network fake-player runner protocol support:
  - `26.2`: protocol `776`.
  - `26.1.2`: protocol `775`.
  - Revalidate packet IDs before using it for newer Minecraft targets.

## Release Checklist

1. Confirm target branch.
   - `main`: release `1.3.x+` for Minecraft `26.2`.
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
     - `<version>+fabric`
     - `<version>+neoforge`
   - Fabric version requires Fabric API dependency (`P7dR8mSH`).
   - NeoForge version currently has no extra Modrinth dependency.
   - Never store the Modrinth token in repo, project memory, or Serena memory.
