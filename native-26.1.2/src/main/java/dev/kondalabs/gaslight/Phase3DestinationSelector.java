package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;

/** Bounded deterministic destination candidate discovery. Catalog candidates are
 * tried first; generated offsets never claim the corresponding chunks are loaded.
 */
public final class Phase3DestinationSelector {
    private Phase3DestinationSelector() {}
    public record Candidate(Chunk offset,boolean catalog) {}
    public static List<Candidate> candidates(List<Chunk> catalog,int maximum,long seed,long epoch,
                                              V2 from,V2 to){
        if(maximum<1||maximum>8||from==null||to==null ||
           !Double.isFinite(from.x())||!Double.isFinite(from.z())||
           !Double.isFinite(to.x())||!Double.isFinite(to.z()))return List.of();
        LinkedHashMap<Chunk,Candidate> ordered=new LinkedHashMap<>();
        if(catalog!=null){
            // Bound even a malicious Host that supplies a gigantic candidate list.
            int examined=0;
            for(Chunk c:catalog){
                if(++examined>64||ordered.size()>=maximum)break;
                if(valid(c,from,to))ordered.putIfAbsent(c,new Candidate(c,true));
            }
        }
        // Generate twice the maximum number of fallbacks to tolerate collisions
        // and invalid positions near the Minecraft world boundary.
        for(int i=0;i<maximum*2 && ordered.size()<maximum;i++){
            long entropy=PortalCandidate.hashCell(seed ^ (epoch*0x9E3779B97F4A7C15L),i,0);
            int x=70_000+(int)Math.floorMod(entropy,190_000L);
            int z=70_000+(int)Math.floorMod(entropy>>>27,190_000L);
            if((entropy&1L)!=0)x=-x;
            if((entropy&2L)!=0)z=-z;
            Chunk c=new Chunk(x,z);
            if(valid(c,from,to))ordered.putIfAbsent(c,new Candidate(c,false));
        }
        return List.copyOf(ordered.values());
    }
    private static boolean valid(Chunk offset,V2 a,V2 b){
        if(offset==null)return false;
        long x=Math.abs((long)offset.x()),z=Math.abs((long)offset.z());
        if(Math.max(x,z)<64||x>1_800_000||z>1_800_000)return false;
        // Map both ends using 64-bit arithmetic and a conservative world limit.
        for(V2 p:List.of(a,b)){
            double px=p.x()+16.0*offset.x(),pz=p.z()+16.0*offset.z();
            if(!Double.isFinite(px)||!Double.isFinite(pz)||
               Math.abs(px)>29_000_000||Math.abs(pz)>29_000_000)return false;
        }
        return true;
    }
}
