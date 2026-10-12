package dev.kondalabs.gaslight.probe;
import dev.kondalabs.gaslight.provider.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTickets;

/** Genuine disposable-world driver; never packaged into the production adapter. */
public final class ServerProbe implements ModInitializer {
    private static String last="";private static FixedPortalAdapter.Token previous;
    private static void check(boolean c,String s){if(!c)throw new AssertionError(s);}
    private static void output(String s){System.out.println("GASLIGHT_A_SERVER "+s);}
    @Override public void onInitialize(){
        if(!Boolean.getBoolean("gaslight.phaseA.probe"))throw new IllegalStateException("probe must be explicitly enabled");
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            var w=server.overworld();var a=GaslightProvider.service(server);
            check(a!=null,"GASLIGHT initialized");
            if(System.getProperty("gaslight.probe.mode","live").equals("recovery")){
                check(a.status().state().equals("QUARANTINED")&&a.status().intents()==1,"restart retains outstanding intent");
                check(a.status().portals()==0&&a.status().ownedLoaders()==0,"restart cannot republish or own leases");
                output("RECOVERY_BLOCKED_AS_REQUIRED "+a.status());return;
            }
            w.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            w.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);w.setDayTime(6000);
            // Exactly two 5x5 pre-existing FULL fixture windows; generation is test setup, not adapter acquisition.
            for(int c:new int[]{0,64})for(int x=c-2;x<=c+2;x++)for(int z=c-2;z<=c+2;z++)w.getChunk(x,z,ChunkStatus.FULL,true);
            for(int c:new int[]{0,1024})for(int x=c-12;x<=c+12;x++)for(int z=c-12;z<=c+12;z++){
                w.setBlockAndUpdate(new BlockPos(x,80,z),c==0?Blocks.STONE.defaultBlockState():Blocks.GLOWSTONE.defaultBlockState());
                for(int y=81;y<86;y++)w.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            for(int x=1023;x<=1025;x++)for(int y=81;y<=84;y++)w.setBlockAndUpdate(new BlockPos(x,y,1021),Blocks.DIAMOND_BLOCK.defaultBlockState());
            var valid=new FixedPortalAdapter.Spec(w,new Vec3(.5,82.5,.5),w,new Vec3(1024.5,82.5,1024.5),4,4);
            var missing=new FixedPortalAdapter.Spec(w,valid.origin(),w,new Vec3(4000000.5,82.5,4000000.5),4,4);
            check(!a.prepare(missing),"unknown target rejected without generation");
            check(!a.prepare(new FixedPortalAdapter.Spec(w,new Vec3(Double.NaN,82,.5),w,valid.destination(),4,4)),"NaN rejected");
            Path policy=server.getWorldPath(LevelResource.ROOT).resolve("gaslight-protected-chunks.csv");
            try{
                Files.writeString(policy,"64,64,64,64\n");check(!a.prepare(valid),"destination protected column rejected");
                Files.writeString(policy,"broken-policy\n");check(!a.prepare(valid),"corrupt policy fail closed");
                Files.writeString(policy,"# explicitly empty disposable fixture policy\n");
            }catch(Exception e){throw new RuntimeException(e);}
            var obstruction=net.minecraft.world.entity.EntityType.ARMOR_STAND.create(w);
            check(obstruction!=null,"fixture obstruction");obstruction.setPos(valid.destination());w.addFreshEntity(obstruction);
            check(!a.prepare(valid),"same-dimension destination occupancy veto");obstruction.discard();
            if(System.getProperty("gaslight.probe.mode","live").equals("prepare-only")){output("FIXTURE_PREPARED_NO_PUBLICATION "+a.status());server.saveEverything(false,true,true);server.halt(false);return;}
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gaslight-fixed create 0.5 82.5 0.5 1024.5 82.5 1024.5");
            check(a.status().state().equals("ACTIVE"),"genuine production console command publication: "+a.status());
            previous=a.token();check(a.status().portals()==4&&a.status().ownedChunks()==50,"bounded four-entity pair, fifty owned chunks");
            check(!a.retire(new FixedPortalAdapter.Token(previous.owner(),previous.generation())),"identity-fenced stale retirement");
            output("READY "+a.status());
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            var player=handler.player;player.setGameMode(GameType.CREATIVE);
            player.teleportTo(server.overworld(),.5,81,3.5,180,0);
            player.getInventory().setItem(0,new ItemStack(Items.GOLD_BLOCK,64));player.containerMenu.broadcastChanges();
            output("JOIN "+player.getGameProfile().getName());
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            try{
                Path request=Path.of("probe-server.txt");if(!Files.exists(request))return;
                String command=Files.readString(request).trim();if(command.isEmpty()||command.equals(last))return;last=command;
                var a=GaslightProvider.service(server);var w=server.overworld();
                if(command.startsWith("snapshot")){
                    int full=0;for(int x=62;x<=66;x++)for(int z=62;z<=66;z++)if(w.getChunkSource().getChunkNow(x,z)!=null)full++;
                    var remote=w.getChunkSource().getChunkNow(64,63);var placed=w.getChunkSource().getChunkNow(64,63);
                    output(command+" "+a.status()+" remoteFull="+full+" providerTickets="+ImmPtlChunkTickets.get(w).getLoadedChunkNum()+" averageTickMs="+(server.getAverageTickTimeNanos()/1000000.0)+" heapUsed="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+" remote="+(remote==null?"UNAVAILABLE":remote.getBlockState(new BlockPos(1024,82,1021)))+" placed="+(placed==null?"UNAVAILABLE":placed.getBlockState(new BlockPos(1024,82,1022)))+" players="+server.getPlayerList().getPlayers().stream().map(p->p.position().toString()).toList());
                }
                else if(command.startsWith("pose")){String[] v=command.split(" ");for(var player:server.getPlayerList().getPlayers())player.teleportTo(w,Double.parseDouble(v[1]),Double.parseDouble(v[2]),Double.parseDouble(v[3]),Float.parseFloat(v[4]),Float.parseFloat(v[5]));output(command+" RESET_FIXTURE_POSE");}
                else if(command.startsWith("dimension")){for(var player:server.getPlayerList().getPlayers())player.teleportTo(server.getLevel(net.minecraft.world.level.Level.NETHER),0,90,0,0,0);output(command+" REAL_DIMENSION_CHANGE");}
                else if(command.startsWith("update")){w.setBlockAndUpdate(new BlockPos(1024,82,1021),Blocks.EMERALD_BLOCK.defaultBlockState());output(command+" UPDATED");}
                else if(command.startsWith("retire"))output(command+" result="+a.retire(previous)+" "+a.status());
                else if(command.startsWith("reprepare")){
                    var next=new FixedPortalAdapter.Spec(w,new Vec3(.5,82.5,.5),w,new Vec3(1024.5,82.5,1024.5),4,4);
                    check(a.prepare(next),"repeat acquisition");check(!a.retire(previous),"old generation cannot retire successor");previous=a.token();output(command+" "+a.status());
                }
                else if(command.startsWith("stop")){output("STOP "+a.status());server.halt(false);}
                else throw new IllegalArgumentException(command);
            }catch(Throwable e){e.printStackTrace();output("FAIL "+e);server.halt(false);}
        });
    }
}
