package dev.kondalabs.gaslight.provider;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Explicit fixed-link operations, still subject to the adapter's observer and world vetoes. */
final class FixedPortalCommands {
    static void register(){
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(
            Commands.literal("gaslight-fixed").requires(s->s.hasPermission(4))
                .then(Commands.literal("status").executes(c->{var a=GaslightProvider.service(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal(a.status().toString()),false);return 1;}))
                .then(Commands.literal("retire").executes(c->{var a=GaslightProvider.service(c.getSource().getServer());boolean ok=a.retire(a.token());c.getSource().sendSuccess(()->Component.literal(a.status().toString()),false);return ok?1:0;}))
                .then(Commands.literal("create")
                    .then(Commands.argument("x",DoubleArgumentType.doubleArg(-29000000,29000000))
                    .then(Commands.argument("y",DoubleArgumentType.doubleArg(-2048,2048))
                    .then(Commands.argument("z",DoubleArgumentType.doubleArg(-29000000,29000000))
                    .then(Commands.argument("tx",DoubleArgumentType.doubleArg(-29000000,29000000))
                    .then(Commands.argument("ty",DoubleArgumentType.doubleArg(-2048,2048))
                    .then(Commands.argument("tz",DoubleArgumentType.doubleArg(-29000000,29000000)).executes(FixedPortalCommands::create))))))))));
    }
    private static int create(CommandContext<CommandSourceStack> c){
        var source=c.getSource();var a=GaslightProvider.service(source.getServer());
        var origin=new Vec3(value(c,"x"),value(c,"y"),value(c,"z"));var target=new Vec3(value(c,"tx"),value(c,"ty"),value(c,"tz"));
        boolean ok=a.prepare(new FixedPortalAdapter.Spec(source.getLevel(),origin,source.getLevel(),target,4,4));
        source.sendSuccess(()->Component.literal(a.status().toString()),false);return ok?1:0;
    }
    private static double value(CommandContext<CommandSourceStack> c,String name){return DoubleArgumentType.getDouble(c,name);}
}
