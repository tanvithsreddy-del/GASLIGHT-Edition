package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Standalone microbenchmarks only. NOT Minecraft FPS, TPS, GPU, or game performance. */
public final class Phase4Benchmarks {
    static final int ROUNDS=9;
    static volatile long checksum;
    static final UUID ID=UUID.fromString("0a000000-1111-2222-3333-444444444444");
    interface Work {void run();}
    static void bench(String label, int ops, Work work){
        long[] nanos=new long[ROUNDS];
        for(int i=0;i<ROUNDS+4;i++){
            long started=System.nanoTime();
            work.run();
            long dur=System.nanoTime()-started;
            if(i>=4)nanos[i-4]=dur;
        }
        Arrays.sort(nanos);
        System.out.printf(Locale.ROOT,
            "BENCH,%s,operations_per_round=%d,median_ns_per_op=%.2f,p95_ns_per_op=%.2f,range_min_ms=%.3f,range_max_ms=%.3f%n",
            label,ops,nanos[ROUNDS/2]/(double)ops,nanos[ROUNDS-1]/(double)ops,
            nanos[0]/1e6,nanos[ROUNDS-1]/1e6);
    }
    public static void main(String[] args){
        Runtime rt=Runtime.getRuntime();
        System.out.printf("PHASE4_BENCH_ENV,java=%s,processors=%d,heap_max_bytes=%d%n",
            System.getProperty("java.version"),rt.availableProcessors(),rt.maxMemory());
        Segment diagonal=new Segment(new V2(-150.4,-250.1),new V2(570.5,620.7));
        bench("geometry_supercover",4000,()->{
            for(int j=0;j<4000;j++)checksum+=Geometry.chunksTouched(diagonal,0,400).size();
        });
        Surface a=new Surface(new V(0,128,-30),new V(1,0,0),24,384);
        Surface b=new Surface(new V(1_000_000,128,-1_000_030),new V(1,0,0),24,384);
        List<Passage> bundle=List.of(new Passage(a,b,ID,1));
        bench("paired_structure_gate",1200,()->{
            for(int j=0;j<1200;j++)checksum+=Phase3SafetyGate.check(bundle,1,500,(x,z)->StructureEnvelope.Result.SAFE).checkedColumns();
        });
        Camera cam=Camera.fromYawPitch(0,65.62,0,25,23,70,16.0/9,128);
        bench("camera_visibility",80000,()->{
            for(int j=0;j<80000;j++)if(Phase3Visibility.hidden(cam,a,8))checksum++;
        });
        List<Segment> route=List.of(new Segment(new V2(0,0),new V2(160,0)));
        bench("paired_terrain_gate",3000,()->{
            for(int j=0;j<3000;j++)checksum+=Phase3TerrainMatcher.evaluate(route,70000,-90000,(s,d)->1,.7,.8,100).sampledPairs();
        });
        System.out.println("BENCH_END,blackhole="+checksum);
    }
}
