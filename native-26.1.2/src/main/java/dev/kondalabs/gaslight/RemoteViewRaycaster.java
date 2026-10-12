package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Original, CPU-bounded voxel raycaster for a *future* remote-view pipeline.
 * This computes samples but does not submit pixels to Minecraft's actual renderer.
 * No external portal library code is used.
 */
public final class RemoteViewRaycaster {
    private RemoteViewRaycaster(){}
    public record Voxel(int rgb,int alpha) {
        public Voxel {if(alpha<0||alpha>255)throw new IllegalArgumentException("alpha");}
        public static Voxel air(){return new Voxel(0,0);}
        public static Voxel opaque(int rgb){return new Voxel(rgb,255);}
    }
    @FunctionalInterface public interface WorldSampler {Voxel sample(int x,int y,int z);}
    public record Sample(boolean crossedPortal,boolean hitTerrain,int color,int samples,long voxelSteps){}
    public record Camera(V position,V forward,V right,V up,double verticalFovDeg,double aspect){
        public Camera {
            if(verticalFovDeg<=0||verticalFovDeg>=170||aspect<=0)throw new IllegalArgumentException("FOV");
        }
    }
    public static Optional<V> rayToPortal(Surface surface,V eye,V direction) {
        V n=surface.normal();
        double denom=direction.dot(n);
        if(Math.abs(denom)<1.e-10)return Optional.empty();
        double t=surface.centre().sub(eye).dot(n)/denom;
        if(t<1e-5)return Optional.empty();
        V p=eye.add(direction.mul(t));
        return surface.contains(p,0)?Optional.of(p):Optional.empty();
    }
    public static Sample trace(Passage portal,WorldSampler source,V cameraOrigin,V ray,int maxSteps,int maxDistance){
        if(maxSteps<1||maxSteps>4096||maxDistance<1||maxDistance>2048)throw new IllegalArgumentException("budget");
        V d=ray.unit();Optional<V> through=rayToPortal(portal.entrance(),cameraOrigin,d);
        if(through.isEmpty())return new Sample(false,false,0,0,0);
        V start=portal.mapPosition(through.get());
        V worldDir=portal.mapVelocity(d).unit();
        // Step beyond the far portal surface before looking up voxels.
        start=start.add(worldDir.mul(0.001));
        int x=(int)Math.floor(start.x()),y=(int)Math.floor(start.y()),z=(int)Math.floor(start.z());
        int sx=worldDir.x()>0?1:-1,sy=worldDir.y()>0?1:-1,sz=worldDir.z()>0?1:-1;
        double tx=nextBoundary(start.x(),worldDir.x());
        double ty=nextBoundary(start.y(),worldDir.y());
        double tz=nextBoundary(start.z(),worldDir.z());
        double dx=worldDir.x()==0?Double.POSITIVE_INFINITY:Math.abs(1/worldDir.x());
        double dy=worldDir.y()==0?Double.POSITIVE_INFINITY:Math.abs(1/worldDir.y());
        double dz=worldDir.z()==0?Double.POSITIVE_INFINITY:Math.abs(1/worldDir.z());
        double distance=0,totalAlpha=0,r=0,g=0,b=0;
        boolean hit=false;
        int samples=0;
        for(int i=0;i<maxSteps && distance<=maxDistance;i++){
            Voxel voxel=source.sample(x,y,z);
            samples++;
            if(voxel==null)break; // unknown data must not pretend to be air
            if(voxel.alpha()>0){
                double transparency=1-totalAlpha;
                double alpha=voxel.alpha()/255d*transparency;
                r+=((voxel.rgb()>>16)&255)*alpha;g+=((voxel.rgb()>>8)&255)*alpha;b+=(voxel.rgb()&255)*alpha;
                totalAlpha+=alpha;hit=true;
                if(totalAlpha>=.995){
                    int color=compose(r,g,b);return new Sample(true,true,color,samples,i+1);
                }
            }
            if(tx<=ty && tx<=tz){distance=tx;tx+=dx;x+=sx;}
            else if(ty<=tz){distance=ty;ty+=dy;y+=sy;}
            else {distance=tz;tz+=dz;z+=sz;}
        }
        return new Sample(true,hit,compose(r,g,b),samples,samples);
    }
    private static int compose(double r,double g,double b){
        return ((int)Math.min(255,Math.max(0,Math.round(r)))<<16)|
          ((int)Math.min(255,Math.max(0,Math.round(g)))<<8)|
          (int)Math.min(255,Math.max(0,Math.round(b)));
    }
    private static double nextBoundary(double origin,double direction){
        if(direction==0)return Double.POSITIVE_INFINITY;
        return (direction>0?Math.floor(origin)+1-origin:origin-Math.floor(origin))/Math.abs(direction);
    }
    /** Pixel samples in row-major order. Pixels outside the portal have crossedPortal=false. */
    public static Sample[] sampleImage(Passage portal, WorldSampler sampler, Camera camera,
                                       int width,int height,int rayBudget,int stepsPerRay){
        if(width<1||height<1||(long)width*height>rayBudget ||rayBudget>262_144)
            throw new IllegalArgumentException("pixel budget");
        Sample[] image=new Sample[width*height];
        double tanY=Math.tan(Math.toRadians(camera.verticalFovDeg()/2));
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            double vx=(2d*(x+.5)/width-1)*camera.aspect()*tanY;
            double vy=(1-2d*(y+.5)/height)*tanY;
            V ray=camera.forward().add(camera.right().mul(vx)).add(camera.up().mul(vy));
            image[y*width+x]=trace(portal,sampler,camera.position(),ray,stepsPerRay,128);
        }
        return image;
    }
}
