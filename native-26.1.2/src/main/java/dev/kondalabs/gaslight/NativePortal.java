package dev.kondalabs.gaslight;

import java.util.*;

/** GASLIGHT's independent portal geometry. Contains no external portal API or game hooks.
 * A portal is a finite vertical rectangular surface, bidirectional and scale preserving.
 * Actual entities/camera are managed by the Minecraft adapter.
 */
public final class NativePortal {
    private NativePortal() {}

    public record V(double x, double y, double z) {
        public V add(V a) { return new V(x+a.x,y+a.y,z+a.z); }
        public V sub(V a) { return new V(x-a.x,y-a.y,z-a.z); }
        public V mul(double d) { return new V(x*d,y*d,z*d); }
        public double dot(V a) {return x*a.x+y*a.y+z*a.z;}
        public double norm() {return Math.sqrt(dot(this));}
        public V unit() {double n=norm();if(n<1.e-8||!Double.isFinite(n))throw new IllegalArgumentException("zero/nonfinite normal");return mul(1/n);}
    }
    public record Surface(V centre, V tangent, double width, double height) {
        public Surface {
            Objects.requireNonNull(centre); tangent = new V(tangent.x,0,tangent.z).unit();
            if(!(width>0&&width<=128&&height>0&&height<=1024) ||
              !Double.isFinite(width) || !Double.isFinite(height) ||
              !Double.isFinite(centre.x) || !Double.isFinite(centre.y) || !Double.isFinite(centre.z))
                throw new IllegalArgumentException("invalid surface");
        }
        public V normal(){return new V(-tangent.z,0,tangent.x);}
        public double side(V p){return p.sub(centre).dot(normal());}
        public double across(V p){return p.sub(centre).dot(tangent);}
        public double altitude(V p){return p.y-centre.y;}
        public boolean contains(V p,double margin) {
            return Math.abs(across(p)) <= width/2-margin && Math.abs(altitude(p)) <= height/2-margin;
        }
    }
    public record Passage(Surface entrance,Surface exit,UUID owner,long generation){
        public Passage {
            Objects.requireNonNull(entrance);Objects.requireNonNull(exit);Objects.requireNonNull(owner);
            if(generation<0)throw new IllegalArgumentException("negative portal generation");
            if(Math.abs(entrance.width-exit.width)>1e-6 || Math.abs(entrance.height-exit.height)>1e-6)
                throw new IllegalArgumentException("no scaling allowed");
        }
        // Normal is flipped across a crossing; movement continues away from the exit plane.
        public V mapPosition(V p){
            V d=p.sub(entrance.centre);
            return exit.centre.add(exit.tangent.mul(d.dot(entrance.tangent)))
                  .add(new V(0,d.y,0)).add(exit.normal().mul(-d.dot(entrance.normal())));
        }
        public V mapVelocity(V v){
            return exit.tangent.mul(v.dot(entrance.tangent))
                 .add(new V(0,v.y,0)).add(exit.normal().mul(-v.dot(entrance.normal())));
        }
        public Passage reversed(){return new Passage(exit, entrance,owner,generation);}
    }
    public record Crossing(V entrancePoint,V exitPoint,V exitMotion,int sign){}

    /** Robust swept-segment crossing: avoids endpoint teleports, tunnelled high-speed travel
     * and looking through an inactive/disconnected surface. sign describes crossing direction.
     */
    public static Optional<Crossing> intersect(Passage p,V previous,V current,double entityHalfWidth,double entityHalfHeight){
        Surface s=p.entrance;
        double a=s.side(previous),b=s.side(current);
        if(!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a-b)<1e-10)return Optional.empty();
        if(a*b>0) return Optional.empty();
        if(Math.abs(a)<1.e-6 && Math.abs(b)<1.e-6)return Optional.empty();
        double t=a/(a-b);
        if(t < 0 || t > 1)return Optional.empty();
        V at=previous.add(current.sub(previous).mul(t));
        double xClearance=Math.max(0.08,entityHalfWidth);
        double yClearance=Math.max(0.08,entityHalfHeight);
        // This intentionally rejects crossing too close to an edge.
        if(!s.contains(at,Math.max(xClearance,yClearance)))return Optional.empty();
        int dir=a>=b ? 1 : -1;
        V remaining=current.sub(at);
        V mappedRemaining=p.mapVelocity(remaining);
        // Epsilon moves final position a little away from portal plane, preventing repeated contact.
        V out=p.mapPosition(at).add(mappedRemaining)
               .add(p.exit.normal().mul(dir*.12));
        return Optional.of(new Crossing(at,out,p.mapVelocity(current.sub(previous)),dir));
    }
    public static double mappedYaw(Passage p,double minecraftYaw){
        double r=Math.toRadians(minecraftYaw);
        V facing=new V(-Math.sin(r),0,Math.cos(r));
        V mapped=p.mapVelocity(facing);
        return Math.toDegrees(Math.atan2(-mapped.x,mapped.z));
    }
}
