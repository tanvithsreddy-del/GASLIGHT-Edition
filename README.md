# GASLIGHT — Minecraft worlds that change out of sight

GASLIGHT's target is a natural, traversable world in which a place can become
unreachable after **one fully unseen rendered frame**. The player must never
watch terrain change. A house, a mined route, or a village may disappear from
the accessible scene, while its original saved blocks remain intact. Travel
must be continuous and bounded in memory and chunk work.

**Current result: development preview, not the finished gameplay mod.** The
[Fabric 1.21.1 source](native-26.1.2/provider-adapters/fabric-1.21.1/)
has a genuinely compiled and client/server-tested *fixed portal* demonstration.
It renders a remote destination, supports block interaction and input-driven
crossing, and cleans up owned chunks in the tested disposable fixture. It does
**not** automatically change the world when you look away. Do not install the
[game-tested development JAR](artifacts/recovery-preview/gaslight-provider-fabric-1.21.1-0.2.0-recovery-preview-dev.jar)
in a valuable world.

| Target | Actual status |
| --- | --- |
| 1.21.1 Fabric | Fixed-link development preview, genuine client/server evidence |
| 26.1.2 Fabric | Genuine data/acquisition build; no seamless gameplay |
| 26.3 Fabric | Portal engine source compiles; this machine's graphical test currently crashes |
| NeoForge, Forge, Quilt, vanilla client, Bedrock | No GASLIGHT gameplay artifact verified |

Start with the [current evidence report](docs/RECOVERY_PREVIEW_REPORT.md), the
[exact observation rule](docs/STRICT_OBSERVATION_RULE.md), and the
[mathematical specification](docs/MATH.md). [Design and release gates](docs/DESIGN.md)
identify what remains. Raw commands, logs, screenshots, source diffs and
artifact hashes are under [evidence](evidence/). The 26.3 source evaluation is
[here](docs/ENGINE_26_3_EVALUATION.md); its independently attributed engine
source is linked there when published.

The core GASLIGHT code in this repository is MIT licensed. Third-party
dependencies keep their own licenses; see
[notices](native-26.1.2/provider-adapters/fabric-1.21.1/THIRD_PARTY_NOTICES.md).
WorldEdit is optional preparation tooling, not a runtime dependency. All game
tests use disposable worlds. No two-million-assertion offline result is treated
as proof of the requested Minecraft experience.
