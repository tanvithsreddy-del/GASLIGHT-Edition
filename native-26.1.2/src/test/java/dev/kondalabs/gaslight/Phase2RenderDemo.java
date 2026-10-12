package dev.kondalabs.gaslight;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.RemoteViewRaycaster.*;

/** Produces a reproducible software-rendered preview from a synthetic voxel world.
 * This is NOT a game screenshot. It exists to inspect the perspective algorithm.
 */
public final class Phase2RenderDemo {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("usage: Phase2RenderDemo /path/frame.ppm");
        var id=UUID.nameUUIDFromBytes("phase2-static-demo".getBytes());
        Passage p=new Passage(new Surface(new V(0,72,0),new V(1,0,0),9,8),
            new Surface(new V(2048,72,2048),new V(1,0,0),9,8),id,1);
        var box=new PortalPreviewCore.Box(2032,56,2040,32,32,32);
        var scene=PortalPreviewCore.capture(box,(x,y,z)->{
            int hill=70+(int)(4*Math.sin((x-2032)*.2)+3*Math.cos((z-2040)*.35));
            if(y>hill)return 0;
            if(y==hill)return 0xff61a64c;
            if(y>hill-3)return 0xff896341;
            return 0xff92939c;
        }).orElseThrow();
        var c=new Camera(new V(0,72.5,5),new V(0,-.02,-1),
            new V(1,0,0),new V(0,1,-.02),75,16d/9d);
        int w=80,h=45;
        var frame=PortalPreviewCore.render(p,scene,c,w,h,96);
        if(frame.visiblePixels()<200)throw new AssertionError("expected at least 200 portal pixels");
        Path target=Path.of(args[0]);Files.createDirectories(target.getParent());
        try(var out=new BufferedOutputStream(Files.newOutputStream(target))){
            out.write(("P6\n"+w+" "+h+"\n255\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int color=frame.visibleAt(x,y)?frame.argbAt(x,y):0xff1b2631;
                out.write((color>>>16)&255);out.write((color>>>8)&255);out.write(color&255);
            }
        }
        System.out.println("PHASE2_DEMO_FRAME: " +w+"x"+h+", mask="+frame.visiblePixels()+
            ", ray-steps="+frame.steps()+", path="+target);
    }
}
