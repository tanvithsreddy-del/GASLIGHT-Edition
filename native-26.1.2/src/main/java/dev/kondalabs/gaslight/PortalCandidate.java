package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Builds a bounded physical seam from an infinite analytic rear line. */
public final class PortalCandidate {
    private PortalCandidate(){}
    public interface Clearance { boolean safe(Surface s); }
    /** Backward offset is measured in blocks and never depends on chunk copying. */
    public static Passage fromRear(V eye,double yaw,float pitch,double destinationDx,double destinationDz,
                                   double backward,double width,double height,long generation,UUID owner){
        if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||Math.abs(destinationDx)<1_000_000
            ||Math.abs(destinationDz)<1_000_000)throw new IllegalArgumentException("long distance required");
        double r=Math.toRadians(yaw);
        V forward=new V(-Math.sin(r),0,Math.cos(r));
        V tangent=new V(Math.cos(r),0,Math.sin(r));
        V a=eye.sub(forward.mul(backward));
        // Centre extends vertically to the player's middle of view for full height coverage.
        V b=a.add(new V(destinationDx,0,destinationDz));
        return new Passage(new Surface(a,tangent,width,height),new Surface(b,tangent,width,height),owner,generation);
    }
    public static boolean certify(Passage p,Clearance c){return c.safe(p.entrance())&&c.safe(p.exit());}
    public static long hashCell(long seed,int cx,int cz){
        long n=seed ^ (((long)cx)*0x9E3779B97F4A7C15L) ^ (((long)cz)*0xC2B2AE3D27D4EB4FL);
        n=(n^(n>>>30))*0xBF58476D1CE4E5B9L;
        n=(n^(n>>>27))*0x94D049BB133111EBL;
        return n^(n>>>31);
    }
}
