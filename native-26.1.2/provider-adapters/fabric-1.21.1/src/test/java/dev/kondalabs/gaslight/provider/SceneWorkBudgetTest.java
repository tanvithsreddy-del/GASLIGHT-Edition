package dev.kondalabs.gaslight.provider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class SceneWorkBudgetTest {
    @Test void failuresCannotCauseAnUnboundedGenerationRetryLoop(){
        var b=new SceneWorkBudget(1);long heap=1024L*1024*1024;
        assertTrue(b.consume(1,100,heap));assertTrue(b.consume(1,100,heap));
        for(int i=0;i<10000;i++)assertFalse(b.consume(20_000_000_001L,100,heap));
        assertTrue(b.consume(30_000_000_001L,100,heap));assertFalse(b.consume(30_000_000_001L,100,heap));
        assertTrue(b.consume(90_000_000_001L,100,heap));assertTrue(b.consume(90_000_000_001L,100,heap));assertFalse(b.consume(90_000_000_001L,100,heap));
    }
    @Test void MemoryPressureAndClockRegressionRefuseWorkWithoutSpendingThePermit(){
        var b=new SceneWorkBudget(100);long heap=1024L*1024*1024;
        assertFalse(b.consume(99,1,heap));assertFalse(b.consume(100,heap*8/10,heap));assertFalse(b.consume(100,-1,heap));
        assertTrue(b.consume(100,1,heap));assertTrue(b.consume(100,1,heap));assertFalse(b.consume(100,1,heap));
    }
}
