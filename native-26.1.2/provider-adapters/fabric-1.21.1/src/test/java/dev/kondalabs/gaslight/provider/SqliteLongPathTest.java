package dev.kondalabs.gaslight.provider;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;
import static org.junit.jupiter.api.Assertions.*;
final class SqliteLongPathTest {
    @Test void nativeDurableStoreWorksAtDeepWorldPath() throws Exception {
        Path root=Files.createTempDirectory("gaslight-long-world-");Path path=root;
        for(int i=0;i<7;i++)path=path.resolve("dimension-folder-"+i);
        path=path.resolve("space # unicode-\u03bb");Files.createDirectories(path);
        Path ledger=path.resolve("scene-intents.sqlite");UUID owner=UUID.randomUUID();
        var a=new Surface(new V(0,64,0),new V(1,0,0),64,384);
        var b=new Surface(new V(8192,64,8192),new V(1,0,0),64,384);
        try(var store=new SqliteIntentStore(ledger)) {
            assertFalse(store.blockedOnOpen(),store.diagnostic()+" path="+path);
            assertTrue(store.begin(List.of(new Passage(a,b,owner,1))),store.diagnostic());
            assertEquals(1,store.outstanding());
        }
        assertTrue(Files.isRegularFile(ledger));
        try(var reopened=new SqliteIntentStore(ledger)) {
            assertTrue(reopened.blockedOnOpen());assertEquals(1,reopened.outstanding());
            assertTrue(reopened.reconcileWithLoadVeto(),reopened.diagnostic());
            assertFalse(reopened.blockedOnOpen());assertEquals(2,reopened.nextGeneration());
        }
    }
}
