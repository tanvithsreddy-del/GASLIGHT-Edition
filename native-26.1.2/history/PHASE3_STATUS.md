# GASLIGHT Native 26.1.2: Phase 3 implementation audit

**Dated 2026-10-12. RELEASE = HARD NO-GO.** The full Phase 3 game experience does not work and has not been certified. This is an original Java offline engineering deliverable, not an installable Minecraft build.

## What changed compared to the Phase 1/2 source

1. `Phase3Visibility.java`: conservative six-plane 3D frustum rejection for vertical portals. Certifies *current* camera invisibility only; no guarantee under future camera turns. Includes a conservative finite-view-excluding mathematical closed polygon.
2. `Phase3Route.java`: bounded A* and exact geometric fast path; full halo-expanded physical segment audits; source and far destination both inspected at 16-block chunk alignment, including unknown and player-protected chunks; caps on expansions, chunk queries and portal facets; no forced chunk generation or terrain edits.
3. `Phase3LoopRoute.java`: end-to-end paired route for an entire closed polygon, all-or-nothing rejection for any unsafe edge, gaps, self-intersections, loss of enclosure, or resource caps. Long route geometry stays mathematical until publication is available.
4. `Phase3Engine.java`: deterministic 20-tick logical generations, optional short rear-boundary or fully closed-loop routing, candidate search and bounded three-location surface-height matching, 2-generation cap, old-generation safe retirement logic, independent commit-time structure veto, optional native crossing integration.
5. `ProtectedChunkRegions.java`: optional explicit no-touch chunk-column rectangles for player-created areas; invalid protection policy globally fails closed. Supports `config/gaslight-protected-chunks.csv`, one rectangle per line `minChunkX,minChunkZ,maxChunkX,maxChunkZ`.
6. `GaslightNativeMod.java`: add opt-in `phase3DynamicMode`, `phase3ClosedLoopMode`, `phase3AllowPhysical`, `phase3AllowCrossing`; server source/terrain inspectors; remove the older automatic scheduler entrypoint that bypassed the new safety gates. All switches default false.
7. `Phase3Tests.java`: tests optical frustum cases, 2,400 random-view finite-loop trials, 2-sided obstacle routing, destination offsets, failed inspections, exact closed-loop topology, protection parsing, atomic publication refusal, retirement, no-client-camera veto and 25 automatic epochs.
8. Gradle `phase3Check` hooked into `check` and local `run_offline_checks.sh`.

## Independent evidence, not claims of gameplay success

| Scope | Result | Evidence |
| --- | --- | --- |
| Existing Phase 1/2 geometry/traversal | PASS offline, 383,217 assertions | `PHASE3_TESTS.log` |
| Existing preview and fixed rig | PASS offline, 1,835 assertions | `PHASE3_TESTS.log` |
| Existing wire/occlusion | PASS offline, 1,273 assertions | `PHASE3_TESTS.log` |
| New Phase 3 route/visibility/loop/state machine | PASS offline, 39,629 assertions | `PHASE3_TESTS.log` |
| Total Java offline assertions | **425,954** | All four suites in `PHASE3_TESTS.log` |
| API-shaped adapter Java syntax | PASS against locally fabricated stubs only | `PHASE3_ADAPTER_CHECK.log` |
| Real JDK 25/Fabric compilation | **BLOCKED**: only JDK 21, Gradle absent; external DNS unavailable | `PHASE3_REAL_FABRIC_GATE.log` |
| Client rendering and in-game portal crossing | **NOT TESTED / NOT IMPLEMENTED SEAMLESSLY** | No gameplay evidence exists |

### Non-negotiable incomplete work

- No real Minecraft 26.1.2 client/server build or launch. The source may contain API compatibility issues.
- No native GPU live remote-world rendering, remote chunk/entity stream, or cross-seam rendering correctness. Phase 2 remains a diagnostic 32³ voxel HUD.
- No authoritative Fabric client-to-server camera feed covering third-person, zoom, shaders, UI mods and latency. Vanilla server yaw/pitch **cannot certify visibility**.
- No atomic whole-portal-set commit/retraction implementation in actual Minecraft. `Phase3Engine.Host.publishAtomically()` and `.retireAtomically()` both default to **false**. Vanilla adapter provides no override; physical activation is blocked even if configuration is mistakenly enabled.
- Closed loops are generated and protected in pure Java, but no complete seamless physical closed-loop portal system exists in the game. Sharp piecewise corners are not proven collision or render continuous.
- Extra protected-region policy catches marked player builds only; it **does not automatically discover all player-built structures**. Vanilla structure metadata coverage and modded structure interactions need actual in-game tests.
- Terrain matching only uses a provisional local height profile in loaded chunks, not full biome, block palettes, fluids, vegetation, lighting, structures or 6-chunk smoothing. No WorldEdit operations or terrain edits are performed.
- No genuine survival/creative/spectator/flight, crash-resume, latency, chunk border, high-speed entity, multiplayer, or fps/tps test.
- Logical epochs happen every 20 ticks **only while enabled and an eligible single player is present**. Physical activation is allowed to defer forever, because physical safety outranks timing.

### Critical safety note

`phase3DynamicMode=true` and `enable=true` can run the logical planner, but are experimental and may query loaded chunk data each epoch. Physical/teleportation are disabled and rejected by the adapter. DO NOT use this on a valuable world, DO NOT use the prior experimental JAR as a substitute, and DO NOT treat offline tests as a release-gate pass.

There is no legitimate 95+/100 release score with mandatory rendering/build/traversal vetoes still open. Phase 3 engineering is materially advanced, but Phase 3 as the requested **in-game experience remains INCOMPLETE**.
