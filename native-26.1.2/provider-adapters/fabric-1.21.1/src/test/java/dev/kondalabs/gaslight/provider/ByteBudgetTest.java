package dev.kondalabs.gaslight.provider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ByteBudgetTest {
    @Test void burstAndSustainedTrafficHaveSeparateBounds(){
        var b=new ByteBudget(200,100,1);assertTrue(b.spend(200,1));assertFalse(b.spend(1,1));
        assertTrue(b.spend(50,500_000_001));assertFalse(b.spend(1,500_000_001));
        assertFalse(b.spend(201,10_000_000_001L));assertTrue(b.spend(200,10_000_000_001L));assertFalse(b.spend(-1,10_000_000_001L));assertFalse(b.spend(0,0));
    }
}
