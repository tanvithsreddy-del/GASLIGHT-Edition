package dev.kondalabs.gaslight;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import dev.kondalabs.gaslight.Geometry.Chunk;

/** Per-world read-only candidate offsets, never a claim that chunks are loaded.
 * All entries must subsequently pass the full source/destination structure,
 * visibility and terrain audits. Missing/malformed catalog => empty candidates.
 */
public final class Phase3PreparedCatalog {
    private Phase3PreparedCatalog() {}
    public record Result(boolean valid,String reason,List<Chunk> offsets) {}
    public static Result load(Path file,int maximum) {
        if(file==null||maximum<1||maximum>8)return new Result(false,"invalid catalog settings",List.of());
        if(Files.notExists(file))return new Result(true,"no catalog configured",List.of());
        try {
            if(Files.size(file)>8192)return new Result(false,"catalog exceeds 8 KiB",List.of());
            List<String> lines=Files.readAllLines(file);
            LinkedHashSet<Chunk> points=new LinkedHashSet<>();
            int lineNumber=0;
            for(String line:lines){
                lineNumber++;
                String trimmed=line.trim();
                if(trimmed.isBlank()||trimmed.startsWith("#"))continue;
                String[] parts=trimmed.split(",",-1);
                if(parts.length!=2)return new Result(false,"invalid CSV line "+lineNumber,List.of());
                int dx=Integer.parseInt(parts[0].trim());
                int dz=Integer.parseInt(parts[1].trim());
                if(Math.max(Math.abs((long)dx),Math.abs((long)dz))<64||
                   Math.abs((long)dx)>1_800_000||Math.abs((long)dz)>1_800_000)
                    return new Result(false,"invalid distance/border line "+lineNumber,List.of());
                if(!points.add(new Chunk(dx,dz)))
                    return new Result(false,"duplicate catalog offset line "+lineNumber,List.of());
                if(points.size()>maximum)return new Result(false,"catalog candidate limit",List.of());
            }
            return new Result(true,"offsets parsed but NOT yet terrain-certified",List.copyOf(points));
        }catch(IOException|RuntimeException failure){
            return new Result(false,"catalog invalid or inaccessible: "+failure.getClass().getSimpleName(),List.of());
        }
    }
}
