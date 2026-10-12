package dev.kondalabs.gaslight;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Optional explicit protections for player-built regions not represented by
 * vanilla StructureStart/structure references. Full vertical columns are vetoed.
 * Corrupt/oversized policies are invalid: caller MUST fail closed.
 */
public final class ProtectedChunkRegions {
    private ProtectedChunkRegions(){}
    public record Rect(int minX,int minZ,int maxX,int maxZ){
        public Rect {if(maxX<minX||maxZ<minZ||Math.abs((long)minX)>1_844_000||
                      Math.abs((long)minZ)>1_844_000||Math.abs((long)maxX)>1_844_000||
                      Math.abs((long)maxZ)>1_844_000)
            throw new IllegalArgumentException("region invalid or outside world border");}
        public boolean contains(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
    }
    public record Policy(boolean valid,String reason,List<Rect> rectangles){
        public boolean protectedAt(int chunkX,int chunkZ){
            if(!valid)return true;
            for(Rect rect:rectangles)if(rect.contains(chunkX,chunkZ))return true;
            return false;
        }
    }
    public static Policy parse(List<String> lines){
        if(lines==null||lines.size()>1024)return new Policy(false,"missing or oversized policy",List.of());
        List<Rect> list=new ArrayList<>();
        try{
            for(int row=0;row<lines.size();row++){
                String line=lines.get(row).strip();
                if(line.isEmpty()||line.startsWith("#"))continue;
                if(line.length()>128)throw new IllegalArgumentException("line too long");
                String[] parts=line.split(",",-1);
                if(parts.length!=4)throw new IllegalArgumentException("expected minX,minZ,maxX,maxZ");
                int[] c=new int[4];
                for(int i=0;i<4;i++)c[i]=Integer.parseInt(parts[i].strip());
                list.add(new Rect(c[0],c[1],c[2],c[3]));
            }
            return new Policy(true,"validated",List.copyOf(list));
        }catch(RuntimeException e){return new Policy(false,"invalid protection policy: "+e.getMessage(),List.of());}
    }
    public static Policy load(Path file){
        try {
            if(Files.notExists(file))return new Policy(true,"no extra protected regions configured",List.of());
            if(Files.size(file)>32768)return new Policy(false,"region file too large",List.of());
            return parse(Files.readAllLines(file));
        }catch(IOException | RuntimeException e){return new Policy(false,"region file could not be read",List.of());}
    }
}
