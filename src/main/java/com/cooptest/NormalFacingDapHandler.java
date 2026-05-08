package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import com.cooptest.client.CoopAnimationHandler;
import java.util.*;
public class NormalFacingDapHandler {
    public static final net.minecraft.util.Identifier LOOP_HOLD_ID =
            net.minecraft.util.Identifier.of("cooptest", "dap_loop_hold");
    public record DapLoopHoldPayload() implements net.minecraft.network.packet.CustomPayload {
        public static final Id<DapLoopHoldPayload> ID = new Id<>(LOOP_HOLD_ID);
        public static final net.minecraft.network.codec.PacketCodec<net.minecraft.network.PacketByteBuf, DapLoopHoldPayload> CODEC =
                net.minecraft.network.codec.PacketCodec.unit(new DapLoopHoldPayload());
        @Override public Id<? extends net.minecraft.network.packet.CustomPayload> getId() { return ID; }
    }
    public static final net.minecraft.util.Identifier SESSION_STATE_ID =
            net.minecraft.util.Identifier.of("cooptest", "face_dap_session");
    public record FaceDapSessionPayload(boolean active) implements net.minecraft.network.packet.CustomPayload {
        public static final Id<FaceDapSessionPayload> ID = new Id<>(SESSION_STATE_ID);
        public static final net.minecraft.network.codec.PacketCodec<net.minecraft.network.PacketByteBuf, FaceDapSessionPayload> CODEC =
                net.minecraft.network.codec.PacketCodec.of((v, buf) -> buf.writeBoolean(v.active()),
                        buf -> new FaceDapSessionPayload(buf.readBoolean()));
        @Override public Id<? extends net.minecraft.network.packet.CustomPayload> getId() { return ID; }
    }
    public static void registerPayloads() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(DapLoopHoldPayload.ID, DapLoopHoldPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
                .register(FaceDapSessionPayload.ID, FaceDapSessionPayload.CODEC);
    }
    private static final double FACE_DIST      = 1.3;
    private static final long   ANIM_TOTAL     = 3333L;
    private static final long   LOOP_ENTRY_MS  = 2080L;
    private static final long   LOOP_CYCLE_MS  = 1250L;
    private static final long   LOOP_END_MS    = 917L;
    private static final long   TRIGGER_COOL   = 1500L;
    private static final double TRIGGER_RANGE  = 1.5;
    private static final long   CLICK_WINDOW   = 2000L;
    private static final Map<UUID, UUID>   activeSessions = new HashMap<>();
    private static final Map<UUID, Boolean> inLoop        = new HashMap<>();
    private static final Map<String, Long> sessionStart   = new HashMap<>();
    private static final Map<UUID, Long>   lastHold       = new HashMap<>();
    private static final Map<UUID, Long>   cycleStart     = new HashMap<>();
    private static final Map<String, Integer> loopCount   = new HashMap<>();
    private static final Map<String, Long>  loopStartTime = new HashMap<>();
    private static final Map<String, UUID>  canonicalP1   = new HashMap<>();
    private static final Map<UUID, UUID>   clickMap       = new HashMap<>();
    private static final Map<UUID, Long>   clickTime      = new HashMap<>();
    private static final Map<UUID, Long>   triggerCooldowns = new HashMap<>();
    public static boolean isActive(UUID id) { return activeSessions.containsKey(id); }
    public static void onLoopHold(ServerPlayerEntity player) {
        if (activeSessions.containsKey(player.getUuid())) {
            lastHold.put(player.getUuid(), System.currentTimeMillis());
        }
    }
    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
    public static void register() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                DapLoopHoldPayload.ID, (payload, context) ->
                        context.server().execute(() -> onLoopHold(context.player())));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (activeSessions.isEmpty()) return;
            Set<UUID> processed = new HashSet<>();
            for (Map.Entry<UUID, UUID> e : new HashMap<>(activeSessions).entrySet()) {
                UUID p1 = e.getKey(), p2 = e.getValue();
                if (processed.contains(p1)) continue;
                processed.add(p1); processed.add(p2);
                pin(server, p1); pin(server, p2);
                String k = key(p1, p2);
                UUID canon = canonicalP1.get(k);
                if (canon == null) continue;
                if (Boolean.TRUE.equals(inLoop.get(canon))) {
                    UUID other = activeSessions.get(canon);
                    if (other != null) doTickLoop(server, canon, other);
                } else {
                    Long ss = sessionStart.get(k);
                    long now = System.currentTimeMillis();
                    if (ss != null && now - ss >= LOOP_ENTRY_MS) {
                        Long h1 = lastHold.get(p1), h2 = lastHold.get(p2);
                        boolean b1 = h1 != null && now - h1 < 2000L;
                        boolean b2 = h2 != null && now - h2 < 2000L;
                        System.out.println("[DAPLOOP] Entry check: b1=" + b1 + " b2=" + b2
                                + " h1=" + (h1 != null ? (now-h1)+"ms ago" : "null")
                                + " h2=" + (h2 != null ? (now-h2)+"ms ago" : "null")
                                + " elapsed=" + (now-ss) + "ms");
                        if (b1 && b2) {
                            startLoop(server, canon, activeSessions.get(canon));
                        }
                    }
                }
            }
        });
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> {
                    if (world.isClient) return net.minecraft.util.ActionResult.PASS;
                    if (!(player instanceof ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
                    if (!(entity instanceof ServerPlayerEntity target)) return net.minecraft.util.ActionResult.PASS;
                    if (sp.isSneaking()) return net.minecraft.util.ActionResult.PASS;
                    if (Boolean.TRUE.equals(inLoop.get(sp.getUuid()))) {
                        UUID partner = activeSessions.get(sp.getUuid());
                        if (partner != null) endLoop(sp.getServer(), sp.getUuid(), partner);
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                    return net.minecraft.util.ActionResult.PASS;
                });
    }
    public static void recordRightClick(ServerPlayerEntity sp, ServerPlayerEntity target) {
        UUID sid = sp.getUuid(), tid = target.getUuid();
        clickMap.put(sid, tid);
        clickTime.put(sid, System.currentTimeMillis());
        sp.sendMessage(net.minecraft.text.Text.literal("§e✦ Waiting for homie..."), true);
    }
    public static boolean isConfirmed(UUID id1, UUID id2) {
        long now = System.currentTimeMillis();
        UUID c1 = clickMap.get(id1); Long t1 = clickTime.get(id1);
        UUID c2 = clickMap.get(id2); Long t2 = clickTime.get(id2);
        return id2.equals(c1) && t1 != null && now - t1 < CLICK_WINDOW
                && id1.equals(c2) && t2 != null && now - t2 < CLICK_WINDOW;
    }
    public static boolean isConfirmedOneSide(UUID who, UUID target) {
        UUID c = clickMap.get(who); Long t = clickTime.get(who);
        return target.equals(c) && t != null && System.currentTimeMillis() - t < CLICK_WINDOW;
    }
    public static void clearConfirm(UUID id1, UUID id2) {
        clickMap.remove(id1); clickTime.remove(id1);
        if (id2 != null) { clickMap.remove(id2); clickTime.remove(id2); }
    }
    public static void start(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid(), id2 = p2.getUuid();
        String k = key(id1, id2);
        activeSessions.put(id1, id2);
        activeSessions.put(id2, id1);
        loopCount.put(k, 0);
        sessionStart.put(k, System.currentTimeMillis());
        canonicalP1.put(k, id1);
        MinecraftServer server = p1.getServer();
        if (server == null) return;
        Vec3d diff = p2.getPos().subtract(p1.getPos());
        Vec3d flat = new Vec3d(diff.x, 0, diff.z).normalize();
        Vec3d mid  = p1.getPos().add(p2.getPos()).multiply(0.5);
        Vec3d pos1 = mid.subtract(flat.multiply(FACE_DIST * 0.5));
        Vec3d pos2 = mid.add(flat.multiply(FACE_DIST * 0.5));
        float yaw1 = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
        float yaw2 = yaw1 + 180f;
        p1.teleport(p1.getServerWorld(), pos1.x, p1.getY(), pos1.z, java.util.Set.of(), yaw1, p1.getPitch());
        p2.teleport(p2.getServerWorld(), pos2.x, p2.getY(), pos2.z, java.util.Set.of(), yaw2, p2.getPitch());
        p1.setYaw(yaw1); p1.setBodyYaw(yaw1); p1.setHeadYaw(yaw1);
        p2.setYaw(yaw2); p2.setBodyYaw(yaw2); p2.setHeadYaw(yaw2);
        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p1, new FaceDapSessionPayload(true));
        ServerPlayNetworking.send(p2, new FaceDapSessionPayload(true));
        PoseNetworking.broadcastAnimState(p1, anim(CoopAnimationHandler.AnimState.DAP_HIT_FACE));
        PoseNetworking.broadcastAnimState(p2, anim(CoopAnimationHandler.AnimState.DAP_HIT_FACE));
        schedule(server, 420L, () -> {
            ServerPlayerEntity a = server.getPlayerManager().getPlayer(id1);
            ServerPlayerEntity b = server.getPlayerManager().getPlayer(id2);
            if (a == null || b == null) return;
            ServerWorld w = a.getServerWorld();
            Vec3d m = a.getPos().add(b.getPos()).multiply(0.5).add(0, 1.3, 0);
            w.playSound(null, m.x, m.y, m.z, ModSounds.DAP_HIT, SoundCategory.PLAYERS, 1.5f, 1.0f);
            w.playSound(null, m.x, m.y, m.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.2f);
            w.spawnParticles(ParticleTypes.CRIT, m.x, m.y, m.z, 12, 0.2, 0.2, 0.2, 0.1);
            w.spawnParticles(ParticleTypes.ENCHANTED_HIT, m.x, m.y, m.z, 6, 0.15, 0.15, 0.15, 0.07);
            w.spawnParticles(ParticleTypes.FLASH, m.x, m.y, m.z, 2, 0, 0, 0, 0);
        });
        long[] punches = {1333, 1417, 1583, 1667, 1833, 2000, 2167};
        for (long t : punches) {
            schedule(server, t, () -> {
                ServerPlayerEntity a = server.getPlayerManager().getPlayer(id1);
                ServerPlayerEntity b = server.getPlayerManager().getPlayer(id2);
                if (a == null || b == null) return;
                Vec3d m = a.getPos().add(b.getPos()).multiply(0.5).add(0, 1.3, 0);
                a.getServerWorld().spawnParticles(ParticleTypes.CRIT, m.x, m.y, m.z, 3, 0.1, 0.1, 0.1, 0.06);
                a.getServerWorld().playSound(null, m.x, m.y, m.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.7f, 1.0f + (float)(Math.random() * 0.3));
            });
        }
        schedule(server, ANIM_TOTAL + 100L, () -> {
            if (!Boolean.TRUE.equals(inLoop.get(id1))) cleanup(server, id1, id2);
        });
    }
    private static void startLoop(MinecraftServer server, UUID id1, UUID id2) {
        long now = System.currentTimeMillis();
        inLoop.put(id1, true);
        inLoop.put(id2, true);
        cycleStart.put(id1, now);
        loopStartTime.put(key(id1, id2), now);
        lastHold.put(id1, now); lastHold.put(id2, now);
        ServerPlayerEntity a = server.getPlayerManager().getPlayer(id1);
        ServerPlayerEntity b = server.getPlayerManager().getPlayer(id2);
        if (a != null) PoseNetworking.broadcastAnimState(a, anim(CoopAnimationHandler.AnimState.DAP_LOOP));
        if (b != null) PoseNetworking.broadcastAnimState(b, anim(CoopAnimationHandler.AnimState.DAP_LOOP));
    }
    private static void doTickLoop(MinecraftServer server, UUID id1, UUID id2) {
        Long cs = cycleStart.get(id1);
        if (cs == null) return;
        long now = System.currentTimeMillis();
        if (now - cs < LOOP_CYCLE_MS) return;
        Long h1 = lastHold.get(id1), h2 = lastHold.get(id2);
        if (h1 == null || now - h1 > 2000L || h2 == null || now - h2 > 2000L) {
            endLoop(server, id1, id2);
            return;
        }
        cycleStart.put(id1, now);
        String k = key(id1, id2);
        int count = loopCount.getOrDefault(k, 0) + 1;
        loopCount.put(k, count);
        ServerPlayerEntity a = server.getPlayerManager().getPlayer(id1);
        ServerPlayerEntity b = server.getPlayerManager().getPlayer(id2);
        if (a == null || b == null) { endLoop(server, id1, id2); return; }
        ServerWorld w = a.getServerWorld();
        Vec3d m = a.getPos().add(b.getPos()).multiply(0.5).add(0, 1.3, 0);
        Long ls = loopStartTime.get(k);
        long sec = ls != null ? (now - ls) / 1000L : 0;
        a.sendMessage(net.minecraft.text.Text.literal("§e⚡ " + count + " §f" + sec + "s"), true);
        b.sendMessage(net.minecraft.text.Text.literal("§e⚡ " + count + " §f" + sec + "s"), true);
        w.playSound(null, m.x, m.y, m.z, ModSounds.DAP_HIT, SoundCategory.PLAYERS, 1.0f + Math.min(count * 0.03f, 0.5f), 1.0f);
        w.spawnParticles(ParticleTypes.CRIT, m.x, m.y, m.z, 4 + Math.min(count, 20), 0.2, 0.2, 0.2, 0.05);
        if (count >= 9)  w.spawnParticles(ParticleTypes.END_ROD, m.x, m.y, m.z, count, 0.5, 0.3, 0.5, 0.05);
        if (count >= 25 && count % 4 == 0) { var l = new net.minecraft.entity.LightningEntity(net.minecraft.entity.EntityType.LIGHTNING_BOLT, w); l.setPos(m.x, m.y, m.z); l.setCosmetic(true); w.spawnEntity(l); }
        if (count >= 480) endLoop(server, id1, id2);
    }
    private static void endLoop(MinecraftServer server, UUID id1, UUID id2) {
        inLoop.remove(id1); inLoop.remove(id2);
        cycleStart.remove(id1);
        ServerPlayerEntity a = server.getPlayerManager().getPlayer(id1);
        ServerPlayerEntity b = server.getPlayerManager().getPlayer(id2);
        if (a != null) PoseNetworking.broadcastAnimState(a, anim(CoopAnimationHandler.AnimState.DAP_LOOP_END));
        if (b != null) PoseNetworking.broadcastAnimState(b, anim(CoopAnimationHandler.AnimState.DAP_LOOP_END));
        if (server != null) schedule(server, LOOP_END_MS, () -> cleanup(server, id1, id2));
    }
    private static void cleanup(MinecraftServer server, UUID id1, UUID id2) {
        String k = key(id1, id2);
        activeSessions.remove(id1); activeSessions.remove(id2);
        inLoop.remove(id1); inLoop.remove(id2);
        cycleStart.remove(id1);
        loopCount.remove(k); loopStartTime.remove(k);
        sessionStart.remove(k); canonicalP1.remove(k);
        lastHold.remove(id1); lastHold.remove(id2);
        ServerPlayerEntity a = server.getPlayerManager().getPlayer(id1);
        ServerPlayerEntity b = server.getPlayerManager().getPlayer(id2);
        if (a != null) { ServerPlayNetworking.send(a, new ChargedDapHandler.PerfectDapFreezePayload(false)); ServerPlayNetworking.send(a, new FaceDapSessionPayload(false)); PoseNetworking.broadcastAnimState(a, 0); }
        if (b != null) { ServerPlayNetworking.send(b, new ChargedDapHandler.PerfectDapFreezePayload(false)); ServerPlayNetworking.send(b, new FaceDapSessionPayload(false)); PoseNetworking.broadcastAnimState(b, 0); }
        long cd = System.currentTimeMillis() + ChargedDapHandler.cooldownMs();
        ChargedDapHandler.cooldowns.put(id1, cd); ChargedDapHandler.cooldowns.put(id2, cd);
    }
    public static void cleanup(UUID id) {
        UUID partner = activeSessions.remove(id);
        if (partner != null) { activeSessions.remove(partner); inLoop.remove(partner); }
        inLoop.remove(id); cycleStart.remove(id);
        clickMap.remove(id); clickTime.remove(id); lastHold.remove(id);
    }
    private static void pin(MinecraftServer server, UUID id) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p != null) { p.setVelocity(0, 0, 0); p.velocityModified = true; }
    }
    private static int anim(CoopAnimationHandler.AnimState state) { return state.ordinal(); }
    private static void schedule(MinecraftServer server, long delayMs, Runnable task) {
        new java.util.Timer(true).schedule(new java.util.TimerTask() {
            @Override public void run() { server.execute(task); }
        }, delayMs);
    }
}