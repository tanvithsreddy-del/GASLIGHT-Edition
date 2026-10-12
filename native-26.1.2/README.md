# GASLIGHT Phase 2 recovery journal (2026-10-12)

Journal writes now retain transaction evidence after uncertainty, reject
ABA-stale writers through a V2 checkpoint revision, and read existing V1
records conservatively. Windows directory durability remains uncertified:
publication is refused and evidence is preserved. See
[RECOVERY_PHASE2.md](RECOVERY_PHASE2.md) for the contract and test limits.
No renderer, traversal or automatic world-reconciliation system was added.

# GASLIGHT Phase 1 genuine build (2026-10-12)

**Compiled development checkpoint, not a playable portal release.** Phase 1 pins
Gradle 9.7.0, Loom 1.18.4, Minecraft 26.1.2, Fabric Loader 0.19.5 and Fabric API
0.155.3+26.1.2. Two clean Linux builds pass with identical development JARs;
main, client and test source sets also compile on Windows. All ten standalone
suites, six mutation probes and the packaging checks pass on Linux. The Windows
directory-fsync journal regression remains fail-closed and unresolved.

See [BUILDING_PHASE1.md](BUILDING_PHASE1.md) for the pinned recipe and limits.
Physical publication/crossing remains disabled. Phase 2 has not been implemented.
The older reports and manifests below describe the preserved handoff baseline.

# GASLIGHT Final Four-Phase Offline Audit (2026-10-12)

**NOT A PLAYABLE RELEASE.** The consolidated verification found and fixed further host-callback failures. Ten offline suites pass (1,983,174 assertions); six mutation probes pass. The genuine Fabric compile and client render remain blocked/unimplemented. Read `PHASE4_FOUR_PHASES_ENVIRONMENT_CEILING_REPORT.md` before interpreting older status documents below.

---

# Final integrated audit: 2026-10-12

**This is a source-only, offline-validated project. Do not call it a released Minecraft mod.**
See `PHASE4_FINAL_ENVIRONMENT_REPORT.md` and `FINAL_PHASES_GATE.json`. The final offline suite consists of nine executables, including hostile-input regressions. Real 26.1.2 compilation and gameplay remain blocked.

---

# GASLIGHT Phase 4: offline validation checkpoint

**NOT A PLAYABLE RELEASE.** For the latest tests, measured CPU microbenchmarks, three corrected defects, and unverified gameplay requirements see [PHASE4_VALIDATION_REPORT.md](PHASE4_VALIDATION_REPORT.md). Run `bash run_offline_checks.sh` for the eight offline suites or `bash scripts/phase4_gate.sh` for the deliberate release veto (exit 42).

# GASLIGHT Native: Phase 3 maximum verified offline checkpoint

Original, independent experimental Fabric mod project targeting **Minecraft Java 26.1.2**. This is **NOT** a playable GASLIGHT release. No dependency on an external portal mod.

## What this checkpoint contains

- Prior offline-tested Phase 1/2 source, diagnostic remote voxel snapshots and HUD preview.
- Native 20-tick logical scheduler, curved source/destination clearance routing, finite closed-loop planning, conservative camera-frustum checks, structure and player-protected chunk no-touch policies, and staged publication/abort/quarantine safety protocol, all-route terrain matching and deduplicated source/destination chunk audits.
- Phase 3 opt-in controller wired to the Fabric server lifecycle adapter; legacy unsafe auto-scheduler entrypoint is retired.
- Test fixtures and fail-closed release gate.

**Physical activation is unavailable on the real adapter.** The existing client renderer is not a seamless GPU portal engine. The 26.1.2 real Fabric toolchain has not compiled or launched this code. See `PHASE3_COMPLETION_REPORT.md` and `PHASE3_GATES.json`.

## Source-only validation (JDK 21+ for standalone tests)

```sh
bash run_offline_checks.sh
bash scripts/check_adapter_syntax.sh
bash scripts/phase3_gate.sh  # deliberate NO-GO until real release criteria are satisfied
```

The adapter check generates fake minimal API classes in a temporary directory. They never go into a distributed mod JAR and do not prove compatibility with Minecraft.

## Real Fabric build (not accomplished in this environment)

Provision Java 25, Gradle 9.3.1, internet access, then run `gradle clean build`. GitHub Actions source is present in `.github/workflows/gradle.yml`, but has **not** run. A genuine dedicated-server smoke plus graphical client are also required.

## Configuration

All `config/gaslight-native.properties` flags are false by default. For isolated logical planning only, set `enable=true` and `phase3DynamicMode=true` on a disposable singleplayer world. The client rendering and atomic publication gates still keep actual new Phase 3 portals disabled. Optional `phase3ClosedLoopMode=true` asks for an expensive full finite polygon route at each eligible 20-tick planning epoch.

For explicit protection of player-built regions, create `config/gaslight-protected-chunks.csv` with lines like:

```csv
# minChunkX,minChunkZ,maxChunkX,maxChunkZ
-10,20,-6,25
3,3,3,3
```

The rectangles apply to all vertical levels; invalid files block planner certification. They are not a replacement for built-in structure inspection or a guarantee that every possible construction is discovered.

No world edits or WorldEdit integration exist. Enabling remote chunk generation for other legacy modes could still write world data, so leave `allowRemoteChunkGeneration=false`.

## Destination selection and validation (new)

The bounded planner now ranks up to 8 candidate offsets (prioritizing the per-world
catalog), deduplicates entries and checks both endpoints against the world border.
The server adapter captures a **3 × 3 fingerprint** from each *already-loaded*
source and destination chunk-column: local height, material category, exact IDs for
otherwise-unknown materials, biome family, water/lava presence and water elevation,
plus two-block headroom. Slope shape and major continuity mismatches are hard vetoes;
a bounded weighted score breaks ties among acceptable candidates. Every touched
route chunk-column is evaluated through the existing supercover audit.

The real Minecraft Host **requires** complete fingerprints, rejecting unavailable
chunks and missing biome/material/fluid data. Its former height-only score is now
used only by legacy synthetic test hosts. Global read budgets apply per epoch, and
all selected route profiles are sampled **afresh before attempted publication**.

This is **NOT an exact voxel-to-voxel full-height seam equivalence proof**, and the
new server calls have only been syntax-checked against API-shaped stubs, not linked
against real Minecraft 26.1.2. Loaded remote chunks must be arranged independently;
a catalog entry is never proof of preloading. See `PHASE3_DESTINATION_STATUS.md`.

## New Phase 3 hardening audit

See **`PHASE3_COMPLETION_REPORT.md`** for the original six offline suites, plus `PHASE3_DESTINATION_STATUS.md` for
19,802 new destination assertions (554,621 total),, durable on-disk publication journal, all-chunk terrain audit, safe retirement, prepared-candidate file and remaining hard blockers. Real client rendering is NOT implemented.

## Phase 3 per-world files

`<world>/data/gaslight-phase3-candidates.csv` may contain up to eight lines of two integer CHUNK offsets: `70000,-80000`. The file only nominates search locations: candidates are rejected if not previously loaded, terrain-incompatible or structure-unsafe. Never interpret an offset as proof a safe destination exists.

`<world>/data/gaslight-phase3-intents.bin` is an automatic crash-intent journal created only before a physical publication attempt. **Do not delete it to bypass a dirty-world safety lockout.** The renderer-aware cleanup logic needed to reconcile those records does not yet exist.
