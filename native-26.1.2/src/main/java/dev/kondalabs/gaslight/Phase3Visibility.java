package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Conservative 3D frustum rejection. A physical facet is certified hidden only
 * if ALL of its vertices lie strictly beyond one *same* camera clipping plane.
 * This test is sufficient to reject visible facets, not a proof of future invisibility.
 */
public final class Phase3Visibility {
    private Phase3Visibility() {}
    private static double dot(Geometry.V3 a,Geometry.V3 b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static Geometry.V3 subtract(Geometry.V3 a,Geometry.V3 b){return new Geometry.V3(a.x()-b.x(),a.y()-b.y(),a.z()-b.z());}
    public static boolean hidden(Geometry.Camera cam, Surface surface, double margin) {
        Objects.requireNonNull(cam);Objects.requireNonNull(surface);
        if(!Double.isFinite(margin)||margin<0)throw new IllegalArgumentException("margin");
        V horizontal=surface.tangent().mul(surface.width()/2);
        double halfHeight=surface.height()/2;
        double tanV=Math.tan(cam.halfVerticalFovRadians());
        double tanH=tanV*cam.aspect();
        boolean behind=true,beyondFar=true,outRight=true,outLeft=true,outTop=true,outBottom=true;
        for(int u:new int[]{-1,1})for(int v:new int[]{-1,1}){
            V point=surface.centre().add(horizontal.mul(u)).add(new V(0,halfHeight*v,0));
            Geometry.V3 relative=subtract(new Geometry.V3(point.x(),point.y(),point.z()),cam.eye());
            double f=dot(relative,cam.forward()),r=dot(relative,cam.right()),h=dot(relative,cam.up());
            if(!Double.isFinite(f)||!Double.isFinite(r)||!Double.isFinite(h))return false;
            behind &= f < -margin;
            beyondFar &= f > cam.farDistance()+margin;
            outRight &= r > f*tanH+margin;
            outLeft &= -r > f*tanH+margin;
            outTop &= h > f*tanV+margin;
            outBottom &= -h > f*tanV+margin;
        }
        return behind||beyondFar||outRight||outLeft||outTop||outBottom;
    }
    /** Proves a polygon's horizontal chords lie outside the current projected view
     * envelope, by enclosing the entire finite projected frustum in a circle.
     * Does NOT certify dynamic client-camera activation across ticks.
     */
    public static List<Geometry.V2> conservativeLoop(Geometry.Camera cam,int facets,double margin,double minRadius){
        if(facets<8||facets>128||!Double.isFinite(margin)||margin<=0||minRadius<0)
            throw new IllegalArgumentException("polygon policy");
        Geometry.V2 center=new Geometry.V2(cam.eye().x(),cam.eye().z());
        double radial=0;
        for(Geometry.V2 p:cam.projectedCorners())radial=Math.max(radial,p.minus(center).norm());
        double radius=Math.max(minRadius,radial+margin)/Math.cos(Math.PI/facets);
        List<Geometry.V2> points=new ArrayList<>(facets);
        for(int i=0;i<facets;i++){
            double a=2*Math.PI*i/facets;
            points.add(center.plus(new Geometry.V2(Math.cos(a),Math.sin(a)).times(radius)));
        }
        return List.copyOf(points);
    }
}
