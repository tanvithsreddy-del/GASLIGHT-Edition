# Source-backed portal provider proof — Minecraft 26.3

Verdict: PASS for this provider's local baseline build and tested portal functions. NO-GO for complete GASLIGHT gameplay. This is a separate 26.3 experiment, not evidence of 26.1.2 or cross-loader compatibility.

Unmodified upstream source: https://github.com/CaseBacon/ImmersivePortalsMod/tree/c5a83c77cb493b4fbab2663a6d4f0de004895ea8
License: Apache-2.0. The provider development JAR is supplied separately from the native GASLIGHT JAR. It must not be described as a finished GASLIGHT mod. No provider production source was modified or copied into the native engine.

Toolchain: Minecraft 26.3, Java 25.0.4.1+1 (Adoptium), Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Loom 1.17.21, Gradle 9.6.0. Additional dependencies are pinned in upstream gradle.properties. Backend: actual OpenGL 3.3 driver context, LWJGL 3.4.3-snapshot; no Sodium or Iris loaded.

Commands (Windows, JAVA_HOME set to the installed JDK 25):

```
gradlew.bat --no-daemon --max-workers=2 build --stacktrace --console=plain
gradlew.bat --no-daemon --max-workers=2 runGametest runClientGametest --stacktrace --console=plain
```

Both commands exited 0. Build took 188.657 seconds including first Gradle download; game/client tests took 227.922 seconds. Reused the already accepted EULA from the disposable Phase 4B test environment for the provider's separate test-run directory. All created worlds were disposable; no source/player save was opened.

PASS: server GameTests, 13 required cases (12 provider cases and Minecraft's always_pass fixture). Includes portal save/load, dimension presence, mounted crossing, remote teleport request rejection and successful crossing.

PASS: graphical client portal view of a real destination room; gold center-pixel assertion at normal and near-plane camera positions; walking through to the Nether, return, same-dimension view, rolled portal, world cleanup. Dedicated-server client round-trip ran over a real network connection; its three screenshots are present.

PASS: graphical portal/direct-view comparison over nine camera poses without shaders. Observed mean channel error 0.00–0.21 out of 255, and bad-pixel fraction 0.0000. This covers the test scene and poses, not every terrain, GPU or camera mode.

PASS: provider remote loading test's own assertions. Observed 113 destination client chunks at two seconds and 289 at five seconds in the Overworld-side view, then 625 in the destination-side view after crossing. Sampled FPS was 29–52; this was an automated flat-world test, not a weak-hardware benchmark or a GASLIGHT performance claim. Bandwidth and full memory graphs were not measured. Provider defaults are too broad to inherit as GASLIGHT's strict budgets without further integration and measurement.

SKIPPED / UNVERIFIED: Iris shader test (Iris absent; warning is in the full client latest.log). No Vulkan, Sodium, shaderpack or other-loader proof. The Gradle success must not be counted as a shader compatibility pass.

Known provider limitations: one portal view layer, no nested views/mirrors in the active modern renderer; same-dimension portal views are unsupported with Sodium in this fork. The older stencil/framebuffer sources are quarantined, not restored by the successful build. A multi-generation GASLIGHT profile that needs recursive views must use a proven complete engine/version or explicitly redesign that view topology; do not silently hide missing nested views.

Next implementation dependency: adapter reuse can save a GPU renderer rewrite, but GASLIGHT still needs terrain/structure certification, real camera/frame publication fences, bounded provider chunk ownership, lifecycle/recovery and automatic invisible replacement. None is proven by this provider-only test. Fabric/NeoForge/Forge need separately built and tested adapters. An unmodified vanilla client cannot load this renderer.
