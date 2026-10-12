# GASLIGHT 26.1.2: consolidated four-phase environment-ceiling report

**Date:** 2026-10-12  
**Project:** Standalone original Fabric-compatible GASLIGHT source  
**Certification:** **OFFLINE ENGINEERING VERIFIED; REAL MINECRAFT RELEASE NO-GO**

## Scope and evidence

This archive updates the preceding Phase 4 deliverable, rather than starting a new project. No code from another portal mod is required at runtime. The previous Phase 4 verified baseline was **1,983,152** isolated JVM assertions and five captured regression mutations. This pass discovered and hardened a previously uncovered failure boundary in the Phase 3 host lifecycle.

**New results:** **1,983,174 offline assertions** across ten standalone JVM suites; **six of six deliberate safety regressions detected**. The combined Phase 4 verification gate finished with **exit 42** (expected release veto). All new tests, the benchmark, and the temporary-stub adapter syntax gate completed. The authentic Fabric build gate exited **21** because `javac 21.0.12.1` was available instead of Java 25. No Gradle binary was installed; external dependency services failed DNS resolution. There is **no genuine Fabric-compiled JAR** and **no Minecraft gameplay validation**.

## New defects detected and fixed in this pass

1. **Uncaught Host operation failure:** Phase3Engine.tick had unguarded client-camera and other external adapter invocations. Any unchecked failure could escape the server tick. The automatic engine now quarantines on unexpected exceptions, forgets crossing state, and ceases interacting with the game Host.
2. **Caught exception silently retried:** Phase3Engine.prepare caught host exceptions as if they were ordinary rejected candidates, letting later ticks attempt again. It now permanently quarantines on any unexpected planner/Host exception.
3. **Missing or throwing recovery journal:** The constructor now fails closed when the Host's journal provider or `blockedOnOpen()` throws or returns an invalid response, instead of failing construction unexpectedly.
4. **Throwing journal settlement:** A failed settlement no longer escapes retirement bookkeeping and conceals orphaned ownership. The generation remains tracked and physical operations are quarantined.
5. Added `Phase4HostIsolationTests`, injecting five Host failures (camera, authenticated-view predicate, witness transport, missing journal provider, dirty-journal inspection) and asserting that physical publication/traversal never occurs and reading the Host ceases after quarantine. **22 new assertions** pass.
6. Added a **sixth mutation test** that reintroduces a swallowed host-camera error, which the new suite detects. All six mutations fail their targeted tests as expected.

## Source-level verification results

| Suite | Assertions |
|---|---:|
| `native_core` | 383,217 |
| `phase2_preview` | 1,835 |
| `phase12_wire` | 1,273 |
| `phase3_optics_route` | 39,629 |
| `phase3_hardening` | 12,115 |
| `phase3_final` | 97,163 |
| `phase3_destination` | 19,803 |
| `phase4_validation` | 1,410,549 |
| `phase4_final_safety` | 17,568 |
| `phase4_host_isolation` | 22 |
| **Total** | **1,983,174** |

Reproduction, from extracted project root:

```sh
bash run_offline_checks.sh                     # exit 0; ten offline suites
python3 scripts/phase4_mutation_probe.py      # exit 0; six mutants detected
bash scripts/check_adapter_syntax.sh          # exit 0; fabricated APIs, not Fabric proof
java -Xmx512m -cp build/core-test-classes dev.kondalabs.gaslight.Phase4Benchmarks
bash phase1_verify.sh                          # exit 21 in this container (Java 25 absent)
bash scripts/phase4_gate.sh                    # exit 42 (correctly release-blocked)
```

Logs: `evidence/PHASE4_MAX_OFFLINE_TESTS.log`, `evidence/PHASE4_MAX_COMBINED_GATE.log`, `evidence/PHASE4_MAX_BENCHMARKS.log`, `evidence/PHASE4_MAX_REAL_BUILD_GATE.log`, `evidence/PHASE4_MAX_ADAPTER_STUB_ONLY.log`. Each log describes operations actually performed. CPU microbenchmarks are **not** game FPS/TPS or proof of an SLA.

## Honest phase completion matrix

| Phase | Available offline work | Still missing for complete Minecraft functionality |
|---|---|---|
| **1: Fabric build foundation** | Java sources, Gradle/CI recipe, fail-closed startup and packaging gates, temporary-API adapter syntax checks | Actual Java 25 + Fabric compile, dedicated server boot and client launch |
| **2: Native static portal** | Mathematically mapped traversal, durable voxel snapshot/wire format, bounded software visualization and occlusion, local mocked integration | Native remote-world textured GPU compositor, live client chunk streaming, real physics and actors |
| **3: Automatic GASLIGHT mechanics** | Structure-protected routing, distant candidate terrain fingerprinting, analytical curves and physical-facet plans, 20-tick logic, recovery ledger and atomic-stage interface, extensive adversarial tests | Real-world atomic renderer bridge, certified client camera/presentation synchronization, full materialized continuous loop and actual in-game crossing |
| **4: Verification and release** | Property/mutation/fault tests, 200K synthetic ticks, CPU microbenchmarks, archive reproducibility and strict release veto | Real gameplay FPS/TPS and GPU output, save corruption/recovery tests on disposable worlds, multiplayer/entity/collision and camera adversarial testing |

**Full original gameplay/release work is not complete.** Source tests cannot substitute for real Minecraft execution; no physical automatic publishing is enabled in the Fabric Host. The project should not be installed on valuable worlds. The source is suitable as a versioned development handoff.

## Non-negotiable continuation gates

- Obtain a genuine Java 25/Fabric 26.1.2 build on a networked runner.
- Integrate a native remote-scene renderer and real client-camera frame fence, test on a disposable Minecraft world.
- Implement authoritative collision/traversal and fully atomic lifecycle before enabling physical publishing.
- Independently verify full structure protection, save recovery and performance with the real game.
- Preserve every veto in `FINAL_PHASES_GATE.json` until corresponding *actual* evidence exists.

**Bottom line:** All feasible source-level verification in this pass has been run and the discovered faults fixed. This does **not** certify four gameplay-complete phases or a distributable JAR.
