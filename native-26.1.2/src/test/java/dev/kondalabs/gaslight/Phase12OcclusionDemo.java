package dev.kondalabs.gaslight;

import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.RemoteViewRaycaster.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Compare an unoccluded synthetic aperture with a locally occluded aperture.
 * This is NOT a Minecraft screenshot or evidence of runtime depth testing.
 */
public final class Phase12OcclusionDemo {
    public static void main(String[] args)throws IOException {
        if(args.length!=1)throw new IllegalArgumentException("output PPM required");
        UUID id=UUID.randomUUID();
        Passage portal=new Passage(
            new Surface(new V(0,65,0),new V(1,0,0),8,10),
            new Surface(new V(1024,65,2048),new V(1,0,0),8,10),id,1);
        PortalPreviewCore.Box box=new PortalPreviewCore.Box(1008,49,2032,32,32,32);
        var scene=PortalPreviewCore.capture(box,(x,y,z)->{
            if(y>=65 && (x+z)%11!=0)return 0;
            if(y<61)return 0xff586947;
            if(y<65)return (x/3+z/4)%2==0?0xff819e67:0xffbcab75;
            return 0xff88aec8;
        }).orElseThrow();
        Camera cam=new Camera(new V(0,65,6),new V(0,0,-1),new V(1,0,0),new V(0,1,0),70,16d/9d);
        var clear=PortalPreviewCore.render(portal,scene,cam,80,45,96);
        WorldSampler wall=(x,y,z)-> x<0 && z==3 && y>=61 && y<70?
            Voxel.opaque(0):Voxel.air();
        var hidden=PortalPreviewCore.renderOccluded(portal,scene,cam,80,45,96,wall,96);
        try(OutputStream out=Files.newOutputStream(Path.of(args[0]))){
            out.write("P6\n160 45\n255\n".getBytes());
            for(int y=0;y<45;y++)for(int x=0;x<160;x++){
                var f=x<80?clear:hidden;
                int sx=x%80;
                int argb=f.visibleAt(sx,y)?f.argbAt(sx,y):0xff1b2831;
                out.write((argb>>16)&255);out.write((argb>>8)&255);out.write(argb&255);
            }
        }
        System.out.println("Synthetic frames: clear="+clear.visiblePixels()+
            ", foreground-occluded="+hidden.visiblePixels()+
            ", ray steps="+clear.steps()+"/"+hidden.steps());
    }
}
