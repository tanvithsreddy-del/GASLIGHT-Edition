package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.Geometry.Camera;
import dev.kondalabs.gaslight.Geometry.V3;

/** Camera evidence policy, NOT a magical proof of what a modded client truly sees.
 * An authenticated and genuinely integrated client-camera transport is required
 * before a real Host may ever provide one of these witnesses.
 */
public final class Phase3CameraWitness {
    private Phase3CameraWitness() {}
    public record Witness(UUID player,long sequence,long clientTick,long receivedServerTick,
                          Camera camera,boolean allViewportsAccountedFor,
                          boolean renderingPipelineSynchronized) {
        public Witness { Objects.requireNonNull(player);Objects.requireNonNull(camera); }
    }
    public record Assessment(boolean accepted,String reason) {}
    /** A fresh, monotonic, stable camera witness with a conservative 3D position bound.
     * The distance bound is measured against the player/server's verified eye point.
     */
    public static Assessment assess(Witness w,UUID expectedPlayer,long now,long lastSequence,
                                    V3 serverEye,long maxAgeTicks,double eyeTolerance) {
        if(w==null||expectedPlayer==null||serverEye==null)return no("missing evidence");
        if(!expectedPlayer.equals(w.player()))return no("wrong owner");
        if(w.sequence()<=lastSequence||w.sequence()<0)return no("replayed camera");
        if(w.clientTick()<0)return no("invalid client tick");
        if(w.receivedServerTick()>now||w.receivedServerTick()<0||
           now-w.receivedServerTick()>maxAgeTicks||maxAgeTicks<0)return no("stale camera");
        if(!w.allViewportsAccountedFor()||!w.renderingPipelineSynchronized())
            return no("client rendering not certified");
        Camera c=w.camera();
        V3 p=c.eye();
        if(!finite(p)||!finite(serverEye)||!finite(c.forward())||!finite(c.right())||!finite(c.up()))
            return no("nonfinite projection");
        if(!Double.isFinite(eyeTolerance)||eyeTolerance<0)return no("invalid position policy");
        double d=Math.hypot(Math.hypot(p.x()-serverEye.x(),p.y()-serverEye.y()),p.z()-serverEye.z());
        if(d>eyeTolerance)return no("camera position mismatch");
        if(c.aspect()>8||c.halfVerticalFovRadians()>1.5||c.farDistance()<8||c.farDistance()>4096)
            return no("untrustworthy projection dimensions");
        if(Math.abs(norm(c.forward())-1)>1e-3||Math.abs(norm(c.right())-1)>1e-3||
           Math.abs(norm(c.up())-1)>1e-3||
           Math.abs(dot(c.forward(),c.right()))>1e-3||
           Math.abs(dot(c.forward(),c.up()))>1e-3||
           Math.abs(dot(c.right(),c.up()))>1e-3)return no("nonorthonormal camera basis");
        return new Assessment(true,"monotonic, fresh camera witness");
    }
    private static boolean finite(V3 a){return Double.isFinite(a.x())&&Double.isFinite(a.y())&&Double.isFinite(a.z());}
    private static double norm(V3 a){return Math.sqrt(dot(a,a));}
    private static double dot(V3 a,V3 b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static Assessment no(String reason){return new Assessment(false,reason);}
}
