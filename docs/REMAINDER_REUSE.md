# GASLIGHT reuse assessment — 2026-10-13

This is a source inspection and implementation record, not proof that an upstream
project supplies the complete GASLIGHT effect.

| Remaining work | Reusable implementation | Decision and boundary |
|---|---|---|
| Effective camera and rendering frame | Immersive Portals `RenderStates`, Fabric `WorldRenderContext` | Actual camera/projection capture is now integrated and exercised in a real client. Connection/session/generation/dimension fencing is implemented. Telemetry is advisory; it does not authorize publication. |
| Geometric visibility | JOML `FrustumIntersection` | Used for conservative whole-box rejection with a margin. One visible portion prevents a hidden classification. This does not cover every recursively rendered view or prove a complete frame barrier. |
| Saved destination preparation | Minecraft chunk scanner, native loading pyramid, owned tickets; Chunky inspected as a reference | Implemented against real 1.21.1 APIs. Two pending operations, 50 target tickets, at most 98 loading dependencies, 30-second deadline. Missing saved terrain fails closed. Chunky source was not copied or bundled. |
| Ownership and cleanup | Minecraft custom entity type, provider reverse/flipped portal helpers | GASLIGHT portal entities now have their own nonpersistent type. Existing load veto remains. These changes prevent new GASLIGHT portals from surviving as ordinary provider portals. |
| Intent durability | SQLite through xerial/sqlite-jdbc 3.53.4.0 | Implemented persistent rollback journal, FULL synchronization, revision CAS, retained tombstones, corrupt-record rejection and process-halt tests. A database transaction does not encompass Minecraft saves or a rendered frame. |
| Boundary intersections and spatial indexing | LocationTech JTS | Useful optional 2D tooling; not added to the production dependency set. Its circular buffers must not replace the existing Chebyshev structure buffers. It cannot certify full-height block/fluid/lighting seams. |
| Occlusion behind terrain | LogisticsCraft/OcclusionCulling, provider occlusion queries | Optimization candidates only. Cached/sample-based CPU occlusion and previous-frame GPU prediction are insufficient as the sole authority for an invisible change. |
| Look-away behaviour from other mods | Jeryn99/Weeping-Angels | Rejected for reuse: inspected LICENSE prohibits code modification/reuse without permission. Its entity-facing tests also do not supply a rendered-camera scene transaction. No code or assets incorporated. |
| Natural terrain matching | Existing GASLIGHT matcher, Minecraft terrain data | Existing correct math retained. No inspected library supplies complete 3D Minecraft seams, protected structures, lighting and collision continuity automatically. |
| Whole-generation publication | JDK immutable objects/atomic references; provider render hooks | Useful primitives, but the actual cross-thread/client/server transaction remains unimplemented. No inspected repository supplied the complete required protocol. |

## Exact inspected source revisions

- Immersive Portals: `2b1d3546aa60c7d866adfcb7236fba136a8433cb`.
- Chunky: `ab45b8b3a4ada40f69fbdb3af63d2a7004ce82a1` (GPLv3).
- OcclusionCulling: `7a346eabc5edcfdef31df3c21202c3c1473fb506` (MIT).
- JTS: `5a0a73632216e3e9d277150336078ec423fd215f` (EPL 2.0 / EDL 1.0).
- Weeping-Angels: `422686c30f975a0581b17e7c0dae8d2893b5e261` (restricted; not reused).

## Important source findings

Provider `PortalRenderInfo.renderAndDecideVisibility` sometimes uses the previous
frame's query result. That is an optimization, not a current-frame publication proof.
Provider chunk-loader removal uses object identity; retain the original loader handle.
The inspected Chunky generation callback increments progress even when its future
finishes exceptionally. GASLIGHT must verify actual FULL/data readiness independently.
Its general task scheduler is not a strict global resource admission controller.

## Sources

- https://github.com/iPortalTeam/ImmersivePortalsMod
- https://qouteall.fun/immptl/wiki/Implementation-Details.html
- https://github.com/pop4959/Chunky
- https://github.com/LogisticsCraft/OcclusionCulling
- https://github.com/locationtech/jts
- https://github.com/JOML-CI/JOML
- https://github.com/Jeryn99/Weeping-Angels
- https://github.com/xerial/sqlite-jdbc
- https://www.sqlite.org/atomiccommit.html

Reuse removes substantial infrastructure work. GASLIGHT still needs its actual
scene-selection, GPU readiness, seamless terrain-boundary and atomic publication
logic. The strict one-hidden-frame rule is not implemented by this preview.
