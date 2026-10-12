# GASLIGHT Phase 3 final achievable-offline checkpoint

**Date:** 2026-10-12  
**Target:** Fabric Minecraft Java 26.1.2  
**Source version:** `0.7.0-phase3-max-offline`  
**Final status:** **PHASE 3 NOT FULLY COMPLETE; REAL GAMEPLAY / RELEASE NO-GO. PHASE 4 NOT AUTHORIZED.**

This is a standalone code-and-offline-tests implementation, not a functioning Minecraft seamless world-switcher. There is no genuine Java 25 + Fabric build, verified client rendering, or in-game atomic portal publication. Synthetic tests cannot substitute for those results.

## Code implemented or corrected in this pass

1. **Every intersected terrain column checked.** `Phase3TerrainMatcher` replaces the previous five-points-per-facet heuristic with finite chunk supercover enumeration. It refuses budgets, missing samples, nonfinite/invalid scores, or a failing sample. No real biome/fluid/material equivalence is implied.
2. **Crash-durable intent record.** `Phase3RecoveryJournal.FileStore` writes entire source/destination facet bundles before publication, using bounded binary format, CRC32 corruption detection, temporary-file fsync, atomic rename and directory fsync. Confirmed abort or retirement clears the intent. Corrupt data or outstanding previous-session entries prevents new publication. A dirty file is deliberately **not self-clearing**: an actual world-aware renderer recovery procedure is still absent.
3. **Journal attached to per-world Minecraft adapter.** The adapter locates its journal at `<world>/data/gaslight-phase3-intents.bin`. It remains unable to create physical portals because its authoritative camera and atomic publisher are missing. The adapter was checked against synthetic Java API shapes ONLY, not compiled with Minecraft binaries.
4. **Old generation checked before new generation.** Every 20-tick epoch audits currently published source/destination structure safety before attempting a fresh generation. If an unsafe seam is near the actor, planning/crossing is suspended; it is not removed from under the actor.
5. **Retirement is proximity and camera gated.** Explicit retry also refuses near-player retirement. Automatic retirement needs a fresh owner-bound monotonic camera witness and tests **both entrance and distant exit** for visibility before removal.
6. **Cross-generation geometry conflict veto.** New `Phase3GenerationGeometry` rejects intersecting or less-than-eight-block-separated physical connections across existing and proposed generations, including perpendicular crossings. Adjacent facets within one generation are intentionally not rejected.
7. **Prepared destination catalog from world save.** `Phase3PreparedCatalog` strictly parses a bounded `<world>/data/gaslight-phase3-candidates.csv`, with each line `offsetXInChunks,offsetZInChunks`. Missing files are empty; malformed, out-of-range, duplicate or oversized catalogs yield no candidates. Entries are **candidates only**, not proof chunks exist or are safe. Structure, world-border and terrain checks remain mandatory.
8. **Offline integration and randomized regressions.** Added `Phase3FinalTests` and wired them into standalone validation and Gradle `check`. Tests cover all touched columns, diagonal holes, dirty/corrupt/truncated journals, restart veto, owner/generation accounting, cancellation and proximity retirement, candidate catalogs, overlap vetoes, and 20-tick shutdown quarantine.

## Latest reproducible evidence

| Test suite | Offline assertions | Result |
|---|---:|---|
| Native math and crossing core | 383,217 | PASS |
| Static portal and diagnostic preview | 1,835 | PASS |
| Snapshot wire format and occlusion | 1,273 | PASS |
| Phase 3 geometry/routing | 39,629 | PASS |
| Phase 3 previous hardening regressions | 12,100 | PASS |
| **New Phase 3 final property/journal/lifecycle tests** | **96,765** | **PASS** |
| **Total** | **534,819** | **PASS, OFFLINE ONLY** |
| Java adapter parsing against generated *fake* APIs | n/a | PASS, SYNTAX ONLY |
| Strict release gate | n/a | **NO-GO, intentional exit 42** |
| Real Fabric 26.1.2 compilation and server boot | n/a | NOT PERFORMED |
| Real client 3D GPU compositor and remote view | n/a | NOT IMPLEMENTED |
| Real atomic publisher, crossing and physics | n/a | NOT IMPLEMENTED / UNVERIFIED |

Logs: `PHASE3_FINAL_TESTS.log`, `PHASE3_FINAL_ADAPTER.log`, `PHASE3_FINAL_RELEASE_GATE.log`, `PHASE3_FINAL_ENVIRONMENT.log`. Commands: `bash run_offline_checks.sh`, `bash scripts/check_adapter_syntax.sh`, `bash scripts/phase3_gate.sh`.

## Exact remaining Phase 3 deficits

- The real Minecraft host intentionally has no authenticated multi-view client camera, no frame-synchronized GPU compositor, and no atomic physical portal stage/commit/abort implementation. **Automatic physical publication is blocked**, and the default config remains OFF.
- No genuine Java 25 Gradle/Fabric build or actual Minecraft singleplayer/dedicated-server launch. The host has JDK 21 and no installed Gradle, and dependency-host DNS is unavailable.
- No physical infinite line (intentionally never materialize infinity) or real textured, traversable curved loop; the loop planner represents a finite polygon only. Rendered terrain at facet boundaries, multi-facet collision, entity travel, chunk streaming and save integrity are not proven.
- No fully certified biome, slope/cliff, fluid or block-material seam matcher, and no reversible terrain edit/smoothing. The current server score is principally a limited loaded-block height similarity metric.
- A persistent candidate catalog exists, but no persistent **verified remote prefetch registry** or generation-free destination cache has been implemented. Unknown chunks still veto.
- Journal persists *intent and uncertainty*, **not automatic postcrash cleanup**. An external Minecraft-aware recovery adapter would need to identify and atomically remove/resume actual renderer state, then reconcile journal entries. Never clear a dirty journal merely to bypass a safety veto.
- Registered structure data plus explicit protected ranges cannot prove detection of every player-built or arbitrary modded structure.
- No benchmark of Minecraft server tick latency, GPU frame rate, memory, multiplayer, save rollback or realistic continuous spinning/flight. The offline assertion count provides no substitute for such results.
- A physical change every 20 ticks **cannot be guaranteed hidden** for every viewpoint. The safe policy is 20-tick logical planning with deferred physical publication if certification is unavailable.

## Gate decision

**No production JAR is delivered or recommended.** `scripts/phase3_gate.sh` must exit 42 while those hard vetoes remain. Phase 4 was not started, added or authorized. Previous status docs/logs are historical and superseded by this document.
