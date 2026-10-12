package dev.kondalabs.gaslight;

import dev.kondalabs.gaslight.NativePortal.*;
import dev.kondalabs.gaslight.Phase3RecoveryJournal.*;
import dev.kondalabs.gaslight.Phase3RecoveryJournal.FileStore;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/** Real filesystem/process tests. Process death models interruption, not hardware power loss. */
public final class RecoveryJournalTests {
    private static final UUID OWNER=UUID.fromString("423071f1-0000-0000-0000-000000000003");
    private static int assertions,crashes,platformBlockedPoints;
    private static void yes(boolean ok,String message){assertions++;if(!ok)throw new AssertionError("RECOVERY: "+message);}
    private static Passage passage(long generation){
        return new Passage(new Surface(new V(5,128,-30),new V(1,0,0),16,384),
            new Surface(new V(1120005,128,-1280030),new V(1,0,0),16,384),OWNER,generation);
    }
    private static List<Path> evidence(Path path)throws IOException{
        try(var stream=Files.list(path.getParent())){
            String prefix=path.getFileName().toString();
            return stream.filter(p->p.getFileName().toString().startsWith(prefix+".pending-")||
                p.getFileName().toString().startsWith(prefix+".candidate-")).toList();
        }
    }
    private static Map<String,byte[]> image(Path dir)throws IOException{
        Map<String,byte[]> result=new TreeMap<>();
        try(var files=Files.list(dir)){
            for(Path p:files.toList())if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                result.put(p.getFileName().toString(),Files.readAllBytes(p));
        }
        return result;
    }
    private static boolean same(Map<String,byte[]> a,Map<String,byte[]> b){
        return a.keySet().equals(b.keySet())&&a.keySet().stream().allMatch(k->Arrays.equals(a.get(k),b.get(k)));
    }
    private static Process child(List<String> args,Path log)throws Exception{
        Set<String> classes=new LinkedHashSet<>();
        for(Class<?> type:List.of(RecoveryJournalTests.class,Phase3RecoveryJournal.class))
            classes.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        List<String> command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),
            "-Xmx128m","-Djava.io.tmpdir="+System.getProperty("java.io.tmpdir"),"-cp",String.join(File.pathSeparator,classes),RecoveryJournalTests.class.getName()));
        command.addAll(args);
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    private static int finish(Process process,Path log)throws Exception{
        if(!process.waitFor(25,TimeUnit.SECONDS)){
            process.destroyForcibly();process.waitFor();throw new AssertionError("journal child timeout");
        }
        String output=Files.readString(log);
        System.out.println("CHILD_LOG,"+log+","+output.replace('\n','|'));
        return process.exitValue();
    }
    private static void nativeBehavior(Path root)throws Exception{
        Path dir=Files.createDirectory(root.resolve("native")),path=dir.resolve("j.bin");
        boolean durable=JournalTestSupport.directorySyncAvailable(dir);
        FileStore store=new FileStore(path);
        boolean acknowledged=store.begin(List.of(passage(1),passage(1).reversed()));
        yes(acknowledged==durable,"real begin must agree with directory durability capability");
        yes(store.outstanding()==1,"ambiguous write remains visible in memory");
        FileStore restart=new FileStore(path);
        yes(restart.blockedOnOpen()&&restart.outstanding()==1,"outstanding publication blocks restart");
        Map<String,byte[]> before=image(dir);
        yes(!restart.settle(OWNER,1)&&!restart.begin(List.of(passage(2))),"restart cannot erase or publish unresolved state");
        yes(same(before,image(dir)),"failed recovery operations leave evidence byte-for-byte unchanged");
        if(durable){
            yes(store.settle(OWNER,1),"same-session confirmed retirement can settle");
            yes(evidence(path).isEmpty(),"successful certified transaction cleans only its own evidence");
            yes(!new FileStore(path).blockedOnOpen(),"certified empty checkpoint can reopen clean");
        }else{
            yes(store.blockedOnOpen()&&store.diagnostic().contains("directory durability unconfirmed"),"Windows failure remains truthful");
            yes(!evidence(path).isEmpty(),"unsupported directory durability keeps transaction evidence");
            yes(!store.settle(OWNER,1)&&same(before,image(dir)),"unsupported writer cannot delete retained records");
        }
        System.out.println("NATIVE_DIRECTORY_DURABILITY,"+durable+",begin_acknowledged="+acknowledged);
    }
    private static void failures(Path root)throws Exception{
        Path probe=Files.createDirectory(root.resolve("capability"));
        boolean durable=JournalTestSupport.directorySyncAvailable(probe);
        for(WritePoint point:WritePoint.values()){
            Path dir=Files.createDirectory(root.resolve("throw-"+point)),path=dir.resolve("j.bin");
            boolean[] reached={false};
            FileStore store=new FileStore(path,p->{if(p==point){reached[0]=true;throw new IOException("injected interruption "+p);}});
            yes(!store.begin(List.of(passage(10))),"IO interruption never acknowledges publication "+point);
            boolean reachable=durable||point.ordinal()<=WritePoint.REPLACED.ordinal();
            yes(reached[0]==reachable,"native callback reachability is truthful "+point);
            yes(store.blockedOnOpen()&&store.outstanding()==1,"failed writer retains ownership "+point);
            yes(!evidence(path).isEmpty(),"failure never silently discards transaction evidence "+point);
            FileStore restart=new FileStore(path);
            yes(restart.blockedOnOpen()&&restart.outstanding()==1,"restart after IO failure quarantines "+point);
            Map<String,byte[]> before=image(dir);
            yes(!restart.begin(List.of(passage(11)))&&!restart.settle(OWNER,10)&&same(before,image(dir)),
                "no publication or deletion after recovery failure "+point);
            System.out.println("IO_FAULT,"+point+",reached="+reached[0]+",reason="+store.diagnostic());
        }
        if(durable){
            for(WritePoint point:WritePoint.values()){
                Path dir=Files.createDirectory(root.resolve("settle-fault-"+point)),path=dir.resolve("j.bin");
                boolean[] settling={false};
                FileStore store=new FileStore(path,p->{if(settling[0]&&p==point)throw new IOException("retirement write interrupted");});
                yes(store.begin(List.of(passage(12))),"setup settlement with certified intent");
                settling[0]=true;
                yes(!store.settle(OWNER,12)&&store.blockedOnOpen(),"failed settlement blocks original owner");
                FileStore restart=new FileStore(path);
                yes(restart.blockedOnOpen()&&restart.outstanding()==1,"old intent survives failed clearing "+point);
            }
        }else{
            System.out.println("SETTLEMENT_FAULTS,BLOCKED_NATIVE_DIRECTORY_DURABILITY,no_certified_begin_available");
        }
    }
    private static void processInterruptions(Path root)throws Exception{
        boolean durable=JournalTestSupport.directorySyncAvailable(root);
        for(WritePoint point:WritePoint.values()){
            Path dir=Files.createDirectory(root.resolve("crash-"+point)),path=dir.resolve("j.bin"),log=dir.resolve("child.log");
            int exit=finish(child(List.of("crash",path.toString(),point.name()),log),log);
            boolean reachable=durable||point.ordinal()<=WritePoint.REPLACED.ordinal();
            yes(exit==(reachable?97:23),"abrupt process death or native durability block "+point);
            if(reachable)crashes++;else platformBlockedPoints++;
            FileStore restart=new FileStore(path);
            yes(restart.blockedOnOpen()&&restart.outstanding()==1,"process interruption preserves intent "+point);
            Map<String,byte[]> before=image(dir);
            yes(!restart.begin(List.of(passage(21)))&&!restart.settle(OWNER,20)&&same(before,image(dir)),
                "restart after process death never publishes or erases "+point);
            System.out.println("PROCESS_INTERRUPT,"+point+",exit="+exit+",native_point_reachable="+reachable);
        }
        Path acknowledged=Files.createDirectory(root.resolve("crash-after-ack")),ackPath=acknowledged.resolve("j.bin");
        Path ackLog=acknowledged.resolve("child.log");
        int ackExit=finish(child(List.of("after-ack",ackPath.toString()),ackLog),ackLog);
        yes(ackExit==(durable?98:23),"death after acknowledged begin is reached only on certified storage");
        FileStore resumed=new FileStore(ackPath);
        yes(resumed.blockedOnOpen()&&resumed.outstanding()==1,"acknowledged intent survives a real child restart");
        if(durable){
            crashes++;
            for(WritePoint point:WritePoint.values()){
                Path dir=Files.createDirectory(root.resolve("settle-crash-"+point)),path=dir.resolve("j.bin"),log=dir.resolve("child.log");
                yes(finish(child(List.of("settle-crash",path.toString(),point.name()),log),log)==97,
                    "confirmed-retirement process interrupted at "+point);
                crashes++;
                FileStore reopened=new FileStore(path);
                if(point.ordinal()<WritePoint.EVIDENCE_REMOVED.ordinal())
                    yes(reopened.blockedOnOpen()&&reopened.outstanding()==1,"interrupted clearing retains old intent "+point);
                else yes(!reopened.blockedOnOpen()&&reopened.outstanding()==0,
                    "already-certified empty checkpoint represents confirmed retirement, not erased uncertainty");
                System.out.println("RETIREMENT_PROCESS_INTERRUPT,"+point+",restart_blocked="+reopened.blockedOnOpen());
            }
        }else platformBlockedPoints++;
    }
    private static void simultaneousProcesses(Path root)throws Exception{
        boolean durable=JournalTestSupport.directorySyncAvailable(root);
        for(int trial=0;trial<5;trial++){
            Path dir=Files.createDirectory(root.resolve("race-"+trial)),path=dir.resolve("j.bin");
            Path firstLog=dir.resolve("one.log"),secondLog=dir.resolve("two.log");
            Process one=child(List.of("race",path.toString(),"101"),firstLog);
            long firstDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(!Files.exists(dir.resolve("ready-101"))&&System.nanoTime()<firstDeadline)Thread.sleep(10);
            yes(Files.exists(dir.resolve("ready-101")),"first OS writer opened before starting the second");
            Process two=child(List.of("race",path.toString(),"102"),secondLog);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(!(Files.exists(dir.resolve("ready-101"))&&Files.exists(dir.resolve("ready-102")))&&System.nanoTime()<deadline)
                Thread.sleep(10);
            yes(Files.exists(dir.resolve("ready-101"))&&Files.exists(dir.resolve("ready-102")),"both OS writers opened before racing");
            Files.writeString(dir.resolve("go"),"go");
            int a=finish(one,firstLog),b=finish(two,secondLog);
            yes(durable?((a==0&&b==23)||(a==23&&b==0)):(a==23&&b==23),"exactly one certified OS writer or no uncertified writers");
            FileStore restart=new FileStore(path);
            yes(restart.blockedOnOpen()&&restart.outstanding()==1,"OS writers preserve exactly one selected intent");
            long selected=ByteBuffer.wrap(Files.readAllBytes(path)).getLong(28);
            yes(selected==101||selected==102,"primary belongs to one real OS writer");
            System.out.println("OS_WRITER_RACE,"+trial+",exits="+a+"/"+b+",selected="+selected);
        }
    }
    private static byte[] crc(byte[] bytes){
        CRC32 value=new CRC32();value.update(bytes,0,bytes.length-8);
        ByteBuffer.wrap(bytes,bytes.length-8,8).putLong(value.getValue());return bytes;
    }
    private static void malformedAndRetained(Path root)throws Exception{
        Path seed=Files.createDirectory(root.resolve("seed")),seedPath=seed.resolve("j.bin");
        new FileStore(seedPath).begin(List.of(passage(30)));
        byte[] valid=Files.readAllBytes(seedPath);
        for(int length=0;length<valid.length;length++){
            Path dir=Files.createDirectory(root.resolve("partial-"+length)),path=dir.resolve("j.bin");
            byte[] partial=Arrays.copyOf(valid,length);Files.write(path,partial);
            FileStore store=new FileStore(path);
            yes(store.blockedOnOpen()&&!store.begin(List.of(passage(31))),"every partial primary is unsafe "+length);
            yes(Arrays.equals(partial,Files.readAllBytes(path)),"partial primary is retained unchanged");
        }
        for(int offset=0;offset<valid.length;offset++){
            Path dir=Files.createDirectory(root.resolve("bit-"+offset)),path=dir.resolve("j.bin");
            byte[] bad=valid.clone();bad[offset]^=1;Files.write(path,bad);
            FileStore store=new FileStore(path);
            yes(store.blockedOnOpen()&&!store.settle(OWNER,30),"every one-bit corruption fails closed");
            yes(Arrays.equals(bad,Files.readAllBytes(path)),"corrupt primary is never erased");
        }
        for(int mode=0;mode<6;mode++){
            Path dir=Files.createDirectory(root.resolve("structural-"+mode)),path=dir.resolve("j.bin");
            byte[] bad=valid.clone();ByteBuffer buffer=ByteBuffer.wrap(bad);
            switch(mode){
                case 0->buffer.putInt(0,0);
                case 1->buffer.putInt(4,99);
                case 2->buffer.putInt(8,9);
                case 3->buffer.putLong(28,-1);
                case 4->buffer.putInt(36,0);
                case 5->buffer.putLong(bad.length-16,-1);
            }
            Files.write(path,crc(bad));
            yes(new FileStore(path).blockedOnOpen(),"CRC-valid malformed record is unsafe "+mode);
        }
        for(String kind:List.of("pending","candidate")){
            Path dir=Files.createDirectory(root.resolve("retained-"+kind)),path=dir.resolve("j.bin");
            Path retained=dir.resolve("j.bin."+kind+"-fixture.bin");Files.write(retained,valid);
            FileStore store=new FileStore(path);
            yes(store.blockedOnOpen()&&store.outstanding()==1,"missing primary does not hide retained "+kind);
            yes(!store.begin(List.of(passage(31)))&&!store.settle(OWNER,30),"retained transaction blocks all operations");
            yes(Arrays.equals(valid,Files.readAllBytes(retained)),"retained evidence stays byte-for-byte intact");
        }
        Path dir=Files.createDirectory(root.resolve("retained-partial")),path=dir.resolve("j.bin");
        Path partial=dir.resolve("j.bin.pending-partial.bin");Files.write(partial,new byte[]{1,2,3});
        yes(new FileStore(path).blockedOnOpen()&&Files.size(partial)==3,"partial retained evidence blocks even without decoded records");
        Path salvage=Files.createDirectory(root.resolve("salvage")),primary=salvage.resolve("j.bin");
        Files.write(primary,new byte[]{1});Files.write(salvage.resolve("j.bin.pending-good.bin"),valid);
        FileStore rescued=new FileStore(primary);
        yes(rescued.blockedOnOpen()&&rescued.outstanding()==1,"valid retained intent survives corrupt primary without authorizing recovery");
        Path oversized=Files.createDirectory(root.resolve("salvage-oversized")),oversizedPath=oversized.resolve("j.bin");
        Files.write(oversizedPath,new byte[256*1024+1]);Files.write(oversized.resolve("j.bin.pending-good.bin"),valid);
        FileStore bounded=new FileStore(oversizedPath);
        yes(bounded.blockedOnOpen()&&bounded.outstanding()==1,"bounded primary rejection still recovers retained ownership metadata");
        Path many=Files.createDirectory(root.resolve("too-many")),manyPath=many.resolve("j.bin");
        for(int i=0;i<17;i++)Files.write(many.resolve("j.bin.pending-"+i+".bin"),valid);
        yes(new FileStore(manyPath).blockedOnOpen(),"too many evidence files fail closed without deleting any");
        yes(evidence(manyPath).size()==17,"bounded scan never discards excess evidence");
        Path legacy=Files.createDirectory(root.resolve("legacy")),legacyPath=legacy.resolve("j.bin");
        Path oldTemp=legacy.resolve(".gaslight-intent-legacy.tmp");Files.write(oldTemp,valid);
        yes(new FileStore(legacyPath).blockedOnOpen()&&Arrays.equals(valid,Files.readAllBytes(oldTemp)),
            "legacy orphaned temporary intent is quarantined and preserved");
        Path collision=Files.createDirectory(root.resolve("conflict")),collisionPath=collision.resolve("j.bin");
        byte[] disagree=valid.clone();ByteBuffer.wrap(disagree).putDouble(40,7);crc(disagree);
        Files.write(collisionPath,valid);Path other=collision.resolve("j.bin.pending-disagree.bin");Files.write(other,disagree);
        FileStore conflict=new FileStore(collisionPath);
        yes(conflict.blockedOnOpen()&&conflict.diagnostic().contains("conflicting journal records"),
            "same generation with conflicting geometry cannot be silently selected");
        yes(Arrays.equals(disagree,Files.readAllBytes(other)),"conflicting evidence is retained for reconciliation");
        Path v1Dir=Files.createDirectory(root.resolve("v1-record")),v1Path=v1Dir.resolve("j.bin");
        byte[] v1=new byte[valid.length-8];System.arraycopy(valid,0,v1,0,valid.length-16);
        ByteBuffer.wrap(v1).putInt(4,1);crc(v1);Files.write(v1Path,v1);
        FileStore old=new FileStore(v1Path);
        yes(old.blockedOnOpen()&&old.outstanding()==1,"V1 unresolved journals remain readable and blocked");
        yes(Arrays.equals(v1,Files.readAllBytes(v1Path)),"legacy journal is not silently migrated or erased");
    }
    private static void revisionFence(Path root)throws Exception{
        Path dir=Files.createDirectory(root.resolve("revision")),path=dir.resolve("j.bin");
        boolean durable=JournalTestSupport.directorySyncAvailable(dir);
        if(durable){
            FileStore initial=new FileStore(path);
            yes(initial.begin(List.of(passage(80)))&&initial.settle(OWNER,80),"seed a certified empty checkpoint");
            FileStore stale=new FileStore(path),fresh=new FileStore(path);
            byte[] before=Files.readAllBytes(path);
            yes(fresh.begin(List.of(passage(81)))&&fresh.settle(OWNER,81),"another writer completes a publish/retire cycle");
            byte[] after=Files.readAllBytes(path);
            yes(!Arrays.equals(before,after),"empty checkpoints retain distinct monotonic revisions");
            yes(!stale.begin(List.of(passage(82)))&&stale.diagnostic().contains("stale journal writer"),"ABA-stale writer is rejected");
            yes(Arrays.equals(after,Files.readAllBytes(path))&&evidence(path).isEmpty(),"stale rejection leaves certified state intact");
        }else{
            // Byte-image CAS fixture, not an assertion of native Windows durability.
            byte[] empty=new byte[28];ByteBuffer frame=ByteBuffer.wrap(empty);
            frame.putInt(0x474C334A).putInt(2).putInt(0).putLong(2);crc(empty);Files.write(path,empty);
            FileStore stale=new FileStore(path);
            ByteBuffer.wrap(empty).putLong(12,4);crc(empty);Files.write(path,empty);
            yes(!stale.begin(List.of(passage(82)))&&stale.diagnostic().contains("stale journal writer"),"revision byte-image CAS rejects ABA before unsupported IO");
            yes(Arrays.equals(empty,Files.readAllBytes(path))&&evidence(path).isEmpty(),"ABA refusal never alters a checkpoint");
            System.out.println("REVISION_FENCE,WINDOWS_BYTE_IMAGE_CAS_ONLY,native_durable_cycle_BLOCKED");
        }
        byte[] maximum=new byte[28];ByteBuffer frame=ByteBuffer.wrap(maximum);
        frame.putInt(0x474C334A).putInt(2).putInt(0).putLong(Long.MAX_VALUE);crc(maximum);
        Path exhausted=dir.resolve("exhausted.bin");Files.write(exhausted,maximum);
        FileStore limited=new FileStore(exhausted);
        yes(!limited.begin(List.of(passage(83)))&&limited.blockedOnOpen(),"revision exhaustion never wraps or publishes");
        yes(Arrays.equals(maximum,Files.readAllBytes(exhausted)),"revision exhaustion preserves original checkpoint");
    }
    private static void engineQuarantine(Path root)throws Exception{
        Path dir=Files.createDirectory(root.resolve("engine")),path=dir.resolve("j.bin");
        new FileStore(path).begin(List.of(passage(50)));
        FileStore restarted=new FileStore(path);
        var host=new Phase4ValidationTests.DisabledHost(){
            @Override public Optional<Store> recoveryJournal(){return Optional.of(restarted);}
            @Override public boolean authoritativeCamera(NativeWorldEngine.Actor actor){return true;}
        };
        Phase3Engine engine=new Phase3Engine(host,new Phase3Engine.Settings(true,true,true,1,12,96,8),1);
        for(int i=0;i<200;i++)engine.tick();
        yes(engine.status().recentStatus().startsWith("QUARANTINED"),"real restarted journal quarantines controller before planning");
        yes(host.stages==0&&host.teleports==0&&engine.status().published()==0,"no stage, crossing or publication after recovery failure");
        yes(restarted.outstanding()==1,"controller does not silently erase unresolved intent");
    }
    private static void volatileStorage()throws Exception{
        Path temp=Path.of("/tmp");
        if(!System.getProperty("os.name").startsWith("Windows")&&Files.isDirectory(temp)&&
           Files.getFileStore(temp).type().equals("tmpfs")){
            Path dir=Files.createTempDirectory(temp,"gaslight-volatile-test-");
            try{
                FileStore store=new FileStore(dir.resolve("j.bin"));
                yes(!store.begin(List.of(passage(70)))&&store.blockedOnOpen(),"RAM-backed storage never certifies a publication");
                yes(store.diagnostic().contains("uncertified storage type tmpfs"),"volatile storage refusal is explicit");
                yes(new FileStore(dir.resolve("j.bin")).blockedOnOpen(),"volatile refusal still preserves recoverable evidence");
                System.out.println("VOLATILE_STORAGE_VETO,tmpfs,PASS");
            }finally{try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        }
    }
    private static void childMain(String[] args)throws Exception{
        Path path=Path.of(args[1]);
        if(args[0].equals("crash")){
            WritePoint target=WritePoint.valueOf(args[2]);
            FileStore store=new FileStore(path,p->{if(p==target){System.out.println("HALT_AT,"+p);System.out.flush();Runtime.getRuntime().halt(97);}});
            boolean accepted=store.begin(List.of(passage(20)));
            System.out.println("NATIVE_RESULT,"+accepted+","+store.diagnostic());System.exit(accepted?0:23);
        }else if(args[0].equals("after-ack")){
            FileStore store=new FileStore(path);boolean accepted=store.begin(List.of(passage(20)));
            System.out.println("ACKNOWLEDGED_BEGIN,"+accepted+","+store.diagnostic());System.out.flush();
            if(accepted)Runtime.getRuntime().halt(98);else System.exit(23);
        }else if(args[0].equals("settle-crash")){
            WritePoint target=WritePoint.valueOf(args[2]);boolean[] settling={false};
            FileStore store=new FileStore(path,p->{if(settling[0]&&p==target){System.out.println("RETIREMENT_HALT_AT,"+p);System.out.flush();Runtime.getRuntime().halt(97);}});
            if(!store.begin(List.of(passage(20))))throw new IOException("retirement setup cannot certify intent");
            // This fixture has no physical objects: its original owner certifies retirement.
            settling[0]=true;store.settle(OWNER,20);throw new AssertionError("retirement fault point missed");
        }else if(args[0].equals("race")){
            long generation=Long.parseLong(args[2]);FileStore store=new FileStore(path);
            Files.writeString(path.getParent().resolve("ready-"+generation),"ready");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(!Files.exists(path.getParent().resolve("go"))&&System.nanoTime()<deadline)Thread.sleep(10);
            if(!Files.exists(path.getParent().resolve("go")))throw new IOException("race barrier timeout");
            boolean accepted=store.begin(List.of(passage(generation)));
            System.out.println("RACE_RESULT,"+accepted+","+store.diagnostic());System.exit(accepted?0:23);
        }else throw new IllegalArgumentException("unknown child mode");
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0){childMain(args);return;}
        assertions=0;crashes=0;platformBlockedPoints=0;
        Path root=Files.createTempDirectory("gaslight-phase2-recovery-");
        try{
            nativeBehavior(root);failures(root);processInterruptions(root);simultaneousProcesses(root);
            malformedAndRetained(root);revisionFence(root);engineQuarantine(root);volatileStorage();
            System.out.println("RECOVERY_JOURNAL_PASS: assertions="+assertions+" real_process_halts="+crashes+
                " native_unreachable_points="+platformBlockedPoints+" OS="+System.getProperty("os.name"));
        }finally{
            // Only discard disposable test fixtures; production recovery never does this.
            try(var files=Files.walk(root)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}
        }
    }
}
