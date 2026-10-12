# GASLIGHT Native 26.1.2: integrated Phase 1–4 final environment audit

**Date:** 2026-10-12  
**Target:** Fabric Minecraft Java 26.1.2, JDK 25  
**Scope:** maximum validated engineering work available in this current environment. The zip is **source and test evidence, not a real Minecraft-compiled or game-tested JAR**.

## Decision

**Offline engineering: PASS, including newly discovered and corrected safety defects.**  
**All four phases as Minecraft functionality: NOT COMPLETE. Release: NO-GO.**

No installed JDK 25, Gradle distribution or reachable dependency host was available. `phase1_verify.sh` exited **21**. Real Fabric compilation and game launches could not be performed. The source-only temporary Fabric API stub check is not evidence of actual Minecraft compatibility.

Native seamless remote-world GPU rendering, synchronized live destination chunk streaming and physical atomic publishing are not implemented in the real Minecraft adapter. The 20-tick engine therefore deliberately refuses physical publication. No honest test count changes that.

## Verified implementation improvements in this final pass

1. **Nonfinite geometry bug repaired.** `Geometry.chunksTouched` previously returned apparently valid chunks for NaN coordinates. World-bound, finite-coordinate, finite-expansion, analytic-line and camera-pose guards are now explicit. Independent tests demonstrate the original behavior was unsound.
2. **Blocked headroom bug repaired.** The terrain comparator previously accepted equally blocked landscapes and scored one blocked destination column as potentially compatible. Any sampled blocked headroom now vetoes the match; the previous permissive test was corrected.
3. **Host failure quarantine added.** World/player inspection exceptions, null collections, invalid actor positions/angles and missing actor IDs disable further physical planning/traversal instead of propagating uncontrolled exceptions.
4. **Standalone adversarial test suite added**, registered with both the offline script and the genuine Gradle `check` lifecycle. Tests cover invalid geometry, valid exact-face chunk enumerations, hostile hosts, headroom failures and malformed clientbound snapshot data.
5. **Mutation sensitivity increased** from 3 to **5 independently injected regressions**. Every fault triggered a corresponding failing safety test when reintroduced into a separate source copy.
6. Updated accurate gates, preserved the actual Java 25 toolchain failure, reran benchmarks, and audited the archive in a clean extraction.

## Test evidence

Nine offline suites, **1,983,152 passing assertions**, with meaningful categories rather than equating assertion count with gameplay proof:

| Suite | Assertions |
|---|---:|
| native core | 383,217 |
| phase2 preview | 1,835 |
| phase12 wire | 1,273 |
| phase3 optics route | 39,629 |
| phase3 hardening | 12,115 |
| phase3 final | 97,163 |
| phase3 destination | 19,803 |
| phase4 validation | 1,410,549 |
| phase4 final safety | 17,568 |

Five safety mutation probes passed, and each deliberately modified copy failed the relevant suite as expected. The first combined gate attempt timed out while redundantly running all nine suites per mutation; the optimized, equivalent targeted-mutant gate subsequently completed and returned expected **exit 42** with all nine suites, five mutation probes, syntax-only adapter check, and static safety checks passing. The timed-out attempt was not counted as successful. A 200,000-tick synthetic run validated 10,000 logical planning epochs with no physical publication when camera authority was absent. These are **synthetic source-only tests**, not Minecraft frame/tick metrics.

## CPU-only performance (Java 21, 4 vCPU, 512 MiB heap)

Current benchmark medians (nine measured repeats, not a statistically rigorous p95):

| Operation | Median ns/operation |
|---|---:|
| Geometry chunk supercover | 83,869 |
| Paired structure clearance | 3,977 |
| Camera frustum test | 55 |
| Paired terrain check | 1,740 |

Times vary by CPU/load, represent isolated operations, and cannot be converted to FPS, TPS or end-to-end 20-tick latency. Inspect `evidence/PHASE4_FINAL_BENCHMARKS.log` for exact sampled results.

## Completion matrix: all four phases

| Phase | Achievable here | In-game release blockers |
|---|---|---|
| 1. Fabric build foundation | Build config, Java source checks against temporary API-shaped stubs, nine offline test suites, CI recipe, fail-closed release gating | Genuine Java25/Fabric compile and dedicated-server/client startup not run |
| 2. Static portal | Original crossing geometry and fixed-portal state machine, serialized read-only remote voxel snapshots, bounded synthetic projection and occlusion | No Minecraft-native textured/depth-tested remote compositor, no reliable live collision/traversal |
| 3. GASLIGHT mechanics | Finite routing and analytical infinite lines, structure and protected regions, destination fingerprints, loop planning, 20-tick logic, stage/retire protocol, durable intent journal; offline tested | Real client-camera authorization and atomic world/GPU publication absent; terrain comparison sampled rather than exact 3-D seam |
| 4. Adversarial verification | Geometry fuzz/property tests, long logical runs, mutation probes, journal races, malformed-packet tests, source compilation against stubs, CPU microbenchmarks | Cannot test real Minecraft TPS/FPS, save integrity, entity traversal, GPU occlusion, or live recovery |

## Current non-negotiable safety policy

- Physical Phase 3 publication stays disabled (`phase3AllowPhysical=false`, `authoritativeCamera` returns false in `GaslightNativeMod`).
- Dirty/crashed publication journal records veto new physical publication.
- Unknown chunks and unknown structure data reject candidate activation.
- No experimental/stub-built JAR is a release artifact.
- Do not use on an important Minecraft save.

## Reproduction

```sh
bash run_offline_checks.sh
python3 scripts/phase4_mutation_probe.py
bash scripts/check_adapter_syntax.sh  # temporary API shapes only
java -Xmx512m -cp build/core-test-classes dev.kondalabs.gaslight.Phase4Benchmarks
bash phase1_verify.sh                 # exits 21 here: JDK25 not installed
bash scripts/phase4_gate.sh          # intentionally exits 42: release veto
```

## What an external environment must still provide before claiming four completed phases

A genuine Java 25 Fabric 26.1.2 build and server launch, a client that actually composites remote world geometry/actors with correct depth and synchronization, reliable cross-boundary physics, a verified stage/commit/rollback bridge, direct game rendering/performance tests, and disposable-world save/recovery testing. Until then **the four-phase project is an extensively tested offline foundation, not a complete Minecraft mod**.

Full combined release gate evidence: `evidence/PHASE4_FINAL_RELEASE_GATE_COMPLETE.log`.
