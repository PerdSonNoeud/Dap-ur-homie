package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;


public class HeavenDapCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("heavendap")
                .executes(HeavenDapCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        ServerPlayerEntity partner = null;
        double closest = 10.0;
        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other == player) continue;
            double d = player.distanceTo(other);
            if (d < closest) { closest = d; partner = other; }
        }

        if (partner == null) {
            context.getSource().sendError(Text.literal("§cNo nearby player found (must be within 10 blocks)."));
            return 0;
        }

        Vec3d mid = player.getPos().add(partner.getPos()).multiply(0.5).add(0, 1.4, 0);
        final ServerPlayerEntity finalPartner = partner;
        ChargedDapHandler.startHeavenDap(player, finalPartner, mid, player.getServerWorld());

        context.getSource().sendFeedback(() ->
                Text.literal("§d§l HEAVEN DAP triggered with " + finalPartner.getName().getString() + "!"), false);
        return 1;
    }
}