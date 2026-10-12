package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.Phase3DestinationProfile.*;

/** Evidence for the destination system only. Never construed as Minecraft gameplay. */
public final class Phase3DestinationTests {
    private static long assertions;
    private static void yes(boolean b,String why){assertions++;if(!b)throw new AssertionError(why+" at "+assertions);}
    private static Column column(int y,Material m,Fluid fluid,String biome){
        return new Column(y,m,fluid,true,biome,"minecraft:"+m.name().toLowerCase(Locale.ROOT),
            fluid==Fluid.NONE?Integer.MIN_VALUE:y+2);
    }
    private static Profile all(Column column){return new Profile(Collections.nCopies(9,column));}
    private static Profile replace(Profile p,int at,Column c){
        ArrayList<Column> items=new ArrayList<>(p.columns());items.set(at,c);return new Profile(items);
    }
    private static void fingerprints(){
        Column grass=column(64,Material.SOIL,Fluid.NONE,"minecraft:plains");
        Profile p=all(grass);
        yes(Phase3DestinationProfile.compare(p,p).accepted(),"identical fingerprint accepted");
        yes(Math.abs(Phase3DestinationProfile.compare(p,p).score()-1)<1e-12,"identical score one");
        yes(!Phase3DestinationProfile.compare(null,p).accepted(),"null source veto");
        yes(!Phase3DestinationProfile.compare(p,null).accepted(),"null destination veto");
        yes(!Phase3DestinationProfile.compare(p,replace(p,2,column(80,Material.SOIL,Fluid.NONE,"minecraft:plains"))).accepted(),"16-block cliff veto");
        yes(!Phase3DestinationProfile.compare(p,replace(p,2,column(70,Material.SOIL,Fluid.NONE,"minecraft:plains"))).accepted(),"local slope veto");
        yes(!Phase3DestinationProfile.compare(p,replace(p,2,column(64,Material.SOIL,Fluid.WATER,"minecraft:plains"))).accepted(),"wet/dry split veto");
        yes(!Phase3DestinationProfile.compare(p,replace(p,2,column(64,Material.SOIL,Fluid.LAVA,"minecraft:plains"))).accepted(),"lava veto");
        yes(!Phase3DestinationProfile.compare(p,all(column(64,Material.SOIL,Fluid.NONE,"minecraft:desert"))).accepted(),"biome mismatch veto");
        yes(Phase3DestinationProfile.compare(p,all(column(64,Material.SOIL,Fluid.NONE,"minecraft:sunflower_plains"))).accepted(),"biome family compatible");
        Column water=column(62,Material.SAND,Fluid.WATER,"minecraft:ocean");
        yes(!Phase3DestinationProfile.compare(all(water),all(new Column(62,Material.SAND,Fluid.WATER,true,"minecraft:ocean","minecraft:sand",67))).accepted(),"water-top mismatch veto");
        yes(Phase3DestinationProfile.compare(all(water),all(water)).accepted(),"same water level valid");
        Column custom=new Column(64,Material.OTHER,Fluid.NONE,true,"minecraft:plains","example:machine_1",Integer.MIN_VALUE);
        Column other=new Column(64,Material.OTHER,Fluid.NONE,true,"minecraft:plains","example:machine_2",Integer.MIN_VALUE);
        yes(!Phase3DestinationProfile.compare(all(custom),all(other)).accepted(),"modded block identifier mismatch veto");
        Column cramped=new Column(64,Material.SOIL,Fluid.NONE,false,"minecraft:plains");
        yes(!Phase3DestinationProfile.compare(p,replace(replace(p,1,cramped),2,cramped)).accepted(),"repeated headroom mismatch veto");
        yes(!Phase3DestinationProfile.compare(p,replace(p,0,cramped)).accepted(),"even one blocked exit sample is unsafe");
        yes(!Phase3DestinationProfile.compare(all(cramped),all(cramped)).accepted(),"identical blocked destination is not safe");
        yes(Phase3DestinationProfile.capture(i->Optional.of(grass)).isPresent(),"nine profile cells read");
        yes(Phase3DestinationProfile.capture(i->i==7?Optional.empty():Optional.of(grass)).isEmpty(),"missing sample veto");
        yes(Phase3DestinationProfile.capture(i->{throw new RuntimeException();}).isEmpty(),"failed sampler veto");
        yes(Phase3DestinationProfile.capture(i->null).isEmpty(),"null sampler result veto");
        yes(Phase3DestinationProfile.capture(null).isEmpty(),"missing sampler veto");
        yes(Phase3DestinationProfile.materialFromId("minecraft:grass_block")==Material.SOIL,"grass category");
        yes(Phase3DestinationProfile.materialFromId("minecraft:water")==Material.OTHER,"water not guessed as solid ground");
        yes(Phase3DestinationProfile.materialFromId("Block{minecraft:stone_stairs}")!=Material.AIR,
            "stairs are not misclassified as air");
        yes(Phase3DestinationProfile.materialFromId("Block{minecraft:cave_air}")==Material.AIR,
            "actual cave air is recognized");
        yes(Phase3DestinationProfile.materialFromId(null)==Material.UNKNOWN,"null block unknown");
        try{p.columns().set(0,cramped);throw new AssertionError("profile should be immutable");}catch(UnsupportedOperationException expected){assertions++;}
        for(int dy=-12;dy<=12;dy++){
            Profile q=all(column(64+dy,Material.SOIL,Fluid.NONE,"minecraft:plains"));
            Match result=Phase3DestinationProfile.compare(p,q);
            yes(Double.isFinite(result.score())==(Math.abs(dy)<=6),"score finite only when hard height veto clears");
            if(Math.abs(dy)>6)yes(!result.accepted(),"large height step veto");
        }
    }
    private static void discovery(){
        V2 a=new V2(0,0),b=new V2(60,40);
        Chunk first=new Chunk(70000,-80000);
        List<Phase3DestinationSelector.Candidate> found=Phase3DestinationSelector.candidates(
            List.of(nullSafe(first),first,new Chunk(0,1),new Chunk(Integer.MAX_VALUE,0),new Chunk(80000,-90000)),
            4,42,3,a,b);
        yes(found.size()==4,"candidate limit and fallbacks");
        yes(found.get(0).offset().equals(first) && found.get(0).catalog(),"catalog first");
        yes(found.get(1).offset().equals(new Chunk(80000,-90000))&&found.get(1).catalog(),"valid second catalog retained");
        yes(new HashSet<>(found.stream().map(x->x.offset()).toList()).size()==found.size(),"deduplication");
        yes(found.equals(Phase3DestinationSelector.candidates(List.of(first,first,new Chunk(0,1),new Chunk(Integer.MAX_VALUE,0),new Chunk(80000,-90000)),4,42,3,a,b)),"determinism");
        yes(Phase3DestinationSelector.candidates(List.of(first),0,0,0,a,b).isEmpty(),"invalid budget");
        yes(Phase3DestinationSelector.candidates(List.of(first),2,0,0,new V2(Double.NaN,0),b).isEmpty(),"nonfinite source");
        yes(Phase3DestinationSelector.candidates(List.of(first),2,0,0,new V2(28_999_990,0),b).stream().noneMatch(x->x.offset().equals(first)),"out-of-border endpoint");
        Random random=new Random(0xABCD);
        for(int i=0;i<2000;i++){
            int bound=1+random.nextInt(8);
            List<Phase3DestinationSelector.Candidate> list=Phase3DestinationSelector.candidates(
                List.of(first,new Chunk(-70000,80000)),bound,random.nextLong(),i,a,b);
            yes(list.size()<=bound && !list.isEmpty(),"bounded candidate selection");
            for(var c:list){
                long dx=Math.abs((long)c.offset().x()),dz=Math.abs((long)c.offset().z());
                yes(Math.max(dx,dz)>=64&&Math.max(dx,dz)<=1_800_000,"bounded chunk offset");
                yes(Math.abs(16.0*c.offset().x())<29_000_000,"border safe X");
            }
        }
    }
    private static Chunk nullSafe(Chunk c){return c;}
    private static final class Host implements Phase3Engine.Host {
        final UUID id=UUID.fromString("00000000-0000-0000-0000-000000000090");
        final NativeWorldEngine.Actor actor=new NativeWorldEngine.Actor(id,new V(0,70,0),0,0,false,false);
        boolean missing=false,unknown=false,breakOnRecheck=false;
        final Set<Chunk> firstReads=new HashSet<>();
        int probes=0,stages=0;
        final Profile compatible=all(column(69,Material.SOIL,Fluid.NONE,"minecraft:plains"));
        final Profile desert=all(column(69,Material.SAND,Fluid.NONE,"minecraft:desert"));
        @Override public boolean singleplayer(){return true;}
        @Override public Collection<NativeWorldEngine.Actor> players(){return List.of(actor);}
        @Override public boolean loadChunk(int x,int z){return false;}
        @Override public boolean canExit(NativeWorldEngine.Actor p,V t){return false;}
        @Override public boolean teleport(NativeWorldEngine.Actor p,V t,double yaw){return false;}
        @Override public StructureEnvelope.Result inspectChunk(int x,int z){
            if(unknown&&x>70000)return StructureEnvelope.Result.UNKNOWN;
            return StructureEnvelope.Result.SAFE;
        }
        @Override public boolean requireDetailedTerrainProfiles(){return true;}
        @Override public Optional<Profile> terrainProfile(Chunk c){
            probes++;
            if(breakOnRecheck && !firstReads.add(c) && c.x()>75000)return Optional.of(desert);
            if(missing && c.x()>70000)return Optional.empty();
            return Optional.of(c.x()>69500&&c.x()<70500?desert:compatible);
        }
        @Override public List<Chunk> preparedDestinationOffsets(NativeWorldEngine.Actor p){
            return List.of(new Chunk(70000,-80000),new Chunk(80000,-90000));
        }
        @Override public double terrainScore(Chunk a,Chunk b){throw new AssertionError("legacy height score must not be used");}
        @Override public boolean authoritativeCamera(NativeWorldEngine.Actor p){return true;}
        @Override public Optional<Geometry.Camera> camera(NativeWorldEngine.Actor p){return Optional.of(
            Geometry.Camera.fromYawPitch(0,71.62,0,0,0,100,16.0/9,128));}
        @Override public Optional<Phase3CameraWitness.Witness> cameraWitness(NativeWorldEngine.Actor p,long tick){
            return Optional.of(new Phase3CameraWitness.Witness(id,tick,tick,tick,
                camera(p).orElseThrow(),true,true));
        }
        @Override public Optional<Phase3RecoveryJournal.Store> recoveryJournal(){return Optional.of(new Phase3RecoveryJournal.TestMemoryStore());}
        @Override public Optional<Phase3Engine.VerticalSpan> verticalSpan(){return Optional.of(new Phase3Engine.VerticalSpan(-64,320));}
        @Override public Optional<Phase3Publication.Transaction> stagePublication(List<Passage> passages){
            stages++;return Optional.of(new Phase3Publication.Transaction(){
                public boolean staged(){return true;}
                public boolean commit(){return true;}
                public boolean abort(){return true;}
            });
        }
    }
    private static void integration(){
        Host host=new Host();
        Phase3Engine engine=new Phase3Engine(host,new Phase3Engine.Settings(true,false,false,2,16,128,8),913);
        for(int tick=0;tick<20;tick++)engine.tick();
        yes(engine.status().planned()==1,"rich-profile selector plans candidate");
        yes(engine.logical().orElseThrow().destinationDx()==80000*16,"reject desert catalog and choose compatible catalog");
        yes(engine.logical().orElseThrow().destinationDz()==-90000*16,"correct paired destination shift");
        yes(host.probes>0 && host.probes<=128,"profile sample work globally budgeted");
        engine.close();
        Host missing=new Host();missing.missing=true;
        Phase3Engine fail=new Phase3Engine(missing,new Phase3Engine.Settings(true,false,false,2,16,128,8),913);
        for(int t=0;t<20;t++)fail.tick();
        yes(fail.status().planned()==0,"missing far chunk profile cannot pass");
        fail.close();
        Host unknown=new Host();unknown.unknown=true;
        Phase3Engine unsafe=new Phase3Engine(unknown,new Phase3Engine.Settings(true,false,false,2,16,128,8),913);
        for(int t=0;t<20;t++)unsafe.tick();
        yes(unsafe.status().planned()==0,"unknown far structure data cannot pass");
        unsafe.close();
        // This host changes already-read snapshots on the next inspection. A
        // physical commit must perform fresh sampling and refuse its changed data.
        Host changed=new Host();
        changed.breakOnRecheck=true;
        Phase3Engine recheck=new Phase3Engine(changed,
            new Phase3Engine.Settings(true,true,false,2,16,128,8),913);
        for(int t=0;t<20;t++)recheck.tick();
        yes(recheck.status().published()==0 && changed.stages==0,
            "commit-time terrain revalidation detects changed world before staging");
        recheck.close();
    }
    public static void main(String[] args){
        fingerprints();discovery();integration();
        System.out.println("PHASE3_DESTINATION_PASS: "+assertions+" assertions");
    }
}
