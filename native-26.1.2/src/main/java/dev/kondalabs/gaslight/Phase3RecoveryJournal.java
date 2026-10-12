package dev.kondalabs.gaslight;

import dev.kondalabs.gaslight.NativePortal.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Crash-surviving fail-closed record of physical publication intentions.
 * IMPORTANT: A journal does not restore/retire renderer entities by itself.
 * On restart, ANY remaining entry blocks future publication until an actual
 * world-aware recovery adapter reconciles and certifies cleanup.
 */
public final class Phase3RecoveryJournal {
    private Phase3RecoveryJournal() {}
    public interface Store {
        /** Durable intent MUST be acknowledged before touching the world. */
        boolean begin(List<Passage> bundle);
        /** Remove intent ONLY after verified abort or confirmed physical retirement. */
        boolean settle(UUID owner,long generation);
        /** A dirty prior session or corrupt journal must veto all new publication. */
        boolean blockedOnOpen();
        int outstanding();
        String diagnostic();
    }
    public record Key(UUID owner,long generation) {}
    // Package-private fault points can interrupt real IO, never bypass its checks.
    enum WritePoint { PREPARED, CANDIDATE_FORCED, REPLACED, DIRECTORIES_FORCED,
                      EVIDENCE_REMOVED, COMMITTED }
    @FunctionalInterface interface FaultPoint { void reached(WritePoint point) throws IOException; }
    /** Non-durable isolated test implementation. NEVER use for a production Host. */
    public static final class TestMemoryStore implements Store {
        private final Map<Key,List<Passage>> records=new LinkedHashMap<>();
        @Override public boolean blockedOnOpen(){return false;}
        @Override public int outstanding(){return records.size();}
        @Override public String diagnostic(){return "test-only memory store";}
        @Override public boolean begin(List<Passage> bundle){
            if(bundle==null||bundle.isEmpty()||bundle.size()>128)return false;
            Passage first=bundle.get(0);
            if(first==null)return false;
            Key key=new Key(first.owner(),first.generation());
            for(Passage p:bundle)if(p==null||!key.owner().equals(p.owner())||key.generation()!=p.generation())return false;
            return records.putIfAbsent(key,List.copyOf(bundle))==null;
        }
        @Override public boolean settle(UUID owner,long generation){return records.remove(new Key(owner,generation))!=null;}
    }
    public static final class FileStore implements Store {
        private static final int MAGIC=0x474C334A, VERSION=2, MAX_FILE=256*1024;
        private record Decoded(LinkedHashMap<Key,List<Passage>> records,long revision){}
        private final Path path;
        private final FaultPoint fault;
        private final LinkedHashMap<Key,List<Passage>> records=new LinkedHashMap<>();
        private boolean blocked,failed;
        // Optimistic on-disk version check, protected by an OS advisory lock.
        // A second Store opened before the first write must never overwrite it.
        private byte[] expectedDiskBytes;
        private long revision;
        private String reason="clean";
        public FileStore(Path path){this(path,point->{});}
        FileStore(Path path,FaultPoint fault){
            this.path=Objects.requireNonNull(path).toAbsolutePath().normalize();
            this.fault=Objects.requireNonNull(fault);
            try {
                Path parent=this.path.getParent();
                if(parent==null)throw new IOException("journal has no parent");
                if(Files.exists(parent,LinkOption.NOFOLLOW_LINKS)){
                    validateParents(parent);
                    try(FileChannel guard=openGuard();FileLock lock=guard.tryLock()){
                        if(lock==null)throw new IOException("journal held by another writer");
                        if(Files.exists(this.path,LinkOption.NOFOLLOW_LINKS)){
                            try{
                                expectedDiskBytes=readBounded(this.path);
                                Decoded primary=decode(expectedDiskBytes);
                                mergeEvidence(primary.records());revision=primary.revision();
                            }
                            catch(IOException bad){blocked=true;failed=true;reason="corrupt primary journal: "+bad.getMessage();}
                        }
                        List<Path> evidence=evidenceFiles();
                        for(Path file:evidence){
                            try{mergeEvidence(decode(readBounded(file)).records());}
                            catch(IOException bad){blocked=true;failed=true;reason="corrupt retained evidence: "+bad.getMessage();}
                        }
                        if(!evidence.isEmpty()){
                            blocked=true;
                            if(!failed)reason="unresolved transaction evidence from previous process";
                        }
                        if(!records.isEmpty()){
                            blocked=true;
                            if(!failed&&evidence.isEmpty())reason="unresolved publication intents from previous process";
                        }
                    }
                }
            }catch(Exception failure){blocked=true;failed=true;reason="unreadable or corrupt journal: "+failure.getClass().getSimpleName()+": "+failure.getMessage();}
        }
        private void mergeEvidence(Map<Key,List<Passage>> recovered)throws IOException{
            for(var entry:recovered.entrySet()){
                List<Passage> prior=records.putIfAbsent(entry.getKey(),entry.getValue());
                if(prior!=null&&!prior.equals(entry.getValue()))throw new IOException("conflicting journal records");
                if(records.size()>8)throw new IOException("too many unresolved generations");
            }
        }
        private FileChannel openGuard()throws IOException{
            return FileChannel.open(path.resolveSibling(path.getFileName()+".lock"),
                StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        }
        private static byte[] readBounded(Path file)throws IOException{
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>MAX_FILE)
                throw new IOException("nonregular or oversized journal evidence");
            try(FileChannel channel=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
                ByteBuffer bytes=ByteBuffer.allocate(MAX_FILE+1);
                while(channel.read(bytes)>0){}
                if(bytes.position()>MAX_FILE)throw new IOException("journal grew beyond permitted size");
                return Arrays.copyOf(bytes.array(),bytes.position());
            }
        }
        private List<Path> evidenceFiles()throws IOException{
            String prefix=path.getFileName().toString();
            List<Path> result=new ArrayList<>();
            try(DirectoryStream<Path> files=Files.newDirectoryStream(path.getParent())){
                for(Path file:files){
                    String name=file.getFileName().toString();
                    if(name.startsWith(prefix+".pending-")||name.startsWith(prefix+".candidate-")||
                       (name.startsWith(".gaslight-intent-")&&name.endsWith(".tmp"))){
                        result.add(file);
                        if(result.size()>16)throw new IOException("too many retained journal files");
                    }
                }
            }
            return result;
        }
        private static void validateParents(Path parent)throws IOException{
            for(Path dir=parent;dir!=null;dir=dir.getParent())
                if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))throw new IOException("non-directory or linked journal ancestor");
        }
        private static void forceDirectories(Path parent)throws IOException{
            String storage=Files.getFileStore(parent).type().toLowerCase(Locale.ROOT);
            // Only the tested local storage families are eligible. A successful
            // fsync on tmpfs, network mounts or an unknown provider is not proof
            // that publication intent survives power loss.
            if(!Set.of("ext4","ntfs").contains(storage))
                throw new IOException("directory durability unconfirmed: uncertified storage type "+storage);
            // Include ancestors: a newly created parent name also needs durability.
            for(Path dir=parent;dir!=null;dir=dir.getParent()){
                try(FileChannel channel=FileChannel.open(dir,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
                    channel.force(true);
                }catch(IOException|UnsupportedOperationException unavailable){
                    throw new IOException("directory durability unconfirmed: "+unavailable.getClass().getSimpleName(),unavailable);
                }
            }
        }
        private static void writeForced(Path file,byte[] encoded)throws IOException{
            try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS)){
                ByteBuffer bytes=ByteBuffer.wrap(encoded);
                while(bytes.hasRemaining())channel.write(bytes);
                channel.force(true);
            }
        }
        @Override public synchronized boolean blockedOnOpen(){return blocked||failed;}
        @Override public synchronized int outstanding(){return records.size();}
        @Override public synchronized String diagnostic(){return reason;}
        @Override public synchronized boolean begin(List<Passage> bundle){
            if(blocked||failed||bundle==null||bundle.isEmpty()||bundle.size()>128||records.size()>=8)return false;
            Passage first=bundle.get(0);
            if(first==null)return false;
            Key key=new Key(first.owner(),first.generation());
            if(records.containsKey(key))return false;
            for(Passage p:bundle)if(p==null||!key.owner().equals(p.owner())||key.generation()!=p.generation())return false;
            LinkedHashMap<Key,List<Passage>> proposal=new LinkedHashMap<>(records);
            proposal.put(key,List.copyOf(bundle));
            if(!write(proposal))return false;
            records.clear();records.putAll(proposal);return true;
        }
        @Override public synchronized boolean settle(UUID owner,long generation){
            if(blocked||failed||owner==null)return false;
            Key key=new Key(owner,generation);
            if(!records.containsKey(key))return false;
            LinkedHashMap<Key,List<Passage>> proposal=new LinkedHashMap<>(records);
            proposal.remove(key);
            if(!write(proposal))return false;
            records.clear();records.putAll(proposal);return true;
        }
        private boolean write(Map<Key,List<Passage>> snapshot){
            Path temp=null,evidence=null;
            Map<Key,List<Passage>> retained=new LinkedHashMap<>(records);
            retained.putAll(snapshot);
            byte[] evidenceBytes=null;
            boolean changedDisk=false;
            String stage="before preparation";
            try {
                long nextRevision=Math.addExact(revision,1);
                byte[] encoded=encode(snapshot,nextRevision);
                evidenceBytes=encode(retained,nextRevision);
                Path parent=path.getParent();
                Files.createDirectories(parent);
                validateParents(parent);
                // Serialize multiple JVMs/processes touching the same world journal.
                // tryLock fails closed rather than waiting on the server tick thread.
                try(FileChannel guardChannel=openGuard()){
                    try(FileLock lock=guardChannel.tryLock()){
                        if(lock==null)throw new IOException("journal held by another writer");
                        try {
                        byte[] actual=Files.exists(path,LinkOption.NOFOLLOW_LINKS)?readBounded(path):null;
                        if(!Arrays.equals(actual,expectedDiskBytes))
                            throw new IOException("stale journal writer or external file change");
                        if(!evidenceFiles().isEmpty())throw new IOException("unresolved transaction evidence");
                        evidence=Files.createTempFile(parent,path.getFileName()+".pending-",".bin");
                        changedDisk=true;stage="preparation";
                        writeForced(evidence,evidenceBytes);
                        fault.reached(WritePoint.PREPARED);
                        temp=Files.createTempFile(parent,path.getFileName()+".candidate-",".bin");
                        writeForced(temp,encoded);
                        fault.reached(WritePoint.CANDIDATE_FORCED);
                        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                        stage="replacement";fault.reached(WritePoint.REPLACED);
                        forceDirectories(parent);
                        fault.reached(WritePoint.DIRECTORIES_FORCED);
                        // Only remove evidence after the new primary is certified.
                        Files.delete(evidence);
                        stage="evidence cleanup";fault.reached(WritePoint.EVIDENCE_REMOVED);
                        forceDirectories(parent);
                        fault.reached(WritePoint.COMMITTED);
                        expectedDiskBytes=encoded.clone();
                        revision=nextRevision;
                        reason="durable checkpoint committed";
                        return true;
                        }catch(Exception ex){
                            // Restore evidence before releasing the writer lock.
                            return quarantine(ex,stage,retained,changedDisk,evidence,evidenceBytes);
                        }
                    }
                }
            }catch(Exception ex){
                return quarantine(ex,stage,retained,changedDisk,evidence,evidenceBytes);
            }
        }
        private boolean quarantine(Exception ex,String stage,Map<Key,List<Passage>> retained,
                boolean changedDisk,Path evidence,byte[] evidenceBytes){
            failed=true;reason="journal write/atomicity unconfirmed at "+stage+": "+ex.getClass().getSimpleName()+": "+ex.getMessage();
            if(changedDisk){
                records.clear();records.putAll(retained);
                // Keep every ambiguous intent, including a failed settlement.
                // Do not erase a candidate or pending file in failure cleanup.
                if(evidence!=null&&!Files.exists(evidence,LinkOption.NOFOLLOW_LINKS)){
                    try{
                        Files.createFile(evidence);writeForced(evidence,evidenceBytes);
                    }catch(Exception restore){reason+="; evidence restoration failed: "+restore.getClass().getSimpleName();}
                }
            }
            return false;
        }
        private static byte[] encode(Map<Key,List<Passage>> contents,long revision)throws IOException {
            var data=new ByteArrayOutputStream();
            var out=new DataOutputStream(data);
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(contents.size());
            for(var entry:contents.entrySet()){
                Key k=entry.getKey();
                out.writeLong(k.owner().getMostSignificantBits());out.writeLong(k.owner().getLeastSignificantBits());
                out.writeLong(k.generation());out.writeInt(entry.getValue().size());
                for(Passage p:entry.getValue()){
                    writeSurface(out,p.entrance());writeSurface(out,p.exit());
                }
            }
            out.writeLong(revision);
            out.flush();
            byte[] body=data.toByteArray();
            if(body.length+8>MAX_FILE)throw new IOException("journal over size limit");
            CRC32 crc=new CRC32();crc.update(body);
            out.writeLong(crc.getValue());out.flush();
            return data.toByteArray();
        }
        private static void writeSurface(DataOutputStream out,Surface s)throws IOException {
            for(double v:new double[]{s.centre().x(),s.centre().y(),s.centre().z(),
                s.tangent().x(),s.tangent().y(),s.tangent().z(),s.width(),s.height()})out.writeDouble(v);
        }
        private static Surface readSurface(DataInputStream in)throws IOException {
            return new Surface(new V(in.readDouble(),in.readDouble(),in.readDouble()),
                new V(in.readDouble(),in.readDouble(),in.readDouble()),in.readDouble(),in.readDouble());
        }
        private static Decoded decode(byte[] bytes)throws IOException {
            LinkedHashMap<Key,List<Passage>> decoded=new LinkedHashMap<>();
            if(bytes.length<20||bytes.length>MAX_FILE)throw new IOException("invalid length");
            CRC32 crc=new CRC32();crc.update(bytes,0,bytes.length-8);
            long expected=ByteBuffer.wrap(bytes,bytes.length-8,8).getLong();
            if(crc.getValue()!=expected)throw new IOException("checksum mismatch");
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-8));
            if(in.readInt()!=MAGIC)throw new IOException("unknown journal magic");
            int version=in.readInt();
            if(version!=1&&version!=VERSION)throw new IOException("unknown journal version");
            int n=in.readInt();if(n<0||n>8)throw new IOException("invalid generation count");
            for(int i=0;i<n;i++){
                UUID owner=new UUID(in.readLong(),in.readLong());long gen=in.readLong();
                int count=in.readInt();if(count<1||count>128)throw new IOException("invalid facet count");
                Key key=new Key(owner,gen);
                List<Passage> passages=new ArrayList<>();
                for(int j=0;j<count;j++){
                    try{passages.add(new Passage(readSurface(in),readSurface(in),owner,gen));}
                    catch(RuntimeException ex){throw new IOException("invalid surface",ex);}
                }
                if(decoded.putIfAbsent(key,List.copyOf(passages))!=null)throw new IOException("duplicate generation");
            }
            long revision=version==1?0:in.readLong();
            if(revision<0)throw new IOException("negative journal revision");
            if(in.available()!=0)throw new IOException("trailing data");
            return new Decoded(decoded,revision);
        }
    }
}
