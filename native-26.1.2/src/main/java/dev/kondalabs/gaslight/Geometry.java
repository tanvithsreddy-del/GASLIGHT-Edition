package dev.kondalabs.gaslight;

import java.util.*;

/** Strictly finite calculations; infinite analytical lines are never enumerated. */
public final class Geometry {
  private Geometry() {}
  private static void requireFinite(V2 point) {
    if(point==null || !Double.isFinite(point.x()) || !Double.isFinite(point.z()))
      throw new IllegalArgumentException("nonfinite or missing horizontal coordinate");
  }
  public record V2(double x, double z) {
    public V2 plus(V2 v) { return new V2(x+v.x,z+v.z); }
    public V2 minus(V2 v) { return new V2(x-v.x,z-v.z); }
    public V2 times(double n) { return new V2(x*n,z*n); }
    public double dot(V2 v) { return x*v.x+z*v.z; }
    public double norm() { return Math.hypot(x,z); }
    public V2 unit() { double m=norm(); if(m<1e-10)throw new IllegalArgumentException("zero vector");return times(1/m); }
  }
  public record V3(double x,double y,double z) {
    public V3 plus(V3 v){return new V3(x+v.x,y+v.y,z+v.z);}
    public V3 times(double n){return new V3(x*n,y*n,z*n);}
  }
  public record Segment(V2 a,V2 b) {
    public double length(){return a.minus(b).norm();}
  }
  public record Chunk(int x,int z) {
    public static Chunk of(V2 p){
      requireFinite(p);
      if(Math.abs(p.x())>30_000_000 || Math.abs(p.z())>30_000_000)
        throw new IllegalArgumentException("position outside supported world");
      return new Chunk((int)Math.floor(p.x()/16),(int)Math.floor(p.z()/16));
    }
    public double minX(){return x*16.0;}
    public double minZ(){return z*16.0;}
  }
  public record Camera(V3 eye, V3 forward, V3 right, V3 up, double halfVerticalFovRadians, double aspect, double farDistance) {
    public Camera {
      if (eye==null||forward==null||right==null||up==null ||
          !Double.isFinite(eye.x())||!Double.isFinite(eye.y())||!Double.isFinite(eye.z())||
          !Double.isFinite(forward.x())||!Double.isFinite(forward.y())||!Double.isFinite(forward.z())||
          !Double.isFinite(right.x())||!Double.isFinite(right.y())||!Double.isFinite(right.z())||
          !Double.isFinite(up.x())||!Double.isFinite(up.y())||!Double.isFinite(up.z()))
        throw new IllegalArgumentException("invalid camera vectors");
      if (!Double.isFinite(halfVerticalFovRadians)||halfVerticalFovRadians<=0||halfVerticalFovRadians>=Math.PI/2)throw new IllegalArgumentException("FOV");
      if (!Double.isFinite(aspect)||aspect<=0||!Double.isFinite(farDistance)||farDistance<=0)throw new IllegalArgumentException("projection");
    }
    public static Camera fromYawPitch(double x,double y,double z,double yawDeg,double pitchDeg,double verticalFovDeg,double aspect,double far) {
      if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||
         !Double.isFinite(yawDeg)||!Double.isFinite(pitchDeg))
        throw new IllegalArgumentException("invalid camera pose");
      double yaw=Math.toRadians(yawDeg),pitch=Math.toRadians(pitchDeg);
      // Minecraft yaw 0 faces +Z and pitch positive looks down.
      V3 f=new V3(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
      V3 r=new V3(Math.cos(yaw),0,Math.sin(yaw));
      // Orthogonal camera-up basis for Minecraft positive-down pitch.
      // Previous signs were inverted, so looking up/down invalidated the witness.
      V3 u=new V3(-Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),Math.cos(yaw)*Math.sin(pitch));
      return new Camera(new V3(x,y,z),f,r,u,Math.toRadians(verticalFovDeg/2),aspect,far);
    }
    public List<V2> projectedCorners() {
      double h=Math.tan(halfVerticalFovRadians), w=h*aspect;
      List<V2> out=new ArrayList<>(8);
      for(double depth:new double[]{0.05,farDistance})for(int xs:new int[]{-1,1})for(int ys:new int[]{-1,1}) {
        V3 p=eye.plus(forward.times(depth)).plus(right.times(depth*xs*w)).plus(up.times(depth*ys*h));
        out.add(new V2(p.x,p.z));
      }
      return out;
    }
  }
  public static double support(V2 origin,List<V2> frustum,V2 axis) {
    double max=Double.NEGATIVE_INFINITY;
    for(V2 v:frustum)max=Math.max(max,v.minus(origin).dot(axis));
    return max;
  }
  /** Infinite behind-plane, local curved displacement is always farther backward. */
  public record InfiniteRearLine(V2 origin,V2 forward,double d,double amplitude,double width) {
    public InfiniteRearLine {
      requireFinite(origin);requireFinite(forward);
      if(!Double.isFinite(d)||!Double.isFinite(amplitude)||!Double.isFinite(width)||
         d<0||amplitude<0||width<=0)throw new IllegalArgumentException("bad rear line");
      forward=forward.unit();
    }
    public V2 at(double u) {
      if(!Double.isFinite(u))throw new IllegalArgumentException("nonfinite coordinate on analytic line");
      V2 transverse=new V2(-forward.z,forward.x);
      return origin.plus(transverse.times(u)).minus(forward.times(d+amplitude*Math.exp(-u*u/(width*width))));
    }
  }
  public static InfiniteRearLine rear(V2 origin,V2 forward,List<V2> projected,double minimumDistance,double margin) {
    requireFinite(origin);requireFinite(forward);
    if(projected==null||projected.isEmpty()||!Double.isFinite(minimumDistance)||
       !Double.isFinite(margin)||minimumDistance<0||margin<0)
      throw new IllegalArgumentException("invalid projection");
    for(V2 p:projected)requireFinite(p);
    V2 f=forward.unit(); double min=Double.POSITIVE_INFINITY;
    for(V2 p:projected)min=Math.min(min,p.minus(origin).dot(f));
    return new InfiniteRearLine(origin,f,minimumDistance+margin+Math.max(0,-min),0,1);
  }
  /**
   * Returns polygon enclosing projected frustum with each polygon EDGE separated
   * from all projected camera points by at least `margin` on its edge-midpoint normal.
   * Avoids the common error where chord cuts back through visibility volume.
   */
  public static List<V2> enclosingLoop(V2 origin,List<V2> projected,double rMin,double margin,int sides) {
    requireFinite(origin);
    if(projected==null||projected.isEmpty()||projected.size()>16384||
       sides<8||sides>4096||!Double.isFinite(rMin)||!Double.isFinite(margin)||
       rMin<0||margin<=0)throw new IllegalArgumentException("loop config");
    for(V2 p:projected)requireFinite(p);
    double step=2*Math.PI/sides, c=Math.cos(step/2);
    List<V2> pts=new ArrayList<>(sides);
    for(int i=0;i<sides;i++){
      double ang=step*i; V2 axis=new V2(Math.cos(ang),Math.sin(ang));
      V2 prev=new V2(Math.cos(ang-step/2),Math.sin(ang-step/2));
      V2 next=new V2(Math.cos(ang+step/2),Math.sin(ang+step/2));
      double r=Math.max(rMin,Math.max(support(origin,projected,axis)+margin,
         Math.max((support(origin,projected,prev)+margin)/c,(support(origin,projected,next)+margin)/c)));
      pts.add(origin.plus(axis.times(r)));
    }
    return Collections.unmodifiableList(pts);
  }
  public static List<Segment> segments(List<V2> closed) {
    List<Segment> seg=new ArrayList<>(closed.size());
    for(int i=0;i<closed.size();i++)seg.add(new Segment(closed.get(i),closed.get((i+1)%closed.size())));
    return seg;
  }
  public static boolean intersectsExpandedChunk(Segment s,Chunk ch,double expand) {
    if(s==null||ch==null||!Double.isFinite(expand)||expand<0)return false;
    if(s.a()==null||s.b()==null||!Double.isFinite(s.a().x())||!Double.isFinite(s.a().z())||
       !Double.isFinite(s.b().x())||!Double.isFinite(s.b().z()))return false;
    // Liang-Barsky closed rectangle intersection; touching counts as intersection.
    double xmin=ch.minX()-expand,xmax=ch.minX()+16+expand;
    double zmin=ch.minZ()-expand,zmax=ch.minZ()+16+expand;
    double dx=s.b.x-s.a.x,dz=s.b.z-s.a.z;
    double[] p={-dx,dx,-dz,dz}; double[] q={s.a.x-xmin,xmax-s.a.x,s.a.z-zmin,zmax-s.a.z};
    double t0=0,t1=1;
    for(int i=0;i<4;i++){
      if(Math.abs(p[i])<1e-12){if(q[i]<0)return false;continue;}
      double t=q[i]/p[i];
      if(p[i]<0)t0=Math.max(t0,t);else t1=Math.min(t1,t);
      if(t0>t1)return false;
    }
    return true;
  }
  public static Set<Chunk> chunksTouched(Segment seg,double expand,int cap) {
    if(seg==null||!Double.isFinite(expand)||expand<0||cap<1||cap>250000)
      throw new IllegalArgumentException("invalid chunk scan request");
    requireFinite(seg.a());requireFinite(seg.b());
    if(Math.max(Math.max(Math.abs(seg.a().x()),Math.abs(seg.a().z())),
       Math.max(Math.abs(seg.b().x()),Math.abs(seg.b().z())))>30_000_000)
      throw new IllegalArgumentException("segment outside world boundary");
    if(seg.length()>50000)throw new IllegalArgumentException("finite physical segment too long");
    double minX=Math.min(seg.a.x,seg.b.x)-expand,maxX=Math.max(seg.a.x,seg.b.x)+expand;
    double minZ=Math.min(seg.a.z,seg.b.z)-expand,maxZ=Math.max(seg.a.z,seg.b.z)+expand;
    // A zero-margin segment lying exactly on a chunk face touches BOTH columns.
    // Expand the lower enumeration bound by one representable double step;
    // the exact line-vs-box test below rejects any unrelated neighbors.
    int a=(int)Math.floor(Math.nextDown(minX/16.0)),b=(int)Math.floor(maxX/16.0);
    int c=(int)Math.floor(Math.nextDown(minZ/16.0)),d=(int)Math.floor(maxZ/16.0);
    long boxes=(long)(b-a+1)*(d-c+1);
    if(boxes>cap*12L)throw new IllegalArgumentException("chunk scan cap");
    Set<Chunk> result=new LinkedHashSet<>();
    for(int x=a;x<=b;x++)for(int z=c;z<=d;z++){
      Chunk ch=new Chunk(x,z);
      if(intersectsExpandedChunk(seg,ch,expand)){
        result.add(ch); if(result.size()>cap)throw new IllegalArgumentException("chunk cap exceeded");
      }
    }
    return result;
  }
  public static boolean polygonEncloses(List<V2> polygon,V2 point) {
    boolean inside=false;
    for(int i=0,j=polygon.size()-1;i<polygon.size();j=i++){
      V2 a=polygon.get(i),b=polygon.get(j);
      if((a.z>point.z)!=(b.z>point.z) && point.x<(b.x-a.x)*(point.z-a.z)/(b.z-a.z)+a.x)inside=!inside;
    }
    return inside;
  }
  public static double pointSegmentDistance(V2 p,Segment s) {
    V2 v=s.b.minus(s.a); double n=v.dot(v); if(n<1e-20)return p.minus(s.a).norm();
    double t=Math.max(0,Math.min(1,p.minus(s.a).dot(v)/n));
    return p.minus(s.a.plus(v.times(t))).norm();
  }
}
