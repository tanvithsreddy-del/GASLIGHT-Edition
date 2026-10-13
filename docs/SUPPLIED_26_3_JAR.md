# Supplied 26.3 seam JAR: older-version integration result

**Verdict: NOT INTEGRATED. No older-version development JAR was produced.** The
supplied file is a complete Minecraft 26.3 Fabric mod, including a modified
Immersive Portals-derived engine. It is useful source material for a port, but
its GASLIGHT-specific controller does not implement the requested observation
rule or the existing project's publication safety contract.

## Exact input and checks

- Input: `C:\Users\Tanvith\Downloads\GASLIGHT-26.3-Smoothing-v0.5.1.jar`
- SHA-256: `fbd57d46d9514aa13f8601c1804d09124120d25c0261c36cbb07e4538a0651ca`
- Size: 6,956,945 bytes; ZIP integrity check: no bad entry.
- Contains 1,147 `.class` files, including 11 under `dev.kondalabs`; no Java
  sources. `fabric.mod.json` identifies `seamlessportals` 0.5.1-smoothing,
  requires Minecraft `>=26.3`, Fabric Loader `>=0.19.5`, and Fabric API and
  Cloth Config. The manifest declares Minecraft 26.3 and official mappings.
- Its notice identifies upstream `fmpapierz/SeamlessPortals` commit
  `0de56eb0b76690c8584b538d28f6b0143cad45f7` (the remote
  `mc-26.3-main` head when checked). The supplied binary's engine source was
  not independently reconstructed or rebuilt here.
- Target available locally: `ACTIVE_PROJECT/provider-adapters/fabric-1.21.1`.
  It compiles against Minecraft 1.21.1 and Immersive Portals 6.0.6.
  That provider lacks the supplied JAR's `SeamClipRenderer` and
  `SeamlessPortalsConfig` classes. The two JARs also have different mod IDs.

## What the supplied controller actually does

The bytecode was inspected with Java 25 `javap -c -p`; the custom classes were
also decompiled for navigation. `RealSeamMod` registers operator commands
`/pl` and `/pc`. Those commands choose a distant same-dimension location,
create four global tracked portals per seam segment, and retire the session
after 200 server ticks. The line window is 512 blocks wide; the circle is a
fixed radius-5 block contour. The destination search tries at most two random
locations with X/Z offsets up to 500,000 chunks each and a combined Manhattan
distance of at least 20,000 chunks, then calls world height and
block-state methods. There is no explicit saved-chunk-only/generation budget,
structure/protected-region audit, durable publication intent or client frame
acknowledgement in this controller. A random point's world-border validity is
checked, but full seam/loader footprints are not certified.

`GaslightSeamClient` checks portal geometry and player section position every
four client ticks; when either changes, it schedules recompilation of nearby
render sections. Its inspected code has no camera-visibility decision or
one-rendered-frame publication fence. Timer-based retirement could remove a
seam while it is visible. Restart cleanup scans portal tags, but this is not
equivalent to a crash-safe journal plus nonpersistent owned entities.

The supplier's modified engine may provide valuable seamless rendering and
traversal in **26.3**. These static checks do not certify its runtime quality,
safe general use, or older-version operation. No game launch of this supplied
JAR was performed, because the decisive older-version metadata/dependency and
publication gaps were already established.

## Integration decision

Directly copying this JAR into the 1.21.1 modpack would fail its Minecraft
version requirement. Removing the requirement would leave incompatible
Minecraft/Fabric/engine APIs. Porting the 26.3 renderer and its many mixins
would be a substantial engine migration; the older project already has a
working 1.21.1 portal provider. Porting only `/pl`/`/pc` would produce a
manual, timer-limited seam that bypasses the older project's fail-closed
acquisition, structure, observer, ownership and recovery gates. It would also
still fail the user's constant look-away gameplay requirement.

The shortest defensible route is to adapt only the relevant geometry and
renderer ideas to the existing 1.21.1 provider **after** destination readiness
and an atomic rendered-frame/server publication barrier are implemented and
tested. Full-height natural seams, timely offscreen replacement, generation
budgets, protected-region rejection and crash reconciliation remain real work.
This supplied binary lowers research effort but does not make the full mod a
low-effort plug-in operation.

No production sources, safety defaults or worlds were changed. No development
JAR was built from this input. The previous fixed-portal preview remains the
last genuine older-version executable artifact; its automatic GASLIGHT verdict
remains NO-GO.

Raw evidence: `evidence/archive-audit.json`, `evidence/fabric.mod.json`,
`evidence/META-INF-MANIFEST.MF`, `evidence/GASLIGHT-NOTICE.txt`,
`evidence/RealSeamMod.javap.txt` and `evidence/GaslightSeamClient.javap.txt`.
