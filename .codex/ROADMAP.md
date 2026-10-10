# Meow Anti-Xray Roadmap

Last updated: 2026-10-10

## Supported release lines

The user approved a unified mod version, `1.7.0`, across all supported Minecraft lines. Each Minecraft/loader combination remains a separate artifact; the version number does not imply that one jar runs across lines.

| Branch | Minecraft | Mod version | Protocol |
| --- | --- | --- | --- |
| `main` | `26.3` | `1.7.0` | `777` |
| `maintenance/26.2.x` | `26.2` | `1.7.0` | `776` |
| `maintenance/26.1.x` | `26.1`, `26.1.1`, `26.1.2` | `1.7.0` | `775` |

The 26.3 release was published on 2026-09-24. The 26.2 and 26.1.x builds were published on 2026-10-10. All six Minecraft/loader entries are listed and their file hashes and compatibility tags were verified; see `reports/1.7.0-multiversion-release.md`.

## Branch policy

- Keep Minecraft dependencies, loader metadata, packet accessors, and runner profiles specific to each branch.
- Backport bounded shared features and fixes; do not merge a newer Minecraft branch wholesale into an older one.
- Old `1.2.x` / `1.6.x` version-number restrictions are superseded by the user's 2026-10-10 instruction to unify at `1.7.0`.
- Publish separate Fabric and NeoForge entries using `1.7.0+mc26.3.fabric`, `1.7.0+mc26.2.fabric`, and `1.7.0+mc26.1.x.fabric` (and their NeoForge equivalents).
- Pass one support line to `tools/publish-modrinth.ps1`. `26.1.x` is a display label; upload game versions must expand to `26.1`, `26.1.1`, and `26.1.2`.

## Release acceptance

1. Preserve the complete source for the already published 26.3 baseline.
2. Build and test both loaders on 26.2 and 26.1.2 with Java 25.
3. Run the actual packaged jars on standalone servers. Check startup, status/config/validate/reload, bad reload preserving the disk file and last-known-good state, UTF-8 BOM, network chunk delivery, and nonzero rewrite tasks.
4. Cover every advertised 26.1 patch version with matching Fabric and NeoForge launchers. Recheck metadata, not only compilation against 26.1.2.
5. Use queue 16 and the same configuration for release smoke samples. These short samples do not replace the Minecraft 26.2 performance matrix or establish a full performance baseline for 26.1.x.
6. Inspect jar metadata/hashes, run the release helper in dry-run mode, publish only new entries, and read back their game versions/loaders/hashes/status.
7. Update source branches, release notes, the shared compatibility table, and featured versions.

## Product direction

- Preserve protection when a configuration mistake is made; keep `/antixray config` and `/antixray validate` read-only.
- Keep Fabric and NeoForge behavior aligned.
- Keep async queue default at `16`. Larger queues remain manual tuning options backed by profile measurements.
- Keep Paper tracking as a release gate; avoid unrelated server controls and cosmetic core rewrites.

## Carried follow-up: bStats activation

The shared client is present, but `service-id=0` keeps submission inactive. Activation is separate from the unified-version release.

1. Register a dedicated bStats `Other` service and use its public numeric ID in code-owned defaults.
2. Migrate existing `service-id=0` configurations while preserving `enabled=false` and `server-uuid`; arbitrary IDs must not redirect metrics.
3. Use one initial-delay-plus-30-minute schedule, idempotent start/stop, reliable HTTP disconnect, and server-thread snapshots for live Minecraft state.
4. Test migration/opt-out/UUID, JSON and gzip payloads, loader names, and HTTP failure handling.
5. Verify accepted submissions for both loaders before declaring metrics active.

## References

- Release status and validation: `reports/1.7.0-multiversion-release.md`.
- Long-term commands and compatibility constraints: `.codex/REFERENCE.md`.
- Paper parity: `reports/paper-parity-status.md`.
- Minecraft 26.2 queue matrix: `reports/1.5.0-pressure-matrix.md`.
