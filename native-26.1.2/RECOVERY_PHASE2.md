# Phase 2 recovery journal contract

This phase changes only the recovery journal and its verification. Physical
publication, crossing, camera authority, protected-region checks and release
vetoes remain unchanged. A passing recovery test does not make GASLIGHT playable.

## Durable acknowledgement and platform limits

`FileStore.begin()` returns true only after the candidate file is forced, an
atomic primary replacement succeeds, directory metadata (including ancestors)
is forced, and transaction-evidence cleanup is also forced. IO errors are not
ignored. An error quarantines that Store and retains its previous/proposed
ownership. `settle()` still requires the original live owner to have confirmed
abort/physical retirement; reopening an unresolved journal cannot settle it.

| Runner/storage | Current contract |
|---|---|
| Linux local ext4, tested with Temurin 25+36 | Certified checkpoint acknowledgement when every required operation succeeds |
| Windows NTFS, tested with Temurin 25.0.4.1 | Directory open/force is unavailable; begin returns false, retains evidence, and blocks further physical operation |
| RAM-backed, network or other storage families | Uncertified; no publication acknowledgement |

The eligibility list is deliberately limited to ext4 and NTFS. NTFS eligibility
does not bypass the failing Windows directory operation. There is no override
flag to ignore failed flushes. Broader platform support needs actual verification.

These guarantees rely on the OS/filesystem/device honouring flush requests.
The tests kill processes and inject failures; they do not switch off hardware
power, certify drive firmware, or provide an equivalent Windows native backend.
Java's force guarantee has a local-storage qualification; successful file flush
alone is not being substituted for namespace durability. See the
[Java 25 FileChannel contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean)).

## Retained transaction evidence

`<journal>.pending-*.bin` retains the union of old ownership and the proposed
checkpoint. `<journal>.candidate-*.bin` contains the new checkpoint before its
atomic move. Evidence is forced before replacement. It is removed only after
the primary checkpoint is certified; a cleanup failure restores evidence while
the writer lock is still held. Failure paths never silently delete candidates
or pending records. Old `.gaslight-intent-*.tmp` files are also treated as
unresolved evidence.

On restart, the primary and retained files are read under the same cooperative
OS lock. Complete records are decoded and validated before adoption. Corrupt,
partial, oversized, conflicting or excess evidence blocks publication. Valid
retained ownership can still be reported when the primary is corrupt. A count
of zero is NOT permission to publish: unreadable evidence may have no decoded
owners, while `blockedOnOpen()` remains true.

Do not delete unresolved files or the persistent `.lock` file to unblock a
world. This phase provides no automatic world-aware reconciliation or reset
command. Real renderer/collision/chunk ownership must be reconciled in an
explicitly authorized later phase before any recovery can be certified.

## Schema and writer fencing

Existing V1 journals remain readable. New checkpoints use V2, retaining the V1
record layout and appending a nonnegative revision before the checksum. Every
checkpoint advances that revision, including a settled empty checkpoint. This
prevents ABA stale writers from being accepted after another writer's
publish-and-retire cycle. Revision exhaustion refuses publication. Dirty V1
records are never automatically migrated, cleared or forgotten. Older binaries
will fail closed on V2 rather than silently reinterpret it.

## Verification scope

The ten baseline suites remain mandatory, plus `RecoveryJournalTests`. Gradle
runs all eleven via JUnit as well. Tests use the project filesystem for fixture
storage because `/tmp` can be RAM-backed. Windows assertions explicitly require
native publication refusal and intact evidence; they do not fake a successful
directory sync. The CAS mutation checks the stale-writer guard on both platforms.

Coverage includes threads and separate OS writers, checkpoint IO exceptions,
abrupt child-process termination before/after replacement and after acknowledged
begin, interrupted settlement, all partial lengths, single-bit corruptions,
CRC-valid malformed records, legacy evidence, conflicting ownership, bounded
reads/scans, V1 compatibility, ABA revision fencing and controller quarantine.
Late Windows checkpoints and a native durable publish/retire cycle remain
unreachable by design and are reported as blocked. The Windows revision
byte-image test verifies CAS only; it is not native durability evidence.

Stop at the Phase 2 boundary. No next phase is authorized by this document.
