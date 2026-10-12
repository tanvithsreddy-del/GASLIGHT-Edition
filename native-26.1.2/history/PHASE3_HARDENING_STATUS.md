# GASLIGHT Phase 3: maximum locally verified hardening checkpoint

Date: 2026-10-12. Target: Minecraft Java 26.1.2, Fabric. Version: `0.6.0-phase3-hardened-offline`.

**Verdict: Phase 3 offline components expanded and tested; genuine Minecraft Phase 3 gameplay is INCOMPLETE. NO-GO for release or valuable saves.** Phase 1 genuine compilation and Phase 2 native seamless 3D rendering are still blockers. There is no honest completed-world-switching claim.

## Implemented in this pass

1. `Phase3TerrainMatcher`: instead of sampling three lucky points, inspect five points on EVERY routed physical segment, deduplicate by source chunk, match precise destination chunk offsets, reject missing/nonfinite/out-of-range data, reject any score below 0.70 or a route-wide mean below 0.80, and enforce a global unique-chunk probe budget. This only evaluates the available height-oriented host score, not comprehensive biome/structure/fluid equivalence.
2. `Phase3SafetyGate`: certify the complete set of source and destination portal footprints as full vertical chunk columns with a Chebyshev one-chunk clearance margin, hard global column limits, deduplicated chunk lookups, world-border validation, and unknown/structure/user-protected hard vetoes. Reruns at physical commit and on active-gen safety checks.
3. `Phase3CameraWitness`: require an explicit, owner-bound, fresh, non-replayed camera witness with authenticated-pipeline assertions, checked camera basis, plausible FOV/far plane and bounded agreement with server eye location. **Neither this class nor its booleans establishes actual client attestation; the real Fabric host does not provide witnesses and therefore cannot publish.**
4. `Phase3Publication`: replaced the obsolete direct `publishAtomically` option with an explicit stage/commit/abort transaction contract. A rejected/throwing publication with unconfirmed cleanup is quarantined; a missing atomic host is refused before any publication. This class cannot by itself make a renderer atomic.
5. `Phase3Engine`: unified stage transaction, sampled the whole route and rechecked terrain before publication, checked source AND destination for visibility, now derives physical geometry from witness camera direction, retains unretired generations after `close()`, supports compensating retirement retries, and disables crossing when cleanup is unconfirmed.
6. `Phase3Engine.Host.preparedDestinationOffsets`: allows the bounded search to try already-known distant chunk offsets before deterministic random offsets, preventing pointless global remote-generation assumptions. Every catalog entry is still subjected to all normal structure, terrain, world-border and visibility checks.
7. `Phase3Engine.VerticalSpan`: physical seam facets now require host-supplied actual world min/max build height and span that range, instead of silently being 32 blocks high. The current Vanilla host can report bounds but cannot publish.
8. `Phase3HardeningTests`: randomized terrain fault injection, malformed/protected/unknown destination checks, stale/owner-mismatched camera witness checks, source/destination paired-column structure audits, commit-stage failure and aborted transaction tests, quarantine-retirement retry, owner limits, and prepared-offset controller integration.
9. Gradle's `check` now depends on the new Phase 3 hardening suite in addition to the previous independent suites. Obsolete direct physical publisher hook removed entirely.

## Verified evidence

| Validation | Result | Evidence |
|---|---|---|
| Native common logic | PASS, 383,217 isolated Java assertions | `PHASE3_HARDENED_TESTS.log` |
| Fixed portal/preview | PASS, 1,835 isolated assertions | same |
| Snapshot/foreground occlusion | PASS, 1,273 isolated assertions | same |
| Prior Phase 3 planner/loops | PASS, 39,629 isolated assertions | same |
| NEW failure-injection Phase 3 | PASS, 12,096 isolated assertions | same |
| **Total** | **PASS, 438,050 offline assertions** | same |
| Common/client adapter Java parsing against temporary API-shaped stubs | PASS as **syntax-only**, not a real Fabric build | `PHASE3_HARDENED_ADAPTER.log` |
| Strict release gate | **NO-GO (intentional exit 42)** | `PHASE3_HARDENED_RELEASE_GATE.log` |
| Genuine Java 25/Fabric compilation and Minecraft launch | **BLOCKED / NOT TESTED** | `PHASE3_HARDENED_ENVIRONMENT.log` |

## Exactly what remains incomplete

- No verified 26.1.2 Fabric build. This container has JDK 21, Gradle not installed, and Gradle distribution hostname fails DNS resolution.
- No Minecraft client or dedicated-server launch; API test stubs do not verify any actual Minecraft signatures.
- No native depth-tested, textured, live remote-world GPU compositor or client entity/chunk streaming. Phase 2 remains a diagnostic voxel HUD. Nothing here changes that.
- No genuinely authenticated C2S live camera-and-projection feedback or 3D compositor frame-sync. The real Fabric host provides neither a camera witness nor staged publication.
- No actual atomic publication, closed-loop seam walking, cross-boundary collision/interactions, Minecraft physics, runtime retreat/recovery or measured client FPS/TPS.
- Catalog has no real on-disk registry of prepared remote chunks, and random millions-away candidates will normally be UNKNOWN/unloaded; safe failure is expected.
- The current host terrain matching uses a few height probes per chunk. It does not certify biome, full blocks, fluids, lighting, or complex cliffs and does not do terrain smoothing. The new matcher improves coverage over that **limited** score, not its underlying fidelity.
- The geometric closed-loop planner is piecewise planar and offline-tested, not a proven continuous physical seam. No unlimited infinity is instantiated.
- Registered vanilla structure chunk references and user-specified protected rectangles cannot guarantee every player-built or modded structure is discovered.
- A staged host can lie or fail atomically; Phase 3 can record and quarantine that uncertainty but cannot repair orphaned in-game portal entities when the actual renderer is absent. After process death no persistent quarantine recovery journal is implemented.
- An always-hidden physical change at exactly every 20 ticks is impossible under some camera/world configurations; the safe contract is: update the logical plan every 20 ticks where enabled, defer actual activation.

## Usage

`bash run_offline_checks.sh` runs all five isolated suites.
`bash scripts/check_adapter_syntax.sh` runs fabricated-API **syntax-only** checking.
`bash scripts/phase3_gate.sh` intentionally exits 42 until real build, 3D rendering, authoritative client camera, atomic publishing, collision and in-game stress gates are satisfied.

All Phase 3 physical publication settings remain OFF by default. The actual Fabric host does not implement `stagePublication` or `cameraWitness`. Do not install old development JARs against an important world. Phase 4 is not authorized by this checkpoint.
