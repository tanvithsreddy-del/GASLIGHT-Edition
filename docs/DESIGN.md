# GASLIGHT streamlined design

## Experience and constraints

Behind the player, familiar terrain can become a different explorable place. Houses, mined corridors and villages may leave the accessible local scene after the player looks away. The illusion changes spatial routing, rather than deleting the player's original saved blocks. Retiring access to a scene is different from physically destroying it. Protected structure columns and their contact buffers remain absolute vetoes; a village can disappear from the accessible view without cutting its blocks. Unsupported cameras or uncertain data postpone the change.

An ordinary player should experience continuity. Absolute undetectability with arbitrary freecam, debug tools, incompatible mods or network conditions is not a promise. Creative flight, F5 and rapid camera rotations are explicit supported-profile tests, not assumptions.

## Six implementation groups replacing sixteen separate engine efforts

1. Shared Java controller: analytic rear boundary, finite safe facets, paired rigid transforms, terrain candidate ranking and generation scheduling. Reuse correct existing Geometry, safety, routing and lifecycle implementations.
2. Portal provider: remote textured rendering, remote worlds, traversal, collision and interaction from a verified open-source engine. Use a version-pinned dependency or separately attributed fork; one renderer provider owns remote scene presentation. Avoid competing native and provider chunk transports.
3. Preparation: a small bounded donor pool, structure-safe seam matching and owned chunk leases. Millions of blocks of separation cost a coordinate transform; they do not justify loading millions of blocks. Cache prepared candidates and defer if budgets expire.
4. Camera/publication safety: actual effective camera/projection and rendered frame, session/generation fencing, all affected views, full-bundle prepare/commit/retire, outstanding journal reconciliation. Portal-provider availability does not imply these GASLIGHT guarantees.
5. Platform adapters: loader events, networking, persistence, structures and provider bindings. Fabric first. NeoForge second on a verified matching provider version. Forge requires a separate supported version. Never duplicate the math/policy engine per loader.
6. Evidence and release gate: build, actual visual/crossing proof, lifecycle/failure tests, world integrity, measured TPS/FPS/memory/bandwidth and crash recovery. Keep every unsupported condition closed and every untested requirement open.

## Compatibility contract

| Platform | Intended behavior | Current local status |
|---|---|---|
| Fabric + client mod | Full portal-based illusion | Genuine 26.1.2 data build exists; gameplay incomplete |
| NeoForge + client mod | Same shared policy, different adapter | Planned; no GASLIGHT build/test |
| Forge + client mod | Same shared policy on separately pinned supported versions | Planned; no GASLIGHT build/test |
| Vanilla client + modified server/plugin | Reduced out-of-view terrain/routing effects | Planned; cannot certify the strict camera/frame guarantee |
| Unmodified client and unmodified server | No arbitrary new engine capability | Unsupported |
| Bedrock / Pocket | Separate future implementation | Deferred |

'Vanilla renderer' inside a modded client is supported by some portal engines; it does not mean an unmodified Minecraft client. A vanilla-client edition must expose its reduced guarantees explicitly and must not bypass the existing authoritative-camera gate to appear equivalent.

## Mathematical model

Let P be horizontal player position, h horizontal forward unit direction, n=(-h_z,h_x), and F the conservative projection of the real camera frustum in world coordinates.

Rear curve: Gamma(u)=P+u*n-[D+A*exp(-u*u/(W*W))]*h, u real, D>=0, A>=0, W>0. Keep this analytic; materialize only finite certified sections within budgets. A full-height wall uses both worlds' actual min/max build heights.

View support: s(theta)=max(v in F) dot(v-P,e(theta)); r(theta)=max(r_min,s(theta)+margin). Polygon facets require chord correction and whole-boundary validation, not only point samples. Existing Geometry.enclosingLoop has conservative support logic; retain its tests.

Rigid mapping: T(p)=B+R*(p-A); T(velocity)=R*velocity. Initially use unit scale and grid-aligned rotations. All facets in one contour share the same T. Check corner continuity, inverse transform, collision clearance and the remaining swept movement after crossing.

Protected columns: for protected set S, D_r(S) includes every column within Chebyshev radius r. Both source and destination portal supercovers must avoid D_1(S). Optional six-chunk smoothing requires seven-chunk structure clearance. Unknown structures/chunks remain unsafe.

Terrain ranking: E=w_h*height_error+w_s*slope_error+w_m*material_error+w_b*biome_error. Hard safety vetoes run before ranking. A low ranking score is not proof of full-height seam continuity.

Cadence: every 20 ticks can propose a logical generation; physical activation waits for all data, camera evidence and atomic publication. At most three logical generations does not imply three portal entities: bound facets, recursion, remote chunks and GPU work independently. Delayed traversal or unsafe retirement can postpone a replacement.

## Private worlds and editing

Default tests use disposable worlds. Original saves are never transplanted, wiped or mutated. Donor preparation is bounded and ahead of activation. WorldEdit is optional preparation tooling, not an every-second bulk edit loop. Avoid six-chunk smoothing in the first functioning portal proof. Reject incompatible seams first; only add bounded, reversible edits with truthful crash recovery when necessary.

## Sixteen observable requirements

| Requirement | Acceptance evidence still needed |
|---|---|
| 1. Real destination view | Locally run textured GPU test |
| 2. Seamless walking | Genuine client/server crossing, retained velocity |
| 3. Mining/placing through boundary | Interaction and collision tests |
| 4. Infinite symbolic rear curve | Existing math plus finite realization tests |
| 5. Full-height coverage | Underground and creative-flight crossing |
| 6. Closed contour | Coherent shared transform and facet-corner tests |
| 7. Unobserved publication | Camera-flick/F5/FOV/frame fence tests |
| 8. Seam alignment | Full boundary terrain and lighting comparison |
| 9. Structure avoidance | Actual starts/references/boxes on both sides |
| 10. Player protection | Source preservation and separate-session tests |
| 11. Distant preparation | Loaded/unloaded/new-generation integration |
| 12. Replacement cadence | Measured deferred activation under budget |
| 13. Safe retirement | Crossing, camera, references and cleanup tests |
| 14. Failure/restart | Outstanding intents, crash injection, reconciliation |
| 15. Efficiency | TPS/FPS, real memory/bandwidth/ticket measurements |
| 16. Platform compatibility | Separate genuine per-loader builds and gameplay |

These requirements are a release checklist, not a completion score.

## Sources inspected

- https://github.com/iPortalTeam/ImmersivePortalsMod (Apache-2.0; archived)
- https://github.com/CaseBacon/ImmersivePortalsMod/tree/26.3 (commit c5a83c77cb493b4fbab2663a6d4f0de004895ea8)
- https://github.com/Luke100000/ImmersiveGateways/tree/26.1.2 (commit 4b018a6be9e2ba2b1545af35195f833e799d0ac5)
- https://github.com/fmpapierz/SeamlessPortals (linked source lacks the advertised engine)
- https://www.curseforge.com/minecraft/mc-mods/immersive-portal/files/8091136
- https://github.com/UpperMoon0/ImmersivePortals (community NeoForge 1.21.1 profile)
- https://github.com/EngineHub/WorldEdit (GPLv3; optional editing integration)

Inspect exact source/license and retain required notices for any reused code. Website credit alone is not the reuse procedure. Provider adoption remains conditional on local build and gameplay evidence.
