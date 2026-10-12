#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/core-test-classes
mkdir -p build/journal-test-temp
journal_tmp="$PWD/build/journal-test-temp"
if command -v cygpath >/dev/null; then journal_tmp="$(cygpath -w "$journal_tmp")"; fi
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=\"$journal_tmp\""
javac --release 21 -encoding UTF-8 -d build/core-test-classes \
  src/main/java/dev/kondalabs/gaslight/Geometry.java \
  src/main/java/dev/kondalabs/gaslight/StructureSafety.java \
  src/main/java/dev/kondalabs/gaslight/NativePortal.java \
  src/main/java/dev/kondalabs/gaslight/PortalCandidate.java \
  src/main/java/dev/kondalabs/gaslight/StructureEnvelope.java \
  src/main/java/dev/kondalabs/gaslight/NativeScheduler.java \
  src/main/java/dev/kondalabs/gaslight/PortalCrossingController.java \
  src/main/java/dev/kondalabs/gaslight/NativeWorldEngine.java \
  src/main/java/dev/kondalabs/gaslight/RemoteViewRaycaster.java \
  src/main/java/dev/kondalabs/gaslight/PortalPreviewCore.java \
  src/main/java/dev/kondalabs/gaslight/FixedPortalRig.java \
  src/main/java/dev/kondalabs/gaslight/PortalForegroundOcclusion.java \
  src/main/java/dev/kondalabs/gaslight/PortalSnapshotWire.java \
  src/test/java/dev/kondalabs/gaslight/NativeTests.java \
  src/test/java/dev/kondalabs/gaslight/Phase2PreviewTests.java \
  src/test/java/dev/kondalabs/gaslight/Phase12WireTests.java \
  src/main/java/dev/kondalabs/gaslight/Phase3Visibility.java \
  src/main/java/dev/kondalabs/gaslight/Phase3Route.java \
  src/main/java/dev/kondalabs/gaslight/Phase3SafetyGate.java \
  src/main/java/dev/kondalabs/gaslight/Phase3TerrainMatcher.java \
  src/main/java/dev/kondalabs/gaslight/Phase3DestinationProfile.java \
  src/main/java/dev/kondalabs/gaslight/Phase3DestinationSelector.java \
  src/main/java/dev/kondalabs/gaslight/Phase3CameraWitness.java \
  src/main/java/dev/kondalabs/gaslight/Phase3Publication.java \
  src/main/java/dev/kondalabs/gaslight/Phase3RecoveryJournal.java \
  src/main/java/dev/kondalabs/gaslight/Phase3PreparedCatalog.java \
  src/main/java/dev/kondalabs/gaslight/Phase3GenerationGeometry.java \
  src/main/java/dev/kondalabs/gaslight/Phase3Engine.java \
  src/main/java/dev/kondalabs/gaslight/ProtectedChunkRegions.java \
  src/main/java/dev/kondalabs/gaslight/Phase3LoopRoute.java \
  src/test/java/dev/kondalabs/gaslight/Phase3Tests.java \
  src/test/java/dev/kondalabs/gaslight/Phase3HardeningTests.java \
  src/test/java/dev/kondalabs/gaslight/Phase3FinalTests.java \
  src/test/java/dev/kondalabs/gaslight/Phase3DestinationTests.java \
  src/test/java/dev/kondalabs/gaslight/Phase4ValidationTests.java \
  src/test/java/dev/kondalabs/gaslight/Phase4FinalSafetyTests.java \
  src/test/java/dev/kondalabs/gaslight/Phase4HostIsolationTests.java \
  src/test/java/dev/kondalabs/gaslight/JournalTestSupport.java \
  src/test/java/dev/kondalabs/gaslight/RecoveryJournalTests.java \
  src/main/java/dev/kondalabs/gaslight/RemoteChunkData.java \
  src/main/java/dev/kondalabs/gaslight/RemoteChunkLeases.java \
  src/test/java/dev/kondalabs/gaslight/RemoteChunkLeaseTests.java \
  src/main/java/dev/kondalabs/gaslight/RemoteWorldWire.java \
  src/main/java/dev/kondalabs/gaslight/RemoteWorldCache.java \
  src/main/java/dev/kondalabs/gaslight/RemoteWorldBudget.java \
  src/test/java/dev/kondalabs/gaslight/RemoteWorldDataTests.java \
  src/test/java/dev/kondalabs/gaslight/Phase4Benchmarks.java
# Mutation probes compile the same source but run only the suite that is capable
# of killing their injected regression. Regular checks always run every suite.
if [[ -n "${GASLIGHT_RUN_ONLY:-}" ]]; then
  case "$GASLIGHT_RUN_ONLY" in
    dev.kondalabs.gaslight.Phase4ValidationTests|dev.kondalabs.gaslight.Phase4FinalSafetyTests|dev.kondalabs.gaslight.Phase3DestinationTests|dev.kondalabs.gaslight.Phase4HostIsolationTests)
      java -Xmx512m -cp build/core-test-classes "$GASLIGHT_RUN_ONLY"
      exit 0 ;;
    *) echo "Invalid isolated test selector" >&2; exit 64 ;;
  esac
fi
java -Xmx256m -cp build/core-test-classes dev.kondalabs.gaslight.NativeTests
java -Xmx256m -cp build/core-test-classes dev.kondalabs.gaslight.Phase2PreviewTests

java -Xmx256m -cp build/core-test-classes dev.kondalabs.gaslight.Phase12WireTests

java -Xmx384m -cp build/core-test-classes dev.kondalabs.gaslight.Phase3Tests

java -Xmx384m -cp build/core-test-classes dev.kondalabs.gaslight.Phase3HardeningTests

java -Xmx384m -cp build/core-test-classes dev.kondalabs.gaslight.Phase3FinalTests

java -Xmx384m -cp build/core-test-classes dev.kondalabs.gaslight.Phase3DestinationTests

java -Xmx512m -cp build/core-test-classes dev.kondalabs.gaslight.Phase4ValidationTests

java -Xmx512m -cp build/core-test-classes dev.kondalabs.gaslight.Phase4FinalSafetyTests

java -Xmx256m -cp build/core-test-classes dev.kondalabs.gaslight.Phase4HostIsolationTests

java -Xmx512m -cp build/core-test-classes dev.kondalabs.gaslight.RecoveryJournalTests

java -Xmx256m -cp build/core-test-classes dev.kondalabs.gaslight.RemoteWorldDataTests
java -Xmx256m -cp build/core-test-classes dev.kondalabs.gaslight.RemoteChunkLeaseTests
