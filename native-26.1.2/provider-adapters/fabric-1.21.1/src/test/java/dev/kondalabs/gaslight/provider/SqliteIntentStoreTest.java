package dev.kondalabs.gaslight.provider;
import dev.kondalabs.gaslight.NativePortal.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SqliteIntentStoreTest {
    @TempDir Path dir;
    static Passage intent(long g){return new Passage(new Surface(new V(.5,82.5,.5),new V(1,0,0),4,4),new Surface(new V(1024.5,82.5,1024.5),new V(1,0,0),4,4),new UUID(7,9),g);}
    @Test void genuineWindowsDurabilityAndRepeatedCycles()throws Exception{
        Path p=dir.resolve("intents.sqlite");
        try(var s=new SqliteIntentStore(p)){
            assertFalse(s.blockedOnOpen(),s.diagnostic());
            for(int i=1;i<=40;i++){assertTrue(s.begin(List.of(intent(i))),s.diagnostic());assertEquals(1,s.outstanding());assertTrue(s.settle(intent(i).owner(),i),s.diagnostic());assertEquals(0,s.outstanding());}
        }
        try(var s=new SqliteIntentStore(p)){assertFalse(s.blockedOnOpen(),s.diagnostic());}
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+p);var q=c.createStatement();var rows=q.executeQuery("SELECT count(*) FROM intents WHERE state='RETIRED'")){assertTrue(rows.next());assertEquals(40,rows.getInt(1),"retirement retains every historical record");}
    }
    @Test void dirtyRestartRequiresExplicitGuardedRecovery(){
        Path p=dir.resolve("dirty.sqlite");
        try(var s=new SqliteIntentStore(p)){assertTrue(s.begin(List.of(intent(1))),s.diagnostic());}
        try(var s=new SqliteIntentStore(p)){
            assertTrue(s.blockedOnOpen());assertEquals(1,s.outstanding());assertFalse(s.begin(List.of(intent(2))));assertFalse(s.settle(intent(1).owner(),1));
            assertTrue(s.reconcileWithLoadVeto(),s.diagnostic());assertFalse(s.blockedOnOpen());assertTrue(s.begin(List.of(intent(2))),s.diagnostic());
        }
    }
    @Test void staleWriterAndStaleRetirementFailClosed(){
        Path p=dir.resolve("cas.sqlite");
        try(var first=new SqliteIntentStore(p);var stale=new SqliteIntentStore(p)){
            assertTrue(first.begin(List.of(intent(1))),first.diagnostic());assertFalse(stale.begin(List.of(intent(2))));assertTrue(stale.blockedOnOpen());
            assertFalse(first.settle(intent(1).owner(),2));assertTrue(first.blockedOnOpen());
        }
        try(var s=new SqliteIntentStore(p)){assertTrue(s.blockedOnOpen());assertEquals(1,s.outstanding());}
    }
    @Test void malformedHistoryCannotRecover()throws Exception{
        Path p=dir.resolve("corrupt.sqlite");try(var s=new SqliteIntentStore(p)){assertTrue(s.begin(List.of(intent(1))),s.diagnostic());}
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+p);var q=c.createStatement()){q.executeUpdate("UPDATE intents SET payload=x'00'");}
        try(var s=new SqliteIntentStore(p)){assertTrue(s.blockedOnOpen());assertFalse(s.reconcileWithLoadVeto());assertFalse(s.begin(List.of(intent(2))));}
    }
    @Test void partialFileIsPreserved()throws Exception{
        Path p=dir.resolve("partial.sqlite");byte[] bytes="SQLite format 3\0broken".getBytes();Files.write(p,bytes);
        try(var s=new SqliteIntentStore(p)){assertTrue(s.blockedOnOpen());assertFalse(s.reconcileWithLoadVeto());}
        assertArrayEquals(bytes,Files.readAllBytes(p));
    }
    @Test void concurrentWritersHaveOneWinner()throws Exception{
        Path p=dir.resolve("concurrent.sqlite");
        try(var a=new SqliteIntentStore(p);var b=new SqliteIntentStore(p)){
            var gate=new java.util.concurrent.CountDownLatch(1);
            var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
            try{
                var x=executor.submit(()->{gate.await();return a.begin(List.of(intent(1)));});
                var y=executor.submit(()->{gate.await();return b.begin(List.of(intent(2)));});
                gate.countDown();assertNotEquals(x.get(),y.get());
            }finally{executor.shutdownNow();}
        }
        try(var s=new SqliteIntentStore(p)){assertEquals(1,s.outstanding());assertTrue(s.blockedOnOpen());}
    }
    @Test void actualProcessHaltsPreserveIntentsAndRollbackPartialUpdates()throws Exception{
        var paths=new LinkedHashSet<String>();
        for(Class<?> c:List.of(SqliteCrashProbe.class,SqliteIntentStore.class,org.sqlite.JDBC.class,org.slf4j.LoggerFactory.class))paths.add(Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        String classpath=String.join(java.io.File.pathSeparator,paths);
        for(String mode:List.of("begin","settle","midwrite")){
            Path file=dir.resolve(mode+".sqlite");
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"--enable-native-access=ALL-UNNAMED","-cp",classpath,SqliteCrashProbe.class.getName(),mode,file.toString()).redirectErrorStream(true).start();
            assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS),"bounded child interruption test");
            String output=new String(process.getInputStream().readAllBytes());System.out.println("REAL_SQLITE_PROCESS_HALT "+mode+" exit="+process.exitValue()+" "+output);
            assertEquals(mode.equals("begin")?71:mode.equals("settle")?72:73,process.exitValue(),output);
            try(var reopened=new SqliteIntentStore(file)){
                if(mode.equals("settle")){assertFalse(reopened.blockedOnOpen(),reopened.diagnostic());assertEquals(0,reopened.outstanding());}
                else{assertTrue(reopened.blockedOnOpen(),reopened.diagnostic());assertEquals(1,reopened.outstanding());assertTrue(reopened.reconcileWithLoadVeto(),reopened.diagnostic());}
            }
        }
    }
    @Test void nonstandardDatabasePageSizeCannotEvadeDiskBudget()throws Exception{
        Path p=dir.resolve("oversized-pages.sqlite");
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+p);var s=c.createStatement()){s.execute("PRAGMA page_size=65536");s.execute("CREATE TABLE foreign_data(value TEXT)");}
        try(var store=new SqliteIntentStore(p)){assertTrue(store.blockedOnOpen());assertTrue(store.diagnostic().contains("page budget"));assertFalse(store.begin(List.of(intent(1))));}
        assertTrue(Files.exists(p));
    }
    @Test void originalReturnAnchorSurvivesRestartAndRejectsCorruption()throws Exception{
        Path p=dir.resolve("anchor.sqlite");UUID player=UUID.randomUUID();var expected=new SqliteIntentStore.Anchor("minecraft:overworld",.5,82,3.5);
        try(var store=new SqliteIntentStore(p)){assertTrue(store.saveAnchor(player,expected),store.diagnostic());}
        try(var store=new SqliteIntentStore(p)){assertEquals(expected,store.loadAnchor(player));}
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+p);var query=connection.createStatement()){query.executeUpdate("UPDATE anchors SET x=123456");}
        try(var store=new SqliteIntentStore(p)){assertNull(store.loadAnchor(player));assertTrue(store.blockedOnOpen());assertFalse(store.begin(List.of(intent(1))));}
        assertThrows(IllegalArgumentException.class,()->new SqliteIntentStore.Anchor("gaslight_provider:unobserved",0,64,0));
    }
}
