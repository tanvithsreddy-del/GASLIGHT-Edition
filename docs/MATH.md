# GASLIGHT geometry and safety mathematics

Notation: horizontal player position `P=(x,z)`, forward horizontal unit vector `h`, perpendicular `n=(-h_z,h_x)`, world vertical `y`; `C` is the authoritative client-view frustum and `F` its projected corners. The present code uses `Geometry.V2/V3/Camera` and scale-preserving `NativePortal.Passage`.

## 1. Analytic, infinite rear line
A **symbolic** straight line behind the player is

```
Gamma_infinity(u) = P - D*h + u*n,            u in R
```

An optional smooth curvature that bends farther back is

```
Gamma(u) = P + u*n - [D + A*exp(-u^2/W^2)]*h
```

`D>=0`, `A>=0`, `W>0`; the actual `Geometry.InfiniteRearLine` expresses this formula. **Never enumerate all u**. Evaluate only finite clips intersecting the currently eligible route/chunk budget. Actual `Geometry.rear()` sets a conservative D based on the minimum projected frustum coordinate; its default amplitude is zero.

## 2. Frustum support and surrounding loop
For a unit azimuth `e(theta)=(cos theta,sin theta)`, support of projected frustum relative to player:

```
s(theta) = max_{v in F} dot(v-P, e(theta))
r(theta) = max(r_min, s(theta)+margin)
loop(theta) = P + r(theta)*e(theta)
```

A polygon chord may lie *inside* the geometric envelope; `Geometry.enclosingLoop()` includes neighboring support radii divided by `cos(pi/N)` for `N` sides, to resist this. The loop must be tested as entire polygon (closedness, no self-intersection, consistent source/target mapping), never published facet by facet if atomic publication unavailable. Projection alone is insufficient for camera/F5/zoom/occlusion state authority in the real game.

## 3. Coordinates through the passage
For source center A, destination B, and a rigid axis-preserving mapping R:

```
T(p) = B + R (p-A)
T(v) = R v        // velocity, not translation
```

No scaling. In `NativePortal.Passage.mapPosition/mapVelocity`, the entrance's tangent and vertical components map to the exit's tangent and vertical components, while the portal-plane normal component is negated. This is the **current actual code mapping**, not a guarantee that all polygon facets share a globally coherent R at their corners. Check corner/vertex continuity and reverse mapping before claiming arbitrary curved portals.

## 4. Swept crossing
For a portal plane with signed side function `d(p)`, from p0 to p1:

```
alpha = d(p0) / (d(p0)-d(p1))
intersection = p0 + alpha*(p1-p0)
```

Require alpha in [0,1] and the intersection inside the finite rectangular portal with entity clearance. Continue remaining motion transformed across the exit. Cooldowns/ownership prevent immediate bounce; real collision and interactive world physics still require engine integration.

## 5. Full-column structure safety
Let `S` be horizontal chunk columns containing any part or reference of a registered structure, including underground pieces; an owner-chosen additional protected set `U`; `UNKNOWN` data is unsafe. Define Chebyshev dilation

```
D_r(S) = { (cx+dx,cz+dz) | (cx,cz) in S, max(|dx|,|dz|)<=r }
```

For a physical source footprint A and paired destination footprint B:

```
A intersect D_1(S_source union U_source) = empty
B intersect D_1(S_target union U_target) = empty
```

The actual planner performs conservative per-column safety inspections of both footprints and halos. With an **optional, hypothetical** six-chunk Chebyshev smoothing reach, extend to D_7; smoothing is not implemented in the current project. Structural information unavailable -> reject; full vertical coverage must use actual world minY/maxY (not an arbitrary 32-block wall).

## 6. Paired path routing
For horizontal chunks `q` visited by proposed curve and fixed chunk offset Delta:

```
source(q) = q
remote(q) = q + Delta
paired_cost(q) = infinity if source OR remote chunk/halo unsafe
```

Use bounded A* on the chunk grid, with source/target simultaneously certified, then a final supercover of every finite physical segment. A path found on the *grid* does not certify the final geometry until every touched chunk has been checked. Do not interpolate across protected buffers.

## 7. Candidate matching (approximate)
The current implementation captures a 3x3 column fingerprint in already-loaded source/target chunks: ground elevation, nine-point slope, material category and exact ID fallback, biome family, fluid/lava/water line, and two-block headroom. A bounded weighted ranking is combined with hard vetoes for missing data, insufficient headroom, severe differences and unknown chunks. This is **not** proof of full voxel-by-voxel equality across a vertical portal wall and cannot independently eliminate a visible seam.

## 8. Safety and logical lifecycle
At most one logical planning epoch per 20 ticks. Each candidate is certified before commit; current active generations get revalidated before proposals. Publication must be staged, invisible, atomic for the **entire** bundle, and confirmed with a durable intent journal. Retirement only when no player is nearby and both source and destination views are certified hidden by fresh client evidence. Failed abort/retirement -> quarantine; never forget an uncertain generation. The default game Host has **no verified atomic stage implementation and returns camera authority false**, so it refuses physical publication.

## 9. Why certainty cannot be promised yet
The frustum and analytical line are merely necessary constraints. Absolute 'never observed' requires actual client-render frame synchronization, all cameras/zoom states, async chunk latency, geometry/collision/lighting consistency, and atomic present/retire. The offline model cannot certify it without a real renderer and game tests.
