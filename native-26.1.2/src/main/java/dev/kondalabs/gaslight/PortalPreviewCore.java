package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.RemoteViewRaycaster.*;

/** Immutable, bounded remote voxel snapshot and native portal-plane projection.
 * This is a DEBUG VIEW, not a replacement for Minecraft's level renderer.
 * No calls to Minecraft occur from here; safe to unit-test offline.
 */
public final class PortalPreviewCore {
    private PortalPreviewCore() {}
    public static final int MAX_VOXELS = 131_072;
    public static final int UNKNOWN = Integer.MIN_VALUE;
    public record Box(int ox,int oy,int oz,int sx,int sy,int sz) {
        public Box {
            if (sx<1||sy<1||sz<1||(long)sx*sy*sz>MAX_VOXELS ||
                (long)ox < -30_000_000 || (long)ox+sx > 30_000_000 ||
                (long)oz < -30_000_000 || (long)oz+sz > 30_000_000 ||
                (long)oy < -4_096 || (long)oy+sy > 4_096)
                throw new IllegalArgumentException("snapshot memory/world bounds exceeded");
        }
        public boolean contains(int x,int y,int z) {
            return x>=ox && x<(long)ox+sx && y>=oy && y<(long)oy+sy && z>=oz && z<(long)oz+sz;
        }
        public int index(int x,int y,int z) {
            if(!contains(x,y,z))throw new IndexOutOfBoundsException();
            return ((y-oy)*sz+(z-oz))*sx+(x-ox);
        }
        public int size(){return sx*sy*sz;}
    }
    public interface VoxelReader { int read(int x,int y,int z); }
    public record Scene(Box box,int[] voxels) implements WorldSampler {
        public Scene {
            Objects.requireNonNull(box);
            Objects.requireNonNull(voxels);
            if(voxels.length!=box.size())throw new IllegalArgumentException("snapshot dimensions");
            voxels=voxels.clone();
        }
        @Override public int[] voxels(){return voxels.clone();}
        public boolean covers(int x,int y,int z){return box.contains(x,y,z);}
        public int codeAt(int x,int y,int z) { return box.contains(x,y,z) ? voxels[box.index(x,y,z)] : UNKNOWN; }
        @Override public Voxel sample(int x,int y,int z) {
            int value=codeAt(x,y,z);
            if(value==UNKNOWN)return null;
            return value==0?Voxel.air():new Voxel(value&0xffffff,(value>>>24)&255);
        }
    }
    /** Fails closed: UNKNOWN anywhere rejects the *entire* snapshot. */
    public static Optional<Scene> capture(Box box,VoxelReader reader){
        Objects.requireNonNull(reader);
        int[] arr=new int[box.size()];
        for(int y=box.oy();y<(long)box.oy()+box.sy();y++)
            for(int z=box.oz();z<(long)box.oz()+box.sz();z++)
                for(int x=box.ox();x<(long)box.ox()+box.sx();x++){
                    int code=reader.read(x,y,z);
                    if(code==UNKNOWN)return Optional.empty();
                    arr[box.index(x,y,z)]=code;
                }
        return Optional.of(new Scene(box,arr));
    }
    public record Frame(int width,int height,int[] argb,boolean[] portalMask,int visiblePixels,long steps){
        public Frame {
            if(width<1||height<1||argb.length!=width*height||portalMask.length!=width*height)
                throw new IllegalArgumentException("frame size");
            argb=argb.clone();portalMask=portalMask.clone();
        }
        @Override public int[] argb(){return argb.clone();}
        @Override public boolean[] portalMask(){return portalMask.clone();}
        public int argbAt(int x,int y){return argb[y*width+x];}
        public boolean visibleAt(int x,int y){return portalMask[y*width+x];}
    }
    /**
     * Every output pixel first intersects the source portal plane, then rays into the
     * destination snapshot. A missing voxel is rendered opaque magenta DEBUG, never as air.
     * This does not depth-test against foreground vanilla-world blocks.
     */
    public static Frame render(Passage passage,Scene scene,Camera camera,
                               int width,int height,int maxRaySteps) {
        if(width<1||height<1||(long)width*height>4096||maxRaySteps<1||maxRaySteps>128)
            throw new IllegalArgumentException("render budget");
        int[] rgba=new int[width*height];
        boolean[] mask=new boolean[rgba.length];
        int count=0;long steps=0;
        Sample[] pixels=RemoteViewRaycaster.sampleImage(passage,scene,camera,
                width,height,4096,maxRaySteps);
        for(int i=0;i<pixels.length;i++){
            Sample p=pixels[i];steps+=p.voxelSteps();
            if(!p.crossedPortal())continue;
            mask[i]=true;count++;
            rgba[i]=p.hitTerrain()?0xff000000|p.color():0xffaa00ff;
        }
        return new Frame(width,height,rgba,mask,count,steps);
    }
    /** Conservative foreground-occluded preview: visible only when each source ray
     * crosses the portal before touching any local solid block. Unknown local chunks
     * hide the pixel, rather than drawing over unloaded terrain.
     */
    public static Frame renderOccluded(Passage passage,Scene scene,Camera camera,
                                       int width,int height,int maxRaySteps,
                                       WorldSampler localForeground,int maxForegroundSteps){
        Objects.requireNonNull(localForeground);
        if(width<1||height<1||(long)width*height>4096||maxRaySteps<1||maxRaySteps>128)
            throw new IllegalArgumentException("render budget");
        int[] rgba=new int[width*height];boolean[] mask=new boolean[rgba.length];
        int visible=0;long totalRemoteSteps=0;
        // Occlusion BEFORE remote raytracing: saves a whole ray on hidden pixels.
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            V ray=PortalForegroundOcclusion.screenRay(camera,width,height,x,y);
            if(!PortalForegroundOcclusion.clear(passage.entrance(),camera.position(),ray,
                                                localForeground,maxForegroundSteps))continue;
            Sample s=RemoteViewRaycaster.trace(passage,scene,camera.position(),ray,maxRaySteps,128);
            totalRemoteSteps+=s.voxelSteps();
            if(!s.crossedPortal())continue;
            int i=y*width+x;mask[i]=true;visible++;
            rgba[i]=s.hitTerrain()?0xff000000|s.color():0xffaa00ff;
        }
        return new Frame(width,height,rgba,mask,visible,totalRemoteSteps);
    }
    public record Published(UUID owner,long generation,Passage passage,Scene scene,long tick){
        public Published {
            Objects.requireNonNull(owner);Objects.requireNonNull(passage);Objects.requireNonNull(scene);
            if(!owner.equals(passage.owner())||generation!=passage.generation())
                throw new IllegalArgumentException("mismatched portal identity");
        }
    }
    /** Singleplayer shared-memory handoff. This deliberately cannot support dedicated-server clients. */
    public static final class Exchange {
        private volatile Published current;
        public void publish(Published p){ current=Objects.requireNonNull(p); }
        public Optional<Published> get(UUID player){
            Published p=current;
            return p!=null&&p.owner().equals(player)?Optional.of(p):Optional.empty();
        }
        public void clear(UUID owner,long generation){
            Published p=current;
            if(p!=null&&p.owner().equals(owner)&&p.generation()==generation)current=null;
        }
        public void clear(){current=null;}
    }
}
