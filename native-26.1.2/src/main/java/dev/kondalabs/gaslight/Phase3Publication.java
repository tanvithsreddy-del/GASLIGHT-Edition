package dev.kondalabs.gaslight;

import java.util.*;
import dev.kondalabs.gaslight.NativePortal.*;

/** Pure-Java publication protocol. Rendering atomicity is ONLY as strong as the
 * concrete host's transaction implementation. Never infer it from this class.
 */
public final class Phase3Publication {
    private Phase3Publication() {}
    public interface Transaction {
        /** Host has staged ALL linked source/destination facets without exposing any. */
        boolean staged();
        /** Commit must expose the entire bundle or expose none. */
        boolean commit();
        /** Undo everything, including partial staging/commit where possible. */
        boolean abort();
    }
    @FunctionalInterface public interface Stager {
        Optional<Transaction> stage(List<Passage> immutableBundle);
    }
    public record Result(boolean committed,boolean cleanupConfirmed,String reason) {}
    public static Result publish(List<Passage> bundle,Stager stager) {
        if(bundle==null||bundle.isEmpty()||bundle.size()>128||stager==null)
            return new Result(false,true,"invalid bundle");
        UUID owner=null;long generation=-1;
        for(Passage p:bundle) {
            if(p==null||p.entrance()==null||p.exit()==null)
                return new Result(false,true,"null facet");
            if(owner==null){owner=p.owner();generation=p.generation();}
            if(!owner.equals(p.owner())||generation!=p.generation())
                return new Result(false,true,"mixed owner or generation");
        }
        Transaction tx;
        try {tx=stager.stage(List.copyOf(bundle)).orElse(null);}
        catch(RuntimeException ex){return new Result(false,false,"stage failed unexpectedly; cleanup not proven");}
        if(tx==null)return new Result(false,true,"atomic stage unavailable");
        try {
            if(!tx.staged())return new Result(false,safeAbort(tx),"stage not certified");
            if(!tx.commit())return new Result(false,safeAbort(tx),"commit refused");
            return new Result(true,true,"committed whole generation");
        }catch(RuntimeException ex){
            return new Result(false,safeAbort(tx),"transaction threw "+ex.getClass().getSimpleName());
        }
    }
    private static boolean safeAbort(Transaction tx){
        try{return tx.abort();}catch(RuntimeException ignored){return false;}
    }
}
