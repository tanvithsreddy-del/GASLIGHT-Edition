package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Hard-veto all nearby vertical chunk columns, including diagonals and structures underground. */
public final class StructureEnvelope {
    private StructureEnvelope(){}
    public enum Result {SAFE, STRUCTURE, UNKNOWN, USER_PROTECTED}
    public interface Inspector { Result check(int cx,int cz); }
    public record Audit(boolean accepted,String reason,int checked){}
    public static Audit audit(Surface s,int halo,int maxScanned,Inspector inspector){
        if(halo<1||maxScanned<1)return new Audit(false,"invalid policy",0);
        V endpointA=s.centre().add(s.tangent().mul(-s.width()/2));
        V endpointB=s.centre().add(s.tangent().mul(s.width()/2));
        Segment segment=new Segment(new V2(endpointA.x(),endpointA.z()),new V2(endpointB.x(),endpointB.z()));
        Collection<Chunk> chunks;
        try{chunks=Geometry.chunksTouched(segment,16.0*halo+1,maxScanned);}catch(RuntimeException e){return new Audit(false,"budget/geometry: "+e.getMessage(),0);}
        int seen=0;
        for(Chunk c:chunks){
            ++seen;
            Result r=inspector.check(c.x(),c.z());
            if(r!=Result.SAFE)return new Audit(false,"unsafe chunk "+c+" "+r,seen);
        }
        return new Audit(seen>0,seen>0?"certified":"empty",seen);
    }
}
