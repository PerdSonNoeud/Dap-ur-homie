package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import com.mojang.brigadier.context.CommandContext;
import java.util.*;
public class SitHandler {
    public record SitFHoldPayload(boolean holding) implements CustomPayload {
        public static final Id<SitFHoldPayload> ID =
                new Id<>(Identifier.of("cooptest", "sit_f_hold"));
        public static final PacketCodec<PacketByteBuf, SitFHoldPayload> CODEC =
                PacketCodec.of((v, buf) -> buf.writeBoolean(v.holding()),
                        buf -> new SitFHoldPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SitFHoldPayload.ID, SitFHoldPayload.CODEC);
    }
    private static final Map<UUID, Double> sittingPlayers = new HashMap<>();
    private static final Map<UUID, UUID>   reachingSitter  = new HashMap<>();
    private static final Set<String>       activePickup    = new HashSet<>();
    public static boolean isSitting(UUID id) { return sittingPlayers.containsKey(id); }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SitFHoldPayload.ID, (payload, context) ->
                context.server().execute(() -> onFHold(context.player(), payload.holding())));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Map.Entry<UUID, Double> e : new HashMap<>(sittingPlayers).entrySet()) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p == null) continue;
                double sitY = e.getValue() - 0.5;
                p.setVelocity(0, 0, 0);
                p.velocityModified = true;
                if (Math.abs(p.getY() - sitY) > 0.05) {
                    p.teleport(p.getServerWorld(), p.getX(), sitY, p.getZ(),
                            java.util.Set.of(), p.getYaw(), p.getPitch());
                }
            }
        });
    }
    public static int executeSit(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        UUID id = player.getUuid();
        if (isSitting(id)) {
            if (!isInPickup(id)) standup(player, null);
        } else {
            sit(player);
        }
        return 1;
    }
    private static void sit(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        double originalY = player.getY();
        double sitY = originalY - 0.5;
        sittingPlayers.put(id, originalY);
        player.teleport(player.getServerWorld(),
                player.getX(), sitY, player.getZ(),
                java.util.Set.of(), player.getYaw(), player.getPitch());
        ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(true));
        PoseNetworking.broadcastAnimState(player,
                com.cooptest.client.CoopAnimationHandler.AnimState.SITTING.ordinal());
        player.sendMessage(net.minecraft.text.Text.literal("§7[Sitting — a friend can hold F to help you up]"), true);
    }
    private static void onFHold(ServerPlayerEntity helper, boolean holding) {
        UUID hid = helper.getUuid();
        if (isSitting(hid)) return;
        if (!holding) {
            UUID sitterId = reachingSitter.remove(hid);
            if (sitterId == null) return;
            ServerPlayerEntity sitter = helper.getServer().getPlayerManager().getPlayer(sitterId);
            if (sitter == null || !isSitting(sitterId)) return;
            if (helper.distanceTo(sitter) > 1.5f) {
                PoseNetworking.broadcastAnimState(helper,
                        com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
                return;
            }
            startPickup(helper, sitter);
        } else {
            ServerPlayerEntity nearest = null;
            double closest = 3.0;
            for (UUID sid : sittingPlayers.keySet()) {
                ServerPlayerEntity s = helper.getServer().getPlayerManager().getPlayer(sid);
                if (s != null && helper.distanceTo(s) < closest) { closest = helper.distanceTo(s); nearest = s; }
            }
            if (nearest == null) return;
            if (isInPickup(nearest.getUuid())) return;
            reachingSitter.put(hid, nearest.getUuid());
            PoseNetworking.broadcastAnimState(helper,
                    com.cooptest.client.CoopAnimationHandler.AnimState.REACH_DOWN.ordinal());
        }
    }
    private static void startPickup(ServerPlayerEntity helper, ServerPlayerEntity sitter) {
        UUID hid = helper.getUuid(), sid = sitter.getUuid();
        String k = hid + ":" + sid;
        final Double originalY = sittingPlayers.get(sid);
        final double sitY      = originalY != null ? originalY - 0.5 : sitter.getY();
        activePickup.add(k);
        Vec3d diff = sitter.getPos().subtract(helper.getPos());
        float helperYaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float sitterYaw  = helperYaw + 180f;
        helper.setYaw(helperYaw); helper.setBodyYaw(helperYaw); helper.setHeadYaw(helperYaw);
        sitter.setYaw(sitterYaw); sitter.setBodyYaw(sitterYaw); sitter.setHeadYaw(sitterYaw);
        helper.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        ServerPlayNetworking.send(helper, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(sitter, new ChargedDapHandler.PerfectDapFreezePayload(true));
        PoseNetworking.broadcastAnimState(helper,
                com.cooptest.client.CoopAnimationHandler.AnimState.REACH_PICKUP.ordinal());
        PoseNetworking.broadcastAnimState(sitter,
                com.cooptest.client.CoopAnimationHandler.AnimState.STAND_UP.ordinal());
        var server = helper.getServer();
        schedule(server, 2880L, () -> {
            ServerPlayerEntity h = server.getPlayerManager().getPlayer(hid);
            ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
            if (h == null || s == null) return;
            Vec3d dir = s.getPos().subtract(h.getPos()).normalize();
            Vec3d mid = h.getPos().add(0, 1.2, 0).add(dir.multiply(0.5));
            h.getServerWorld().playSound(null, mid.x, mid.y, mid.z,
                    ModSounds.DAP_HIT, net.minecraft.sound.SoundCategory.PLAYERS, 1.2f, 0.8f);
            h.getServerWorld().playSound(null, mid.x, mid.y, mid.z,
                    net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            h.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                    mid.x, mid.y, mid.z, 8, 0.15, 0.15, 0.15, 0.06);
            h.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANTED_HIT,
                    mid.x, mid.y, mid.z, 4, 0.1, 0.1, 0.1, 0.04);
        });
        final long   LIFT_START_MS = 3880L;
        final long   LIFT_END_MS   = 5170L;
        final int    LIFT_STEPS    = 10;
        final long   stepInterval  = (LIFT_END_MS - LIFT_START_MS) / LIFT_STEPS;
        for (int i = 0; i <= LIFT_STEPS; i++) {
            final int step = i;
            long delay = LIFT_START_MS + step * stepInterval;
            schedule(server, delay, () -> {
                ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
                if (s == null) return;
                if (step == 0) {
                    sittingPlayers.remove(sid);
                }
                if (originalY == null) return;
                double t   = (double) step / LIFT_STEPS;
                double liftY = sitY + (originalY - sitY) * t;
                s.teleport(s.getServerWorld(), s.getX(), liftY, s.getZ(),
                        java.util.Set.of(), s.getYaw(), s.getPitch());
            });
        }
        final double SITTER_PUSH_FORWARD  = 0.2;
        final double HELPER_PUSH_BACKWARD = 0.2;
        schedule(server, 4290L, () -> {
            ServerPlayerEntity h = server.getPlayerManager().getPlayer(hid);
            ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
            if (h == null || s == null) return;
            Vec3d dir2 = s.getPos().subtract(h.getPos()).normalize();
            Vec3d newHelperPos = h.getPos().add(dir2.multiply(-HELPER_PUSH_BACKWARD));
            h.teleport(h.getServerWorld(), newHelperPos.x, h.getY(), newHelperPos.z,
                    java.util.Set.of(), h.getYaw(), h.getPitch());
            Vec3d newSitterPos = s.getPos().add(dir2.multiply(-SITTER_PUSH_FORWARD));
            s.teleport(s.getServerWorld(), newSitterPos.x, s.getY(), newSitterPos.z,
                    java.util.Set.of(), s.getYaw(), s.getPitch());
        });
        schedule(server, 5200L, () -> {
            ServerPlayerEntity h = server.getPlayerManager().getPlayer(hid);
            ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
            if (h != null) ServerPlayNetworking.send(h, new ChargedDapHandler.PerfectDapFreezePayload(false));
            if (s != null) ServerPlayNetworking.send(s, new ChargedDapHandler.PerfectDapFreezePayload(false));
        });
        schedule(server, 6100L, () -> {
            activePickup.remove(k);
            ServerPlayerEntity h = server.getPlayerManager().getPlayer(hid);
            ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
            if (h != null) PoseNetworking.broadcastAnimState(h,
                    com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
            if (s != null) PoseNetworking.broadcastAnimState(s,
                    com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
        });
    }
    private static void standup(ServerPlayerEntity player, Double originalY) {
        UUID id = player.getUuid();
        Double oy = sittingPlayers.remove(id);
        if (oy != null) {
            player.teleport(player.getServerWorld(),
                    player.getX(), oy, player.getZ(),
                    java.util.Set.of(), player.getYaw(), player.getPitch());
        }
        ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(false));
        PoseNetworking.broadcastAnimState(player,
                com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
    }
    private static boolean isInPickup(UUID id) {
        return activePickup.stream().anyMatch(k -> k.contains(id.toString()));
    }
    private static void schedule(net.minecraft.server.MinecraftServer server, long ms, Runnable r) {
        new java.util.Timer(true).schedule(new java.util.TimerTask() {
            @Override public void run() { server.execute(r); }
        }, ms);
    }
    public static void cleanup(UUID id) {
        sittingPlayers.remove(id);
        reachingSitter.remove(id);
        activePickup.removeIf(k -> k.contains(id.toString()));
    }
}