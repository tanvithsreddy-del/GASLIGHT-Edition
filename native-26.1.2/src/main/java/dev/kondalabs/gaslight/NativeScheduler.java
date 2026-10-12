package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Deterministic, no-Minecraft-dependency state machine. Only certified pairs can be activated. */
public final class NativeScheduler {
    public interface Certifier { boolean certify(Passage p); }
    public record Generation(long epoch,Passage passage,long createdAt,boolean active){}
    private final Deque<Generation> generations = new ArrayDeque<>();
    private long currentTick;
    private boolean enabled=false;
    private final int period,maxGenerations;
    private final long lifetime;
    public NativeScheduler(int period,int maxGenerations,long lifetime){
        if(period<1||maxGenerations<1||lifetime<period)throw new IllegalArgumentException();
        this.period=period;this.maxGenerations=maxGenerations;this.lifetime=lifetime;
    }
    public void enable(boolean value){enabled=value;if(!value)generations.clear();}
    public boolean enabled(){return enabled;}
    public long currentTick(){return currentTick;}
    public List<Generation> active(){return List.copyOf(generations);}
    public void tick(){
        currentTick++;
        while(!generations.isEmpty() && currentTick-generations.peekFirst().createdAt>=lifetime)
            generations.removeFirst();
        while(generations.size()>maxGenerations)generations.removeFirst();
    }
    public boolean isEpoch(){return enabled && currentTick % period==0;}
    public boolean admit(Passage p,Certifier certifier){
        if(!isEpoch()||!certifier.certify(p))return false;
        while(generations.size()>=maxGenerations)generations.removeFirst();
        generations.addLast(new Generation(p.generation(),p,currentTick,true));return true;
    }
    public void retire(UUID owner){generations.removeIf(g->g.passage.owner().equals(owner));}
}
