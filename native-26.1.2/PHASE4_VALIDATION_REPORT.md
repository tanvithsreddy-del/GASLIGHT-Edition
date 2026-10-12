# GASLIGHT Native 26.1.2: Phase 4 offline validation report

**Scope:** Phase 4 authorization, 12 October 2026. This package is an independently testable Java/Fabric **source tree**, not a tested or installable release JAR.

## Outcome

**Offline: PASS. Real Minecraft release: NO-GO.** All eight JVM offline suites and three mutation probes pass. Actual Fabric 26.1.2 building, Java 25 runtime loading, native GPU portal compositing, camera synchronization, authoritative traversal, server TPS/client FPS, save integrity in Minecraft and the 95+ release condition are **unverified or unimplemented**. The release gate deliberately exits **42**. No claims about a visually seamless gameplay experience are made.

## Three newly discovered implementation bugs, now corrected

1. **Chunk-face omission:** `Geometry.chunksTouched` originally enumerated the chunk containing the minimum X/Z coordinate only, omitting its neighbor when the segment lay *exactly* on a chunk boundary. Lower bounding indices now use `Math.nextDown(...)`; the intersection predicate still independently filters candidates. The new exact-face tests and regression mutation check detect reintroduction. All prior tests also pass after the fix.
2. **Incorrect pitched-camera basis:** `Geometry.Camera.fromYawPitch` gave the up vector incorrect horizontal signs for nonzero pitch, making the 3D basis non-orthonormal. This invalidated legitimate camera witnesses while looking up or down and could compromise view-frustum calculations. Corrected signs and tested thousands of random pitch/yaw combinations.
3. **Lost recovery-journal intent:** Two `Phase3RecoveryJournal.FileStore` instances initialized from a clean path could both write, allowing the second to overwrite the first unretired intent. Every write now uses an advisory OS file lock and checks that the exact on-disk bytes match the writer's previously observed version. Concurrent/stale writes, symlinks and oversized journal files fail closed; write-failure state is quarantined.

This third fix serializes cooperative journal writers. It is **not** an independent proof of crash recovery with real portal entities; that remains blocked by missing renderer/host integration.

## Reproducible validation

From the project root (local JVM 21 is enough for *offline* source-only tests):

```sh
bash run_offline_checks.sh
java -Xmx512m -cp build/core-test-classes dev.kondalabs.gaslight.Phase4Benchmarks
bash scripts/check_adapter_syntax.sh  # temporary fake API shapes, syntax ONLY
python3 scripts/phase4_mutation_probe.py
bash scripts/phase4_gate.sh             # expected exit 42: hard release veto
```

Baseline tests: Native core 383,217; Phase 2 preview 1,835; Phase 1/2 wire 1,273; Phase 3 optics/routes 39,629; Phase 3 hardening 12,115; Phase 3 final 97,163; destination 19,802; Phase 4 validation 1,410,549. **Total: 1,965,583 assertions**. Counts are assertions, not independent gameplay scenarios.

New Phase 4 evidence includes a fixed-seed independent point-sampling oracle for 18,000 line segments with **594,000** sampled points, randomized structure/unknown/player-protected chunk injection, deterministic bounded destination discovery, randomized camera pitch/yaw, portal-coordinate and velocity inverse properties, 128 publication transaction variants, crash/journal tampering, **40 simultaneous journal-writer races**, 180 fully executed safe/unknown logical planning epochs, and **200,000 virtual controller ticks (10,000 epochs)** with unavailable camera authority. The last test must publish **zero** physical portals. There are also **three mutation probes** run on disposable copies of the original source: exact chunk boundary, camera pitch, and stale journal writer.

The deterministic test seed is printed in the `PHASE4_OFFLINE_TESTS.log`. Reproduce with the same seed and source files.

## CPU microbenchmark results (not game FPS/TPS)

The observed results on this container's 4-vCPU Java 21 JVM (`-Xmx512m`) are in `evidence/PHASE4_BENCHMARKS.log`; these are **individual operation timings**, not 20-tick end-to-end Minecraft latencies. The benchmark warms each operation, measures 9 repeats, and outputs a median and the maximum sampled repeat (labeled p95 because nine repeats do not resolve a true 95th percentile). Do not extrapolate to performance on a game client, shaders, GPU, server thread or multiplayer.

Performance assertions are deliberately not release gates because this hardware cannot emulate in-game loading/rendering. Further performance work needs real profiling on a disposable game world.

## Scope exclusions and remaining release vetoes

| Gate | Status |
|---|---|
| Offline algorithm/property/fault-injection suites | PASS |
| Temporary-stub Java syntax checks | PASS, NOT REAL API COMPILATION |
| Mutation sensitivity of three safety invariants | PASS |
| 20-tick simulated scheduling | PASS |
| Genuine JDK 25 / Fabric 26.1.2 build | NOT TESTED HERE |
| Real dedicated server and graphical Minecraft client start | NOT TESTED HERE |
| Native GPU portal renderer and synchronized remote world/actors | NOT IMPLEMENTED |
| Real atomic facet publication and client camera authority | NOT IMPLEMENTED |
| Seamless in-game traversal, velocity, collision and view transitions | NOT VERIFIED |
| In-game world-save integrity, multiplayer, crash recovery | NOT VERIFIED |
| In-game FPS/TPS/memory benchmarks | NOT MEASURED |
| Strict release score >95 and mandatory vetoes cleared | **FAILED / NO-GO** |

**Important:** Large assertion counts cannot prove complete camera visibility, physical safety, or gameplay behavior. The production Fabric host keeps physical publication disabled. Never install an old experimental/stub-built JAR into an important Minecraft world.

## Supporting files

- `evidence/PHASE4_OFFLINE_TESTS.log` and `evidence/PHASE4_OFFLINE_RESOURCES.log`
- `evidence/PHASE4_BENCHMARKS.log` and `evidence/PHASE4_BENCH_RESOURCES.log`
- `evidence/PHASE4_MUTATION_TESTS.log`
- `evidence/PHASE4_ADAPTER_STUB_ONLY.log`
- `evidence/PHASE4_RELEASE_GATE.log`
- `PHASE4_GATES.json` with explicit machine-readable statuses
- `SHA256SUMS.txt` for archived contents, except itself
