# Minecraft 26.3 portal-engine source evaluation

Date: 2026-10-13. Source base: SeamlessPortals commit
0de56eb0b76690c8584b538d28f6b0143cad45f7 from
https://github.com/fmpapierz/SeamlessPortals, Apache-2.0. This exact commit is
named in the supplied GASLIGHT-26.3-Smoothing-v0.5.1 JAR's notice. The
user-supplied JAR contains additional GASLIGHT classes but no matching Java
source. Their bytecode is evidence, not an automatically publishable source
license or proof of the requested look-away behavior.

The source has Fabric, NeoForge, and opt-in Forge modules. They require
independent builds and game tests. On this Windows host, JDK 25.0.4.1
(Eclipse Adoptium), Gradle 9.7.0, Minecraft 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, and Fabric Loom 1.17.11 compiled
common tests and the Fabric build with exit 0. The working copy removes a
machine-specific Azul path and pins the Gradle wrapper to 9.7.0 with SHA-256.
The fresh upstream command and final post-fix command logs are retained in
[evidence](../evidence/engine-26.3-2026-10-13/).

The dedicated 26.3 Fabric server initialized the engine, created a
disposable world, saved, and shut down normally; the Gradle run exited 0.
This is server startup evidence only. The multiloader common module uses
in-tree compile stubs for some optional Fabric/IP interfaces; the Fabric
JAR itself includes the real Fabric API integration and did not package
those stub classes. Compilation is not a client test.

Graphical result: **FAIL / not certified.** Both entity-portal-on crossing
GameTest and entity-portal-off title-card GameTest terminated with Windows
access violation 0xC0000005 during client startup. An experiment that
temporarily skipped the shader source rewrite reached a disposable world once
but failed later with an unapplied IEParticle accessor mixin. The accessor
registration has been fixed in the working engine source; a following
diagnostic run again hit the native access violation. The temporary shader
skip was removed. No 26.3 crossing or visual pass is claimed.

The last observed native crash did not produce a Java crash report, and
Windows Error Reporting identifies only java.exe/an unknown fault module.
The available evidence does not isolate the fault to the JDK, graphics
driver, shader patch, or another injected native component. A separate
vanilla 26.3 control, alternate graphics driver/host, and repeated actual
portal visual tests are needed before using this engine as the gameplay
foundation.

The engine itself supplies real portal rendering and traversal code. It does
not supply GASLIGHT's whole-generation frame publication, strict one-hidden-
frame disappearance, safe natural full-height contour, or player-specific
adaptive world lifecycle. Those require implementation and live proof; a
successful engine compile cannot substitute for them.
