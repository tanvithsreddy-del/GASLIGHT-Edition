package dev.kondalabs.gaslight;
import java.util.*;
import dev.kondalabs.gaslight.Geometry.*;

/** Source and destination are independently validated; UNKNOWN is never considered safe. */
public final class StructureSafety {
  public enum State {SAFE, STRUCTURE, UNKNOWN, PLAYER_BUILD, UNSAFE_TERRAIN}
  @FunctionalInterface public interface Oracle {State get(Chunk c);}
  public record Check(boolean safe, String reason, Set<Chunk> checked){}
  private StructureSafety(){}
  public static Check inspect(Collection<Segment> segments, Oracle oracle,int bufferChunks,int maxChunks) {
    if(bufferChunks<1||maxChunks<1)throw new IllegalArgumentException("clearance");
    Set<Chunk> visited=new LinkedHashSet<>();
    try {
      // Sweeping by a full width, not just testing a line's centre points.
      for(Segment segment:segments){
        for(Chunk cell:Geometry.chunksTouched(segment,bufferChunks*16.0+0.5,maxChunks)){
          if(!visited.add(cell))continue;
          if(visited.size()>maxChunks)return new Check(false,"scan budget exceeded",Set.copyOf(visited));
          State state=oracle.get(cell);
          if(state!=State.SAFE)return new Check(false,"protected or unknown chunk "+cell+": "+state,Set.copyOf(visited));
        }
      }
      return new Check(!visited.isEmpty(),visited.isEmpty()?"no physical segments":"all checked",Set.copyOf(visited));
    } catch(RuntimeException ex) {return new Check(false,"unable to certify: "+ex.getMessage(),Set.copyOf(visited));}
  }
}
