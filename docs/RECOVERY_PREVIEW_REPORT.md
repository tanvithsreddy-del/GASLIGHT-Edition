# GASLIGHT recovery preview — execution report

Date: 2026-10-13. **Overall verdict: NO-GO for the requested automatic GASLIGHT experience.**
Decision: `STOP_MAJOR_WORK_REQUIRED` at the physical-publication acceptance gate.
Implementation work is already authorized; no additional permission is requested.
This package is a genuine development preview, not completion of all phases.

## Result

| Component | Verdict | Actual evidence |
|---|---|---|
| Genuine Fabric build | PASS | Minecraft 1.21.1, Fabric, JDK25; production, integration and test source sets compile. No temporary Minecraft API stubs. |
| Existing regressions | PASS | All thirteen standalone suites; six safety mutation probes caught. These do not prove Minecraft functionality. |
| New unit/adversarial tests | PASS on Windows and Linux | Sixteen tests with zero failures/errors/skips on both platforms: native SQLite durability, repeated cycles, concurrent/stale writers, corrupt/partial records, disk-page budget, three real child-process halts, clip geometry, session/generation/dimension checks, flood limits and malformed packet decoding. XML files give exact counts. |
| Windows graphical/server startup | PASS for fixed fixture | Actual packaged development JAR loaded by both game processes. Remote textured blocks, light level14, block placement/mining and input-driven walking crossing observed. |
| Saved-but-unloaded acquisition | PASS in disposable fixture | A restarted server begins with remote chunk(64,64) unavailable. It scans 98 real loading dependencies, acquires 50 target tickets and reaches FULL readiness in approximately3.35–5.51seconds across observed runs. |
| Retirement/reacquisition/reconnect | PASS in tested fixture | Four entities/two loaders→zero; remote FULL chunks/provider tickets subsequently reach zero. Reacquisition produces a new owner/generation and reconnect sees the actual destination again. |
| Actual rendered camera | PASS for tested capture | Visible plane→excluded=false; turn away→true; third-person front camera→false. Server receives bounded camera observations from the real client. |
| Crash recovery | PASS for tested logical ownership | A real dedicated server was forcibly halted after saving an active disposable world. OPEN intent survived. Restart recovered to IDLE with no portals/loaders/tickets. Payloads/digests remained in retained RECOVERED history. New entities use a nonpersistent GASLIGHT type. |
| Missing saved destination | PASS: fail closed | At four million blocks, absent data is rejected before taking tickets or recording a publication intent. Unknown terrain is never converted into empty terrain. |
| Cancellation during native loading | NOT CERTIFIED | One cancellation attempt arrived after preparation had already failed; another missed the loading window. Neither counts as passing cancellation evidence. |
| Controlled new terrain generation | NOT IMPLEMENTED in this adapter | Saved-only acquisition refuses unavailable destinations. The preserved native26.1.2 experiment remains separately labeled; its generation gaps are unchanged. |
| Automatic one-hidden-frame disappearance | FAIL / unimplemented | Looking away and back still reveals the same diamond fixture. Camera telemetry is advisory, not an authenticated whole-generation publication barrier. |
| Natural full-height seams/indefinite exploration | NOT IMPLEMENTED | A4×4 fixed fixture does not prove forests, houses, caves, villages, a closed contour or bedrock-to-build-limit continuity. |
| Forge/NeoForge/vanilla/Bedrock | NOT RUN | Only the specified Fabric profile has local game evidence. |

## Source and versions

Authoritative source:
`work/handoff/GASLIGHT_CODEX_HANDOFF/ACTIVE_PROJECT/provider-adapters/fabric-1.21.1`.
Five correct pure Java classes are reused from ACTIVE_PROJECT's parent source.
HISTORY was not merged. The native26.1.2 implementation was not rewritten.

Minecraft1.21.1; Fabric Loader0.19.5; Fabric API0.109.0+1.21.1;
Immersive Portals6.0.6; Loom1.18.4; Gradle9.7.0; SQLite JDBC3.53.4.0;
Minecraft-supplied JOML1.10.5. Windows Temurin25.0.4.1+1-LTS;
Linux Temurin25+36-LTS. This deliberately separate profile does not claim26.1.2 compatibility.

Exact commands, exit codes and durations are in evidence/commands.jsonl and the raw
logs. JAR contents, metadata, bytecode versions and hashes are in jar-verification.json.
The named development JAR is the game-tested format. The remapped normal-loader
JAR is compiled/inspected; a separate normal-launcher graphical run is NOT RUN.

Final runtime development JAR SHA-256:
`3b2f88f459fd05210bba1832b9f5d4a32b5af52f05276b1cfac21a5f3689da35`.
Windows and the fresh Linux checkout produced identical development bytecode.
An initial remapped-JAR comparison differed only in THIRD_PARTY_NOTICES.md because
that notice was updated between builds; the exact comparison and final rebuild
are retained. Both final server and client code-source paths point to immutable
SHA-256 directories. The final graphical screenshot shows the real destination.

The obsolete native stub-syntax check was NOT RUN in this iteration. Earlier
STUB ONLY failures remain in the prior checkpoint; no stub façade enters this build.

## Changes

- Native SQLite FULL synchronization replaces the unsupported directory-force path
  only in an explicit disposable-world profile. Persistent rollback journals and
  retained history preserve truthful recovery evidence. Legacy journals are not migrated or deleted.
- Revision compare-and-swap rejects stale writers. Unknown durability/integrity failures quarantine publication.
- New GASLIGHT portal entities cannot be serialized. Existing owned-entity load veto remains.
- Saved dependency scanning and identity-owned tickets permit bounded unloaded acquisition.
- Actual camera/projection capture and bounded session-fenced telemetry are integrated.
- Runtime test launches freeze immutable hashed JARs, preventing rebuilds from corrupting a running game's class reads.

File-by-file diffs and exact production line totals are attached. The original
testing-only/minor-fix caps were superseded by explicit implementation authorization.
The preserved native safety defaults were not enabled or bypassed.

## Failures retained

A test launch rebuilt a JAR while the server was reading it, producing an invalid
ZIP header and disconnect. The harness now freezes JARs; failed evidence remains.
One command-line mode argument was split by PowerShell; a project property replaced it.
Several compilation/harness attempts failed before corrected retries. Interrupted
rebuild logs lacking exit codes are not passes. Minecraft's EULA refusal in a new
server directory is recorded; the already accepted test-environment EULA was reused.

Upstream `Null RenderChunk` and transient chunk-loading errors still occur around
cold entry. Later fixed views work, but cold-frame completeness is not certified.
These are material blockers to mandatory immediate scene replacement.

## Measurements and limitations

Acquisition: approximately3.35–5.51s observed; not a cold-disk latency distribution.
Owned preparation: at most50 targets,98 dependency checks,two pending operations,
30s deadline. Once active,50 owned chunk references; ordinary player/provider
tickets are additional. Observed provider totals included106 near source and162
after crossing. Independent total-memory/GPU/bandwidth/generation admission is absent.

Client FPS samples included19,29 and59–60 with a60FPS cap and render distance4.
These are snapshots, not a sustained or weak-hardware benchmark. Cold startup
included a roughly2.3s server lag warning. Heap snapshots and tick means are in controls.jsonl.
Camera payload size is recorded by the real packet codec test; five observations/sec
does not provide a one-frame network guarantee. Total portal bandwidth is NOT MEASURED.

Zero remote FULL chunks/tickets was observed after disconnect. A later poll's
0.094s command duration is not teardown latency; the disconnect-to-first-zero
observation interval must be read from raw timestamps. Full long-run retained
heap/GPU leak analysis is NOT RUN. Dimension changes fence camera sessions, but
the fixed adapter can retain its portal leases until disconnect; that limitation remains.

Only new disposable worlds were used. Original Minecraft saves were not touched.
Fixture block changes are intentional test setup. Byte-for-byte source-world file
integrity across ordinary native loading/saving is NOT CERTIFIED by this preview.

## Release gate and remaining work

No release score is assigned. The original two million offline assertions do not
clear gameplay gates. Automatic activation remains disabled.

The requested rule—one rendered frame completely unseen must make a place disappear—
still requires a prepared natural destination, complete client chunk/lighting/GPU
readiness, certification across all affected views, atomic generation publication,
safe interaction/collision transition and retirement. A prior camera sample does
not prove that a later arriving commit will be unseen. When these proofs are
unavailable, fail-closed safety prevents changing the world; that is a failed
gameplay requirement, not a passing approximation.

Remaining implementation is already authorized. This checkpoint supplies genuine
artifacts and improved tested infrastructure; it does not claim that houses disappear,
that unseen terrain changes continuously, or that the full mod is ready for a larger userbase.


Source archive SHA-256: `6974b67a07a1bd6e3b468dc9c81edca3492e572d3fa3de3484cfb32481e94a68`.
Production diff: 448 added / 17 removed / 465 changed lines.
