package dev.kondalabs.gaslight;

import java.util.*;

/** A bounded, immutable 3x3 terrain fingerprint, sampled only from already-loaded
 * columns. It is an approximate continuity filter, NOT exact 3-D seam equivalence.
 */
public final class Phase3DestinationProfile {
    private Phase3DestinationProfile() {}
    public enum Material { AIR, SOIL, SAND, STONE, WOOD, FOLIAGE, SNOW, ICE, CLAY, OTHER, UNKNOWN }
    public enum Fluid { NONE, WATER, LAVA, UNKNOWN }
    public record Column(int groundY,Material ground,Fluid fluid,boolean twoBlockClear,String biome,
                         String blockId,int fluidTopY) {
        public Column(int groundY,Material ground,Fluid fluid,boolean twoBlockClear,String biome){
            this(groundY,ground,fluid,twoBlockClear,biome,ground.name().toLowerCase(Locale.ROOT),
                 fluid==Fluid.NONE?Integer.MIN_VALUE:groundY+1);
        }
        public Column {
            Objects.requireNonNull(ground);Objects.requireNonNull(fluid);
            if(ground==Material.UNKNOWN || ground==Material.AIR || fluid==Fluid.UNKNOWN ||
               biome==null || biome.isBlank() || biome.length()>160 || groundY<-2048 || groundY>2048 ||
               blockId==null || blockId.isBlank() || blockId.length()>200 ||
               (fluid==Fluid.NONE && fluidTopY!=Integer.MIN_VALUE) ||
               (fluid!=Fluid.NONE && (fluidTopY<groundY || fluidTopY>2048)))
                throw new IllegalArgumentException("incomplete terrain column");
            biome=biome.toLowerCase(Locale.ROOT);
        }
    }
    public record Profile(List<Column> columns) {
        public Profile {
            Objects.requireNonNull(columns);
            if(columns.size()!=9 || columns.stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException("nine certified columns required");
            columns=List.copyOf(columns);
        }
    }
    @FunctionalInterface public interface Probe { Optional<Column> read(int horizontalIndex); }
    public static Optional<Profile> capture(Probe probe) {
        if(probe==null)return Optional.empty();
        ArrayList<Column> cells=new ArrayList<>(9);
        try{
            for(int i=0;i<9;i++){
                Optional<Column> sample=probe.read(i);
                if(sample==null || sample.isEmpty())return Optional.empty();
                cells.add(sample.get());
            }
            return Optional.of(new Profile(cells));
        }catch(RuntimeException failure){return Optional.empty();}
    }
    /** Each aligned cell is compared, preserving slope, water, biome and ground.
     * A hard incompatibility anywhere rejects the entire chunk-column pair.
     */
    public record Match(boolean accepted,double score,String reason) {}
    public static Match compare(Profile source,Profile target) {
        if(source==null||target==null)return reject("missing complete fingerprint");
        if(source.columns().size()!=9||target.columns().size()!=9)return reject("invalid fingerprint");
        double score=0;
        // Headroom is a safety precondition, not a similarity bonus. Two
        // identically blocked exits must never be classified as compatible.
        for(int i=0;i<9;i++){
            Column a=source.columns().get(i),b=target.columns().get(i);
            if(a==null||b==null)return reject("missing column");
            if(!a.twoBlockClear()||!b.twoBlockClear())return reject("blocked headroom at sample "+i);
            if(a.fluid()==Fluid.LAVA||b.fluid()==Fluid.LAVA)return reject("lava at seam");
            if(a.fluid()!=b.fluid())return reject("fluid discontinuity at sample "+i);
            if(a.fluid()==Fluid.WATER && Math.abs(a.fluidTopY()-b.fluidTopY())>2)
                return reject("water level mismatch at sample "+i);
            if(!family(a.biome()).equals(family(b.biome())))
                return reject("biome family mismatch at sample "+i);
            if(a.ground()==Material.OTHER && b.ground()==Material.OTHER &&
               !a.blockId().equals(b.blockId()))return reject("unknown block mismatch at sample "+i);
            int dy=Math.abs(a.groundY()-b.groundY());
            if(dy>6)return reject("height step exceeds six blocks at "+i);
            double height=Math.max(0,1-dy/8.0);
            double material=materials(a.ground(),b.ground());
            double biome=biomes(a.biome(),b.biome());
            score+=0.46*height+0.26*material+0.18*biome+0.10*(a.twoBlockClear()==b.twoBlockClear()?1:0);
        }
        // Local shape is more important than mean height: equal-height planes with
        // incompatible inclines form plainly visible cliffs at facet boundaries.
        int[][] adjacent={{0,1},{1,2},{3,4},{4,5},{6,7},{7,8},{0,3},{3,6},{1,4},{4,7},{2,5},{5,8}};
        double shape=0;
        for(int[] edge:adjacent){
            int sa=source.columns().get(edge[0]).groundY()-source.columns().get(edge[1]).groundY();
            int sb=target.columns().get(edge[0]).groundY()-target.columns().get(edge[1]).groundY();
            int delta=Math.abs(sa-sb);
            if(delta>5)return reject("slope discontinuity");
            shape+=Math.max(0,1-delta/6.0);
        }
        double overall=0.85*(score/9.0)+0.15*(shape/adjacent.length);
        return new Match(overall>=0.76,overall,overall>=0.76?"compatible sampled terrain":"incompatible sampled terrain");
    }
    private static Match reject(String msg){return new Match(false,Double.NaN,msg);}
    private static double materials(Material a,Material b){
        if(a==b)return 1;
        if(a==Material.UNKNOWN||b==Material.UNKNOWN)return 0;
        if((a==Material.SOIL||a==Material.SAND||a==Material.CLAY) &&
           (b==Material.SOIL||b==Material.SAND||b==Material.CLAY))return .68;
        if((a==Material.SNOW||a==Material.ICE) && (b==Material.SNOW||b==Material.ICE))return .8;
        if((a==Material.WOOD||a==Material.FOLIAGE) && (b==Material.WOOD||b==Material.FOLIAGE))return .65;
        return .15;
    }
    private static double biomes(String a,String b){
        if(a.equals(b))return 1;
        String fa=family(a),fb=family(b);
        return fa.equals(fb)? .80 : .20;
    }
    private static String family(String biome){
        for(String token:new String[]{"jungle","swamp","desert","badlands","savanna","forest",
               "taiga","ocean","river","beach","snow","frozen","ice","mushroom","plains",
               "mountain","peak","nether","end","cave"})if(biome.contains(token))return token;
        return biome;
    }
    /** Simple classification only; modded block types not understood are OTHER.
     * Never infer a fluid from a solid block category.
     */
    public static Material materialFromId(String id){
        if(id==null)return Material.UNKNOWN;
        String s=id.toLowerCase(Locale.ROOT);
        // The substring "air" also occurs in "stairs". Match actual air IDs only.
        if(s.endsWith(":air")||s.contains(":air}")||
           s.endsWith(":cave_air")||s.contains(":cave_air}")||
           s.endsWith(":void_air")||s.contains(":void_air}"))return Material.AIR;
        if(s.contains("snow"))return Material.SNOW;
        if(s.contains("ice"))return Material.ICE;
        if(s.contains("sand"))return Material.SAND;
        if(s.contains("clay")||s.contains("terracotta"))return Material.CLAY;
        if(s.contains("dirt")||s.contains("grass_block")||s.contains("mud")||s.contains("podzol")||s.contains("mycelium"))return Material.SOIL;
        if(s.contains("stone")||s.contains("deepslate")||s.contains("gravel")||s.contains("tuff")||s.contains("ore"))return Material.STONE;
        if(s.contains("log")||s.contains("wood")||s.contains("planks"))return Material.WOOD;
        if(s.contains("leaves")||s.contains("moss")||s.contains("vine"))return Material.FOLIAGE;
        return Material.OTHER;
    }
}
