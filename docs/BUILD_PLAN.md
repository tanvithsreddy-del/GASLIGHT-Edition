# GASLIGHT: four implementation phases

This new track follows the user's authorization to restructure and reuse open-source implementations. A–D distinguish it from the earlier native Phases 1–4B. No earlier unfinished gate is marked passed by this plan.

## A — Integrate a real portal engine

Pin one Minecraft version and one source-backed provider that can support the required topology. Fabric is the first adapter; keep geometry and policy independent of loader APIs. Reuse rendering, remote-world presentation, traversal, collision and interaction rather than implement those engines again. Preserve the native 26.1.2 implementation and its tests as a checkpoint. Exercise a fixed portal in disposable worlds, including remote blocks, lighting, interaction, walking, creative flight, destination availability, disconnect and cleanup. Keep physical safety gates intact.

Exit: genuinely compiled GASLIGHT adapter and repeatable graphical client/server evidence for this fixed-portal scope. A provider-only success does not satisfy this exit gate. The tested 26.3 fork has a one-layer view limit and cannot automatically be selected for a topology needing recursion. Use a proven compatible provider/version if that limit matters; do not silently begin a renderer port.

## B — Implement the actual look-away illusion

Integrate real camera/frame evidence, fenced publication, prepared bounded destinations, full-height finite portal facets, seam matching and source/destination structure buffers. Begin with one coherent contour and a shared rigid transformation across its facets. Avoid terrain edits in the first complete behavior; reject bad seams. Original blocks remain preserved while spatial access changes.

Exit: forest-to-different-terrain, house-out-of-view and mining-corridor scenarios demonstrated on a real client. Camera flicks, F5, zoom, underground and creative-flight tests must show no observed transition. Missing destination data or unknown safety evidence must defer activation. Claims apply only to the tested camera/mod/network profile.

## C — Make repeated gameplay safe and efficient

Implement safe replacement and retirement, traversal occupancy checks, bounded provider chunk ownership, failure cancellation, reconnect/world-change cleanup and recovery of outstanding publication intents. Preserve unresolved journals and truthful Windows durability limitations. Measure actual FPS/TPS, loading latency, heap/remote cache, bandwidth and owned-ticket cleanup. Retain all prior regressions and mutation probes; add targeted adversarial and crash-injection tests. Never overwrite original saved worlds.

Exit: repeatable complete Fabric gameplay in disposable worlds with no known safety veto or unexplained resource growth. This is the first gate for the full GASLIGHT gameplay claim. Report actual failures and unverified limits; do not assign a reassuring percentage from model assertions.

## D — Port and polish the verified experience

Add and genuinely test NeoForge and Forge adapters on individually pinned supported provider/game versions. Keep the shared controller and safety rules unchanged unless a documented platform difference requires an adapter contract change. A vanilla-client/server-plugin edition is a separately described reduced experience: an unmodified client cannot run the portal renderer or provide the same authenticated camera/frame guarantee. Do not bypass that guarantee to claim equivalence. Bedrock/Pocket remain deferred.

Exit: a compatibility table backed by per-loader builds and real gameplay evidence, documented installation, source/licenses/notices, genuine development artifacts and measured performance on the supported profiles. Unsupported versions remain explicitly unsupported.

## Effort and rigor

Reduce implementation effort through provider reuse, a shared Java core, a small prepared destination pool, one initial contour and minimal optional editing. WorldEdit remains preparation tooling if later seam evidence justifies it. It is not required for the first working experience.

Mathematical properties need explicit assumptions and bounded checks. Minecraft behavior needs genuine client/server evidence. Performance needs measurements. These three forms of evidence are reported separately. Every phase ends with PASS/FAIL/BLOCKED, raw commands/logs, relevant screenshots, modified source, regressions and remaining limitations.

Four phases are an organizational plan, not a guaranteed duration. First prove the complete Fabric experience; broad compatibility cannot be honestly obtained by relabeling that JAR.
