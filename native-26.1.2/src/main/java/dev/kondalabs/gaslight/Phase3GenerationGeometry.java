package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Reject conflicting physical generations before adding more travel surfaces.
 * Neighboring facets INSIDE one closed loop are intentionally allowed to meet;
 * this only checks different publication generations.
 */
public final class Phase3GenerationGeometry {
    private Phase3GenerationGeometry(){}
    public static boolean separated(List<Passage> candidate,List<Passage> existing,double clearance){
        if(candidate==null||existing==null||candidate.size()>128||existing.size()>128||
            !Double.isFinite(clearance)||clearance<1)return false;
        for(Passage a:candidate){
            if(a==null)return false;
            for(Passage b:existing){
                if(b==null)return false;
                for(Surface x:List.of(a.entrance(),a.exit()))
                    for(Surface y:List.of(b.entrance(),b.exit())){
                        if(Math.abs(x.centre().y()-y.centre().y())>
                            (x.height()+y.height())/2+clearance)continue;
                        if(distance(asSegment(x),asSegment(y))<=clearance)return false;
                    }
            }
        }
        return true;
    }
    private static Segment asSegment(Surface s){
        V a=s.centre().sub(s.tangent().mul(s.width()/2));
        V b=s.centre().add(s.tangent().mul(s.width()/2));
        return new Segment(new V2(a.x(),a.z()),new V2(b.x(),b.z()));
    }
    public static double distance(Segment a,Segment b){
        if(intersects(a,b))return 0;
        return Math.min(Math.min(Geometry.pointSegmentDistance(a.a(),b),Geometry.pointSegmentDistance(a.b(),b)),
            Math.min(Geometry.pointSegmentDistance(b.a(),a),Geometry.pointSegmentDistance(b.b(),a)));
    }
    private static double orientation(V2 a,V2 b,V2 c){
        return (b.x()-a.x())*(c.z()-a.z())-(b.z()-a.z())*(c.x()-a.x());
    }
    private static boolean on(V2 a,V2 b,V2 p){
        return p.x()>=Math.min(a.x(),b.x())-1e-8&&p.x()<=Math.max(a.x(),b.x())+1e-8&&
               p.z()>=Math.min(a.z(),b.z())-1e-8&&p.z()<=Math.max(a.z(),b.z())+1e-8;
    }
    private static boolean intersects(Segment s,Segment t){
        V2 a=s.a(),b=s.b(),c=t.a(),d=t.b();
        double p=orientation(a,b,c),q=orientation(a,b,d),r=orientation(c,d,a),u=orientation(c,d,b);
        if(((p>0&&q<0)||(p<0&&q>0))&&((r>0&&u<0)||(r<0&&u>0)))return true;
        return (Math.abs(p)<1e-8&&on(a,b,c))||(Math.abs(q)<1e-8&&on(a,b,d))||
            (Math.abs(r)<1e-8&&on(c,d,a))||(Math.abs(u)<1e-8&&on(c,d,b));
    }
}
