package dev.kondalabs.gaslight.provider;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

final class GenerationFenceTest {
    @Test void requiresEveryReadinessArmAndActualHiddenFrame() {
        UUID s=UUID.randomUUID(); var f=new GenerationFence(s,7,1000);
        assertFalse(f.ready(s,7,true,true,false,10));
        assertFalse(f.select(s,7,1,true,true,10));
        assertTrue(f.ready(s,7,true,true,true,10));
        assertFalse(f.select(s,7,2,false,true,20));
        assertFalse(f.select(s,7,2,true,false,20));
        assertTrue(f.select(s,7,3,true,true,20));
        assertFalse(f.commit(s,7,2,30));
        assertTrue(f.commit(s,7,3,30));
        assertFalse(f.retire(s,7,false,true));
        assertFalse(f.retire(s,7,true,false));
        assertTrue(f.retire(s,7,true,true));
    }
    @Test void rejectsStaleSessionsEpochsDuplicateCommitsAndExpiredOffers() {
        UUID s=UUID.randomUUID(); var f=new GenerationFence(s,2,100);
        assertFalse(f.ready(UUID.randomUUID(),2,true,true,true,0));
        assertFalse(f.ready(s,1,true,true,true,0));
        assertTrue(f.ready(s,2,true,true,true,0));
        assertFalse(f.select(s,2,-1,true,true,1));
        assertFalse(f.select(s,2,1,true,true,100));
        assertTrue(f.select(s,2,2,true,true,99));
        assertFalse(f.commit(s,2,2,100));
        f.fail();
        assertEquals(GenerationFence.State.FAILED,f.state());
        assertFalse(f.commit(s,2,2,99));
    }
    @Test void concurrentSelectionsConsumeOnePermit() throws Exception {
        UUID s=UUID.randomUUID(); var f=new GenerationFence(s,1,1000);
        assertTrue(f.ready(s,1,true,true,true,0));
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks=new java.util.ArrayList<Future<Boolean>>();
            for(int i=0;i<64;i++){int frame=i;tasks.add(workers.submit(()->f.select(s,1,frame,true,true,1)));}
            int accepted=0;for(var result:tasks)if(result.get())accepted++;
            assertEquals(1,accepted);
        }
    }
    @Test void dirtyDataInvalidatesReadinessBeforeSelection(){
        UUID s=UUID.randomUUID();var f=new GenerationFence(s,1,100);
        assertTrue(f.ready(s,1,true,true,true,1));assertFalse(f.invalidateReady(UUID.randomUUID(),1,2));
        assertTrue(f.invalidateReady(s,1,2));assertFalse(f.select(s,1,2,true,true,2));
        assertTrue(f.ready(s,1,true,true,true,3));assertTrue(f.select(s,1,3,true,true,3));
        assertFalse(f.invalidateReady(s,1,4));assertTrue(f.commit(s,1,3,4));
    }
}
