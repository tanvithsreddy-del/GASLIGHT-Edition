package dev.kondalabs.gaslight.provider;
import dev.kondalabs.gaslight.*;
import dev.kondalabs.gaslight.NativePortal.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class SceneFootprintTest {
    @Test void structureHaloIsOwnedAcrossEveryChunkOffsetAndAxis() {
        boolean needsFourth=false;
        for(int base:new int[]{-8192,0,8192})for(int offset=0;offset<16;offset++)for(boolean x:new boolean[]{false,true}) {
            double coord=base+offset+.5;int centre=Math.floorDiv((int)Math.floor(coord),16);
            var s=new Surface(new V(coord,128,coord),x?new V(0,0,1):new V(1,0,0),64,384);
            var audit=StructureEnvelope.audit(s,1,64,(cx,cz)->Math.abs(cx-centre)<=4&&Math.abs(cz-centre)<=4?StructureEnvelope.Result.SAFE:StructureEnvelope.Result.UNKNOWN);
            assertTrue(audit.accepted(),"full-height safety envelope must fit the owned FULL chunk window");
            needsFourth|=!StructureEnvelope.audit(s,1,64,(cx,cz)->Math.abs(cx-centre)<=3&&Math.abs(cz-centre)<=3?StructureEnvelope.Result.SAFE:StructureEnvelope.Result.UNKNOWN).accepted();
        }
        assertTrue(needsFourth,"regression: radius three omitted a required halo column");
    }
}
