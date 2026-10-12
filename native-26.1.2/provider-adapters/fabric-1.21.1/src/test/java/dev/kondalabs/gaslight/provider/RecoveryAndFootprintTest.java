package dev.kondalabs.gaslight.provider;

import dev.kondalabs.gaslight.*;
import dev.kondalabs.gaslight.NativePortal.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual filesystem durability/CAS and loader-independent structure-footprint checks.
 * These unit results are not Minecraft rendering or traversal evidence. */
final class RecoveryAndFootprintTest {
    @TempDir Path dir;
    private Passage intent(long generation){
        return new Passage(new Surface(new V(.5,82.5,.5),new V(1,0,0),4,4),
            new Surface(new V(1024.5,82.5,1024.5),new V(1,0,0),4,4),UUID.fromString("f52ec5ee-1d5c-4212-97a5-8dd9ac5b02d7"),generation);
    }
    @Test void actualDurabilityOrConservativeRefusal()throws Exception{
        Path file=dir.resolve("intent.bin");var store=new Phase3RecoveryJournal.FileStore(file);var p=intent(1);
        if(store.begin(List.of(p))){
            var restarted=new Phase3RecoveryJournal.FileStore(file);
            assertTrue(restarted.blockedOnOpen());assertEquals(1,restarted.outstanding());
            assertTrue(store.settle(p.owner(),p.generation()));
            assertFalse(new Phase3RecoveryJournal.FileStore(file).blockedOnOpen());
        }else{
            assertTrue(store.blockedOnOpen(),"failed durability must prevent physical publication");
            try(var entries=Files.list(dir)){assertTrue(entries.anyMatch(Files::isRegularFile),"unresolved evidence must survive refusal");}
        }
    }
    @Test void staleActualFilesystemWriterCannotReplaceIntent(){
        Path file=dir.resolve("cas.bin");var first=new Phase3RecoveryJournal.FileStore(file);var stale=new Phase3RecoveryJournal.FileStore(file);
        if(!first.begin(List.of(intent(1)))){assertTrue(first.blockedOnOpen());return;}
        assertFalse(stale.begin(List.of(intent(2))));
        var reopened=new Phase3RecoveryJournal.FileStore(file);assertEquals(1,reopened.outstanding());assertTrue(reopened.blockedOnOpen());
    }
    @Test void fullColumnContactAndUnknownAreHardVetoes(){
        var surface=new Surface(new V(.5,82.5,.5),new V(1,0,0),4,4);
        var audited=new ArrayList<String>();
        var safe=StructureEnvelope.audit(surface,1,25,(x,z)->{audited.add(x+","+z);return StructureEnvelope.Result.SAFE;});
        assertTrue(safe.accepted());assertTrue(audited.contains("1,1"),"diagonal structure contact column included");
        for(var rejection:List.of(StructureEnvelope.Result.STRUCTURE,StructureEnvelope.Result.UNKNOWN,StructureEnvelope.Result.USER_PROTECTED)){
            assertFalse(StructureEnvelope.audit(surface,1,25,(x,z)->x==1&&z==1?rejection:StructureEnvelope.Result.SAFE).accepted());
        }
        assertFalse(StructureEnvelope.audit(surface,1,1,(x,z)->StructureEnvelope.Result.SAFE).accepted(),"exhausted scan budget vetoes");
    }
}
