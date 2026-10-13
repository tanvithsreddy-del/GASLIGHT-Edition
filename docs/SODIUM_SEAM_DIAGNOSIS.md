# Sodium seam failure: source-backed diagnosis, 2026-10-13

**Verdict: FAIL for fractional portal seams with Sodium 0.9.2 on Minecraft 26.3.** This is a missing renderer compatibility path, not a delayed redraw. It does not certify GASLIGHT gameplay or a public release.

The genuine graphical crossing test reached the seam pixel gate and reported `recompilesScheduled=0` after 200 ticks. The source explains why: `SeamClipRenderer.active()` explicitly returns false whenever the active Sodium invoker is present, logging `sodium present — seam clip self-gated OFF (no meshing hook)`. The vanilla seam implementation removes qualifying blocks from a section mesh, then draws the owned half dynamically. Sodium uses a different section mesher, and this fork has no equivalent exclusion hook. The fractional state/collision feature is enabled by default (`SeamFractional.CUT_IMPLEMENTED=true`), so a whole rendered cube can disagree with its half-block behavior. The original test's “section never recompiled” message described the symptom but misidentified the cause.

I changed only the crossing test to fail immediately with `UNSUPPORTED SODIUM SEAM RENDERING` when Sodium is installed and the required clip is self-gated. This preserves the failure gate; it does not skip the test or enable a renderer with missing mesh data. The [guard source](https://github.com/tanvithsreddy-del/GASLIGHT-Portal-Engine-26.3/blob/main/fabric/src/main/java/com/warwa/seamlessportals/fabric/gametest/CrossingSmoke.java#L10815-L10823) is in the portal-engine repository. The unchanged renderer remains off under Sodium.

An isolated development experiment added a Sodium section-build snapshot, fluid preservation, lifecycle dirtying and a mesh-completion callback. Java 25 compiled it and the 48 common JUnit tests passed, but four ordinary graphical startup attempts returned Windows `0xC0000005` before the test world loaded. A C1-only run also crashed. The unchanged baseline loaded the world and reproduced the original seam gate failure. Diagnostic variants with the new mixins inactive, the snapshot skipped, or capture-only each reached a world; a variant carrying the snapshot while suppressing only its air substitution crashed. One capture-only run logged `thread=Render thread clientThread=true`. These comparisons narrow the area to the snapshot handoff or a correlated native startup fault; they do **not** identify an exact native faulting instruction or prove that a specific Java line caused the crash. Windows Application Error events for the new attempts reported `java.exe`, `0xc0000005`, and faulting module `unknown`. The experiment was removed from production source and retained only as a labeled, unverified patch.

The source correction built with `BUILD SUCCESSFUL` and 48/48 common JUnit tests passing. Its first genuine graphical rerun crashed natively before world entry. The next rerun reached the seam gate and failed with the intended explicit `UNSUPPORTED SODIUM SEAM RENDERING` assertion after earlier crossing legs passed. The full game suite remains **FAIL**. A build success cannot override that game gate.

Reproduction and build commands, from the 26.3 portal-engine checkout, with `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`:

```powershell
.\gradlew.bat :common:test :fabric:build --no-daemon --max-workers=2 --console=plain
.\gradlew.bat :fabric:runCrossingGametest -PsodiumRuntime=true --no-daemon --max-workers=2 --console=plain
```

Raw [baseline, failed experiments, differential runs, build logs and rejected source patch](../evidence/engine-26.3-2026-10-13/seam-diagnosis/) are retained. The patch is **not** a fix to apply or a release artifact. Tests used the client's disposable GameTest world; no original Minecraft world was modified.

Completion requires a correctly fenced Sodium section capture and meshing exclusion, preservation of fluids and neighboring faces, safe section invalidation and ownership cleanup, and pixel-level crossing/reconnect tests with the actual 26.3 client. Native startup stability must also be established. Separately, GASLIGHT still needs its hidden-frame world-change/publication system; this seam work cannot make the full mod functional on its own.
