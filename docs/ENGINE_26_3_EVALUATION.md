# Minecraft 26.3 portal-engine source evaluation

Date: 2026-10-13. Source base: SeamlessPortals commit
0de56eb0b76690c8584b538d28f6b0143cad45f7 from
https://github.com/fmpapierz/SeamlessPortals, Apache-2.0. This exact commit is
named in the supplied GASLIGHT-26.3-Smoothing-v0.5.1 JAR's notice. The
user-supplied JAR contains additional GASLIGHT classes but no matching Java
source. Their bytecode is evidence, not an automatically publishable source
license or proof of the requested look-away behavior.
The [public 26.3 source fork](https://github.com/tanvithsreddy-del/GASLIGHT-Portal-Engine-26.3)
records its upstream base, build/accessor changes, and vanilla portal defaults. It excludes
disposable saves and the supplied binary's custom classes.
The vanilla-portal source and development JAR were published at commit
[`4bb83c3`](https://github.com/tanvithsreddy-del/GASLIGHT-Portal-Engine-26.3/commit/4bb83c3).

The source has Fabric, NeoForge, and opt-in Forge modules. They require
independent builds and game tests. On this Windows host, JDK 25.0.4.1
(Eclipse Adoptium), Gradle 9.7.0, Minecraft 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, and Fabric Loom 1.17.11 compiled
common tests and the Fabric build with exit 0. The working copy removes a
machine-specific Azul path and pins the Gradle wrapper to 9.7.0 with SHA-256.
The final common JUnit run reports 48 tests, zero failures, errors, or skips.
The fresh upstream command and final build logs are retained in
[evidence](../evidence/engine-26.3-2026-10-13/).

The dedicated 26.3 Fabric server initialized the engine, created a
disposable world, saved, reopened that same disposable world, and shut down
normally on both launches; both Gradle runs exited 0.
This is server startup evidence only. The multiloader common module uses
in-tree compile stubs for some optional Fabric/IP interfaces; the Fabric
JAR itself includes the real Fabric API integration and did not package
those stub classes. Compilation is not a client test.

Graphical result: **partial PASS, full engine FAIL.** Initial entity-portal-on
crossing and entity-portal-off title-card attempts hit Windows access violation
0xC0000005. An experiment that temporarily skipped shader rewriting reached a
world but exposed an unapplied IEParticle accessor; the accessor registration
was fixed and the temporary shader skip removed. With Sodium 0.9.2 at runtime,
the genuine crossing suite reached and passed multiple server and renderer
legs. Its first run stopped at a seam-registry assertion because the test
fixture forgot to turn on `passthroughExtras`; the fixture now explicitly
enables that switch only in its disposable run directory. The next run passed
the registry, fragment arithmetic/binding, disjoint and minecart legs, then
**failed** `RS-SEAM-CLIP`: the gold cell's section did not recompile within
200 ticks (`recompilesScheduled=0`). This is a real outstanding visual
failure; the full crossing suite is not green.

For the user's vanilla portal requirement, this fork defaults to
`preserveVanillaDimensionPortals=true`. It enforces vanilla Nether/End modes
even if an existing Immersive Portals config asks for `adaptive`/`normal`.
The one-time IP introduction, first-portal wiki chat tip, and automatic IP
update announcement have been removed; real compatibility and error warnings
remain. A separate **genuine graphical 26.3 client/integrated-server GameTest**
with Sodium and a disposable world loaded an old-style config requesting both
conversions but omitting the new guard. It verified six vanilla Nether portal
blocks, nine vanilla End portal blocks, zero converted portal entities, then
transported the player to the Nether and End through those blocks. The final
run ended `BUILD SUCCESSFUL`. An earlier travel attempt failed because its
fixture re-entered while Minecraft's normal 300-tick portal cooldown was
active; another run crashed natively. After the final UI-annotation-only
source change, the first repeat crashed natively before world entry, and the
second repeat again passed both dimension transfers with `BUILD SUCCESSFUL`.
All raw successes and failures remain in evidence.

Raw commands (PowerShell, Java 25) and logs:

```powershell
.\gradlew.bat :common:test :fabric:build --no-daemon --max-workers=2 --console=plain
.\gradlew.bat :fabric:runCrossingGametest -PsodiumRuntime=true --no-daemon --max-workers=2 --console=plain
.\gradlew.bat :fabric:runVanillaPortalGametest -PsodiumRuntime=true --no-daemon --max-workers=2 --console=plain
```

See `engine-crossing-sodium.log`, `engine-crossing-sodium-passthrough.log`,
`vanilla-portals-sodium-gametest.log`, both failed travel logs,
`vanilla-portals-travel-v3-sodium-gametest.log`, and the final failed and
passing repeats in [evidence](../evidence/engine-26.3-2026-10-13/).
The same folder includes the [text source diff](../evidence/engine-26.3-2026-10-13/vanilla-portals-source.patch)
and [artifact manifest](../evidence/engine-26.3-2026-10-13/vanilla-portals-artifact-manifest.json).

The genuine development JAR is
[seamlessportals-fabric-26.3-vanilla-portals-dev.jar](https://github.com/tanvithsreddy-del/GASLIGHT-Portal-Engine-26.3/blob/main/artifacts/development/seamlessportals-fabric-26.3-vanilla-portals-dev.jar),
SHA-256 `bf1fa5ca9982e5bd962ea81c3c02259c1e6ae124b5e4a084c6317dde09a01311`.
It is the portal-engine fork, mod id `seamlessportals` version `1.0.0`,
Minecraft `>=26.3`, 4,417,749 bytes, 1,331 ZIP entries. The packaged JAR
contains the vanilla portal GameTest and neither deleted intro class/mixin.
It is **not a GASLIGHT gameplay release**; the full crossing suite still fails.

The native crashes did not identify a reliable fault module. Their cause is
still unknown. The successful Sodium run is valuable gameplay evidence but
does not certify the vanilla-renderer path, eliminate intermittent crashes,
or resolve the seam-mesh failure. Repeatable visual tests on another graphics
driver/host remain necessary before this engine can serve as a release base.
The vanilla portal regression covers `entityPortals=true`, the shipped default.
The legacy `entityPortals=false` block-era path still intentionally intercepts
vanilla portal behavior; it is **not** a verified GASLIGHT configuration. A
bootstrap I/O failure can select that fallback, so the public engine JAR is
not yet a safe general-use GASLIGHT distribution.

The engine itself supplies real portal rendering and traversal code. It does
not supply GASLIGHT's whole-generation frame publication, strict one-hidden-
frame disappearance, safe natural full-height contour, or player-specific
adaptive world lifecycle. Those require implementation and live proof; a
successful engine compile cannot substitute for them.
