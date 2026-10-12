package dev.kondalabs.gaslight;

import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.RemoteViewRaycaster.*;
import java.util.*;

/** Conservative voxel occlusion for an experimental preview.
 * Unknown local terrain, solid foreground voxels, or excessive sampling hide pixels.
 * This is NOT vanilla per-fragment GPU depth testing and treats all solid blocks
 * as full cubes; it intentionally under-renders fences, glass and translucent blocks.
 */
public final class PortalForegroundOcclusion {
    private PortalForegroundOcclusion() {}
    public static V screenRay(Camera c,int w,int h,int x,int y){
        if(w<=0||h<=0||x<0||x>=w||y<0||y>=h)throw new IllegalArgumentException("screen");
        double tanY=Math.tan(Math.toRadians(c.verticalFovDeg()/2));
        double vx=(2d*(x+.5)/w-1)*c.aspect()*tanY;
        double vy=(1d-2d*(y+.5)/h)*tanY;
        return c.forward().add(c.right().mul(vx)).add(c.up().mul(vy)).unit();
    }
    public static boolean clear(Surface portal,V origin,V direction,WorldSampler local,int budget){
        if(budget<1||budget>256)throw new IllegalArgumentException("step budget");
        Optional<V> hit=RemoteViewRaycaster.rayToPortal(portal,origin,direction.unit());
        if(hit.isEmpty())return false;
        V d=direction.unit();double distance=hit.get().sub(origin).norm();
        if(distance>128 || distance<0.001)return false;
        V start=origin.add(d.mul(.0001));
        int x=(int)Math.floor(start.x()),y=(int)Math.floor(start.y()),z=(int)Math.floor(start.z());
        int sx=d.x()>0?1:-1,sy=d.y()>0?1:-1,sz=d.z()>0?1:-1;
        double tx=first(start.x(),d.x()),ty=first(start.y(),d.y()),tz=first(start.z(),d.z());
        double dx=d.x()==0?Double.POSITIVE_INFINITY:Math.abs(1/d.x());
        double dy=d.y()==0?Double.POSITIVE_INFINITY:Math.abs(1/d.y());
        double dz=d.z()==0?Double.POSITIVE_INFINITY:Math.abs(1/d.z());
        for(int step=0;step<budget;step++){
            RemoteViewRaycaster.Voxel v=local.sample(x,y,z);
            if(v==null||v.alpha()!=0)return false;
            double next=Math.min(tx,Math.min(ty,tz));
            if(next>=distance-0.001)return true;
            if(tx<=ty&&tx<=tz){x+=sx;tx+=dx;}
            else if(ty<=tz){y+=sy;ty+=dy;}
            else{z+=sz;tz+=dz;}
        }
        return false;
    }
    private static double first(double p,double d){
        if(d==0)return Double.POSITIVE_INFINITY;
        return (d>0?Math.floor(p)+1-p:p-Math.floor(p))/Math.abs(d);
    }
}
