# GASLIGHT — Minecraft worlds that change out of sight

GASLIGHT's target is a natural, traversable world in which a place can become
unreachable after **one fully unseen rendered frame**. The player must never
watch terrain change. A house, a mined route, or a village may disappear from
the accessible scene, while its original saved blocks remain intact. Travel
must be continuous and bounded in memory and chunk work.

**Current result: an automatic development prototype; full GASLIGHT is NO-GO.**
The [Fabric 1.21.1 source](native-26.1.2/provider-adapters/fabric-1.21.1/) now
prepares actual private-world destinations, warms native render data and selects
hidden generations. A real graphical client/server demonstrated automatic commits
and input-driven crossing. Natural seams, continuous one-frame disappearance and
indefinite exploration remain incomplete. See the
[evidence report](docs/AUTOMATIC_GAMEPLAY_REPORT.md).

The [bundled development JAR](artifacts/automatic-gameplay/GASLIGHT-0.3.0-mc1.21.1-Fabric-DEVELOPMENT.jar)
contains the pinned portal engine, Fabric API and SQLite. For disposable testing:
install Fabric Loader 0.19.5 for Minecraft 1.21.1, use Java 25, put the JAR in
`mods`, select the vanilla renderer with render distance 2–4, and run
`/gaslight begin`. `/gaslight leave` returns to the saved location. One unmounted
player is supported. Do not install this candidate in a valuable world.

| Target | Actual status |
| --- | --- |
| 1.21.1 Fabric | Automatic development prototype; genuine build, hidden selections and crossing; full release FAIL |
| 26.1.2 Fabric | Genuine data/acquisition build; no seamless gameplay |
| 26.3 Fabric | Genuine vanilla Nether/End portal client test passes with Sodium; full crossing/render suite fails |
| NeoForge, Forge, Quilt, vanilla client, Bedrock | No GASLIGHT gameplay artifact verified |

Start with the [current evidence report](docs/AUTOMATIC_GAMEPLAY_REPORT.md), the
[exact observation rule](docs/STRICT_OBSERVATION_RULE.md), and the
[mathematical specification](docs/MATH.md). [Design and release gates](docs/DESIGN.md)
identify what remains. Raw commands, logs, screenshots, source diffs and
artifact hashes are under [evidence](evidence/). The 26.3 source evaluation is
[here](docs/ENGINE_26_3_EVALUATION.md). The separately attributed
[26.3 engine source experiment](https://github.com/tanvithsreddy-del/GASLIGHT-Portal-Engine-26.3)
contains its complete source and pinned build.

The core GASLIGHT code in this repository is MIT licensed. Third-party
dependencies keep their own licenses; see
[notices](native-26.1.2/provider-adapters/fabric-1.21.1/THIRD_PARTY_NOTICES.md).
WorldEdit is optional preparation tooling, not a runtime dependency. All game
tests use disposable worlds. No two-million-assertion offline result is treated
as proof of the requested Minecraft experience.
