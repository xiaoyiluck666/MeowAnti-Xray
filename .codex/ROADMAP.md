# Meow Anti-Xray Roadmap

Last updated: 2026-06-08

## Current State

- Latest published version: `1.2.0`.
- Current local release candidate: none.
- Modrinth project: `meowanti-xray` / project id `8pl8obwY`.
- Latest Modrinth versions:
  - Fabric: `1.2.0+fabric`, version id `ZYvAlFb3`.
  - NeoForge: `1.2.0+neoforge`, version id `8nPHUeNS`.
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
- `1.2.0` candidate started: config parser now accepts quoted YAML scalar values, quoted inline list entries, quoted list items, quoted dimension aliases, and `#` inside quoted strings. README and Modrinth intro docs now include a compact per-dimension config example and upgrade compatibility note.
- `1.2.0` release prep started: `mod_version=1.2.0`, `CHANGELOG.md`, `changelogs/1.2.0.zh-CN.md`, and `changelogs/1.2.0.en-US.md` are prepared.
- `1.2.0` published to Modrinth as separate listed versions: Fabric `ZYvAlFb3` and NeoForge `8nPHUeNS`.

## Post-1.1.1 Developer Maintenance

These items are developer-side maintenance only. Do not bump `mod_version` or add user-facing changelogs unless runtime code, config behavior, compatibility, or packaged artifacts change.

1. Improve release automation safety.
   - Release helper added at `tools/publish-modrinth.ps1`; it prints jar metadata, SHA512/SHA1 checksums, duplicate Modrinth version details, and a post-upload `.codex` release record snippet.
   - Do not store Modrinth tokens in repo or `.codex`.
   - Continue publishing separate `x.y.z+fabric` and `x.y.z+neoforge` Modrinth versions.

2. Update checker UX polish.
   - Unit coverage confirms `1.1.1+fabric` / `1.1.1+neoforge` compare equal to local `1.1.1`.
   - Keep this as test/verification work unless the in-game message behavior changes.

3. Fabric Polymer compatibility follow-up.
   - Issue #1 was fixed and published in `1.1.1`.
   - Issue #1 is closed; keep a watch item for future Polymer / Fabric API packet context behavior changes.

4. Roadmap hygiene.
   - Keep completed release items out of patch candidates so internal maintenance is not mistaken for a user-facing version plan.

## 1.2.0 Candidates

These are broader and should not be mixed into a patch release unless there is a strong reason.

1. Config compatibility and upgrade polish.
   - Done: quoted YAML scalar/list/dimension parsing compatibility.
   - Done: generated config header and public docs explain missing-key supplementation and supported quoted/inline YAML forms.
   - Done: Fabric and NeoForge real dev-server `/antixray status` + `/antixray reload` smoke passed.

2. Platform abstraction cleanup.
   - Review Fabric and NeoForge entrypoints, permission bridges, and command registration.
   - Reduce duplicate loader-specific code only where it clearly lowers maintenance cost.

3. Public documentation refresh.
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
