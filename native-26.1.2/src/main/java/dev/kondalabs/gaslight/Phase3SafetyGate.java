package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Deduplicated, budgeted, complete vertical-column clearance of physical facets.
 * The registry inspector must itself return UNKNOWN for not-loaded/not-proven chunks.
 */
public final class Phase3SafetyGate {
    private Phase3SafetyGate() {}
    public record Verdict(boolean accepted,String reason,int checkedColumns) {}
    public static Verdict check(List<Passage> facets,int halo,int maxColumns,
                                StructureEnvelope.Inspector inspector) {
        if(facets==null||facets.isEmpty()||facets.size()>128||halo<1||halo>7||
           maxColumns<1||maxColumns>25000||inspector==null)
            return new Verdict(false,"invalid safety parameters",0);
        LinkedHashSet<Chunk> checked=new LinkedHashSet<>();
        try {
            for(Passage passage:facets) {
                if(passage==null)return new Verdict(false,"null portal facet",checked.size());
                for(Surface surface:List.of(passage.entrance(),passage.exit())) {
                    V a=surface.centre().sub(surface.tangent().mul(surface.width()/2));
                    V b=surface.centre().add(surface.tangent().mul(surface.width()/2));
                    Segment s=new Segment(new V2(a.x(),a.z()),new V2(b.x(),b.z()));
                    // Geometry enumerates all chunks within the full forbidden halo.
                    Set<Chunk> footprint=Geometry.chunksTouched(s,halo*16.0+1, maxColumns);
                    for(Chunk c:footprint) {
                        if(!checked.add(c))continue;
                        if(checked.size()>maxColumns)return new Verdict(false,"global column budget",checked.size());
                        if(Math.abs((long)c.x())>1_844_000||Math.abs((long)c.z())>1_844_000)
                            return new Verdict(false,"world border",checked.size());
                        StructureEnvelope.Result status=inspector.check(c.x(),c.z());
                        if(status!=StructureEnvelope.Result.SAFE)
                            return new Verdict(false,"unsafe column "+c+" "+status,checked.size());
                    }
                }
            }
            return new Verdict(true,"fully certified unique chunk columns",checked.size());
        }catch(RuntimeException ex){
            return new Verdict(false,"column audit exception: "+ex.getClass().getSimpleName(),checked.size());
        }
    }
}
