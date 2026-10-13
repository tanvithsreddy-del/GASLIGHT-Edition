package dev.kondalabs.gaslight.provider;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;

/** Immutable camera-relative clip transform. Conservative whole-box rejection;
 * this alone is not a publication fence or proof about other rendered views. */
public final class FrameClip {
    private final double x,y,z;private final float[] matrix;
    public FrameClip(double x,double y,double z,float[] matrix){
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(y)>4096||Math.abs(z)>30_000_000||matrix==null||matrix.length!=16)throw new IllegalArgumentException("camera/clip bounds");
        this.x=x;this.y=y;this.z=z;this.matrix=matrix.clone();
        for(float f:matrix)if(!Float.isFinite(f)||Math.abs(f)>10000)throw new IllegalArgumentException("non-finite/unbounded clip matrix");
        if(Math.abs(new Matrix4f().set(matrix).determinant())<1e-9)throw new IllegalArgumentException("degenerate clip matrix");
    }
    public double x(){return x;}public double y(){return y;}public double z(){return z;}
    public float[] matrix(){return matrix.clone();}
    public boolean excludes(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)||minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("invalid affected box");
        // Expand before conversion to floats; never certify a grazing box hidden.
        double margin=.25;
        return !new FrustumIntersection(new Matrix4f().set(matrix)).testAab((float)(minX-x-margin),(float)(minY-y-margin),(float)(minZ-z-margin),(float)(maxX-x+margin),(float)(maxY-y+margin),(float)(maxZ-z+margin));
    }
}
