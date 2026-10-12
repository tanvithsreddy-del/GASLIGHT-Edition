package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;

/** Bounded, read-only paired route terrain inspection. No implicit generation. */
public final class Phase3TerrainMatcher {
    private Phase3TerrainMatcher() {}
    @FunctionalInterface public interface Sampler { double score(Chunk source,Chunk destination); }
    public record Verdict(boolean accepted, String reason, double minimum,
                          double average, int sampledPairs) {}
    /** Evaluate EVERY chunk column touched by every route facet; cache common pairs.
     * A NaN, out-of-range, exception or budget overrun vetoes the entire candidate.
     */
    public static Verdict evaluate(List<Segment> route,int dxChunks,int dzChunks,
                                   Sampler sampler,double minAllowed,double avgAllowed,
                                   int budget) {
        if(route==null||route.isEmpty()||sampler==null||budget<1||
           !Double.isFinite(minAllowed)||!Double.isFinite(avgAllowed)||
           minAllowed<0||minAllowed>1||avgAllowed<0||avgAllowed>1)
            return new Verdict(false,"invalid terrain policy",Double.NaN,Double.NaN,0);
        Map<Chunk,Double> memo=new LinkedHashMap<>();
        try {
            for(Segment segment:route) {
                if(segment==null||segment.a()==null||segment.b()==null||
                   !Double.isFinite(segment.a().x())||!Double.isFinite(segment.a().z())||
                   !Double.isFinite(segment.b().x())||!Double.isFinite(segment.b().z())||
                   segment.length()<1e-7)return rejected("invalid route segment",memo);
                // Fixed fractional samples can jump over intermediate chunk columns
                // on long or diagonal segments. A supercover scan includes all
                // columns touched even at their edges/corners. Do not interpolate
                // missing samples as if they were certified.
                Set<Chunk> touched=Geometry.chunksTouched(segment,0,budget);
                for(Chunk a:touched) {
                    if(memo.containsKey(a))continue;
                    if(memo.size()>=budget)return rejected("terrain inspection budget exceeded",memo);
                    Chunk b=new Chunk(Math.addExact(a.x(),dxChunks),Math.addExact(a.z(),dzChunks));
                    double value=sampler.score(a,b);
                    if(!Double.isFinite(value)||value<0||value>1)
                        return rejected("missing or invalid terrain sample",memo);
                    memo.put(a,value);
                    if(value<minAllowed)return rejected("terrain discontinuity threshold",memo);
                }
            }
        } catch(RuntimeException ex) {
            return rejected("terrain probe failed: "+ex.getClass().getSimpleName(),memo);
        }
        if(memo.isEmpty())return rejected("no terrain samples",memo);
        double min=1,sum=0;
        for(double value:memo.values()){min=Math.min(min,value);sum+=value;}
        double avg=sum/memo.size();
        return new Verdict(avg>=avgAllowed,avg>=avgAllowed?"entire route sampled":"average terrain mismatch",
            min,avg,memo.size());
    }
    private static Verdict rejected(String msg, Map<Chunk,Double> samples) {
        return new Verdict(false,msg,Double.NaN,Double.NaN,samples.size());
    }
}
