package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Native traversal engine, no dependency on any other portal mod. */
public final class PortalCrossingController {
    private final Map<UUID,V> lastPositions=new HashMap<>();
    private final Map<UUID,Long> lastTeleportTicks=new HashMap<>();
    private final int cooldownTicks;
    public PortalCrossingController(int cooldownTicks){if(cooldownTicks<1)throw new IllegalArgumentException();this.cooldownTicks=cooldownTicks;}
    public record Hop(Passage portal,Crossing crossing,double exitYaw){}
    public Optional<Hop> visit(UUID player,V position,double yaw,long tick,Collection<Passage> portals){
        V previous=lastPositions.put(player,position);
        if(previous==null)return Optional.empty();
        if(tick-lastTeleportTicks.getOrDefault(player,Long.MIN_VALUE/2) < cooldownTicks)return Optional.empty();
        // No accidental teleport of rapidly displaced entities (commands / other mods).
        if(position.sub(previous).norm()>14)return Optional.empty();
        for(Passage portal:portals){
            for(Passage p:List.of(portal,portal.reversed())){
                Optional<Crossing> c=NativePortal.intersect(p,previous,position,0.31,0.91);
                if(c.isPresent()){
                    lastTeleportTicks.put(player,tick);
                    lastPositions.put(player,c.get().exitPoint());
                    return Optional.of(new Hop(p,c.get(),NativePortal.mappedYaw(p,yaw)));
                }
            }
        }
        return Optional.empty();
    }
    public void forget(UUID player){lastPositions.remove(player);lastTeleportTicks.remove(player);}
    public int observedPlayers(){return lastPositions.size();}
}
