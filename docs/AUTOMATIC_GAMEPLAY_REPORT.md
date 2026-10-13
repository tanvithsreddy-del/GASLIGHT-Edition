# GASLIGHT automatic gameplay — 2026-10-13

**Full requested mod: NO-GO / FAIL. Development artifact: genuinely compiled,
bundled and tested, with real automatic selections and a real crossing.**
The complete one-unseen-frame, natural, continuously traversable experience has
not been delivered. No completion percentage or release-ready label is assigned.

Required-report decision: **STOP_MAJOR_WORK_REQUIRED for full acceptance**.
This is an implementation checkpoint under the user's broader authorization,
not a request for another permission or a completed full-release gate.

The latest human instructions authorized implementation and restructuring. They
supersede the earlier 12-fix/200-line, testing-only and individual phase stops.
The safety requirements remain. This is substantial authorized implementation,
not a claim that the new source consists only of minor mechanical fixes.

## Source and artifact

Authoritative implementation: `native-26.1.2/provider-adapters/fabric-1.21.1/`
in this repository, using the existing GASLIGHT geometry/structure/journal source.
No HISTORY snapshot was merged. The native 26.1.2 backend remains separate.

Original supplied handoff SHA-256:
`cb98808b017168257fc31343d4913510a321a222cae272335b871cb04f4a14c5`.
Supplied 26.3 smoothing reference JAR SHA-256:
`fbd57d46d9514aa13f8601c1804d09124120d25c0261c36cbb07e4538a0651ca`.
The latter is a reference, not the delivered 1.21.1 binary.

Windows toolchain: Temurin `25.0.4.101`, Gradle `9.7.0`, Loom `1.18.4`,
Minecraft `1.21.1`, Fabric Loader `0.19.5`, Fabric API `0.109.0+1.21.1`,
Immersive Portals `6.0.6`, SQLite JDBC `3.53.4.0`.
Linux journal test runtime: Temurin `25.0.4.1+1`, Ubuntu WSL, genuine ext4
temporary fixtures. Exact library/binary hashes are in the evidence JSON.

[Bundled production-namespace development JAR](../artifacts/automatic-gameplay/GASLIGHT-0.3.0-mc1.21.1-Fabric-DEVELOPMENT.jar):
SHA-256 `dd2bdfbd813ebb14d18650a0f1341d7b2e54ab4b9b0b5106d59e32f8eebc90a4`.
It embeds unchanged IP, Fabric API and SQLite dependencies, retaining their
metadata/licenses. It still requires Fabric Loader, Minecraft 1.21.1 and Java 25.
The named development and source JARs are in the same artifact directory.

The separate clean working directory rebuilt the same JAR byte for byte.
JAR inspection verifies metadata, private dimension, production classes, mixins
and nested dependencies; no Minecraft API stub or integration-driver class is
embedded. The final rebuild changes notices only relative to the production
artifact used in the graphical run; runtime class behavior is unchanged.

## Evidence results

| Gate | Actual result and limit |
| --- | --- |
| Genuine production/integration compilation | PASS; clean builds, all module source sets, real MC/Fabric dependencies |
| Separate clean-directory rebuild | PASS; identical final SHA-256 |
| Original offline regression suites | PASS; ten baseline plus recovery, remote data and lease model suites |
| Six mutation probes | PASS; each expected assertion detected, including journal CAS; raw per-probe logs retained |
| STUB ONLY syntax check | FAIL; stale fabricated 26.1.2 API coverage produces missing-type errors. Not a genuine build failure or gameplay proof |
| Windows module unit tests | PASS; 26 tests, no skipped/failing cases |
| Linux module unit tests | PASS; all 26 tests with genuine dependencies/JNI, ext4 files and process halts; no graphical Linux test |
| Dedicated server | PASS; normal Fabric launcher loads only the bundled GASLIGHT distribution and recognizes its nested dependencies |
| Disposable world save/reopen | PASS; shutdown/save, restart and normal client connection recorded |
| Graphical automatic selection | PASS for tested instances; checksum, native compilation, server arm, excluded frame, ack |
| Input-driven automatic portal crossing | PASS for an observed instance; matching server/client coordinates after native input |
| Natural seam / unrestricted exploration | FAIL; copied strip can produce a wall, finite ends remain, long-run history is bounded |
| Continuous every-unseen-frame behavior | FAIL; preparation, protection vetoes, visible retiring apertures and occupancy can prevent selection |
| Original return location | PASS; `/gaslight leave` restored `[-8.5,-60,-7.5]` in the disposable Overworld |
| Vanilla Nether and End portals | PASS; real transitions to `minecraft:the_nether` and `minecraft:the_end`, graphical evidence |
| Safety/structures | PASS for observed vetoes; actual buried mineshaft/trial-chamber references logged, not ignored |
| Owned cleanup | PASS for recorded disconnect/leave states; zero GASLIGHT entities/tickets/intents in active service, retained DB evidence |
| Full heap/GPU/native holder leak certification | NOT RUN; an admission limit and observed cleanup are not exhaustive leak proof |
| Multiplayer, shaders, Forge/NeoForge, vanilla client, Bedrock | NOT RUN / NOT IMPLEMENTED |

All Minecraft tests used specifically created disposable runtime directories.
Nether/End blocks were test fixtures in that disposable world. No valuable
Minecraft world was opened or changed. The runtime does not copy or erase source
terrain; it edits unused reservations in its private realm. Original-world save
integrity over every possible player action is not claimed from these tests.

## Concrete observations and performance

`automatic-server-12.log` and its graphical client log record generations 46 and
51 with 265 and 251 compiled sections and 11.86/23.10 ms commit acknowledgements.
Those old log labels say "meshes"; they count compiled sections including empty
ones, not that many nonempty GPU meshes. The corrected logger separates geometry.

`automatic-server-13.log` records generation 59 at frame 3410 and generation 66 at
frame 15673. Native input then changed the player from the original realm site
to `(8704.699999988079,100,16640.5)` on both client and server. There was no test
teleport command in that crossing. The player stopped against destination terrain;
this is evidence of real collision and of the incomplete seam, not free exploration.

The normal remapped distribution run (`distribution-server-02.log`) records
generation 2 with every selection condition explicitly true, server arm and
client acknowledgement. Its client compiled 289 sections, 265 containing geometry.
Generation 3 compiled 224 sections, 215 containing geometry. Acknowledgements took
about 11.91 and 31.71 ms. Readiness after those offers took about 30.40 and 34.58 s
in the recorded waiting/revalidation path; the early isolated warm observations
took 2.0–2.95 s. These are observations, not a statistically isolated benchmark.

At a successful earlier commit the real encoded/compressed wire meter recorded
2,231,516 outbound and 58,189 inbound bytes since session start. A longer session
ended at 8,215,189 / 570,943 bytes. These include ordinary traffic, not just remote
terrain. FPS was commonly 59–60 with a 60 FPS cap. Server mean tick measurements
were commonly a few milliseconds, but real generation produced multi-second
stalls (including a roughly 26.5 s observation under the earlier retry behavior).
Observed server heap reached roughly 989 MB with a 1 GiB heap. These observations
do not certify weak hardware, minimum FPS or a hard bound on native allocations.

## Fixed defects

The source diffs and file-by-file count are in the evidence directory. Major new
classes implement actual private-realm preparation, transport integration, fence,
portal lifecycle, raw private-state copying and traffic budgets. Mechanical fixes
include:

The source audit records 41 changed source/document/build files, 22 production
files, 915 added and 9 removed production lines (906 net, 924 changed). The
earlier small-fix cap was superseded by the later implementation authorization.
These counts are not described as minor-only work. Exact per-file values are in
`edit-counts.json` and `file-diffs.tsv`.

1. Windows SQLite extended-length URI handling, preserving native VFS locking,
   FULL synchronization and integrity checks. A deep-space/Unicode test reopens
   and recovers a real unresolved record.
2. Structure halo ownership expanded to the required fourth chunk offset; an
   exhaustive offset/axis footprint test covers it.
3. FULL/ticking distinction and placement callback restart: physics was changing
   copied gravel. Native preparation plus surveyed-state copying fixes the proven
   checksum failure without fabricating empty terrain.
4. Client failure shutdown deferred out of the world-render callback; the original
   path caused a real `gameMode` null crash. Raw crash evidence is retained.
5. Disconnect cleanup moved onto the server thread; the original Netty callback
   produced `server thread required` errors.
6. Retained native section objects after same-dimension travel are trimmed against
   the current required set. A real `1018/255` readiness anomaly is preserved in logs.
7. Shared EMPTY compilation identity is handled by the actual native synchronous
   compiler, within the two-section-per-frame budget.
8. Client movement/interaction hold, explicit server arm, local retiring-aperture
   occupancy check and fresh readiness invalidation prevent premature selection.
9. Unselected cancellation preserves the active view; generation 73 cancellation
   and acknowledgement are recorded. Already-selected failures retain intents.
10. Original return anchors persist with checksums; corrupt anchors refuse entry.
11. Retry/memory/wire budgets stop uncontrolled preparation and close whole
    connections on traffic exhaustion.
12. Reservations reject saved/already-loaded FULL terrain before copying; source
    movement or approaching the reservation cancels/refuses work conservatively.

No generation, data, structure, occupancy or recovery gate was bypassed to obtain
the successful examples. Several earlier failures and test-driver mistakes remain
in the raw logs rather than being removed from the evidence.

## Full-release blockers and next work

This is not a fully verified release candidate. The important remaining work is
the coherent, natural contour/lifecycle architecture, not another confirmation
that the fixed provider can render blocks. One flat 64-block aperture with a
two-column strip cannot satisfy all unseen regions, seamless terrain/caves,
arbitrary creative movement and perpetual retirement. Preparing one new site at
a bounded rate also cannot guarantee a new scene for every camera flick.

Full scope remains authorized by the user; no extra implementation permission is
being requested. The missing implementation must be completed and genuinely
tested before calling the full mod finished or porting a passed experience to
other loaders. The current artifact is a development checkpoint for inspection
and disposable testing. See the separate technical reference for equations,
assumptions, budgets and the limitations of the evidence.

## Raw reproduction

The evidence directory contains untouched build/runtime logs, Windows/Linux JUnit
XML, mutation outputs, STUB ONLY errors, client crash reports, native screenshots,
commands in `automatic-controls.jsonl` / `distribution-commands.jsonl`, and hashes.
Typical commands use Java 25 and `--no-daemon --max-workers=2 --console=plain`:

```text
python setup_deps.py
gradlew.bat clean build
gradlew.bat runServerProof -PgaslightAutomatic -PgaslightProbe=automatic -PgaslightPackaged
gradlew.bat runClientProof -PgaslightAutomatic -PgaslightPackaged -PgaslightEvidence
java -Xmx1024m --enable-native-access=ALL-UNNAMED -jar fabric-server.jar nogui
bash run_offline_checks.sh
GASLIGHT_BASH=<Git Bash> GASLIGHT_MUTATION_LOG_DIR=<raw logs> python scripts/phase4_mutation_probe.py
bash scripts/check_adapter_syntax.sh    # STUB ONLY; failed as recorded
```

The distribution server uses the ordinary Fabric 1.21.1/Loader 0.19.5/Installer
1.1.2 launcher and only the bundled production JAR in `mods`. RCON is enabled only
on loopback in the disposable test fixture. EULA acceptance was copied from the
already accepted test environment. No game binary/assets or worlds are distributed.
