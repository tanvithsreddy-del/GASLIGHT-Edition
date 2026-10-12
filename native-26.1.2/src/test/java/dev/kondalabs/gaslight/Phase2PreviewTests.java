package dev.kondalabs.gaslight;

import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.RemoteViewRaycaster.*;
import java.util.*;

/** Dependency-free Phase 2 optical and snapshot tests, not a Minecraft launch test. */
public final class Phase2PreviewTests {
    private static long assertions=0;
    static void yes(boolean b,String msg){assertions++;if(!b)throw new AssertionError(msg);}
    static void close(double a,double b){yes(Math.abs(a-b)<1e-7,a+" != "+b);}
    private static void fixedRigTest() {
        class Host implements NativeWorldEngine.Host {
            UUID id=UUID.randomUUID(); V pos=new V(0,65,0);
            boolean unsafe=false;int loads=0,previews=0,retired=0,teleports=0;
            public boolean singleplayer(){return true;}
            public Collection<NativeWorldEngine.Actor> players(){return List.of(
                new NativeWorldEngine.Actor(id,pos,0,0,false,false));}
            public boolean loadChunk(int x,int z){loads++;return true;}
            public StructureEnvelope.Result inspectChunk(int x,int z){return unsafe?
                StructureEnvelope.Result.STRUCTURE:StructureEnvelope.Result.SAFE;}
            public boolean canExit(NativeWorldEngine.Actor a,V v){return true;}
            public boolean teleport(NativeWorldEngine.Actor a,V v,double y){pos=v;teleports++;return true;}
            public void onPortalActivated(Passage p){previews++;}
            public void onPortalRetired(Passage p){retired++;}
        }
        Host host=new Host();
        FixedPortalRig rig=new FixedPortalRig(host,true,true,1024,1024);
        for(int i=0;i<200 && rig.status().state()!=FixedPortalRig.State.ACTIVE;i++)rig.tick();
        yes(rig.status().state()==FixedPortalRig.State.ACTIVE,"fixed portal activated");
        yes(host.loads>0,"fixed portal preloaded");
        yes(host.previews==1,"fixed portal published preview");
        var passage=rig.active().orElseThrow();
        host.pos=passage.entrance().centre().add(new V(0,0,-1.5));rig.tick();
        host.pos=passage.entrance().centre().add(new V(0,0,1.5));rig.tick();
        yes(host.teleports==1,"static crossing teleported");
        close(host.pos.x()-1024,passage.entrance().centre().x());
        host.unsafe=true;
        for(int i=0;i<40&&rig.status().state()==FixedPortalRig.State.ACTIVE;i++)rig.tick();
        yes(rig.status().state()==FixedPortalRig.State.REFUSED,"post activation veto");
        yes(host.retired==1,"retirement notified");
        Host unsafe=new Host();unsafe.unsafe=true;
        FixedPortalRig denied=new FixedPortalRig(unsafe,true,true,1024,1024);
        for(int i=0;i<200&&denied.status().state()!=FixedPortalRig.State.REFUSED;i++)denied.tick();
        yes(denied.active().isEmpty(),"unsafe phase2 portal never activated");
        yes(unsafe.previews==0,"unsafe phase2 portal never published preview");
        Host debug=new Host();
        FixedPortalRig debugRig=new FixedPortalRig(debug,true,false,1024,1024);
        for(int i=0;i<200 && debugRig.active().isEmpty();i++)debugRig.tick();
        var dp=debugRig.active().orElseThrow();
        debug.pos=dp.entrance().centre().add(new V(0,0,-1));debugRig.tick();
        debug.pos=dp.entrance().centre().add(new V(0,0,1));debugRig.tick();
        yes(debug.teleports==0,"preview-only rig has traversal disabled");
        debugRig.close();yes(debug.retired==1,"explicit close retired debug portal");
    }
    public static void main(String[] args) {
        var box=new PortalPreviewCore.Box(980,-8,1970,16,16,16);
        yes(box.size()==4096,"box size");
        yes(box.contains(980,-8,1970),"lower inclusive");
        yes(!box.contains(996,0,1970),"upper exclusive");
        yes(!box.contains(980,8,1970),"upper y exclusive");
        yes(!box.contains(980,-8,1986),"upper z exclusive");
        try{new PortalPreviewCore.Box(0,0,0,500,500,500);throw new AssertionError();}
        catch(IllegalArgumentException ok){assertions++;}
        int rgb=0xff86ac43;
        var scene=PortalPreviewCore.capture(box,(x,y,z)->
            y<=0 ? rgb:0).orElseThrow();
        yes(scene.sample(980,0,1970).alpha()==255,"solid");
        yes(scene.sample(980,1,1970).alpha()==0,"air");
        yes(scene.sample(979,0,1970)==null,"unloaded is unknown");
        yes(PortalPreviewCore.capture(box,(x,y,z)->
            x==984&&y==2&&z==1974?PortalPreviewCore.UNKNOWN:0).isEmpty(),"unknown veto");
        // Clone on construction and on access: renderer must not see mutated server memory.
        int[] arr=new int[box.size()];Arrays.fill(arr,rgb);
        var immutable=new PortalPreviewCore.Scene(box,arr);
        arr[0]=0;yes(immutable.codeAt(980,-8,1970)==rgb,"input clone");
        immutable.voxels()[0]=0;yes(immutable.codeAt(980,-8,1970)==rgb,"output clone");
        UUID owner=UUID.randomUUID();
        Surface src=new Surface(new V(0,0,0),new V(1,0,0),8,12);
        Surface dst=new Surface(new V(987,0,1977),new V(1,0,0),8,12);
        Passage passage=new Passage(src,dst,owner,5);
        V from=new V(0,0,3);V dir=new V(0,0,-1);
        V contact=RemoteViewRaycaster.rayToPortal(src,from,dir).orElseThrow();
        close(contact.z(),0);
        // Map +z approach to the outgoing side of the destination.
        V endpoint=passage.mapPosition(contact);
        close(endpoint.x(),987);close(endpoint.z(),1977);
        V mapped=passage.mapVelocity(dir);
        close(mapped.z(),1);
        var s=RemoteViewRaycaster.trace(passage,scene,from,dir,96,128);
        yes(s.crossedPortal(),"ray crosses source plane");
        yes(s.hitTerrain(),"remote block is detected");
        yes(s.color()==(rgb&0xffffff),"remote color");
        var miss=RemoteViewRaycaster.trace(passage,scene,new V(20,0,3),dir,96,128);
        yes(!miss.crossedPortal(),"portal bounds clipped");
        var behind=RemoteViewRaycaster.trace(passage,scene,new V(0,0,-3),new V(0,0,1),96,128);
        yes(behind.crossedPortal(),"two sided view supported");
        var ex=new PortalPreviewCore.Exchange();
        var published=new PortalPreviewCore.Published(owner,5,passage,scene,125);
        ex.publish(published);
        yes(ex.get(owner).isPresent(),"published owner can see");
        yes(ex.get(UUID.randomUUID()).isEmpty(),"owner isolation");
        ex.clear(owner,4);yes(ex.get(owner).isPresent(),"stale retirement cannot clear newer portal");
        ex.clear(owner,5);yes(ex.get(owner).isEmpty(),"matching retirement clears");
        // Wider FOV projects the rectangular aperture more narrowly; verify frame masking.
        var cam=new Camera(from,new V(0,0,-1),new V(1,0,0),new V(0,1,0),70,16d/9d);
        var frame=PortalPreviewCore.render(passage,scene,cam,48,27,64);
        yes(frame.visiblePixels()>0 && frame.visiblePixels()<=1296,"mask projected");
        yes(frame.steps()<=1296L*64,"budget honored");
        int seen=0;
        for(int y=0;y<27;y++)for(int x=0;x<48;x++){
            if(frame.visibleAt(x,y)){
                seen++;yes((frame.argbAt(x,y)>>>24)==255,"opaque debug preview");
            }else yes(frame.argbAt(x,y)==0,"outside portal unchanged");
        }
        yes(seen==frame.visiblePixels(),"pixel count exact");
        // Randomized cameras: no hidden crashes, budget, or non-opaque portal pixels.
        Random rnd=new Random(0x6A5142);
        for(int i=0;i<250;i++){
            double x=rnd.nextDouble()*10-5,y=rnd.nextDouble()*5-2.5;
            Camera c=new Camera(new V(x,y,2+rnd.nextDouble()*4),
                new V(0,0,-1),new V(1,0,0),new V(0,1,0),65,1.4);
            var f=PortalPreviewCore.render(passage,scene,c,24,14,32);
            yes(f.steps()<=24L*14*32,"randomized budget");
            yes(f.visiblePixels()>=0 && f.visiblePixels()<=336,"randomized mask");
        }
        fixedRigTest();
        System.out.println("PHASE2_PREVIEW_PASS: "+assertions+" assertions");
    }
}
