package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;

/** All-or-nothing finite closed-loop clearance routing. A logical infinity is
 * NOT enumerated. Any failed edge, invalid chunk, segment cap or self-crossing
 * rejects the entire physical-loop proposal. Geometry only, no render bridge.
 */
public final class Phase3LoopRoute {
    private Phase3LoopRoute(){}
    public record Result(boolean accepted,String reason,List<Segment> segments,
                         int queries,Set<Chunk> checkedSource,Set<Chunk> checkedDestination){}
    public static Result route(List<V2> polygon,int dxChunks,int dzChunks,
                               Phase3Route.Inspector inspector,Phase3Route.Limits limits,
                               int maxTotalFacets,int maxTotalQueries){
        if(polygon==null||polygon.size()<8||polygon.size()>64||maxTotalFacets<8||maxTotalQueries<1)
            return refuse("invalid loop limits",0,Set.of(),Set.of());
        double area=0,sumX=0,sumZ=0;
        for(int i=0;i<polygon.size();i++){
            V2 a=polygon.get(i),b=polygon.get((i+1)%polygon.size());
            if(a==null||b==null||!Double.isFinite(a.x())||!Double.isFinite(a.z()))
                return refuse("invalid polygon vertex",0,Set.of(),Set.of());
            area+=a.x()*b.z()-a.z()*b.x();
            sumX+=a.x();sumZ+=a.z();
        }
        V2 anchor=new V2(sumX/polygon.size(),sumZ/polygon.size());
        if(!Double.isFinite(area)||Math.abs(area)<100||!Geometry.polygonEncloses(polygon,anchor))
            return refuse("degenerate or non-enclosing input loop",0,Set.of(),Set.of());
        List<Segment> entire=new ArrayList<>();
        Set<Chunk> source=new LinkedHashSet<>(),destination=new LinkedHashSet<>();
        int queries=0;
        for(int i=0;i<polygon.size();i++){
            V2 a=polygon.get(i),b=polygon.get((i+1)%polygon.size());
            Phase3Route.Plan part=Phase3Route.route(a,b,dxChunks,dzChunks,inspector,limits);
            queries+=part.inspections();
            source.addAll(part.sourceChecked());destination.addAll(part.destinationChecked());
            if(!part.accepted())return refuse("loop edge "+i+": "+part.reason(),queries,source,destination);
            entire.addAll(part.segments());
            if(entire.size()>maxTotalFacets||queries>maxTotalQueries)
                return refuse("loop budget exceeded",queries,source,destination);
        }
        if(entire.size()<8)return refuse("insufficient facets",queries,source,destination);
        // The endpoints of every edge must actually meet in physical coordinates.
        for(int i=0;i<entire.size();i++){
            Segment a=entire.get(i),b=entire.get((i+1)%entire.size());
            if(a.b().minus(b.a()).norm()>1e-6)
                return refuse("gap between adjacent facets",queries,source,destination);
        }
        // Any crossing of nonadjacent facets breaks the closed-curve topology.
        for(int i=0;i<entire.size();i++){
            for(int j=i+2;j<entire.size();j++){
                if(i==0&&j==entire.size()-1)continue;
                if(intersect(entire.get(i),entire.get(j)))
                    return refuse("self-intersecting route",queries,source,destination);
            }
        }
        List<V2> routedPolygon=new ArrayList<>(entire.size());
        double newArea=0;
        for(Segment facet:entire){
            routedPolygon.add(facet.a());
            newArea+=facet.a().x()*facet.b().z()-facet.a().z()*facet.b().x();
        }
        if(Math.signum(area)!=Math.signum(newArea)||!Geometry.polygonEncloses(routedPolygon,anchor))
            return refuse("loop no longer encloses player anchor",queries,source,destination);
        return new Result(true,"fully certified finite loop",List.copyOf(entire),queries,
            Set.copyOf(source),Set.copyOf(destination));
    }
    private static double turn(V2 a,V2 b,V2 c){return (b.x()-a.x())*(c.z()-a.z())-(b.z()-a.z())*(c.x()-a.x());}
    private static boolean on(V2 a,V2 b,V2 p){
        return Math.min(a.x(),b.x())-1e-9<=p.x()&&p.x()<=Math.max(a.x(),b.x())+1e-9 &&
               Math.min(a.z(),b.z())-1e-9<=p.z()&&p.z()<=Math.max(a.z(),b.z())+1e-9;
    }
    private static boolean intersect(Segment s,Segment t){
        V2 a=s.a(),b=s.b(),c=t.a(),d=t.b();
        double p=turn(a,b,c),q=turn(a,b,d),r=turn(c,d,a),u=turn(c,d,b);
        double eps=1e-8;
        if(((p>eps&&q< -eps)||(p< -eps&&q>eps))&&((r>eps&&u< -eps)||(r< -eps&&u>eps)))return true;
        return (Math.abs(p)<=eps&&on(a,b,c))||(Math.abs(q)<=eps&&on(a,b,d)) ||
               (Math.abs(r)<=eps&&on(c,d,a))||(Math.abs(u)<=eps&&on(c,d,b));
    }
    private static Result refuse(String reason,int n,Set<Chunk> src,Set<Chunk> dst){
        return new Result(false,reason,List.of(),n,Set.copyOf(src),Set.copyOf(dst));
    }
}
