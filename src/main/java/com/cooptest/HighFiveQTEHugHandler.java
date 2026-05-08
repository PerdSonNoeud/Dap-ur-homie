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
import java.util.*;
public class HighFiveQTEHugHandler {
    private static final long HUG_QTE_WINDOW_MS    = 1000;
    private static final int TICK_RIGHT_PARTICLES  =  6;
    private static final int TICK_DISTANCE_ADJUST  = 22;
    private static final int TICK_LEFT_FX_1        = 34;
    private static final int TICK_LEFT_FX_2        = 38;
    private static final int TICK_INNER_QTE_OPEN   = 33;
    private static final int TICK_PUSH_BACK        = 68;
    private static final int TICK_INNER_EVALUATE   = 70;
    private static final int TICK_HUG2             = 73;
    private static final int TICK_END              = 88;
    private static final double DIST_CLOSE = 1.0;
    private static final double DIST_FAR   = 1.2;
    public static void registerPayloads()       {
    }
    public static void registerClientPayloads() {
    }
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HighFiveQTEHugHandler::tick);
    }
    private static class HugSession {
        final UUID p1Id, p2Id;
        ServerPlayerEntity p1Ref, p2Ref;
        final long entryWindowEnd;
        boolean p1EntryPressed = false, p2EntryPressed = false;
        boolean started = false;
        int     tick    = 0;
        Vec3d p1BasePos, p2BasePos;
        Vec3d p1Forward, p2Forward;
        boolean innerQTESent   = false;
        boolean p1InnerHit     = false, p2InnerHit = false;
        boolean innerEvaluated = false;
        boolean hug2Started    = false;
        int     hug2Tick       = 0;
        HugSession(ServerPlayerEntity p1, ServerPlayerEntity p2) {
            p1Id = p1.getUuid(); p2Id = p2.getUuid();
            p1Ref = p1; p2Ref = p2;
            entryWindowEnd = System.currentTimeMillis() + HUG_QTE_WINDOW_MS;
        }
    }
    private static final Map<UUID, HugSession> sessions = new HashMap<>();
    public static void startHugQTE(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid(), id2 = p2.getUuid();
        if (sessions.containsKey(id1) || sessions.containsKey(id2)) return;
        HugSession s = new HugSession(p1, p2);
        sessions.put(id1, s);
        sessions.put(id2, s);
        ServerPlayNetworking.send(p1, new DapFusionHandler.FusionQTEPayload(id1, "G", 1, 0L, HUG_QTE_WINDOW_MS, true, 0));
        ServerPlayNetworking.send(p2, new DapFusionHandler.FusionQTEPayload(id2, "G", 1, 0L, HUG_QTE_WINDOW_MS, true, 0));
    }
    public static boolean isInHugSession(UUID id) { return sessions.containsKey(id); }
    public static boolean onButtonPress(ServerPlayerEntity player, String button) {
        UUID id = player.getUuid();
        HugSession s = sessions.get(id);
        if (s == null) return false;
        if (!s.started) {
            if (System.currentTimeMillis() > s.entryWindowEnd) return false;
            if ("G".equals(button)) {
                ServerPlayNetworking.send(s.p1Ref, new DapFusionHandler.FusionQTEPayload(s.p1Id, "G", 0, 0L, 0L, false, 0));
                ServerPlayNetworking.send(s.p2Ref, new DapFusionHandler.FusionQTEPayload(s.p2Id, "G", 0, 0L, 0L, false, 0));
                executeHug(s);
                return true;
            } else {
                ServerPlayNetworking.send(s.p1Ref, new DapFusionHandler.FusionQTEPayload(s.p1Id, "G", 0, 0L, 0L, false, 0));
                ServerPlayNetworking.send(s.p2Ref, new DapFusionHandler.FusionQTEPayload(s.p2Id, "G", 0, 0L, 0L, false, 0));
                cleanup(id);
                return true;
            }
        }
        if (s.innerQTESent && !s.innerEvaluated) {
            if ("G".equals(button)) {
                if (id.equals(s.p1Id)) s.p1InnerHit = true;
                else if (id.equals(s.p2Id)) s.p2InnerHit = true;
            }
            return true;
        }
        return false;
    }
    public static void cleanup(UUID id) {
        HugSession s = sessions.remove(id);
        if (s == null) return;
        sessions.remove(s.p1Id);
        sessions.remove(s.p2Id);
    }
    private static void executeHug(HugSession s) {
        s.started = true;
        s.tick    = 0;
        ServerPlayerEntity p1 = s.p1Ref, p2 = s.p2Ref;
        Vec3d pos1 = p1.getPos(), pos2 = p2.getPos();
        double dx = pos2.x - pos1.x, dz = pos2.z - pos1.z;
        double len = Math.sqrt(dx*dx + dz*dz);
        if (len >= 0.001) {
            float yaw1 = (float)(Math.atan2(dz, dx) * 180 / Math.PI) - 90;
            float yaw2 = yaw1 + 180;
            p1.setYaw(yaw1); p1.setBodyYaw(yaw1); p1.setHeadYaw(yaw1);
            p1.prevYaw = yaw1; p1.prevBodyYaw = yaw1; p1.prevHeadYaw = yaw1;
            p2.setYaw(yaw2); p2.setBodyYaw(yaw2); p2.setHeadYaw(yaw2);
            p2.prevYaw = yaw2; p2.prevBodyYaw = yaw2; p2.prevHeadYaw = yaw2;
            s.p1Forward = new Vec3d(dx / len, 0, dz / len);
            s.p2Forward = new Vec3d(-dx / len, 0, -dz / len);
        } else {
            s.p1Forward = new Vec3d(0, 0, 1);
            s.p2Forward = new Vec3d(0, 0, -1);
        }
        s.p1BasePos = p1.getPos();
        s.p2BasePos = p2.getPos();
        DapSessionManager.createSession(p1.getUuid(), p2.getUuid(), 1.2, DapSession.DapType.NORMAL_DAP);
        ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
        int ord = com.cooptest.client.CoopAnimationHandler.AnimState.HIGHFIVE_HUG.ordinal();
        PoseNetworking.broadcastAnimState(p1, ord);
        PoseNetworking.broadcastAnimState(p2, ord);
        for (ServerPlayerEntity p : List.of(p1, p2)) {
            var swing = new net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket(
                    p, net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket.SWING_MAIN_HAND);
            for (ServerPlayerEntity obs : p.getServerWorld().getPlayers())
                if (!obs.getUuid().equals(p.getUuid())) obs.networkHandler.sendPacket(swing);
        }
    }
    private static void tick(MinecraftServer server) {
        Set<HugSession> processed = new HashSet<>();
        for (HugSession s : new ArrayList<>(sessions.values())) {
            if (!processed.add(s)) continue;
            ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(s.p1Id);
            ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(s.p2Id);
            if (p1 == null || p2 == null) { cleanup(s.p1Id); continue; }
            s.p1Ref = p1; s.p2Ref = p2;
            if (!s.started) {
                if (System.currentTimeMillis() > s.entryWindowEnd) {
                    ServerPlayNetworking.send(p1, new DapFusionHandler.FusionQTEPayload(s.p1Id, "G", 0, 0L, 0L, false, 0));
                    ServerPlayNetworking.send(p2, new DapFusionHandler.FusionQTEPayload(s.p2Id, "G", 0, 0L, 0L, false, 0));
                    cleanup(s.p1Id);
                }
                continue;
            }
            s.tick++;
            ServerWorld world = p1.getServerWorld();
            if (s.tick == TICK_RIGHT_PARTICLES) {
                for (ServerPlayerEntity p : List.of(p1, p2)) {
                    Vec3d arm = getRightArmTip(p);
                    world.spawnParticles(ParticleTypes.CRIT,         arm.x, arm.y, arm.z, 8, 0.1, 0.1, 0.1, 0.08);
                    world.spawnParticles(ParticleTypes.ENCHANTED_HIT, arm.x, arm.y, arm.z, 5, 0.08, 0.08, 0.08, 0.05);
                }
                world.playSound(null, p1.getX(), p1.getY(), p1.getZ(),
                        ModSounds.DAP_HIT, SoundCategory.PLAYERS, 1.2f, 1.0f);
            }
            if (s.tick == TICK_DISTANCE_ADJUST) adjustDistance(p1, p2, s, DIST_CLOSE);
            if (s.tick == TICK_LEFT_FX_1) playLeftArmFX(p1, p2, world);
            if (s.tick == TICK_LEFT_FX_2) playLeftArmFX(p1, p2, world);
            if (s.tick == TICK_INNER_QTE_OPEN && !s.innerQTESent) {
                s.innerQTESent = true;
                ServerPlayNetworking.send(p1, new DapFusionHandler.FusionQTEPayload(
                        s.p1Id, "G", 1, 1100L, 1400L, true, 1));
                ServerPlayNetworking.send(p2, new DapFusionHandler.FusionQTEPayload(
                        s.p2Id, "G", 1, 1100L, 1400L, true, 1));
            }
            if (s.tick == TICK_PUSH_BACK) adjustDistance(p1, p2, s, DIST_FAR);
            if (s.tick == TICK_INNER_EVALUATE && !s.innerEvaluated) {
                s.innerEvaluated = true;
                ServerPlayNetworking.send(p1, new DapFusionHandler.FusionQTEPayload(s.p1Id, "", 0, 0, 0, false, 0));
                ServerPlayNetworking.send(p2, new DapFusionHandler.FusionQTEPayload(s.p2Id, "", 0, 0, 0, false, 0));
            }
            if (s.tick == TICK_HUG2 && !s.hug2Started) {
                s.hug2Started = true;
                if (s.p1InnerHit && s.p2InnerHit) {
                    int ord = com.cooptest.client.CoopAnimationHandler.AnimState.HIGHFIVE_HUG2.ordinal();
                    PoseNetworking.broadcastAnimState(p1, ord);
                    PoseNetworking.broadcastAnimState(p2, ord);
                }
            }
            if (s.hug2Started && s.p1InnerHit && s.p2InnerHit) {
                s.hug2Tick++;
                if (s.hug2Tick == 6) {
                    for (ServerPlayerEntity p : List.of(p1, p2)) {
                        Vec3d arm = getRightArmTip(p);
                        world.spawnParticles(ParticleTypes.CRIT,          arm.x, arm.y, arm.z, 10, 0.1, 0.1, 0.1, 0.08);
                        world.spawnParticles(ParticleTypes.ENCHANTED_HIT,  arm.x, arm.y, arm.z,  6, 0.08, 0.08, 0.08, 0.05);
                    }
                    world.playSound(null, p1.getX(), p1.getY(), p1.getZ(),
                            ModSounds.DAP_HIT, SoundCategory.PLAYERS, 1.3f, 1.0f);
                    world.playSound(null, p1.getX(), p1.getY(), p1.getZ(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.8f, 1.2f);
                }
                if (s.hug2Tick == 26) {
                    world.playSound(null, p1.getX(), p1.getY(), p1.getZ(),
                            ModSounds.SNAP, SoundCategory.PLAYERS, 1.5f, 1.0f);
                    world.playSound(null, p1.getX(), p1.getY(), p1.getZ(),
                            SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.8f, 2.0f);
                    Vec3d mid = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1.2, 0);
                    world.spawnParticles(ParticleTypes.CLOUD, mid.x, mid.y, mid.z, 12, 0.2, 0.2, 0.2, 0.04);
                    world.spawnParticles(ParticleTypes.POOF,  mid.x, mid.y, mid.z,  8, 0.15, 0.15, 0.15, 0.03);
                }
            }
            int endTick = (s.hug2Started && s.p1InnerHit && s.p2InnerHit) ? 124 : TICK_END;
            if (s.tick >= endTick) {
                ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(false));
                ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(false));
                PoseNetworking.broadcastAnimState(p1, 0);
                PoseNetworking.broadcastAnimState(p2, 0);
                DapSessionManager.removeSessionForPlayer(s.p1Id);
                cleanup(s.p1Id);
            }
        }
    }
    private static void adjustDistance(ServerPlayerEntity p1, ServerPlayerEntity p2,
                                       HugSession s, double targetDist) {
        Vec3d pos1 = p1.getPos(), pos2 = p2.getPos();
        double dx = pos2.x - pos1.x, dz = pos2.z - pos1.z;
        double len = Math.sqrt(dx*dx + dz*dz);
        if (len < 0.001) return;
        Vec3d dir = new Vec3d(dx / len, 0, dz / len);
        Vec3d mid  = pos1.add(pos2).multiply(0.5);
        Vec3d t1   = mid.subtract(dir.multiply(targetDist / 2.0));
        Vec3d t2   = mid.add(dir.multiply(targetDist / 2.0));
        p1.teleport(p1.getServerWorld(), t1.x, t1.y, t1.z, p1.getYaw(), p1.getPitch());
        p2.teleport(p2.getServerWorld(), t2.x, t2.y, t2.z, p2.getYaw(), p2.getPitch());
        s.p1BasePos = t1; s.p2BasePos = t2;
    }
    private static void playLeftArmFX(ServerPlayerEntity p1, ServerPlayerEntity p2, ServerWorld world) {
        for (ServerPlayerEntity p : List.of(p1, p2)) {
            Vec3d arm = getLeftArmTip(p);
            world.spawnParticles(ParticleTypes.CRIT, arm.x, arm.y, arm.z, 6, 0.1, 0.1, 0.1, 0.06);
        }
        world.playSound(null, p1.getX(), p1.getY(), p1.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9f, 1.1f);
        for (ServerPlayerEntity p : List.of(p1, p2)) {
            var swing = new net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket(
                    p, net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket.SWING_MAIN_HAND);
            for (ServerPlayerEntity obs : p.getServerWorld().getPlayers())
                if (!obs.getUuid().equals(p.getUuid())) obs.networkHandler.sendPacket(swing);
        }
    }
    private static Vec3d getRightArmTip(ServerPlayerEntity p) {
        double yaw = Math.toRadians(p.getBodyYaw());
        return new Vec3d(p.getX() + -Math.cos(yaw)*0.3 + -Math.sin(yaw)*0.4, p.getY()+1.0,
                p.getZ() +  Math.sin(yaw)*0.3 +  Math.cos(yaw)*0.4);
    }
    private static Vec3d getLeftArmTip(ServerPlayerEntity p) {
        double yaw = Math.toRadians(p.getBodyYaw());
        return new Vec3d(p.getX() +  Math.cos(yaw)*0.4 + -Math.sin(yaw)*0.3, p.getY()+1.3,
                p.getZ() + -Math.sin(yaw)*0.4 +  Math.cos(yaw)*0.3);
    }
}