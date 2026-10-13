package dev.kondalabs.gaslight.provider.mixin;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Connection.class)
public interface SceneChannelAccessor {
    @Accessor("channel") Channel gaslight$channel();
}
