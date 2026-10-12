package dev.kondalabs.gaslight;

import org.junit.jupiter.api.Test;

/** Expose every existing main-based regression suite to Gradle's real test task.
 * The independent JavaExec gates remain mandatory in check/build as well.
 */
final class StandaloneSuitesTest {
    @Test void nativeCore() throws Exception { NativeTests.main(new String[0]); }
    @Test void preview() throws Exception { Phase2PreviewTests.main(new String[0]); }
    @Test void wire() throws Exception { Phase12WireTests.main(new String[0]); }
    @Test void routes() throws Exception { Phase3Tests.main(new String[0]); }
    @Test void hardening() throws Exception { Phase3HardeningTests.main(new String[0]); }
    @Test void finalPhase3() throws Exception { Phase3FinalTests.main(new String[0]); }
    @Test void destinations() throws Exception { Phase3DestinationTests.main(new String[0]); }
    @Test void validation() throws Exception { Phase4ValidationTests.main(new String[0]); }
    @Test void finalSafety() throws Exception { Phase4FinalSafetyTests.main(new String[0]); }
    @Test void hostIsolation() throws Exception { Phase4HostIsolationTests.main(new String[0]); }
    @Test void recoveryJournal() throws Exception { RecoveryJournalTests.main(new String[0]); }
    @Test void remoteWorldData() throws Exception { RemoteWorldDataTests.main(new String[0]); }
    @Test void remoteChunkLeases() throws Exception { RemoteChunkLeaseTests.main(new String[0]); }
}
