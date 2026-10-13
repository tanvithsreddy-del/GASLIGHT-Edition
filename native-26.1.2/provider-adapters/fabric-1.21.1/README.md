# GASLIGHT recovery and saved-destination development preview

**Current version: 0.2.0-recovery-preview. Automatic look-away changes remain
unimplemented. This is not the complete GASLIGHT experience.**

The new explicit recovery profile uses `-Dgaslight.sqlite=true` (or
`-PgaslightSqlite` for the proof server task). It is restricted to the same exact
disposable-world marker. It refuses any legacy `data/gaslight-phase-a` directory;
it does not migrate, ignore or delete unresolved legacy journals.

SQLite performs native platform-specific FULL synchronization with a persistent
rollback journal. The database is capped at 4096 pages of 4096 bytes; retained
history is capped at 4096 records. Errors veto publication. These guarantees depend
on the storage stack honoring SQLite's flush operations; process-halt tests are
not hardware power-cut certification. Intent records are retained as RETIRED or
RECOVERED tombstones. All new portal entities are nonpersistent GASLIGHT types;
the permanent load veto also rejects stale owned entities.

Operator console operations remain observer-free and marker-gated:

```
gaslight-fixed create x y z tx ty tz
gaslight-fixed acquire x y z tx ty tz
gaslight-fixed cancel-acquisition
gaslight-fixed retire
gaslight-fixed status
```

`acquire` loads saved chunks only. It checks the real native loading dependencies
before taking tickets, admits at most two pending operations, 50 target chunks and
98 dependency chunks, and times out after 30 seconds. It never grants new terrain
generation. Final structure, protection, occupancy and journal checks still apply.

Actual rendered-camera capture includes the effective camera/projection and sends
bounded, session/generation/dimension-fenced telemetry. It is **advisory only** and
does not enable the original authoritative-camera or atomic-publication gates.

`-PgaslightPackaged` now freezes each runtime JAR under a SHA-256 directory before
launch. Rebuilding cannot overwrite an artifact being read by a running game.
The older Phase A evidence and limitations below describe the prior checkpoint;
Windows durability and repeated observer-free retirement have new evidence in the
recovery-preview report. Full-height natural seams and automatic publication remain
absent.

**Minecraft 1.21.1, Fabric, Java 25. Full GASLIGHT remains NO-GO.** This module is
separate from the preserved native 26.1.2 data/acquisition experiment. It does not
make houses or terrain disappear when you look away. Those automatic changes remain
disabled because their authenticated frame barrier and crash reconciliation are missing.

The genuine named development JAR was loaded directly in a graphical Windows client
and Linux/ext4 dedicated server. Fixed remote views, lighting, live block updates,
placement/mining through the link, walking/return, creative flight, dimension changes,
disconnect/reconnect and chunk unloading were exercised. See the evidence report for
the exact scope, known provider errors and performance sampling limits.

## Reproduce the isolated proof

Keep this module inside `ACTIVE_PROJECT/provider-adapters/fabric-1.21.1` or the
repository's corresponding `native-26.1.2/provider-adapters/fabric-1.21.1` directory.
Five existing pure GASLIGHT classes are compiled from the parent native source tree.
No HISTORY code or temporary Minecraft stubs are used.

```
python setup_deps.py
./gradlew --no-daemon --max-workers=2 clean build
python prepare_disposable_test.py 01
# Accept Minecraft's EULA for this new test environment if needed.
./gradlew --no-daemon --max-workers=2 runServerProof -PgaslightPackaged
./gradlew --no-daemon --max-workers=2 runClientProof -PgaslightPackaged
```

Use `gradlew.bat` on Windows. The older non-SQLite journal path still vetoes
Windows publication after directory-force failure. The explicit disposable-
world SQLite profile above has separate Windows process-halt evidence; it is
not certification for ordinary worlds or automatic gameplay. The earlier
mixed Windows/WSL proof used a loopback-only byte relay; its commands and
code remain in the evidence tools. No firewall or security settings were
changed by the agent.

`-PgaslightPackaged` removes loose production classes from the run classpath and loads
the actual named development JAR. Both logs print `GASLIGHT_CODE_SOURCE` for verification.
The integration driver is a separate test mod and is absent from the production JAR.
It generates exactly two 5x5 fixture windows and edits fixture floors/blocks only in
the new disposable world. Native generation has neighboring work beyond these 50
target chunks; the target count is not a proof of total generation cost.

After the client joins, the test driver's `probe-client.txt` accepts `resume`, `view-N`,
`place-N 1`, `mine-N 1`, `walk-N 35`, `fly-N 18`, `look 0 0`, `disconnect-N`, `connect-N`
and `quit-N`. Use unique command labels. The server's `probe-server.txt` accepts
`snapshot-N`, `update-N`, `pose .5 81 1.5 180 0`, `dimension-N`, and `stop-N`.
Fixture poses are explicit setup; only subsequent player-input crossings count as
portal traversal evidence. Do not run the test driver in any valuable world.

## Production adapter and safety limits

Without the exact disposable marker, creation is disabled. In a marked world, the
operator console has `gaslight-fixed status`, `gaslight-fixed retire`, and
`gaslight-fixed create x y z tx ty tz` (fixed 4x4 same-dimension link).
Creation/retirement require no connected observers. Unknown/unloaded chunk columns,
structure starts/references, protected columns, malformed policies, occupancy and
durability failures veto creation. The adapter does not generate or edit terrain.

This is one fixed pair made of four provider entities, with two owned radius-2
loaders (50 chunk references). Server indirect loading radius is capped at 2 while
this marked service is active. These limits do not bound all vanilla/provider work,
GPU allocations, bandwidth or client recursion. Do not treat the JVM heap ceiling
as a bound on all process memory.

The last player's disconnect retires the link and releases ownership. The journal
retains its intent because a vanilla save return value is not a durable entity-removal
receipt. A restarted dirty world is quarantined. **Never delete a journal to unlock it.**
Repeated safely reconciled publication in one saved world is not implemented.

Installable intermediary JARs and named development JARs are labeled separately.
The named JAR belongs in this Gradle development environment; it is not a normal
launcher mod JAR. Install Fabric API 0.109.0+1.21.1 and Immersive Portals 6.0.6 separately
for a normal modpack. Never add the extracted nested development dependencies there.

Fabric Loader 0.19.5, Fabric API 0.109.0+1.21.1, Loom 1.18.4, Gradle 9.7.0 and JDK 25
are pinned. Provider and nested dependency hashes are in `provider.lock.json`.
Forge, NeoForge, vanilla-client seamless rendering and Bedrock are not supported by
this prototype. Provider reuse does not supply GASLIGHT's missing safety/controller gates.
