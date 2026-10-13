package dev.kondalabs.gaslight.provider;

import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.Phase3RecoveryJournal;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Durable intent history; never deletes unresolved records. SQLite's native VFS
 * performs platform-specific FULL synchronization. This is not a transaction over
 * Minecraft saves. Recovery relies on the permanent owned-entity load veto. */
final class SqliteIntentStore implements Phase3RecoveryJournal.Store, AutoCloseable {
    private static final int MAX_BLOB=65536, MAX_RECORDS=4096;
    private Connection connection;
    private long revision;
    private boolean blocked, failed;
    private int outstanding=-1;
    private String reason="clean";
    SqliteIntentStore(Path file) {
        try {
            file=file.toAbsolutePath().normalize();
            for(Path p=file;p!=null;p=p.getParent())
                if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&Files.isSymbolicLink(p))throw new IOException("symlink in ledger path");
            Files.createDirectories(file.getParent());
            if(Files.exists(file)&&(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>20*1024*1024L))throw new IOException("invalid ledger file");
            Class.forName("org.sqlite.JDBC");
            connection=DriverManager.getConnection("jdbc:sqlite:"+file);
            sql("PRAGMA busy_timeout=1500");sql("PRAGMA trusted_schema=OFF");
            if(!scalar("PRAGMA journal_mode=PERSIST").equalsIgnoreCase("persist"))throw new IOException("persistent rollback journal unavailable");
            sql("PRAGMA synchronous=FULL");sql("PRAGMA max_page_count=4096");
            sql("PRAGMA cache_size=-2048");
            if(!scalar("PRAGMA page_size").equals("4096")||!scalar("PRAGMA max_page_count").equals("4096"))throw new IOException("ledger disk page budget");
            if(!scalar("PRAGMA synchronous").equals("2")||!scalar("PRAGMA quick_check").equals("ok"))throw new IOException("durability/integrity check failed");
            String version=scalar("PRAGMA user_version");
            if(!version.equals("0")&&!version.equals("1"))throw new IOException("unsupported ledger schema");
            sql("CREATE TABLE IF NOT EXISTS metadata (singleton INTEGER PRIMARY KEY CHECK(singleton=1), revision INTEGER NOT NULL)");
            sql("INSERT OR IGNORE INTO metadata VALUES(1,0)");
            sql("CREATE TABLE IF NOT EXISTS intents (owner TEXT NOT NULL, generation INTEGER NOT NULL CHECK(generation>0), state TEXT NOT NULL CHECK(state IN ('OPEN','RETIRED','RECOVERED')), payload BLOB NOT NULL, digest TEXT NOT NULL, PRIMARY KEY(owner,generation))");
            sql("PRAGMA user_version=1");
            revision=Long.parseLong(scalar("SELECT revision FROM metadata WHERE singleton=1"));
            if(revision<0||revision>100000)throw new IOException("invalid ledger revision");
            validateHistory();
            outstanding=Integer.parseInt(scalar("SELECT count(*) FROM intents WHERE state='OPEN'"));
            blocked=outstanding!=0;
            if(blocked)reason="unresolved durable intents; owned-entity reconciliation required";
        }catch(Exception e){fail(e);}
    }
    private void sql(String command)throws SQLException {try(var s=connection.createStatement()){s.execute(command);}}
    private String scalar(String command)throws SQLException {try(var s=connection.createStatement();var r=s.executeQuery(command)){if(!r.next())throw new SQLException("missing scalar");return r.getString(1);}}
    private void validateHistory()throws Exception {
        if(Integer.parseInt(scalar("SELECT count(*) FROM intents"))>MAX_RECORDS)throw new IOException("ledger history budget");
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT owner,generation,state,length(payload),payload,digest FROM intents")){
            while(r.next()){
                if(r.getInt(4)<1||r.getInt(4)>MAX_BLOB)throw new IOException("payload size");
                byte[] payload=r.getBytes(5);
                if(!digest(payload).equals(r.getString(6)))throw new IOException("payload checksum mismatch");
                var bundle=decode(payload);
                if(!bundle.getFirst().owner().toString().equals(r.getString(1))||bundle.getFirst().generation()!=r.getLong(2))throw new IOException("intent identity mismatch");
            }
        }
    }
    private static String digest(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static byte[] encode(List<Passage> bundle)throws IOException {
        if(bundle==null||bundle.isEmpty()||bundle.size()>128)throw new IOException("bundle count");
        Passage first=Objects.requireNonNull(bundle.getFirst());
        if(first.generation()<1)throw new IOException("generation");
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)){
            out.writeInt(0x474C5349);out.writeInt(bundle.size());
            out.writeLong(first.owner().getMostSignificantBits());out.writeLong(first.owner().getLeastSignificantBits());out.writeLong(first.generation());
            for(Passage p:bundle){
                if(p==null||!p.owner().equals(first.owner())||p.generation()!=first.generation())throw new IOException("mixed bundle fence");
                for(Surface surface:List.of(p.entrance(),p.exit())){
                    for(double d:new double[]{surface.centre().x(),surface.centre().y(),surface.centre().z(),surface.tangent().x(),surface.tangent().y(),surface.tangent().z(),surface.width(),surface.height()}){
                        if(!Double.isFinite(d))throw new IOException("non-finite surface");out.writeDouble(d);
                    }
                }
            }
        }
        if(bytes.size()>MAX_BLOB)throw new IOException("payload budget");return bytes.toByteArray();
    }
    private static List<Passage> decode(byte[] bytes)throws IOException {
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readInt()!=0x474C5349)throw new IOException("payload magic");
            int count=in.readInt();if(count<1||count>128)throw new IOException("payload count");
            UUID owner=new UUID(in.readLong(),in.readLong());long generation=in.readLong();var result=new ArrayList<Passage>();
            for(int i=0;i<count;i++){
                var surfaces=new ArrayList<Surface>();
                for(int j=0;j<2;j++)surfaces.add(new Surface(new V(in.readDouble(),in.readDouble(),in.readDouble()),new V(in.readDouble(),in.readDouble(),in.readDouble()),in.readDouble(),in.readDouble()));
                result.add(new Passage(surfaces.get(0),surfaces.get(1),owner,generation));
            }
            if(in.available()!=0||generation<1)throw new IOException("payload trailing bytes/generation");return List.copyOf(result);
        }catch(RuntimeException bad){throw new IOException("invalid intent geometry",bad);}
    }
    private void transaction()throws SQLException {
        sql("BEGIN IMMEDIATE");
        try(var update=connection.prepareStatement("UPDATE metadata SET revision=revision+1 WHERE singleton=1 AND revision=?")){
            update.setLong(1,revision);if(update.executeUpdate()!=1)throw new SQLException("stale journal writer");
        }
    }
    private void commit()throws SQLException {sql("COMMIT");revision++;}
    private void fail(Exception e){
        failed=true;blocked=true;outstanding=-1;reason=e.getClass().getSimpleName()+": "+e.getMessage();
        if(connection!=null)try{sql("ROLLBACK");}catch(SQLException ignored){}
    }
    @Override public synchronized boolean begin(List<Passage> bundle){
        if(blocked||failed)return false;
        try{
            byte[] payload=encode(bundle);Passage p=bundle.getFirst();transaction();
            if(Integer.parseInt(scalar("SELECT count(*) FROM intents"))>=MAX_RECORDS||!scalar("SELECT count(*) FROM intents WHERE state='OPEN'").equals("0"))throw new SQLException("intent/history budget");
            try(var insert=connection.prepareStatement("INSERT INTO intents VALUES(?,?,'OPEN',?,?)")){
                insert.setString(1,p.owner().toString());insert.setLong(2,p.generation());insert.setBytes(3,payload);insert.setString(4,digest(payload));insert.executeUpdate();
            }
            commit();outstanding=1;reason="durable intent acknowledged";return true;
        }catch(Exception e){fail(e);return false;}
    }
    @Override public synchronized boolean settle(UUID owner,long generation){
        if(failed||blocked||outstanding!=1)return false;
        try{
            transaction();
            try(var update=connection.prepareStatement("UPDATE intents SET state='RETIRED' WHERE owner=? AND generation=? AND state='OPEN'")){
                update.setString(1,owner.toString());update.setLong(2,generation);if(update.executeUpdate()!=1)throw new SQLException("stale retirement");
            }
            commit();outstanding=0;reason="retired intent retained as tombstone";return true;
        }catch(Exception e){fail(e);return false;}
    }
    /** Called only after the adapter's permanent load veto is installed, all owned
     * runtime entities/loaders are absent, and there are no connected observers.
     * Old saved entities stay unauthorized on every subsequent chunk load. */
    synchronized boolean reconcileWithLoadVeto(){
        if(failed)return false;if(!blocked)return true;
        try{transaction();validateHistory();sql("UPDATE intents SET state='RECOVERED' WHERE state='OPEN'");commit();outstanding=0;blocked=false;reason="recovered under permanent owned-entity load veto; evidence retained";return true;}
        catch(Exception e){fail(e);return false;}
    }
    @Override public synchronized boolean blockedOnOpen(){return blocked||failed;}
    @Override public synchronized int outstanding(){return outstanding;}
    @Override public synchronized String diagnostic(){return reason;}
    @Override public synchronized void close(){if(connection!=null)try{connection.close();connection=null;}catch(SQLException e){fail(e);}}
}
