# Meow Anti-Xray Roadmap

Last updated: 2026-06-06

## Current State

- Latest published version: `1.1.1`.
- Current local release candidate: none.
- Modrinth project: `meowanti-xray` / project id `8pl8obwY`.
- Latest Modrinth versions:
  - Fabric: `1.1.1+fabric`, version id `u02Xc7ol`.
  - NeoForge: `1.1.1+neoforge`, version id `bJBqlAOa`.
- Current Minecraft target: `26.1.2`; published compatibility range is `26.1`, `26.1.1`, `26.1.2`.
- Current Java target: `25`.
- Current package name: `com.meowantixray`.

## Recently Completed

- `1.0.1`: runtime diagnostics, `/antixray profile` async pressure metrics, sync chunk send counters, and snapshot allocation reduction.
- `1.0.2`: Paper engine mode 1 natural replacement parity and Fabric local palette crash fix.
- `1.1.0` local release candidate: package, entrypoint, mixin plugin, and command class identity migrated from MeowConsole naming to Meow Anti-Xray naming; mod id and config path unchanged.
- Post-rename local optimization: Paper parity target lookup now follows actual obfuscation targets for mode 1/2/3, replacement pass checks reuse static arrays, and `/antixray profile` reports async pressure plus sync fallback ratio.
- Fabric 1.1.0 startup + spark run passed: `91s`, 8/8 fake clients, `28766` chunks, `316.01 chunks/s`, `0` errors, profile rewrite avg `2.662ms`, sync fallback `43.0%`, spark `https://spark.lucko.me/A9EOkRRmFJ`.
- NeoForge 1.1.0 clean startup + spark run passed: `91s`, 8/8 fake clients, `29699` chunks, `326.29 chunks/s`, `0` errors, profile rewrite avg `2.893ms`, sync fallback `38.2%`, spark `https://spark.lucko.me/emnV8jJ4yG`.
- `1.1.0` published to Modrinth as separate listed versions: Fabric `RBQd8pvr` and NeoForge `k257upBH`.
- `1.1.1`: Fabric + Polymer compatibility fix for missing chunk packet context warning; published to Modrinth as Fabric `u02Xc7ol` and NeoForge `bJBqlAOa`.

## Next Patch Candidates

These are suitable for `1.1.1` or another small maintenance release if the change stays narrow.

1. Improve release automation safety.
   - Release helper added at `tools/publish-modrinth.ps1`; future polish can add checksum display or automatic post-upload `.codex` release record snippets.
   - Do not store Modrinth tokens in repo or `.codex`.
   - Continue publishing separate `x.y.z+fabric` and `x.y.z+neoforge` Modrinth versions.

2. Update checker UX polish.
   - Verify admin notification format after `1.0.2`.
   - Confirm update checker treats `1.1.0+fabric` / `1.1.0+neoforge` as equivalent to local `1.1.0`.

3. Fabric Polymer compatibility follow-up.
   - Issue #1 was fixed and published in `1.1.1`.
   - Consider closing the GitHub issue after the reporter confirms the Modrinth build works on their server.

## 1.2.0 Candidates

These are broader and should not be mixed into a patch release unless there is a strong reason.

1. Platform abstraction cleanup.
   - Review Fabric and NeoForge entrypoints, permission bridges, and command registration.
   - Reduce duplicate loader-specific code only where it clearly lowers maintenance cost.

2. Public documentation refresh.
   - Update README / Modrinth intro if new profiling data changes the story.
   - Keep Fabric and NeoForge installation guidance explicit so users do not assume one jar supports both loaders.

## Watch List

- Minecraft `26.1.x` and NeoForge beta changes.
  - Current NeoForge target is `26.1.2.30-beta`.
  - Published NeoForge Minecraft range is `[26.1,26.1.3)` in metadata.
- Paper anti-xray implementation changes.
  - Paper can rewrite packet buffers in the native serialization path; this mod must snapshot enough state at the mod layer, so exact implementation parity is not expected.
  - Last checked Paper `main`: `76d2ac758cb3abe75aceefa88207443768f585c6` on 2026-06-06.
  - Paper default hidden/replacement fields were unchanged at that HEAD.
  - This project intentionally includes extra default hidden blocks for Nether protection: `ancient_debris`, `nether_quartz_ore`, `nether_gold_ore`.
- Async queue defaults.
  - Default `async-queue-size=16` prioritizes memory safety.
  - Larger queues (`32` / `64`) can improve throughput but increase memory headroom requirements.
- Network fake-player runner protocol support.
  - `tools/mc-2612-load-runner.js` is protocol `775` / Minecraft `26.1.2` specific.
  - Revalidate packet IDs before using it for a newer Minecraft target.

## Release Checklist

1. Decide release scope.
   - Patch: bug fixes, parity tweaks, diagnostics, small docs.
   - Minor: package rename, broad loader abstraction, larger behavior changes.

2. Update local files.
   - `gradle.properties`: `mod_version`.
   - `CHANGELOG.md`: version index.
   - `changelogs/<version>.zh-CN.md`.
   - `changelogs/<version>.en-US.md`.

3. Verify locally.
   - `.\gradlew.bat clean test :neoforge:test releaseAllLoaders --console=plain`
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
   - Game versions: `26.1`, `26.1.1`, `26.1.2` unless compatibility changes.
   - Never store the Modrinth token in repo, project memory, or Serena memory.

5. Post-release record.
   - Update `.codex/PROJECT_CONTEXT.md` with build command, jar names, version ids, and compatibility.
   - Commit release files and release record.
   - Confirm `git status --short` is clean.
