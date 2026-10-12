package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.Geometry.*;

public class NativeTests {
  static long checks=0;
  static void ok(boolean v,String message){ checks++; if(!v) throw new AssertionError(message+" at assertion "+checks); }
  static boolean close(double a,double b){return Math.abs(a-b)<1e-8;}
  static void portalProperties(){
    Random random=new Random(12718);
    for(int j=0;j<60000;j++){
      double angle=random.nextDouble()*Math.PI*2;
      V tangent=new V(Math.cos(angle),0,Math.sin(angle));
      V a=new V(random.nextDouble()*1000,128,random.nextDouble()*1000);
      V b=new V(random.nextDouble()*1000+2_000_000,128,random.nextDouble()*1000-2_000_000);
      Passage passage=new Passage(new Surface(a,tangent,12,384),new Surface(b,tangent,12,384),UUID.randomUUID(),j);
      V dx=new V((random.nextDouble()-.5)*10,(random.nextDouble()-.5)*100,(random.nextDouble()-.5)*8);
      V moved=passage.reversed().mapPosition(passage.mapPosition(a.add(dx)));
      ok(moved.sub(a.add(dx)).norm()<1e-7,"inversion of positional mapping");
      V transformed=passage.reversed().mapVelocity(passage.mapVelocity(dx));
      ok(transformed.sub(dx).norm()<1e-7,"velocity reversal");
      ok(close(passage.mapVelocity(dx).norm(),dx.norm()),"speed preserved");
      V p0=a.add(passage.entrance().normal().mul(1.0));
      V p1=a.add(passage.entrance().normal().mul(-1.0));
      Optional<Crossing> crossing=NativePortal.intersect(passage,p0,p1,.31,.91);
      ok(crossing.isPresent(),"plane swept crossing not detected");
      if(crossing.isPresent()) ok(crossing.get().exitPoint().sub(b).norm()<5,"exit not near counterpart");
      ok(NativePortal.intersect(passage,p0,p0.add(tangent.mul(.5)),.31,.91).isEmpty(),"parallel movement crossed");
    }
  }
  static void geometryProperties(){
    Random rng=new Random(72);
    for(int i=0;i<2500;i++){
      var cam=Geometry.Camera.fromYawPitch(rng.nextDouble()*100,60,rng.nextDouble()*100,
          rng.nextDouble()*360,rng.nextDouble()*180-90,30+rng.nextDouble()*80,1.0+rng.nextDouble()*2,100+rng.nextDouble()*150);
      V2 base=new V2(cam.eye().x(),cam.eye().z());
      List<V2> projected=cam.projectedCorners();
      List<V2> loop=Geometry.enclosingLoop(base,projected,24,16,128);
      ok(Geometry.polygonEncloses(loop,base),"player not surrounded");
      for(V2 f:projected)ok(Geometry.polygonEncloses(loop,f),"frustum corner not protected");
    }
  }
  static void structureProperties(){
    Surface s=new Surface(new V(0,128,0),new V(1,0,0),12,384);
    var unknown=StructureEnvelope.audit(s,1,200,(x,z)->(x==0&&z==0)?StructureEnvelope.Result.UNKNOWN:StructureEnvelope.Result.SAFE);
    ok(!unknown.accepted(),"unknown chunk accepted");
    var safe=StructureEnvelope.audit(s,1,200,(x,z)->StructureEnvelope.Result.SAFE);
    ok(safe.accepted(),"safe candidate rejected");
    ok(safe.checked()>=9,"diagonal halo not checked");
    var structure=StructureEnvelope.audit(s,1,200,(x,z)->(x==1&&z==1)?StructureEnvelope.Result.STRUCTURE:StructureEnvelope.Result.SAFE);
    ok(!structure.accepted(),"diagonal structure not vetoed");
    var cap=StructureEnvelope.audit(s,7,2,(x,z)->StructureEnvelope.Result.SAFE);
    ok(!cap.accepted(),"over-budget scan accepted");
  }
  static void schedulerProperties(){
    NativeScheduler schedule=new NativeScheduler(20,3,60);schedule.enable(true);
    Passage p=PortalCandidate.fromRear(new V(0,128,0),0,0,2_000_000,2_000_000,80,12,384,1,UUID.randomUUID());
    for(int i=0;i<200;i++){
      schedule.tick();
      if(schedule.isEpoch())ok(schedule.admit(p,pp->true),"epoch admission failed");
      ok(schedule.active().size()<=3,"too many generations");
      for(var gen:schedule.active())ok(schedule.currentTick()-gen.createdAt()<60,"stale generation");
    }
    schedule.enable(false);ok(schedule.active().isEmpty(),"disable did not retire generations");
  }
  static class Host implements NativeWorldEngine.Host {
    final UUID id=UUID.randomUUID();
    V pos=new V(0,65,0);
    double yaw=0;
    int loads=0,teleports=0;
    boolean unsafe=false;
    @Override public Collection<NativeWorldEngine.Actor> players(){return List.of(new NativeWorldEngine.Actor(id,pos,yaw,0,false,false));}
    @Override public boolean loadChunk(int x,int z){loads++;return true;}
    @Override public StructureEnvelope.Result inspectChunk(int x,int z){return unsafe?StructureEnvelope.Result.UNKNOWN:StructureEnvelope.Result.SAFE;}
    @Override public boolean canExit(NativeWorldEngine.Actor a,V p){return true;}
    @Override public boolean teleport(NativeWorldEngine.Actor a,V p,double yaw){teleports++;pos=p;this.yaw=yaw;return true;}
    @Override public boolean singleplayer(){return true;}
  }
  static void integration(){
    Host host=new Host();NativeWorldEngine engine=new NativeWorldEngine(host,456,true);
    for(int t=0;t<70;t++)engine.tick();
    var s=engine.snapshot();
    ok(s.generations()>=3,"not generating at epoch");
    ok(host.loads>0,"not preloading chunks");
    ok(s.activated()>0,"not creating a live portal");
    // Actual simulated crossing of first paired world boundary, after fast relocation outside threshold.
    host.pos=new V(0,65,-79);engine.tick();
    host.pos=new V(0,65,-81);engine.tick();
    ok(host.teleports==1,"activated native portal did not teleport player");
    ok(Math.abs(host.pos.x())>=999_000,"portal did not map to a distant destination");
    // Holding the new location cannot bounce back across the paired portal.
    for(int i=0;i<15;i++)engine.tick();
    ok(host.teleports==1,"unexpected bounce-back teleport");
    Host blocked=new Host();blocked.unsafe=true;
    NativeWorldEngine fail=new NativeWorldEngine(blocked,42,true);
    for(int t=0;t<120;t++)fail.tick();
    ok(fail.snapshot().activated()==0,"unsafe structure allowed");
    ok(fail.snapshot().refused()>0,"unsafe structure did not produce refusal");
    engine.enabled(false);ok(engine.snapshot().active()==0,"disabled engine kept portals");
  }
  static void remoteRaycastProperties(){
    UUID owner=UUID.randomUUID();
    Passage portal=new Passage(new Surface(new V(0,64,0),new V(1,0,0),12,24),
                  new Surface(new V(2_000_000,64,0),new V(1,0,0),12,24),owner,1);
    V observer=new V(0,64,3); V ray=new V(0,0,-1);
    ok(RemoteViewRaycaster.rayToPortal(portal.entrance(),observer,ray).isPresent(),"center portal ray missing");
    ok(RemoteViewRaycaster.rayToPortal(portal.entrance(),observer,new V(0,0,1)).isEmpty(),"ray behind camera hit portal");
    var hit=RemoteViewRaycaster.trace(portal,(x,y,z)->x==2_000_000&&z==2?
        RemoteViewRaycaster.Voxel.opaque(0xAA5522):RemoteViewRaycaster.Voxel.air(),observer,ray,64,64);
    // Ray leaves the exit on the positive-Z side (because the normal flips).
    ok(hit.crossedPortal(),"remote ray did not enter portal");
    ok(hit.hitTerrain(),"remote ray did not hit remote terrain");
    ok(hit.color()==0xAA5522,"remote material color incorrect");
    ok(hit.voxelSteps()<=64,"raycast exceeded step budget");
    var image=RemoteViewRaycaster.sampleImage(portal,(x,y,z)->RemoteViewRaycaster.Voxel.opaque(0x808080),
      new RemoteViewRaycaster.Camera(observer,new V(0,0,-1),new V(1,0,0),new V(0,1,0),70,16d/9),
      12,8,100,80);
    ok(image.length==96,"frame buffer size");
    long visible=Arrays.stream(image).filter(RemoteViewRaycaster.Sample::crossedPortal).count();
    ok(visible>20,"no visible portal pixels");
    try{RemoteViewRaycaster.sampleImage(portal,(x,y,z)->RemoteViewRaycaster.Voxel.air(),
        new RemoteViewRaycaster.Camera(observer,new V(0,0,-1),new V(1,0,0),new V(0,1,0),70,1.7),
        512,512,10,80);throw new AssertionError("image budget ignored");}
    catch(IllegalArgumentException expected){ok(true,"pixel budget rejected");}
  }
  public static void main(String[] args){
    long begin=System.nanoTime();portalProperties();geometryProperties();structureProperties();schedulerProperties();integration();remoteRaycastProperties();
    System.out.println("PASSED "+checks+" standalone assertions in "+((System.nanoTime()-begin)/1_000_000)+" ms");
  }
}
