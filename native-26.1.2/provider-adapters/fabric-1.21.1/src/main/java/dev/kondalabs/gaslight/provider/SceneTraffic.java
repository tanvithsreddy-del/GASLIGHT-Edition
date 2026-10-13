package dev.kondalabs.gaslight.provider;
import java.util.concurrent.atomic.AtomicLong;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import net.minecraft.network.Connection;
import dev.kondalabs.gaslight.provider.mixin.*;
import net.minecraft.server.level.ServerPlayer;

/** Measures the real compressed play-channel payload, including ordinary traffic
 * during the session. Exceeding the link budget closes the whole connection;
 * it cannot leave a playable connection with selectively missing chunk data. */
final class SceneTraffic extends ChannelDuplexHandler implements AutoCloseable {
    private static final String NAME="gaslight_scene_budget";
    private final Channel channel;
    private final AtomicLong sent=new AtomicLong(),received=new AtomicLong();
    private final ByteBudget outbound=new ByteBudget(16L*1024*1024,8L*1024*1024,System.nanoTime());
    private final ByteBudget inbound=new ByteBudget(2L*1024*1024,1024*1024,System.nanoTime());
    private volatile boolean enabled=true;
    private SceneTraffic(Channel channel){this.channel=channel;}
    static SceneTraffic attach(ServerPlayer player){
        try{
            Connection connection=((SceneConnectionAccessor)player.connection).gaslight$connection();
            Channel channel=((SceneChannelAccessor)connection).gaslight$channel();
            if(channel==null)throw new IllegalStateException("play channel unavailable");
            var meter=new SceneTraffic(channel);
            channel.eventLoop().execute(()->{if(channel.isActive()){if(channel.pipeline().get(NAME)!=null)channel.pipeline().remove(NAME);channel.pipeline().addFirst(NAME,meter);}});
            return meter;
        }catch(ClassCastException error){throw new IllegalStateException("version-pinned channel binding unavailable",error);}
    }
    String status(){return "compressedSent="+sent.get()+",compressedReceived="+received.get();}
    @Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise)throws Exception{
        if(enabled&&message instanceof ByteBuf buffer){int size=buffer.readableBytes();sent.addAndGet(size);if(!outbound.spend(size,System.nanoTime())){io.netty.util.ReferenceCountUtil.release(message);promise.tryFailure(new IllegalStateException("GASLIGHT outbound byte budget"));GaslightProvider.LOG.error("GASLIGHT_BYTE_BUDGET_CLOSED {}",status());ctx.close();return;}}
        super.write(ctx,message,promise);
    }
    @Override public void channelRead(ChannelHandlerContext ctx,Object message)throws Exception{
        if(enabled&&message instanceof ByteBuf buffer){int size=buffer.readableBytes();received.addAndGet(size);if(!inbound.spend(size,System.nanoTime())){io.netty.util.ReferenceCountUtil.release(message);GaslightProvider.LOG.error("GASLIGHT_BYTE_BUDGET_CLOSED {}",status());ctx.close();return;}}
        super.channelRead(ctx,message);
    }
    @Override public void close(){enabled=false;GaslightProvider.LOG.info("GASLIGHT_TRAFFIC_END {}",status());channel.eventLoop().execute(()->{if(channel.pipeline().get(NAME)==this)channel.pipeline().remove(NAME);});}
}
