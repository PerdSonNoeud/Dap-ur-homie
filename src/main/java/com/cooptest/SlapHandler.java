package com.cooptest;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;
public class SlapHandler {
    private static final double SLAP_RANGE      = 1.2;
    private static final double SNAP_DISTANCE   = 0.9;
    private static final double BACK_THRESHOLD  = 0.70;
    private static final double FRONT_THRESHOLD = -0.50;
    private static final double AIM_THRESHOLD   = 0.80;
    private static final long   IMPACT_DELAY_MS = 130L;
    private static final long   FRONT_IMPACT_MS = 250L;
    private static final int    ANIM_SLAP       = 67;
    private static final int    ANIM_SLAP_FRONT = 82;
    public record CameraFlickPayload(UUID playerId, float pitchDelta) implements CustomPayload {
        public static final Id<CameraFlickPayload> ID =
                new Id<>(Identifier.of("testcoop", "camera_flick"));
        public static final PacketCodec<PacketByteBuf, CameraFlickPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> { buf.writeUuid(val.playerId()); buf.writeFloat(val.pitchDelta()); },
                        buf -> new CameraFlickPayload(buf.readUuid(), buf.readFloat())
                );
        @Override public Id<CameraFlickPayload> getId() { return ID; }
    }
    public record CameraYawFlickPayload(UUID playerId, float yawDelta) implements CustomPayload {
        public static final Id<CameraYawFlickPayload> ID =
                new Id<>(Identifier.of("testcoop", "camera_yaw_flick"));
        public static final PacketCodec<PacketByteBuf, CameraYawFlickPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> { buf.writeUuid(val.playerId()); buf.writeFloat(val.yawDelta()); },
                        buf -> new CameraYawFlickPayload(buf.readUuid(), buf.readFloat())
                );
        @Override public Id<CameraYawFlickPayload> getId() { return ID; }
    }
    public record ScreenClosePayload(UUID playerId) implements CustomPayload {
        public static final Id<ScreenClosePayload> ID =
                new Id<>(Identifier.of("testcoop", "screen_close"));
        public static final PacketCodec<PacketByteBuf, ScreenClosePayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> buf.writeUuid(val.playerId()),
                        buf -> new ScreenClosePayload(buf.readUuid())
                );
        @Override public Id<ScreenClosePayload> getId() { return ID; }
    }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(CameraFlickPayload.ID,    CameraFlickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraYawFlickPayload.ID, CameraYawFlickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScreenClosePayload.ID,    ScreenClosePayload.CODEC);
    }
    public static boolean checkSlapOnRelease(ServerPlayerEntity attacker) {
        Vec3d aEye  = attacker.getPos().add(0, attacker.getEyeHeight(attacker.getPose()), 0);
        Vec3d aLook = attacker.getRotationVec(1.0f);
        ServerPlayerEntity victim = null;
        double closest = SLAP_RANGE + 0.001;
        for (ServerPlayerEntity candidate : attacker.getServerWorld().getPlayers()) {
            if (candidate == attacker) continue;
            double dist = attacker.getPos().distanceTo(candidate.getPos());
            if (dist >= closest) continue;
            Vec3d victimLook = candidate.getRotationVec(1.0f);
            double lookDot = aLook.dotProduct(victimLook);
            boolean isBack  = lookDot >= BACK_THRESHOLD;
            boolean isFront = lookDot <= FRONT_THRESHOLD;
            if (!isBack && !isFront) continue;
            Vec3d victimHead = candidate.getPos().add(0, 1.6, 0);
            Vec3d toHead     = victimHead.subtract(aEye);
            double distToHead = toHead.length();
            if (distToHead < 0.01) continue;
            double aimDot = toHead.normalize().dotProduct(aLook);
            if (aimDot < AIM_THRESHOLD) continue;
            victim = candidate;
            closest = dist;
        }
        if (victim == null) return false;
        Vec3d victimLookFinal = victim.getRotationVec(1.0f);
        if (aLook.dotProduct(victimLookFinal) <= FRONT_THRESHOLD) {
            executeFrontSlap(attacker, victim);
        } else {
            executeSlap(attacker, victim);
        }
        return true;
    }
    private static void executeSlap(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        ServerWorld world = attacker.getServerWorld();
        Vec3d victimPos  = victim.getPos();
        Vec3d victimFwd  = victim.getRotationVec(1.0f);
        Vec3d victimFwdH = new Vec3d(victimFwd.x, 0, victimFwd.z).normalize();
        if (victimFwdH.lengthSquared() < 0.001) victimFwdH = new Vec3d(1, 0, 0);
        Vec3d snapPos = victimPos.subtract(victimFwdH.multiply(SNAP_DISTANCE));
        double safeY = snapPos.y;
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos check = BlockPos.ofFloored(snapPos.x, snapPos.y - dy, snapPos.z);
            if (!world.getBlockState(check).isAir()) {
                safeY = check.getY() + 1.0;
                break;
            }
        }
        snapPos = new Vec3d(snapPos.x, safeY, snapPos.z);
        attacker.requestTeleport(snapPos.x, snapPos.y, snapPos.z);
        Vec3d diff = victimPos.subtract(snapPos);
        float yaw = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x))) - 90f;
        attacker.setYaw(yaw);
        attacker.setBodyYaw(yaw);
        attacker.setHeadYaw(yaw);
        attacker.prevBodyYaw = yaw;
        attacker.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        attacker.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        PoseNetworking.broadcastAnimState(attacker, ANIM_SLAP);
        final UUID victimId = victim.getUuid();
        new Thread(() -> {
            try { Thread.sleep(IMPACT_DELAY_MS); } catch (InterruptedException ignored) {}
            attacker.getServer().execute(() -> {
                ServerPlayerEntity v = attacker.getServer().getPlayerManager().getPlayer(victimId);
                if (v == null) return;
                Vec3d hitPos = v.getPos().add(0, 1.7, 0);
                v.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 6, false, true));
                float reactYaw = v.getHeadYaw() + 90f;
                v.setHeadYaw(reactYaw);
                CameraFlickPayload flick = new CameraFlickPayload(victimId, 70f);
                for (var p : attacker.getServer().getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(p, flick);
                }
                ServerPlayNetworking.send(v, new ScreenClosePayload(victimId));
                v.sendMessage(net.minecraft.text.Text.literal("§c§l I like ya cut G"), true);
                attacker.sendMessage(net.minecraft.text.Text.literal("§6§l SLAP!"), true);
                world.spawnParticles(ParticleTypes.CRIT,
                        hitPos.x, hitPos.y, hitPos.z, 10, 0.15, 0.1, 0.15, 0.15);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                        hitPos.x, hitPos.y, hitPos.z, 4, 0.1, 0.05, 0.1, 0.05);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                        hitPos.x, hitPos.y, hitPos.z, 6, 0.1, 0.1, 0.1, 0.08);
                world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                        ModSounds.SLAP, SoundCategory.PLAYERS, 1.4f, 1.0f);
                world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.8f, 2.0f);
            });
        }).start();
    }
    private static void executeFrontSlap(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        ServerWorld world = attacker.getServerWorld();
        Vec3d diff = victim.getPos().subtract(attacker.getPos());
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        attacker.setYaw(yaw); attacker.setBodyYaw(yaw); attacker.setHeadYaw(yaw);
        attacker.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        attacker.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        PoseNetworking.broadcastAnimState(attacker, ANIM_SLAP_FRONT);
        final UUID victimId = victim.getUuid();
        new Thread(() -> {
            try { Thread.sleep(FRONT_IMPACT_MS); } catch (InterruptedException ignored) {}
            attacker.getServer().execute(() -> {
                ServerPlayerEntity v = attacker.getServer().getPlayerManager().getPlayer(victimId);
                if (v == null) return;
                Vec3d hitPos = v.getPos().add(0, 1.7, 0);
                v.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 5, false, true));
                CameraYawFlickPayload yawFlick = new CameraYawFlickPayload(victimId, +45f);
                for (var p : attacker.getServer().getPlayerManager().getPlayerList())
                    ServerPlayNetworking.send(p, yawFlick);
                ServerPlayNetworking.send(v, new ScreenClosePayload(victimId));
                world.spawnParticles(ParticleTypes.CRIT,         hitPos.x, hitPos.y, hitPos.z, 10, 0.15, 0.1, 0.15, 0.15);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK,  hitPos.x, hitPos.y, hitPos.z,  4, 0.1,  0.05, 0.1, 0.05);
                world.playSound(null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP,  SoundCategory.PLAYERS, 1.4f, 0.9f);
                world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.8f, 1.8f);
            });
        }).start();
    }
}