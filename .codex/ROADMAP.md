# Meow Anti-Xray Roadmap

Last updated: 2026-06-02

## Current State

- Latest published version: `1.0.2`.
- Modrinth project: `meowanti-xray` / project id `8pl8obwY`.
- Latest Modrinth versions:
  - Fabric: `1.0.2+fabric`, version id `z8PGUT9X`.
  - NeoForge: `1.0.2+neoforge`, version id `vmQINJlp`.
- Current Minecraft target: `26.1.2`; published compatibility range is `26.1`, `26.1.1`, `26.1.2`.
- Current Java target: `25`.
- Current package name is still `com.meowconsole`; this is intentional for now.

## Recently Completed

- `1.0.1`: runtime diagnostics, `/antixray profile` async pressure metrics, sync chunk send counters, and snapshot allocation reduction.
- `1.0.2`: Paper engine mode 1 natural replacement parity and Fabric local palette crash fix.
- Fabric 8 fake network player run passed after the 1.0.2 fix: `91s`, `15833` chunks, `173.94 chunks/s`, `0` client errors.
- NeoForge 8 fake network player stress data exists from earlier runs; conservative default queue remains `async-queue-size=16`.

## Next Patch Candidates

These are suitable for `1.0.3` or another small maintenance release if the change stays narrow.

1. Improve release automation safety.
   - Add a small local release helper script that builds, checks jar metadata, lists target Modrinth version numbers, and refuses to upload if the version already exists.
   - Do not store Modrinth tokens in repo or `.codex`.
   - Continue publishing separate `x.y.z+fabric` and `x.y.z+neoforge` Modrinth versions.

2. Fabric profiling follow-up.
   - Optional spark report for Fabric 8 fake network player run.
   - Current Fabric 8-player runner result has no spark link, only runner metrics.
   - Capture profile only if useful for public release notes or performance comparison.

3. Profile output polish.
   - Consider adding sync fallback ratio or clearer pressure status to `/antixray profile`.
   - Keep output compact enough for in-game chat.

4. Update checker UX polish.
   - Verify admin notification format after `1.0.2`.
   - Confirm update checker treats `1.0.2+fabric` / `1.0.2+neoforge` as equivalent to local `1.0.2`.

## 1.1.0 Candidates

These are broader and should not be mixed into a patch release unless there is a strong reason.

1. Package rename from `com.meowconsole` to `com.meowantixray`.
   - User impact should be low if mod id, config path, metadata, and behavior stay unchanged.
   - Risk is developer-side: entrypoints, mixin JSON, tests, reflection helpers, launch configs, and crash-log continuity.
   - Treat as an internal identity cleanup release, likely `1.1.0`.

2. Platform abstraction cleanup.
   - Review Fabric and NeoForge entrypoints, permission bridges, and command registration.
   - Reduce duplicate loader-specific code only where it clearly lowers maintenance cost.

3. Public documentation refresh.
   - Update README / Modrinth intro if package rename or new profiling data changes the story.
   - Keep Fabric and NeoForge installation guidance explicit so users do not assume one jar supports both loaders.

## Watch List

- Minecraft `26.1.x` and NeoForge beta changes.
  - Current NeoForge target is `26.1.2.30-beta`.
  - Published NeoForge Minecraft range is `[26.1,26.1.3)` in metadata.
- Paper anti-xray implementation changes.
  - Paper can rewrite packet buffers in the native serialization path; this mod must snapshot enough state at the mod layer, so exact implementation parity is not expected.
  - Last checked Paper `main`: `10a73fe40f39d51e6a35e55154229bc9508a16d1` on 2026-06-02.
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
