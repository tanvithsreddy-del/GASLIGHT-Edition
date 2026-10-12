package dev.kondalabs.gaslight;

import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.RemoteViewRaycaster.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Offline tests for a real bounded binary transport format and source-side occlusion. */
public final class Phase12WireTests {
    private static long n;
    static void ok(boolean passed,String why){n++;if(!passed)throw new AssertionError(why);}
    static void bad(byte[] bytes,String why){
        try{PortalSnapshotWire.decode(bytes);throw new AssertionError("accepted "+why);}
        catch(IOException expected){n++;}
    }
    public static void main(String[] argv)throws Exception {
        UUID id=UUID.randomUUID(),wrong=UUID.randomUUID();
        Surface a=new Surface(new V(1,65,1),new V(1,0,0),8,10);
        Surface b=new Surface(new V(1025,65,1025),new V(1,0,0),8,10);
        Passage passage=new Passage(a,b,id,7);
        PortalPreviewCore.Box box=new PortalPreviewCore.Box(1008,48,1008,32,32,32);
        try {new PortalPreviewCore.Box(Integer.MAX_VALUE,10,2,2,2,2);throw new AssertionError("box integer overflow accepted");}
        catch(IllegalArgumentException expected){n++;}
        try {new PortalPreviewCore.Box(0,4_095,0,1,2,1);throw new AssertionError("unbounded Y accepted");}
        catch(IllegalArgumentException expected){n++;}
        var scene=PortalPreviewCore.capture(box,(x,y,z)->y<65?0xff778855:0).orElseThrow();
        var p=new PortalPreviewCore.Published(id,7,passage,scene,200);
        byte[] wire=PortalSnapshotWire.encode(PortalSnapshotWire.Message.snapshot(p));
        ok(wire.length<PortalSnapshotWire.MAX_BYTES,"wire budget");
        var decoded=PortalSnapshotWire.decode(wire);
        ok(!decoded.retired()&&decoded.generation()==7,"scene opcode");
        ok(decoded.snapshot().owner().equals(id),"owner");
        ok(decoded.snapshot().tick()==200,"tick");
        ok(decoded.snapshot().passage().entrance().centre().equals(a.centre()),"geometry");
        ok(decoded.snapshot().passage().exit().centre().equals(b.centre()),"mapping");
        ok(Arrays.equals(decoded.snapshot().scene().voxels(),scene.voxels()),"voxel parity");
        ok(decoded.snapshot().scene().box().equals(box),"geometry of box");
        var cameraOverWire=new Camera(new V(1,65,5),new V(0,0,-1),
            new V(1,0,0),new V(0,1,0),70,16d/9d);
        var projectedOverWire=PortalPreviewCore.renderOccluded(
            decoded.snapshot().passage(),decoded.snapshot().scene(),cameraOverWire,
            40,24,64,(x,y,z)->Voxel.air(),96);
        ok(projectedOverWire.visiblePixels()>0,"wire -> camera -> remote voxels rendered");
        byte[] retired=PortalSnapshotWire.encode(PortalSnapshotWire.Message.retire(id,7));
        ok(retired.length<wire.length,"retire small");
        ok(PortalSnapshotWire.decode(retired).retired(),"retirement roundtrip");
        var clock=new AtomicLong(10_000);
        var inbox=new PortalSnapshotWire.Inbox(clock::get,3_000);
        ok(!inbox.accept(decoded,wrong),"wrong-player packet veto");
        ok(inbox.accept(decoded,id),"accept correct owner");
        ok(inbox.get(id).isPresent(),"active snapshot visible");
        clock.addAndGet(2_000);
        ok(inbox.get(id).isPresent(),"retained under expiry");
        ok(inbox.accept(PortalSnapshotWire.decode(retired),id),"retirement accepted");
        ok(inbox.get(id).isEmpty(),"retire clears");
        ok(!inbox.accept(decoded,id),"retired epoch never resurrects");
        var newer=PortalSnapshotWire.Message.snapshot(new PortalPreviewCore.Published(id,8,
            new Passage(a,b,id,8),scene,300));
        ok(inbox.accept(newer,id),"newer generation accepted");
        ok(!inbox.accept(PortalSnapshotWire.decode(retired),id),"stale retire veto");
        clock.addAndGet(3_001);
        ok(inbox.get(id).isEmpty(),"stale snapshot expires");
        ok(inbox.accept(newer,id),"same generation refresh after expiry allowed");
        ok(inbox.get(id).isPresent(),"fresh snapshot again visible");
        inbox.reset();ok(inbox.get(id).isEmpty(),"disconnect resets");
        byte[] broken=wire.clone();broken[0]^=1;bad(broken,"magic");
        broken=wire.clone();broken[4]=99;bad(broken,"version");
        broken=wire.clone();broken[5]=77;bad(broken,"opcode");
        broken=wire.clone();broken[wire.length-3]^=0x10;bad(broken,"crc/corruption");
        bad(Arrays.copyOf(wire,wire.length-4),"truncated stream");
        broken=wire.clone();broken[wire.length-1]^=0x01;bad(broken,"trailing corruption");
        bad(new byte[PortalSnapshotWire.MAX_BYTES+1],"over max payload");
        var maximumBox=new PortalPreviewCore.Box(0,0,0,64,32,64);
        var maxVoxels=new int[maximumBox.size()];
        Random noisy=new Random(123);
        for(int i=0;i<maxVoxels.length;i++){
            int code=noisy.nextInt();
            maxVoxels[i]=code==PortalPreviewCore.UNKNOWN?0:code;
        }
        try{
            var bigScene=new PortalPreviewCore.Scene(maximumBox,maxVoxels);
            PortalSnapshotWire.encode(PortalSnapshotWire.Message.snapshot(
                new PortalPreviewCore.Published(id,7,passage,bigScene,201)));
            throw new AssertionError("incompressible packet should be bounded");
        }catch(IOException expected){n++;}
        byte[] padded=Arrays.copyOf(retired,retired.length+1);bad(padded,"retirement trailing");
        Random r=new Random(0xD007);
        for(int i=0;i<512;i++){
            byte[] altered=wire.clone();
            // Modify a random signed byte in the compressed voxel stream (exclude last 4-byte checksum tail).
            int pos=wire.length-8-r.nextInt(Math.min(64,wire.length-16));
            altered[pos]^=(byte)(1<<r.nextInt(8));
            try{
                var out=PortalSnapshotWire.decode(altered);
                ok(Arrays.equals(out.snapshot().scene().voxels(),scene.voxels()),"mutation not accepted changed voxels");
            }catch(IOException expected){n++;}
        }
        V eye=new V(0,65,3),direction=new V(0,0,-1);
        var air=(RemoteViewRaycaster.WorldSampler)(x,y,z)->Voxel.air();
        var foreground=(RemoteViewRaycaster.WorldSampler)(x,y,z)->z==2?Voxel.opaque(0):Voxel.air();
        var unknown=(RemoteViewRaycaster.WorldSampler)(x,y,z)->z==2?null:Voxel.air();
        ok(PortalForegroundOcclusion.clear(a,new V(1,65,4),direction,air,100),"clear foreground");
        ok(!PortalForegroundOcclusion.clear(a,new V(1,65,4),direction,foreground,100),"solid local veto");
        ok(!PortalForegroundOcclusion.clear(a,new V(1,65,4),direction,unknown,100),"unknown local veto");
        ok(!PortalForegroundOcclusion.clear(a,new V(100,65,4),direction,air,100),"outside plane veto");
        var camera=new Camera(new V(1,65,4),direction,new V(1,0,0),new V(0,1,0),70,16d/9d);
        var raw=PortalPreviewCore.render(passage,scene,camera,36,20,64);
        var visible=PortalPreviewCore.renderOccluded(passage,scene,camera,36,20,64,air,100);
        var blocked=PortalPreviewCore.renderOccluded(passage,scene,camera,36,20,64,foreground,100);
        ok(visible.visiblePixels()==raw.visiblePixels(),"clear optics preserve mask");
        ok(blocked.visiblePixels()<visible.visiblePixels(),"foreground clips remote pixels");
        for(int y=0;y<20;y++)for(int x=0;x<36;x++){
            ok(!blocked.visibleAt(x,y)||visible.visibleAt(x,y),"occluded is subset");
        }
        System.out.println("PHASE12_WIRE_AND_OCCLUSION_PASS: "+n+" assertions, "+wire.length+" compressed bytes");
    }
}
