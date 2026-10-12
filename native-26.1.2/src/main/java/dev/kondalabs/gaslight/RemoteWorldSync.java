package dev.kondalabs.gaslight;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import static dev.kondalabs.gaslight.RemoteWorldWire.*;

/** One permanent data link, with predictive destination leases; no physical activation. */
public final class RemoteWorldSync {
    private static final Map<MinecraftServer,RemoteWorldSync> SERVERS=new IdentityHashMap<>();
    private static final AtomicLong EPOCH=new AtomicLong();
    private static final UUID PORTAL=UUID.nameUUIDFromBytes("gaslight:permanent-remote-data-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final MinecraftServer server;private final ResourceKey<Level> source,destination;
    private final NativePortal.Surface entrance,exit;private final RemoteWorldBudget budget=new RemoteWorldBudget(System::nanoTime);
    private final RemoteWorldBudget replyBudget=new RemoteWorldBudget(System::nanoTime,4096,2048);
    private final boolean followCertified,allowGeneration;private NativePortal.Passage certified,candidate;
    private final RemoteChunkAcquisition acquisition;private final RemoteChunkLeases leases;
    private RemoteChunkLeases.Lease preparation;private UUID preparingOwner;private long nextPreparationTick;
    private Session session;private long tick,bytesSent,captures,captureNanos,peakCaptureNanos,retries,refusals,acks;
    private int cursor;private final ArrayDeque<Packet> controls=new ArrayDeque<>();
    private static final class Slot {long revision;byte[] hash;boolean available;String refusal;}
    private static final class Pending {final int x,z;final long revision;final List<Packet> packets;int next,attempts;long sentTick;
        Pending(int x,int z,long rev,List<Packet> p){this.x=x;this.z=z;revision=rev;packets=p;}}
    private static final class Session {final Scope scope;final Slot[] slots=new Slot[MAX_CHUNKS];boolean opened;long lastAck;Pending pending;
        Session(Scope scope,long tick){this.scope=scope;lastAck=tick;Arrays.setAll(slots,i->new Slot());}}
    private RemoteWorldSync(MinecraftServer server,Properties p){
        this.server=server;source=key(p.getProperty("sourceDimension","minecraft:overworld"));destination=key(p.getProperty("destinationDimension","minecraft:overworld"));
        followCertified=Boolean.parseBoolean(p.getProperty("followCertifiedStaticPortal","false"));
        allowGeneration=Boolean.parseBoolean(p.getProperty("allowGeneration","false"));
        var defaults=RemoteChunkLeases.Limits.defaults();
        var limits=new RemoteChunkLeases.Limits(integer(p,"maxActiveChunks",defaults.active()),integer(p,"maxRequests",defaults.requests()),integer(p,"maxPendingLoads",defaults.pending()),
            integer(p,"maxDataBytes",(int)defaults.dataBytes()),integer(p,"maxNativeChunks",defaults.nativeChunks()),integer(p,"maxGenerationChunks",defaults.generationChunks()),
            integer(p,"maxGenerationSteps",defaults.generationSteps()),integer(p,"preparationTimeoutSeconds",30)*1_000_000_000L,defaults.freshnessNanos());
        acquisition=new RemoteChunkAcquisition(server,limits,allowGeneration,integer(p,"maxNativeMemoryBytes",128*1_048_576));
        leases=new RemoteChunkLeases(acquisition,limits,System::nanoTime);
        double yaw=Math.toRadians(number(p,"yaw",0));var tangent=new NativePortal.V(Math.cos(yaw),0,Math.sin(yaw));
        entrance=new NativePortal.Surface(new NativePortal.V(number(p,"sourceX",0.5),number(p,"sourceY",68),number(p,"sourceZ",0.5)),tangent,8,10);
        exit=new NativePortal.Surface(new NativePortal.V(number(p,"destinationX",1024.5),number(p,"destinationY",68),number(p,"destinationZ",1024.5)),tangent,8,10);
    }
    private static ResourceKey<Level> key(String text){identifier(text);return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,Identifier.parse(text));}
    private static double number(Properties p,String key,double fallback){double v=Double.parseDouble(p.getProperty(key,Double.toString(fallback)));if(!Double.isFinite(v))throw new IllegalArgumentException("nonfinite portal coordinate");return v;}
    private static int integer(Properties p,String key,int fallback){return Integer.parseInt(p.getProperty(key,Integer.toString(fallback)));}
    public static void register(){
        RemoteWorldPayload.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            try{
                Path file=Path.of("config/gaslight-remote-world.properties");
                if(Files.notExists(file)){Files.createDirectories(file.getParent());Files.writeString(file,"# One permanent data link; generation requires both explicit permissions.\nenabled=false\nallowGeneration=false\nmaxActiveChunks=18\nmaxRequests=4\nmaxPendingLoads=2\nmaxNativeChunks=625\nmaxGenerationChunks=625\nmaxGenerationSteps=8192\nmaxDataBytes=8388608\nmaxNativeMemoryBytes=134217728\npreparationTimeoutSeconds=30\nsourceDimension=minecraft:overworld\nsourceX=0.5\nsourceY=68\nsourceZ=0.5\ndestinationDimension=minecraft:overworld\ndestinationX=1024.5\ndestinationY=68\ndestinationZ=1024.5\nyaw=0\n");return;}
                var p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}
                if(!Boolean.parseBoolean(p.getProperty("enabled","false")))return;
                if(!ProtectedChunkRegions.load(Path.of("config/gaslight-protected-chunks.csv")).valid())throw new IOException("invalid protection policy");
                var sync=new RemoteWorldSync(server,p);ServerLevel dst=server.getLevel(sync.destination);
                if(server.getLevel(sync.source)==null||dst==null)throw new IOException("unknown dimension");
                // Validate configured geometry and height before any session/network access.
                sync.scope(UUID.randomUUID(),UUID.randomUUID(),1,dst);
                SERVERS.put(server,sync);System.out.println("[GASLIGHT remote] predictive acquisition enabled; data chunks=9 viewers=1 generation="+sync.allowGeneration+" bandwidth=262144B/s");
            }catch(IOException|RuntimeException e){System.err.println("[GASLIGHT remote] configuration refused: "+e);}
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{var sync=SERVERS.get(server);if(sync!=null)sync.tick();});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{var sync=SERVERS.remove(server);if(sync!=null){sync.close("server stopping");sync.leases.close();}});
        ServerPlayNetworking.registerGlobalReceiver(RemoteWorldPayload.Reply.TYPE,(payload,context)->{
            var sync=SERVERS.get(context.server());if(sync!=null)sync.reply(context.player(),payload.bytes());
        });
    }
    private Scope scope(UUID id,UUID owner,long epoch,ServerLevel dst){
        var passage=candidate==null?new NativePortal.Passage(entrance,exit,owner,1):candidate;
        String type=dst.dimensionTypeRegistration().unwrapKey().orElseThrow().identifier().toString();
        return new Scope(id,PORTAL,owner,epoch,source.identifier().toString(),destination.identifier().toString(),type,
            (int)Math.floor(passage.exit().centre().x()/16),(int)Math.floor(passage.exit().centre().z()/16),dst.getMinY(),dst.getSectionsCount(),passage);
    }
    public static void offer(MinecraftServer server,NativePortal.Passage passage){
        var sync=SERVERS.get(server);if(sync==null||!sync.followCertified)return;
        if(!destinationReady(server,passage))return;sync.certified=passage;
    }
    public static void hint(MinecraftServer server,NativePortal.Passage passage){
        var sync=SERVERS.get(server);if(sync==null||!sync.followCertified)return;
        if(sync.candidate!=null&&!sync.candidate.equals(passage))return;sync.candidate=passage;
    }
    public static boolean destinationReady(MinecraftServer server,NativePortal.Passage passage){
        var sync=SERVERS.get(server);return sync!=null&&passage.equals(sync.candidate)&&sync.preparation!=null&&sync.preparation.ready()&&passage.owner().equals(sync.preparingOwner);
    }
    /** Server-only API for additional preparation consumers; never exposed in C2S packets. */
    public static Optional<RemoteChunkLeases> acquisition(MinecraftServer server){var sync=SERVERS.get(server);return sync==null?Optional.empty():Optional.of(sync.leases);}
    public static String acquisitionMetrics(MinecraftServer server){var sync=SERVERS.get(server);return sync==null?"closed":sync.metrics();}
    public static void retire(MinecraftServer server,NativePortal.Passage passage){
        var sync=SERVERS.get(server);if(sync!=null&&sync.followCertified&&passage.equals(sync.candidate)){sync.close("portal retired");sync.certified=null;sync.candidate=null;}
    }
    private boolean eligible(ServerPlayer p){var center=certified==null?entrance.centre():certified.entrance().centre();return
        (!followCertified||certified!=null&&certified.owner().equals(p.getUUID()))&&p.level().dimension().equals(source)&&p.position().distanceToSqr(center.x(),center.y(),center.z())<=4096&&
        ServerPlayNetworking.canSend(p,RemoteWorldPayload.TYPE)&&ServerPlayNetworking.canSend(p,PortalSnapshotPayload.TYPE);}
    private ServerPlayer owner(){return session==null?null:server.getPlayerList().getPlayer(session.scope.owner());}
    private boolean predictiveEligible(ServerPlayer p){var center=candidate==null?entrance.centre():candidate.entrance().centre();return
        (!followCertified||candidate!=null&&candidate.owner().equals(p.getUUID()))&&p.level().dimension().equals(source)&&p.position().distanceToSqr(center.x(),center.y(),center.z())<=256*256&&
        ServerPlayNetworking.canSend(p,RemoteWorldPayload.TYPE)&&ServerPlayNetworking.canSend(p,PortalSnapshotPayload.TYPE);}
    private void preparePredictively(){
        ServerPlayer player=preparingOwner==null?null:server.getPlayerList().getPlayer(preparingOwner);
        if(preparation!=null&&(player==null||!predictiveEligible(player))){close("preparation cancelled: disconnect/dimension/range");return;}
        if(preparation!=null&&preparation.state()==RemoteChunkLeases.State.FAILED){System.out.println("[GASLIGHT acquisition] PREPARATION_FAILED "+preparation.reason());close("acquisition failure");nextPreparationTick=tick+100;return;}
        if(preparation==null&&tick>=nextPreparationTick&&tick%20==0)for(var p:server.getPlayerList().getPlayers())if(predictiveEligible(p)){
            var proposed=scope(UUID.randomUUID(),p.getUUID(),EPOCH.incrementAndGet(),server.getLevel(destination));preparingOwner=p.getUUID();
            preparation=leases.prepare(proposed.session(),proposed.epoch(),proposed.destinationDimension(),proposed.centerX(),proposed.centerZ(),allowGeneration);
            System.out.println("[GASLIGHT acquisition] PREPARING epoch="+proposed.epoch()+" destination="+proposed.centerX()+","+proposed.centerZ());break;
        }
    }
    private void tick(){
        tick++;
        try{
            leases.tick();preparePredictively();
            if(session!=null&&(preparation==null||!preparation.ready())){close("destination lease no longer ready");return;}
            if(session!=null&&(owner()==null||!eligible(owner()))){close("disconnect/dimension/range");return;}
            if(session==null){
                if(tick%20!=0||preparation==null||!preparation.ready())return;
                for(var p:server.getPlayerList().getPlayers())if(eligible(p)){
                    ServerLevel dst=server.getLevel(destination);if(dst==null)return;
                    if(!p.getUUID().equals(preparingOwner))continue;
                    session=new Session(scope(preparation.id(),p.getUUID(),preparation.generation(),dst),tick);cursor=0;
                    queue(control(Kind.OPEN,session.scope,session.scope.centerX(),session.scope.centerZ(),0));
                    System.out.println("[GASLIGHT remote] OPEN epoch="+session.scope.epoch()+" destination="+session.scope.destinationDimension());break;
                }
            }
            if(session==null)return;
            if(!session.opened){
                if(tick-session.lastAck>100){close("open acknowledgement timeout");return;}
                if(tick%20==0&&controls.isEmpty())queue(control(Kind.OPEN,session.scope,session.scope.centerX(),session.scope.centerZ(),0));
                drain();return;
            }
            if(tick-session.lastAck>200){close("receipt timeout");return;}
            if(tick%20==0)for(int i=0;i<MAX_CHUNKS;i++){
                Slot slot=session.slots[i];if(slot.available)queue(control(Kind.HEARTBEAT,session.scope,x(i),z(i),slot.revision));
            }
            Pending pending=session.pending;
            if(pending!=null&&pending.next==pending.packets.size()&&tick-pending.sentTick>=40){
                if(++pending.attempts>3){close("fragment acknowledgement timeout");return;}
                retries++;pending.next=0;
            }
            if(session.pending==null&&tick%3==0&&budget.canCapture())captureOne();
            drain();
            if(tick%200==0)System.out.println("[GASLIGHT remote] METRICS "+metrics());
        }catch(IOException|RuntimeException e){refusals++;close("fail closed: "+e.getMessage());}
    }
    private int x(int i){return session.scope.centerX()+i%3-1;}private int z(int i){return session.scope.centerZ()+i/3-1;}
    private void captureOne()throws IOException{
        int i=cursor++%MAX_CHUNKS,cx=x(i),cz=z(i);Slot slot=session.slots[i];long started=System.nanoTime();
        try{
            ServerLevel dst=server.getLevel(destination);if(dst==null)throw new IOException("dimension unavailable");
            RemoteChunkData data=leases.data(preparation,cx,cz,started+RemoteWorldBudget.CAPTURE_NANOS);
            byte[] raw=data.raw();byte[] digest=hash(raw);RemoteChunkCapture.budget(started+RemoteWorldBudget.CAPTURE_NANOS);
            if(slot.available&&Arrays.equals(digest,slot.hash))return;
            byte[] compressed=RemoteChunkData.compress(raw);RemoteChunkCapture.budget(started+RemoteWorldBudget.CAPTURE_NANOS);
            slot.revision++;slot.hash=digest;slot.available=true;
            session.pending=new Pending(cx,cz,slot.revision,fragments(session.scope,cx,cz,slot.revision,compressed));
            queue(control(Kind.INVALIDATE,session.scope,cx,cz,slot.revision));
            slot.refusal=null;
        }catch(IOException|RuntimeException e){
            String reason=e.getClass().getSimpleName()+": "+e.getMessage();
            if(!reason.equals(slot.refusal)){slot.refusal=reason;System.out.println("[GASLIGHT remote] UNAVAILABLE chunk="+cx+","+cz+" reason="+reason);}
            refusals++;if(slot.available||slot.revision==0){slot.revision++;slot.available=false;slot.hash=null;queue(control(Kind.UNAVAILABLE,session.scope,cx,cz,slot.revision));}
        }finally{long cost=System.nanoTime()-started;captures++;captureNanos+=cost;peakCaptureNanos=Math.max(peakCaptureNanos,cost);budget.captureCost(cost);}
    }
    private void queue(Packet p)throws IOException{if(controls.size()>=64)throw new IOException("control queue budget");controls.add(p);}
    private void drain()throws IOException{
        Packet packet=controls.peek();Pending p=session.pending;
        if(packet==null&&p!=null&&p.next<p.packets.size())packet=p.packets.get(p.next);
        if(packet==null)return;byte[] bytes=encode(packet);if(!budget.send(bytes.length+64))return;
        ServerPlayer player=owner();if(player==null)return;ServerPlayNetworking.send(player,new RemoteWorldPayload(bytes));bytesSent+=bytes.length+64;
        if(!controls.isEmpty())controls.remove();else {p.next++;if(p.next==p.packets.size())p.sentTick=tick;}
    }
    private void reply(ServerPlayer player,byte[] wire){
        if(session==null||!session.scope.owner().equals(player.getUUID())||!eligible(player))return;
        // ACK storms cannot induce unbounded processing, captures, queues or world loads.
        if(!replyBudget.send(wire.length+64))return;
        try{
            Packet p=decode(wire);if(!p.session().equals(session.scope.session())||p.epoch()!=session.scope.epoch())return;
            if(p.kind()==Kind.RESYNC){close("client requested fresh session");return;}
            if(p.kind()!=Kind.ACK||!session.scope.contains(p.x(),p.z()))return;
            if(p.revision()==0){session.opened=true;session.lastAck=tick;acks++;return;}
            int i=(p.z()-session.scope.centerZ()+1)*3+p.x()-session.scope.centerX()+1;
            if(session.slots[i].available&&session.slots[i].revision==p.revision()){session.lastAck=tick;acks++;}
            Pending pending=session.pending;
            if(pending!=null&&pending.x==p.x()&&pending.z==p.z()&&pending.revision==p.revision())session.pending=null;
        }catch(IOException|RuntimeException e){refusals++;}
    }
    private void close(String reason){
        if(session!=null){ServerPlayer p=owner();try{if(p!=null&&ServerPlayNetworking.canSend(p,RemoteWorldPayload.TYPE)){
            byte[] data=encode(control(Kind.CLOSE,session.scope,session.scope.centerX(),session.scope.centerZ(),0));if(budget.send(data.length+64)){ServerPlayNetworking.send(p,new RemoteWorldPayload(data));bytesSent+=data.length+64;}}}catch(IOException|RuntimeException ignored){}
            System.out.println("[GASLIGHT remote] CLOSE reason="+reason+" "+metrics());}
        controls.clear();session=null;
        if(preparation!=null)leases.release(preparation);preparation=null;preparingOwner=null;
    }
    public String metrics(){return "bytes="+bytesSent+" captures="+captures+" capture_ns="+captureNanos+" peak_capture_ns="+peakCaptureNanos+" retries="+retries+" refusals="+refusals+" acks="+acks+" server_retained_compressed_limit=1048576 "+leases.metrics()+" "+acquisition.metrics();}
}
