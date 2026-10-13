# Latest GASLIGHT observation requirement

The user's 2026-10-13 clarification requires that even one rendered frame fully
out of sight suffice for disappearance. No grace period or movement trigger.
Visible terrain must remain stable. Looking back should reveal different natural,
traversable terrain, with continuous exploration and bounded resources.

This supersedes older gameplay descriptions that treated deferred replacement
as successful behaviour. Safety vetoes still apply: an unsafe/unready change must
not happen, and that missed change counts as a failed gameplay requirement.

For a generation G, readiness must include server ownership, destination blocks,
block entities, lighting, client cache, uploaded meshes, coherent portal surfaces,
proper rigid mapping and recoverable intent. A camera observation of frame f must
cover all affected rendered views, not merely player yaw or an old occlusion query.
Publication must atomically select the complete generation between frames while
keeping collision, interaction and stale-packet handling coherent.

The recovery preview does not implement that transaction. Its camera telemetry is
advisory. Its 4x4 fixed fixture is not proof of a full-height natural contour. See
RECOVERY_PREVIEW_REPORT.md for actual passing components and unmet gates.
