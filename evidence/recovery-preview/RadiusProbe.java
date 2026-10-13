public class RadiusProbe {
 public static void main(String[] args){
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var s=net.minecraft.world.level.chunk.status.ChunkStatus.FULL;
  System.out.println("ACTUAL_LOADING_RADIUS="+net.minecraft.world.level.chunk.status.ChunkPyramid.LOADING_PYRAMID.getStepTo(s).accumulatedDependencies().getRadius());
  System.out.println("ACTUAL_GENERATION_RADIUS="+net.minecraft.world.level.chunk.status.ChunkPyramid.GENERATION_PYRAMID.getStepTo(s).accumulatedDependencies().getRadius());
 }
}
