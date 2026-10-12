# GASLIGHT Phase 3: destination selection and validation update

**Target:** Minecraft Java 26.1.2 / Fabric  
**Date:** 2026-10-12  
**Phase:** 3 only, Phase 4 untouched  
**Result:** Offline destination planner and strict Minecraft-host sampling implemented; source validated in isolated JVM tests. Game compilation and visual seam certification NOT achieved.

## Implemented in this update

1. `Phase3DestinationSelector`: deterministic, bounded 1–8 search candidates, vetted catalog entries prioritized; duplicates, near-zero offsets, overflow and out-of-border endpoints excluded. Fallback pseudo-random offsets are candidates, not claims of remotely loaded chunks.
2. `Phase3DestinationProfile`: immutable nine-column 3×3 fingerprints with integer terrain height, block category, unknown-block identity, biome family, water/lava status, water level and two-block headroom. All samples are mandatory. Hard veto on missing data, fluid discontinuity, lava, major height/slope mismatch, water surface divergence and unknown block mismatch. A weighted score ranks surviving candidates.
3. `GaslightNativeMod.VanillaHost`: a real-source implementation that samples *already loaded* blocks and biome keys around player elevation; it requests no world generation and makes no block changes. It requires detailed profiles for Phase 3, so absence or a decode/lookup error is a rejection, not a height-only fallback.
4. `Phase3Engine`: all relevant route chunk pairs are inspected with memoization and a strict maximum of 128 distinct profile snapshots per planning epoch. Candidate selection uses the new scorer. The chosen route is **rechecked with a fresh profile cache** immediately before any attempted publication, followed by structure checks, recovery journaling and the pre-existing atomic stage contract.
5. Tests: randomized selection and border properties, nine-column snapshot completeness, malformed/missing samples, biome/material/fluid/headroom/slope mismatch cases, catalog preference and duplicate elimination, source/destination selection, unknown structure veto and a simulated destination mutation between initial selection and commit.

## Evidence (source-only)

| Command/check | Observed result |
|---|---|
| `bash run_offline_checks.sh` | PASS: 554,621 assertions across seven suites |
| `bash scripts/check_adapter_syntax.sh` | PASS: syntax against 24 temporary fabricated API shapes only |
| `bash scripts/phase3_gate.sh` | Expected NO-GO (exit 42), real release veto remains active |
| Genuine Fabric Java 25 compile | NOT RUN, Java 25/dependency toolchain unavailable |
| Actual Minecraft biome/block sampling | NOT GAME TESTED |
| Live chunks generation-free verified prefetch | NOT IMPLEMENTED; unprepared locations reject |
| Exact whole-height seam-block equivalence | NOT IMPLEMENTED; 3×3 terrain fingerprint is a conservative approximate filter |
| Actual GPU portal, camera synchronization and collision | NOT IMPLEMENTED / NOT VERIFIED |

The new selector is a genuine engineering improvement, but it **cannot certify that two arbitrary distant environments are visually or physically continuous**. Matching sampled surfaces is not sufficient for Minecraft's 3D fluids, sky light, structures, caves, entities and block interactions. Existing structure buffer checks do not detect every user-built structure without the explicit protection file. Even a very high sample similarity does not waive any safety/visibility/publisher veto.

**Release remains NO-GO. Phase 4 was not started.**
