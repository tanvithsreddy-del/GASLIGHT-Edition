package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;

/** Source-and-destination-coincident clearance-constrained grid routing.
 * Every candidate source chunk and its translated destination are checked,
 * including a full Chebyshev chunk halo, and the final finite line is re-audited.
 * UNKNOWN and USER_PROTECTED are hard vetoes. No terrain writes occur.
 */
public final class Phase3Route {
    private Phase3Route(){}
    public interface Inspector { StructureEnvelope.Result get(int chunkX,int chunkZ); }
    public record Limits(int halo,int maxQueries,int maxVisited,int maxPhysicalChunks,int maxFacets){
        public Limits {
            if(halo<1||halo>7||maxQueries<1||maxVisited<1||maxPhysicalChunks<1||maxFacets<1)
                throw new IllegalArgumentException("invalid route limits");
        }
    }
    public record Plan(boolean accepted,String reason,List<Segment> segments,
                       Set<Chunk> sourceChecked,Set<Chunk> destinationChecked,int inspections){}
    private record Node(Chunk at,int cost,int estimate){}
    private record Pair(Chunk source,Chunk destination){}
    private static final int[][] DIR={{1,0},{-1,0},{0,1},{0,-1}};

    public static Plan route(V2 from,V2 to,int deltaChunkX,int deltaChunkZ,
                             Inspector inspector,Limits limit){
        Objects.requireNonNull(from);Objects.requireNonNull(to);Objects.requireNonNull(inspector);
        if(limit==null)return refused("missing limits",0);
        if(!Double.isFinite(from.x())||!Double.isFinite(from.z())||!Double.isFinite(to.x())||!Double.isFinite(to.z()))
            return refused("nonfinite endpoint",0);
        if(Math.abs((long)deltaChunkX)>1_800_000||Math.abs((long)deltaChunkZ)>1_800_000)
            return refused("offset exceeds border",0);
        Chunk begin=Chunk.of(from),goal=Chunk.of(to);
        if(Math.abs((long)begin.x()-goal.x())+Math.abs((long)begin.z()-goal.z())>limit.maxVisited())
            return refused("route exceeds budget",0);
        class Survey {
            int queries=0;
            final Map<Chunk,StructureEnvelope.Result> memo=new HashMap<>();
            final Set<Chunk> src=new LinkedHashSet<>(),dst=new LinkedHashSet<>();
            StructureEnvelope.Result get(Chunk chunk) {
                if(memo.containsKey(chunk))return memo.get(chunk);
                if(++queries>limit.maxQueries())throw new IllegalStateException("inspection budget");
                if(Math.abs((long)chunk.x())>1_844_000||Math.abs((long)chunk.z())>1_844_000)
                    throw new IllegalStateException("world border");
                StructureEnvelope.Result state=inspector.get(chunk.x(),chunk.z());
                if(state==null)state=StructureEnvelope.Result.UNKNOWN;
                memo.put(chunk,state);return state;
            }
            boolean safe(Chunk chunk) {
                for(int dx=-limit.halo();dx<=limit.halo();dx++)
                    for(int dz=-limit.halo();dz<=limit.halo();dz++){
                        Chunk a=new Chunk(Math.addExact(chunk.x(),dx),Math.addExact(chunk.z(),dz));
                        Chunk b=new Chunk(Math.addExact(a.x(),deltaChunkX),Math.addExact(a.z(),deltaChunkZ));
                        src.add(a);dst.add(b);
                        if(get(a)!=StructureEnvelope.Result.SAFE || get(b)!=StructureEnvelope.Result.SAFE)return false;
                    }
                return true;
            }
        }
        Survey scan=new Survey();
        try{
            // Fast path: an exactly straight, fully buffer-certified paired seam
            // is both cheaper and more geometrically faithful than A* waypoints.
            boolean straightSafe=true;
            Segment direct=new Segment(from,to);
            for(Chunk ch:Geometry.chunksTouched(direct,limit.halo()*16.0+.5,limit.maxPhysicalChunks())){
                Chunk other=new Chunk(Math.addExact(ch.x(),deltaChunkX),Math.addExact(ch.z(),deltaChunkZ));
                scan.src.add(ch);scan.dst.add(other);
                if(scan.get(ch)!=StructureEnvelope.Result.SAFE ||
                   scan.get(other)!=StructureEnvelope.Result.SAFE){straightSafe=false;break;}
            }
            if(straightSafe&&direct.length()>1e-7){
                int pieces=(int)Math.ceil(direct.length()/128.0);
                if(pieces<=limit.maxFacets()){
                    List<Segment> simple=new ArrayList<>(pieces);
                    for(int k=0;k<pieces;k++)
                        simple.add(new Segment(lerp(from,to,(double)k/pieces),lerp(from,to,(double)(k+1)/pieces)));
                    return new Plan(true,"certified straight",List.copyOf(simple),Set.copyOf(scan.src),Set.copyOf(scan.dst),scan.queries);
                }
            }
            if(!scan.safe(begin)||!scan.safe(goal))return new Plan(false,"unsafe endpoint",List.of(),Set.copyOf(scan.src),Set.copyOf(scan.dst),scan.queries);
            PriorityQueue<Node> frontier=new PriorityQueue<>(Comparator.comparingLong(n->(long)n.cost+n.estimate));
            Map<Chunk,Chunk> parent=new HashMap<>();
            Map<Chunk,Integer> costs=new HashMap<>();
            frontier.add(new Node(begin,0,heuristic(begin,goal)));costs.put(begin,0);
            while(!frontier.isEmpty()){
                Node node=frontier.poll();
                if(costs.getOrDefault(node.at,Integer.MAX_VALUE)!=node.cost)continue;
                if(node.at.equals(goal)){
                    ArrayList<Chunk> cells=new ArrayList<>();
                    Chunk c=goal;cells.add(c);
                    while(!c.equals(begin)){
                        c=parent.get(c);if(c==null)throw new IllegalStateException("broken parent chain");cells.add(c);
                    }
                    Collections.reverse(cells);
                    List<V2> points=new ArrayList<>();points.add(from);
                    for(Chunk ch:cells)points.add(new V2(ch.minX()+8,ch.minZ()+8));
                    points.add(to);
                    List<V2> compact=new ArrayList<>();
                    for(V2 p:points) {
                        compact.add(p);
                        while(compact.size()>=3){int n=compact.size();
                            V2 a=compact.get(n-3),b=compact.get(n-2),d=compact.get(n-1);
                            double cross=(b.x()-a.x())*(d.z()-b.z())-(b.z()-a.z())*(d.x()-b.x());
                            if(Math.abs(cross)>1e-8)break;
                            compact.remove(n-2);
                        }
                    }
                    List<Segment> facets=new ArrayList<>();
                    for(int i=1;i<compact.size();i++){
                        Segment segment=new Segment(compact.get(i-1),compact.get(i));
                        if(segment.length()<1e-7)continue;
                        // Verify the entire source and destination finite segment after smoothing.
                        // A path through a diagonal corner is not automatically safe.
                        for(Chunk ch:Geometry.chunksTouched(segment,limit.halo()*16.0+.5,limit.maxPhysicalChunks())){
                            Chunk other=new Chunk(Math.addExact(ch.x(),deltaChunkX),Math.addExact(ch.z(),deltaChunkZ));
                            scan.src.add(ch);scan.dst.add(other);
                            if(scan.get(ch)!=StructureEnvelope.Result.SAFE ||
                               scan.get(other)!=StructureEnvelope.Result.SAFE)
                                return new Plan(false,"post-route veto",List.of(),Set.copyOf(scan.src),Set.copyOf(scan.dst),scan.queries);
                        }
                        if(segment.length()>128){
                            int pieces=(int)Math.ceil(segment.length()/128.0);
                            for(int k=0;k<pieces;k++){
                                V2 a=lerp(segment.a(),segment.b(),(double)k/pieces);
                                V2 b=lerp(segment.a(),segment.b(),(double)(k+1)/pieces);
                                facets.add(new Segment(a,b));
                            }
                        }else facets.add(segment);
                        if(facets.size()>limit.maxFacets())return new Plan(false,"facet budget",List.of(),Set.copyOf(scan.src),Set.copyOf(scan.dst),scan.queries);
                    }
                    return new Plan(!facets.isEmpty(),facets.isEmpty()?"empty route":"certified",List.copyOf(facets),Set.copyOf(scan.src),Set.copyOf(scan.dst),scan.queries);
                }
                if(costs.size()>=limit.maxVisited())break;
                for(int[] step:DIR){
                    Chunk next=new Chunk(Math.addExact(node.at.x(),step[0]),Math.addExact(node.at.z(),step[1]));
                    if(costs.containsKey(next))continue; // unit weights => first visit is optimal
                    if(!scan.safe(next))continue;
                    costs.put(next,node.cost+1);parent.put(next,node.at);
                    frontier.add(new Node(next,node.cost+1,heuristic(next,goal)));
                }
            }
            return new Plan(false,"no bounded safe route",List.of(),Set.copyOf(scan.src),Set.copyOf(scan.dst),scan.queries);
        }catch(RuntimeException e){
            return new Plan(false,"fail-closed: "+e.getMessage(),List.of(),Set.copyOf(scan.src),Set.copyOf(scan.dst),scan.queries);
        }
    }
    private static V2 lerp(V2 a,V2 b,double t){return new V2(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t);}
    private static int heuristic(Chunk a,Chunk b){return (int)Math.min(Integer.MAX_VALUE,Math.abs((long)a.x()-b.x())+Math.abs((long)a.z()-b.z()));}
    private static Plan refused(String reason,int count){return new Plan(false,reason,List.of(),Set.of(),Set.of(),count);}
}
