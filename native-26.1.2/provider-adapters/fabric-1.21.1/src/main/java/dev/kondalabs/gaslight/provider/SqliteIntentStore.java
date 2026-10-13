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
    private final int maxOpen;
    SqliteIntentStore(Path file) {
        this(file,1);
    }
    SqliteIntentStore(Path file,int maxOpen) {
        if(maxOpen<1||maxOpen>3)throw new IllegalArgumentException("open intent budget");
        this.maxOpen=maxOpen;
        try {
            file=file.toAbsolutePath().normalize();
            for(Path p=file;p!=null;p=p.getParent())
                if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&Files.isSymbolicLink(p))throw new IOException("symlink in ledger path");
            Files.createDirectories(file.getParent());
            if(Files.exists(file)&&(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>20*1024*1024L))throw new IOException("invalid ledger file");
            Class.forName("org.sqlite.JDBC");
            String nativePath=file.toString();
            if(file.getFileSystem().getSeparator().equals("\\")&&nativePath.length()>=240) {
                // Increasing the VFS budget alone does not extend Windows names.
                // Escape '?' so JDBC cannot parse the prefix as PRAGMA options.
                // win32-longpath retains the normal interprocess locking.
                String extended=nativePath.startsWith("\\\\")?"\\\\?\\UNC\\"+nativePath.substring(2):"\\\\?\\"+nativePath;
                nativePath="file:"+java.net.URLEncoder.encode(extended,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20")+"?vfs=win32-longpath";
            }
            connection=DriverManager.getConnection("jdbc:sqlite:"+nativePath);
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
            sql("CREATE TABLE IF NOT EXISTS anchors (player TEXT PRIMARY KEY, dimension TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, digest TEXT NOT NULL)");
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
            int existing=Integer.parseInt(scalar("SELECT count(*) FROM intents WHERE state='OPEN'"));
            if(Integer.parseInt(scalar("SELECT count(*) FROM intents"))>=MAX_RECORDS||existing>=maxOpen)throw new SQLException("intent/history budget");
            try(var insert=connection.prepareStatement("INSERT INTO intents VALUES(?,?,'OPEN',?,?)")){
                insert.setString(1,p.owner().toString());insert.setLong(2,p.generation());insert.setBytes(3,payload);insert.setString(4,digest(payload));insert.executeUpdate();
            }
            commit();outstanding=existing+1;reason="durable intent acknowledged";return true;
        }catch(Exception e){fail(e);return false;}
    }
    @Override public synchronized boolean settle(UUID owner,long generation){
        if(failed||blocked||outstanding<1)return false;
        try{
            transaction();
            try(var update=connection.prepareStatement("UPDATE intents SET state='RETIRED' WHERE owner=? AND generation=? AND state='OPEN'")){
                update.setString(1,owner.toString());update.setLong(2,generation);if(update.executeUpdate()!=1)throw new SQLException("stale retirement");
            }
            commit();outstanding--;reason="retired intent retained as tombstone";return true;
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
    record Anchor(String dimension,double x,double y,double z){
        Anchor{if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||dimension.equals("gaslight_provider:unobserved")||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>29_000_000||Math.abs(z)>29_000_000||Math.abs(y)>2048)throw new IllegalArgumentException("invalid original return anchor");}
    }
    private static String anchorDigest(UUID player,Anchor a)throws Exception{return digest((player+"\n"+a.dimension()+"\n"+Double.toHexString(a.x())+"\n"+Double.toHexString(a.y())+"\n"+Double.toHexString(a.z())).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    synchronized boolean saveAnchor(UUID player,Anchor a){
        if(blocked||failed)return false;
        try{transaction();if(Integer.parseInt(scalar("SELECT count(*) FROM anchors"))>=1024)throw new IOException("anchor budget");
            try(var insert=connection.prepareStatement("INSERT INTO anchors VALUES(?,?,?,?,?,?) ON CONFLICT(player) DO UPDATE SET dimension=excluded.dimension,x=excluded.x,y=excluded.y,z=excluded.z,digest=excluded.digest")){
                insert.setString(1,player.toString());insert.setString(2,a.dimension());insert.setDouble(3,a.x());insert.setDouble(4,a.y());insert.setDouble(5,a.z());insert.setString(6,anchorDigest(player,a));insert.executeUpdate();
            }commit();return true;
        }catch(Exception e){fail(e);return false;}
    }
    synchronized Anchor loadAnchor(UUID player){
        if(blocked||failed)return null;
        try(var query=connection.prepareStatement("SELECT dimension,x,y,z,digest FROM anchors WHERE player=?")){
            query.setString(1,player.toString());try(var rows=query.executeQuery()){if(!rows.next())return null;var a=new Anchor(rows.getString(1),rows.getDouble(2),rows.getDouble(3),rows.getDouble(4));if(!anchorDigest(player,a).equals(rows.getString(5)))throw new IOException("return anchor checksum mismatch");return a;}
        }catch(Exception e){fail(e);return null;}
    }
    synchronized long nextGeneration(){try{return Long.parseLong(scalar("SELECT COALESCE(max(generation),0)+1 FROM intents"));}catch(SQLException e){fail(e);return -1;}}
    @Override public synchronized void close(){if(connection!=null)try{connection.close();connection=null;}catch(SQLException e){fail(e);}}
}
