# Meow Anti-Xray Project Context

- Current branch: `maintenance/26.2.x`.
- Minecraft support: `26.2`; mod version: `1.7.0`; Java: `25`.
- Shared package: `com.meowantixray`; Fabric entry is under `src/fabric`, NeoForge under `neoforge/src/main/java`.
- User approved unified 1.7.0 across Minecraft 26.1.x, 26.2, and 26.3 on 2026-10-10. Minecraft-specific metadata/dependencies/accessors remain isolated.
- Fabric Loader >=0.19.5 is required to match packaged MixinExtras processing. Old packet integration uses `getChunkData()`.
- 2026-10-10: final Fabric/NeoForge tests pass, 56 each (112 total), zero failures/errors/skips; release jars built and metadata verified.
- 2026-10-10: standalone packaged-jar startup, status/config/validate/reload, invalid reload preserving the disk file and last-known-good state, UTF-8 BOM, and two-client 25-second rewrite smoke pass for every advertised target. NeoForge 26.1.x additionally passes a real block-break event check on every patch version.
- Publication: listed and verified on 2026-10-10; Fabric `vMWdETU7` / NeoForge `cxc12pbD`. Modrinth tags and SHA512/SHA1 match the tested artifacts; downloaded CDN bytes were checked. Full artifact hashes and evidence: `reports/1.7.0-multiversion-release.md` and `.json`.
- Commands/policy: `.codex/REFERENCE.md`; roadmap: `.codex/ROADMAP.md`.
- Carried follow-up: bStats remains inactive with default `service-id=0`; activation/migration hardening is a separate task.
