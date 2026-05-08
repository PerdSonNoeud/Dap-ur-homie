package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.*;
public class ClapHandler {
    private static final long TIER_SLOW_MS   = 600;
    private static final long TIER_MEDIUM_MS = 200;
    private static final long SYNC_WINDOW_MS = 300;
    private static final double SYNC_RANGE   = 6.0;
    private static final int IMPACT_TICKS_SLOW   = 5;
    private static final int IMPACT_TICKS_MEDIUM = 3;
    private static final int IMPACT_TICKS_FAST   = 2;
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> lastPressTime = new HashMap<>();
    private static final List<ScheduledEffect> scheduled = new ArrayList<>();
    private static class ScheduledEffect {
        final ServerWorld world;
        final Vec3d armTip;
        final boolean syncClap;
        final int tier;
        int ticksRemaining;
        ScheduledEffect(ServerWorld world, Vec3d armTip, boolean syncClap, int tier, int delay) {
            this.world = world; this.armTip = armTip;
            this.syncClap = syncClap; this.tier = tier; this.ticksRemaining = delay;
        }
    }
    public record ClapRequestPayload() implements CustomPayload {
        public static final Identifier ID_LOC = Identifier.of("cooptest", "clap_request");
        public static final Id<ClapRequestPayload> ID = new Id<>(ID_LOC);
        public static final PacketCodec<PacketByteBuf, ClapRequestPayload> CODEC =
                PacketCodec.of((p, buf) -> {}, buf -> new ClapRequestPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ClapRequestPayload.ID, ClapRequestPayload.CODEC);
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ClapRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onClapRequest(player));
        });
        ServerTickEvents.END_SERVER_TICK.register(ClapHandler::tick);
    }
    private static void onClapRequest(ServerPlayerEntity player) {
        if (GrabMechanic.isHolding(player)) return;
        UUID id = player.getUuid();
        long now = System.currentTimeMillis();
        Long last = lastPressTime.get(id);
        int tier;
        if (last == null || now - last > TIER_SLOW_MS) {
            tier = 0;
        } else if (now - last > TIER_MEDIUM_MS) {
            tier = 1;
        } else {
            tier = 2;
        }
        lastPressTime.put(id, now);
        com.cooptest.client.CoopAnimationHandler.AnimState animState = switch (tier) {
            case 1 -> com.cooptest.client.CoopAnimationHandler.AnimState.CLAP_SPAM;
            case 2 -> com.cooptest.client.CoopAnimationHandler.AnimState.CLAP_STRONG;
            default -> com.cooptest.client.CoopAnimationHandler.AnimState.CLAP;
        };
        PoseNetworking.broadcastAnimState(player, animState.ordinal());
        if (tier == 2) {
            net.minecraft.util.math.Box fearBox = player.getBoundingBox().expand(15.0);
            player.getServerWorld().getEntitiesByClass(
                    net.minecraft.entity.passive.AnimalEntity.class, fearBox,
                    a -> !a.isRemoved()
            ).forEach(animal -> {
                net.minecraft.util.math.Vec3d away = animal.getPos().subtract(player.getPos());
                if (away.horizontalLengthSquared() < 0.001) away = new net.minecraft.util.math.Vec3d(1, 0, 0);
                away = away.normalize();
                animal.setVelocity(away.x * 0.55, 0.35, away.z * 0.55);
                animal.velocityModified = true;
                if (animal instanceof net.minecraft.entity.mob.MobEntity mob) {
                    mob.setTarget(null);
                    net.minecraft.util.math.Vec3d fleeTarget = animal.getPos().add(away.multiply(8.0));
                    animal.getNavigation().startMovingTo(
                            fleeTarget.x, fleeTarget.y, fleeTarget.z, 1.4);
                }
            });
        }
        boolean isSync = false;
        for (ServerPlayerEntity other : player.getServer().getPlayerManager().getPlayerList()) {
            if (other == player) continue;
            Long otherLast = lastPressTime.get(other.getUuid());
            if (otherLast == null || now - otherLast > SYNC_WINDOW_MS) continue;
            if (player.getPos().distanceTo(other.getPos()) <= SYNC_RANGE) { isSync = true; break; }
        }
        double yawRad = Math.toRadians(player.getBodyYaw());
        Vec3d armTip = player.getPos().add(-Math.sin(yawRad) * 0.5, 1.2, Math.cos(yawRad) * 0.5);
        int delay = switch (tier) {
            case 1 -> IMPACT_TICKS_MEDIUM;
            case 2 -> IMPACT_TICKS_FAST;
            default -> IMPACT_TICKS_SLOW;
        };
        scheduled.add(new ScheduledEffect(player.getServerWorld(), armTip, isSync, tier, delay));
    }
    private static void tick(MinecraftServer server) {
        Iterator<ScheduledEffect> it = scheduled.iterator();
        while (it.hasNext()) {
            ScheduledEffect fx = it.next();
            if (--fx.ticksRemaining <= 0) { playEffects(fx); it.remove(); }
        }
    }
    private static void playEffects(ScheduledEffect fx) {
        Vec3d p = fx.armTip;
        net.minecraft.sound.SoundEvent sound =
                ModSounds.CLAP_SOUNDS[RANDOM.nextInt(ModSounds.CLAP_SOUNDS.length)];
        if (fx.syncClap) {
            fx.world.playSound(null, p.x, p.y, p.z, sound, SoundCategory.PLAYERS, 1.4f, 1.1f);
            fx.world.spawnParticles(ParticleTypes.FIREWORK,  p.x, p.y, p.z, 14, 0.1, 0.1, 0.1, 0.05);
            fx.world.spawnParticles(ParticleTypes.WAX_ON,    p.x, p.y, p.z,  8, 0.1, 0.1, 0.1, 0.02);
            fx.world.spawnParticles(ParticleTypes.END_ROD,   p.x, p.y, p.z,  5, 0.1, 0.1, 0.1, 0.02);
        } else switch (fx.tier) {
            case 0 -> {
                fx.world.playSound(null, p.x, p.y, p.z, sound, SoundCategory.PLAYERS, 0.9f, 1.0f);
                fx.world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 5, 0.1, 0.1, 0.1, 0.03);
            }
            case 1 -> {
                fx.world.playSound(null, p.x, p.y, p.z, sound, SoundCategory.PLAYERS, 1.0f, 1.1f);
                fx.world.spawnParticles(ParticleTypes.CRIT,          p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.04);
                fx.world.spawnParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.03);
            }
            case 2 -> {
                fx.world.playSound(null, p.x, p.y, p.z, sound, SoundCategory.PLAYERS, 1.2f, 1.3f);
                fx.world.spawnParticles(ParticleTypes.CRIT,          p.x, p.y, p.z, 4, 0.12, 0.12, 0.12, 0.05);
                fx.world.spawnParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 2, 0.1,  0.1,  0.1,  0.04);
                fx.world.spawnParticles(ParticleTypes.FIREWORK,      p.x, p.y, p.z, 2, 0.1,  0.1,  0.1,  0.03);
            }
        }
    }
    public static void cleanup(UUID playerId) { lastPressTime.remove(playerId); }
}