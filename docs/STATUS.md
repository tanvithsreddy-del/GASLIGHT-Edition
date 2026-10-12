# GASLIGHT checkpoint: Java first

Date: 2026-10-12. This is an experimental development checkpoint, not a playable GASLIGHT release.

The user superseded the original testing-only and Phase 4B-only restrictions with permission to restructure the implementation and reuse open-source portal engines. World safety, protected structures, fail-closed behavior and truthful evidence remain mandatory. Java is now the first target. Fabric, NeoForge, Forge and vanilla-client support are desired; no untested platform is claimed supported.

## Native Minecraft 26.1.2 checkpoint

PASS: genuine Java 25 production/client/test compilation and clean Gradle build. The ten original baseline suites, previous recovery/transport tests and new acquisition model tests run in the regular build. Raw build log is included. Model assertions do not prove gameplay.

PASS in earlier real Minecraft tests: read nine saved-but-unloaded destination chunks without native loading tickets; three disconnect/reconnect cycles; source integrity across all 50 disposable-world files in the frozen read window; full-height blocks/biomes/lighting/heightmaps and block-entity update data reached the separate remote client cache.

Observed, incompletely verified: a previously ungenerated distant 3x3 destination became available on a real client. Native generation needed a 25x25 dependency footprint (625 holders), 9 owned loading tickets and 1,011 observed generation steps. The integration helper subsequently failed because it queried a near-origin biome instead of the distant destination. Its correction compiled but has not yet been rerun. Therefore this test is not labeled PASS.

Recorded measurements: remote data about 3.11 MB; measured saved-path client object graph about 3.09 MB plus 544 bytes after disconnect; saved native FULL holders and owned tickets zero. Distant generation observation about 7.74 seconds; this was not an isolated latency benchmark. Native memory reservation about 100 MB is an estimate, not a hard bound on whole JVM memory. Peak observed capture/preparation/disk work about 17.0/17.4/42.3 ms respectively in the generation run. No FPS or GPU performance result exists.

FAIL / INCOMPLETE for Phase 4B acceptance: cancellation during native generation, repeated native generation/unloading, actual simultaneous native-generation owners, exhaustive failure/resource injection, full native holder/heap leak detection and final Linux rerun remain unverified. New real-Minecraft helpers for saved-path concurrency, cycles, delayed IO, IO failure, memory exhaustion and malformed regions compiled but have not been executed.

Precise unresolved defects to address before relying on this backend:

1. Legitimate partially generated saved chunks currently fail the strict FULL decoder; resuming explicitly authorized generation after interruption needs a distinct unavailable/proto state while preserving corruption rejection.
2. Native ticket type is static across backend instances. Multiple independent services in one server could share ticket identity and interfere with release. The production path currently has one service, but general overlap ownership is not certified.
3. Explicit portal retirement is implemented for the certified-passage path; a configured independent permanent data-link can be prepared again while its viewer remains nearby. It needs retirement/definition fencing before that lifecycle is claimed complete.

No original worlds were used. Physical publication and crossing safety gates remain closed by default. The native 26.1.2 code still has no seamless textured portal compositor, authenticated frame barrier or complete safe publication/traversal implementation. This JAR is a compiled data/acquisition experiment, not the requested playable experience.

## Reuse investigation

The original Apache-2.0 Immersive Portals source is a strong reuse basis but its upstream repository is archived. WorldEdit is a GPLv3 terrain-editing library, not a portal renderer. Prefer separate integrations and preserve licenses, notices and source obligations rather than copying code with website credit alone.

The CurseForge project 'Immersive Portal' advertises a modern rewrite, but its listed Minecraft 26.1.2 artifact dates from before that rewrite. Its linked source repository contained only LICENSE when inspected. Do not infer that its 26.1.2 build supplies modern arbitrary invisible portals.

Immersive Gateways has real Fabric/NeoForge 26.1.2 source, but the inspected renderer draws end-gateway/rune effects and its traversal uses ordinary entity teleportation. It does not replace the seamless remote-scene engine.

CaseBacon's source-backed 26.3 fork has a real single-layer remote-view renderer, portal traversal and graphical GameTests. Its documented limits include no nested portals/mirrors in this renderer and no same-dimension views with Sodium. It is under genuine-build investigation; upstream claims are not local proof. A separate Minecraft version experiment must never be described as 26.1.2 compatibility.

The community NeoForge fork UpperMoon0/ImmersivePortals targets 1.21.1, not 26.1.2 or 26.3. A source-backed 1.21.1 Fabric/NeoForge profile is the fallback if the modern renderer cannot pass local proof. Forge needs its own verified version/provider. No universal cross-loader JAR is promised.

## Next acceptance gate

Prove a selected provider renders the real destination and supports actual crossing in a disposable client/server world, then integrate the existing controller and safety rules. Provider-only test success does not certify GASLIGHT visibility, seams, structure protection or retirement. Do not port every loader or write another renderer before this proof.
