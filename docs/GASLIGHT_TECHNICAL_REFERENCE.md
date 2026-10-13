# GASLIGHT technical reference — development implementation

This is the separate reference document requested for personal/website use.
The current implementation is a development prototype, not a completed release.
It targets Minecraft 1.21.1, Fabric and a Java 25 runtime. The 26.1.2 native backend
and 26.3 source experiment remain distinct; neither receives compatibility by
renaming this artifact. See AUTOMATIC_GAMEPLAY_REPORT.md for the actual verdict.

## What runs

`/gaslight begin` explicitly enters a private `gaslight_provider:unobserved` realm.
The original return location is durably stored before the transfer. One compatible,
unmounted player is supported. The tested profile uses the vanilla renderer and
render distance 2–4. Nether and End portal modes remain vanilla. The introductory
portal wiki message is suppressed; compatibility and memory warnings remain.

The server prepares a real destination in the private realm. It reads existing
saved status before loading, rejects already-FULL/visited reservations, admits
native generation under budgets, and owns UUID-specific chunk tickets. No unknown
chunk is treated as air. A FULL chunk is insufficient for physics preparation:
the native ticking state and completed lighting work are required too.

The engine uses Immersive Portals 6.0.6 for real chunk/block-entity/light transport,
native meshes, portal compositing, collision, traversal and interaction. These are
unmodified Apache-2.0 upstream implementations. GASLIGHT supplies acquisition,
surveying, journal ownership, frame selection and lifecycle gates. No WorldEdit
code is included. Fabric API and SQLite JDBC retain their own embedded notices.

## Geometry and assumptions

For source plane origin `a` and destination origin `b`, the current mapping is

`T(p) = p + (b − a)`.

There is no rotation or scale. Its derivative is the identity, so lengths, angles
and volumes are preserved. Integer X/Z translations preserve the block lattice.
This proves properties of the mapping; it does not prove that two unrelated
terrains join naturally.

The current aperture is 64 blocks wide, from the dimension's minimum build Y to
maximum build Y. Two surveyed block columns meet the plane. They are copied into
unused destination terrain with native section operations, heightmap updates,
lighting checks and network dirtying. Block entities in either copied strip veto
the reservation. Old private generation ticks in that strip are removed before
copying; copying does not restart placement callbacks on naturally suspended
blocks. Subsequent player actions use normal Minecraft operations. Original
source blocks are read, not replaced.

This narrow strip is an incomplete seam algorithm. Larger terrain height/cave
differences can create a wall just beyond it, and finite ends can reveal the
construction. A naturally blended, joined contour is not implemented.

## Observation and publication

A connection has a random session nonce, monotonically retained generation and
actual rendered frame number. The client collects projection/view matrices from
the real renderer, including encountered nested portal views. A conservative
frustum test expands affected boxes before float conversion. Every collected
view must exclude the changed rear half-space and retiring portal apertures.
Unsupported cameras, unavailable data or uncertain matrices do not authorize a
selection. This is certified for the tested renderer/profile only, not arbitrary
camera, shader or resource-pack modifications.

Readiness has distinct stages: native server ownership and intent; real client
chunks and lighting; checksum-matched strip; completed native compilation/GPU
uploads where geometry exists; server readiness/clearance receipt; excluded
rendered frame; matching server commit acknowledgement. Empty compiled sections
are not counted as uploaded geometry. During selection, client movement and block
or entity interaction are held. Failed acknowledgement closes the scene rather
than rolling an already selected view back.

The server repeats identity, camera-distance, source-distance, occupancy, seam and
deadline checks. Rendering selection is one generation identity between frames.
Physical publication follows the matching acknowledgement. Staged portals reject
visibility, teleportability, collision and interaction. Unselected cancellation
preserves the previous active view and retains a journal tombstone.

The frame data is bound to the specific play connection's player identity and server
nonce. The disposable integration server is offline-mode on loopback; this is not
an online-account authentication test or a guarantee against a modified client.

## Explicit budgets

| Resource | Current bound |
| --- | --- |
| Players / staged reservations | 1 / 1 |
| Owned FULL chunks per destination | 81 |
| Native generation dependency footprint | at most 1,024; observed bound 961 |
| Pending saved reads / native load futures | 2 / 2 |
| Generation admission | burst 2, replenish 1 per 30 seconds; failed attempts spend a permit |
| New-work memory admission | below both 700 MiB and 70% of maximum Java heap |
| Strip copying | 2,048 block operations per server tick |
| Client presentation / mesh window | 49 / 25 chunks |
| Native mesh scheduling | 2 sections per frame, at most 16 waiting |
| Encoded outbound bandwidth | 8 MiB/s with 16 MiB burst |
| Encoded inbound bandwidth | 1 MiB/s with 2 MiB burst |
| Preparation / unselected client offer | 60 seconds / cancel after 40 seconds |
| Selection/cancellation acknowledgement | 2 seconds |
| Retained journal | 4,096 intents, 4,096 pages of 4 KiB; 4,000 placement reservations |

The wire meter covers all encoded/compressed traffic while the session is active,
not only remote chunks. Exhaustion closes the whole connection; selectively
missing terrain packets are not allowed. Heap admission is not a hard bound on
all native GPU allocations or the Minecraft engine's internal dependency work.
Infinite exploration and worst-case resource/leak certification remain unmet.

## Persistence and failure

SQLite uses PERSIST rollback journaling, FULL synchronization, integrity checks,
checksummed payloads and compare-and-swap revision updates. Retired and recovered
records remain in history. On Windows, long names use an escaped extended-length
URI and the locking `win32-longpath` VFS. Directory fsync errors in the original
file store still cause conservative refusal; they are not ignored.

Owned scene entities are nonpersistent. A permanent entity-load veto rejects
unowned/stale instances. Reconciliation retains unresolved evidence and clears
authorization before a new generation. The journal is not an atomic transaction
over Minecraft region saves. Tests use real process halts and corrupted records;
they do not claim physical power-cut certification.

## What the full requested experience still needs

The current one-plane controller cannot continuously replace every unseen region
while keeping another portal visible. A coherent contour and retirement model,
natural seam matching, preparation that keeps up with movement/camera flicks,
and bounded long-running storage are still required. Every missed one-frame
replacement counts as failure under the user's strict rule. Passing geometry
assertions, compiling a JAR or demonstrating a few hidden selections cannot
turn these missing requirements into a passed release gate.

Forge/NeoForge require genuine matching adapters and game tests after the complete
experience is proven. An unmodified vanilla client has no equivalent renderer or
frame fence. Bedrock/Pocket and shader profiles are not implemented or certified.
