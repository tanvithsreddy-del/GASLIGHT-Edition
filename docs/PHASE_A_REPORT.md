# GASLIGHT execution report

**Overall verdict: NO-GO for full GASLIGHT. Not all authorized phases are complete.**
Decision: `STOP_MAJOR_WORK_REQUIRED` for the complete experience. The user's later
implementation authorization supersedes the handoff's testing-only limits; remaining
implementation is already authorized. No new approval is requested by this report.

| Phase | Verdict | Actual readiness |
|---|---|---|
| A — provider integration | PASS for the fixed-link prototype | Genuine adapter JAR loaded directly by a graphical Windows client and Linux/ext4 dedicated server; remote view, lighting, updates, interaction, walking/return, collision, flight and cleanup verified. Known upstream errors remain; this is experimental. |
| B — automatic look-away illusion | FAIL / not implemented | No authenticated camera/frame fence or atomic client generation publication. No full-height contour, certified seam matching, forest/house/mining scenario proof or camera-flick/F5/zoom proof. Automatic changes remain disabled. |
| C — repeated safe gameplay | FAIL / partial safety evidence | Disconnect releases ownership and chunks; dirty restart quarantines correctly. Durable physical-retirement reconciliation, repeated publication in one saved world, arbitrary saved-but-unloaded acquisition in this adapter, strict resource accounting and long-run leak tests remain absent. Windows publication is blocked. |
| D — loader ports and polish | BLOCKED / NOT RUN | No GASLIGHT Forge or NeoForge adapter compiled/tested. No reduced vanilla-client edition. Bedrock deferred. Complete Fabric gameplay prerequisite is unmet. |

## Authoritative source and versions

Source: `work/handoff/GASLIGHT_CODEX_HANDOFF/ACTIVE_PROJECT/provider-adapters/fabric-1.21.1`.
Only ACTIVE_PROJECT source was used. HISTORY was not merged. The 26.1.2 native source
is unchanged from the prior checkpoint; file hashes are in `evidence/modified-files.json`.
The new prototype deliberately uses **Minecraft 1.21.1**, not 26.1.2. This follows the
user's later version/provider restructuring freedom and does not claim 26.1.2 compatibility.

Pinned: Fabric Loader 0.19.5; Fabric API 0.109.0+1.21.1; Immersive Portals 6.0.6,
Modrinth version vOmUATGw; Loom 1.18.4; Gradle 9.7.0 (wrapper distribution SHA-256
84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae).
Windows toolchain: Temurin 25.0.4.1+1-LTS. Linux toolchain: Temurin 25+36-LTS.
Dependencies and their hashes, toolchain release files, metadata and JAR contents
are recorded under `evidence/toolchains` and `evidence/jar-verification.json`.
The source archive SHA-256 is recorded under `GASLIGHT_SOURCE.zip` in
`SHA256SUMS.json`; the delivered Git revision is recorded in `GIT_REF.txt`.

Final named development JAR SHA-256:
`daa597b6b33384de793897ec7445d9e6d318e5ecccfb831f2d74b90e9667176c`.
Final intermediary JAR SHA-256:
`0d35f7e49e141ebf879d3674a62b148978cd3190fb59ac86e340d6de3f21985b`.
Windows and Linux builds produced identical final hashes. Named JARs were directly
runtime-tested; normal-launcher intermediary JARs were genuinely compiled, remapped
and inspected, but have not received a separate normal-launcher gameplay run.
No JAR is a full GASLIGHT release.

## Verification and raw evidence

| Check | Result | Evidence |
|---|---|---|
| Ten original baseline suites plus recovery/remote-data/lease suites | PASS on Windows and Linux | `all-native-regressions.log`; `final-linux-native-regressions-and-mutations-attempt-3.log` |
| Six mutation probes, including journal CAS | PASS: all six caught on both platforms | `six-native-mutations.log`; Linux regression/mutation log and `linux-mutations/` |
| Real journal process interruptions, concurrent/stale writers, corruption and unresolved records | PASS for tested fail-closed semantics | Linux recovery: 890 assertions, 13 real process halts. Windows: 854 assertions, 3 halts, 4 unreachable durability points. Windows's acknowledged durable cycle remains blocked. These are process-interruption simulations, not hardware power-cut tests. |
| Old adapter syntax script | FAIL, **STUB ONLY** | `native-syntax-STUB-ONLY.log`, exit 123, 208 missing fabricated-API symbols. The stub façade is stale after acquisition changes. No fabricated APIs entered any genuine build/artifact. |
| Actual native 26.1.2 production/client/test build | PASS | `native-26.1.2-genuine-final-build.log`, exit 0, 26.422 seconds; preserved data/acquisition experiment, not seamless GASLIGHT gameplay. |
| New adapter production + integration source sets and three JUnit tests | PASS | Final Windows clean build, fresh Linux-directory clean build, zero failures/errors/skips XML results. Windows filesystem tests include conservative durability-refusal branches; do not infer successful Windows durability/CAS cycles from their green result. |
| Direct packaged development-JAR startup and graphical connection | PASS | `linux-dedicated-server-packaged-attempt-3.log` and `windows-real-client-packaged-attempt-3.log`; both `GASLIGHT_CODE_SOURCE` lines point to the JAR. Loopback relay transports real Minecraft packets. |
| Destination textured view, lighting and live updates | PASS in the fixed fixture | Genuine framebuffer screenshots; client remote diamond→emerald state and light level 14; real server block updates. |
| Placement and mining through the portal | PASS in final repeat | Server gold block appears/disappears at (1024,82,1022) while player remains at (0.5,81,1.5). Initial held-click/GUI tests are retained as failed/confounded attempts. Final driver injects Minecraft's initial click queue and ordinary held input. |
| Walking, return and collision | PASS in fixed fixture | Ordinary forward input changes source (0.5,81,3.5) to destination (1024.5,81,1022.3); wall stops movement; return input crosses back. Setup teleports are explicitly labeled fixture resets and do not count as traversal. |
| Creative flight | PASS in fixed fixture | Actual client ability `flying=true`, input crossing and server-confirmed destination position. This is not full build-height coverage. |
| Dimension change and reconnect | PASS for fixed-link disconnection isolation | Real Nether transition, remote data `UNAVAILABLE`, client cleanup `initialized=false`; reconnect cannot revive retired portals or the far chunk window. World-change lease retirement while still connected is not certified. |
| Structures/protected columns/unknown data | PASS for tested veto cases | Unit full-column/diagonal contact, unknown and protection cases; actual loaded Minecraft starts/references inspected; CSV protection, corruption, NaN, unavailable destination and same-dimension destination occupancy reject before intent publication. A naturally generated village/structure-boundary gameplay test is NOT RUN. |
| Saved world integrity | Original worlds untouched | Only newly created named disposable worlds were used. Fixture block edits are intentional test setup. No full source-file integrity benchmark for automatic GASLIGHT exists because that feature is absent. |
| Lease/ticket cleanup | PASS for fixed ownership | Four entities and two loaders → zero; destination FULL chunks 25 → zero; provider tickets → zero after disconnect. Reconnect loads only ordinary nearby tickets, with remote FULL chunks still zero. |
| Restart with outstanding intent | PASS: correct quarantine | `genuine-restart-quarantine.log`: intent 1, portals 0, owned loaders 0, no republish. No unresolved world journal was deleted or silently settled. |

Commands, exit codes and measured command durations are in `evidence/commands.jsonl`.
All failed attempts remain alongside successful retries. Do not count Gradle exit 0
alone: earlier game launches exited unsuccessfully despite a successful Gradle task.

## Measurements and limits

`PERFORMANCE.json` contains all sampled values and per-connection byte counts.
Observed client FPS snapshots were 57–60 with a **60 FPS cap**, render distance 4 and
a small framebuffer. These are not sustained FPS/weak-hardware benchmarks. Highest
sampled used Java heap was about 570 MiB client / 464 MiB
server; samples include ordinary game state and garbage. They do not prove retained
cache size, native/GPU memory, leak freedom or a strict total-memory bound.

Typical sampled active server average tick time was around 1–2 ms; dimension-change
samples were about 11–13 ms. Zero-player cleanup samples were about 0.2 ms. Full raw
arrays remain available; no broad TPS/performance certification is inferred.

Two radius-2 owned loaders hold 50 chunk references, with a 25-chunk remote window.
Ordinary nearby/traversal provider tickets are additional (106 active near the source,
162 or more during travel in tested snapshots). Server heap is capped at 768 MiB and
client heap at 1536 MiB, but separate bandwidth, GPU memory, generation-work and
preparation-time admission budgets are not implemented. Native neighboring generation
work exceeds the 50 target fixture chunks and was not fully counted. One final warm
cleanup was observed after 0.687 seconds, including command delivery and two polls at
a 0.15-second interval; see `evidence/cleanup-measurement.json`. This is not a latency
distribution benchmark. Initial-acquisition latency benchmarks are NOT RUN.
WSL's clock was about 30 minutes behind
Windows, so cross-host log timestamps are not used to infer latency.

## Defects and remaining implementation

1. **No automatic GASLIGHT behavior.** The adapter publishes only an explicit fixed
   link before observers join. Native `authoritativeCamera` remains false. Provider
   reuse does not implement an authenticated frame fence, whole-generation client
   staging/commit, recursive viewport accounting or cancellation-safe visibility.
2. **Retirement reconciliation is incomplete.** Retiring removes runtime entities
   and loaders but leaves one durable intent. A vanilla save boolean is not treated
   as a durable removal receipt. Subsequent publication and dirty restart stay blocked.
3. **Windows durability is blocked.** Real directory force raises AccessDeniedException;
   retained evidence and fail-closed behavior are preserved. No equivalent guarantee
   or recoverable publication strategy has yet been implemented for Windows gameplay.
4. **Destination preparation is insufficient for full gameplay.** This adapter
   requires already loaded certified FULL windows. Saved-but-unloaded acquisition,
   explicitly permitted bounded generation, cancellation/timeouts and precise native
   dependency budgets must be integrated before arbitrary distant destinations work.
5. **Known provider runtime errors remain.** `Chunk loading failure ... {}` and
   `Null RenderChunk` appear around initial loading. Subsequent fixed remote views and
   interaction passed, but cold-frame completeness and genuine throttle-failure
   handling are not certified. The first diagnosis incorrectly assumed active loading
   was disabled; actual configuration enables it. The ineffective readiness patch was
   removed. A stale copy of that abandoned source was detected in the Linux mirror,
   removed, and a new clean build/JAR gameplay repeat was performed.
6. **Full-height geometry/seams are absent from the provider adapter.** The fixed
   4x4 fixture is not a full-height rear contour. Full-height paired block/fluid/lighting
   seam checks, shared transforms across facets and the requested natural gameplay
   examples need implementation and graphical adversarial tests.
7. **Client and server budgets are not equivalent.** Server-side portal globals do
   not enforce client recursion/GPU limits. Existing working transport cannot be
   assumed to provide whole-generation atomic publication or bounded client cache
   admission simply because loaded fixed chunks render correctly.
8. **Portability remains unimplemented.** Fabric's build is not a Forge/NeoForge JAR.
   An unmodified vanilla client cannot execute the seamless portal renderer or the
   same camera/frame contract. No such edition is claimed supported.
9. **The old passage mapper is not the provider's rigid transform.** NativePortal's
   normal-flipping map is used here only as a journal geometry carrier. It negates one
   basis component and has determinant -1; a proper rigid rotation has determinant +1.
   Do not use that old movement map as the future provider transform without reconciling
   orientation, handedness and corner continuity. Current physical movement uses the
   provider's unit-scale translation, not that map.

## Changes, safety and handoff

Three new production Java files total **199 physical source lines**: FixedPortalAdapter,
GaslightProvider and FixedPortalCommands. Five existing pure GASLIGHT classes are reused
unchanged. Build/configuration, pinned dependency setup, isolated integration driver,
three unit tests, disposable fixture setup, notices and documentation were added.
The same-world destination occupancy defect was fixed by auditing explicit world/position
pairs; the test uses a real destination armor stand and observes the veto.

`evidence/adapter-new-files.diff` and `modified-files.json` provide exact file-by-file
changes and line counts. The earlier small-fix-only authorization was superseded;
this new integration is described as implementation, not disguised as mechanical maintenance.
Original safety defaults, unknown/structure/protected-column vetoes and recovery records
remain intact. No release score is assigned from offline assertions.

Stop reason: the complete experience fails the camera/publication, retirement/recovery,
destination-preparation and platform gates. Remaining work is already authorized, but
is not complete or verified in this delivery. Continue at Phase B's actual frame-safe
publication contract, then complete recovery/resource lifecycle before porting the
experience. The packaged fixed-link prototype is reviewable evidence; the complete
GASLIGHT experience remains unfinished.
