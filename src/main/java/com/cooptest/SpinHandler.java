package com.cooptest;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.*;
public class SpinHandler {
    private static final float  YAW_PER_TICK       = 22.0f;
    private static final double GRAVITY_CAP        = -0.15;
    private static final long   MAX_SPIN_MS        = 30000L;
    private static final int    SOUND_INTERVAL     = 12;
    private static final double HELICOPTER_H_RANGE = 2.5;
    private static final double HELICOPTER_V_RANGE = 2.0;
    private static final int    ANIM_SPIN          = 64;
    private static final int    ANIM_NONE          = 0;
    public record SpinStartPayload() implements CustomPayload {
        public static final Id<SpinStartPayload> ID = new Id<>(Identifier.of("testcoop", "spin_start"));
        public static final PacketCodec<PacketByteBuf, SpinStartPayload> CODEC = PacketCodec.unit(new SpinStartPayload());
        @Override public Id<SpinStartPayload> getId() { return ID; }
    }
    public record SpinStopPayload() implements CustomPayload {
        public static final Id<SpinStopPayload> ID = new Id<>(Identifier.of("testcoop", "spin_stop"));
        public static final PacketCodec<PacketByteBuf, SpinStopPayload> CODEC = PacketCodec.unit(new SpinStopPayload());
        @Override public Id<SpinStopPayload> getId() { return ID; }
    }
    public record SpinSyncPayload(UUID playerId, boolean spinning) implements CustomPayload {
        public static final Id<SpinSyncPayload> ID = new Id<>(Identifier.of("testcoop", "spin_sync"));
        public static final PacketCodec<PacketByteBuf, SpinSyncPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> { buf.writeUuid(val.playerId()); buf.writeBoolean(val.spinning()); },
                        buf -> new SpinSyncPayload(buf.readUuid(), buf.readBoolean())
                );
        @Override public Id<SpinSyncPayload> getId() { return ID; }
    }
    public record HelicopterLaunchPayload(UUID spinnerId, UUID riderId) implements CustomPayload {
        public static final Id<HelicopterLaunchPayload> ID = new Id<>(Identifier.of("testcoop", "helicopter_launch"));
        public static final PacketCodec<PacketByteBuf, HelicopterLaunchPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> { buf.writeUuid(val.spinnerId()); buf.writeUuid(val.riderId()); },
                        buf -> new HelicopterLaunchPayload(buf.readUuid(), buf.readUuid())
                );
        @Override public Id<HelicopterLaunchPayload> getId() { return ID; }
    }
    private static final Set<UUID>           activeSpin         = new HashSet<>();
    private static final Map<UUID, Long>     spinStartTime      = new HashMap<>();
    private static final Map<UUID, Float>    spinYaw            = new HashMap<>();
    private static final Map<UUID, Integer>  spinTick           = new HashMap<>();
    private static final Map<UUID, UUID>     helicopterRider    = new HashMap<>();
    private static final Map<UUID, UUID>     helicopterSpinner  = new HashMap<>();
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SpinStartPayload.ID,       SpinStartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SpinStopPayload.ID,        SpinStopPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpinSyncPayload.ID,        SpinSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HelicopterLaunchPayload.ID, HelicopterLaunchPayload.CODEC);
    }
    public static void register() {
        registerPayloads();
        ServerPlayNetworking.registerGlobalReceiver(SpinStartPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!CoopMovesConfig.get().enableSpin) return;
                UUID id = player.getUuid();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                if (pose != PoseState.GRABBED) return;
                if (GrabMechanic.heldBy.containsKey(id)) return;
                if (activeSpin.contains(id)) return;
                activeSpin.add(id);
                spinStartTime.put(id, System.currentTimeMillis());
                spinYaw.put(id, player.getBodyYaw());
                spinTick.put(id, 0);
                DapHoldHandler.forceUnfreeze(player.getServer(), id);
                PoseNetworking.broadcastAnimState(player, ANIM_NONE);
                PoseNetworking.broadcastAnimState(player, ANIM_SPIN);
                broadcastSpinSync(player.getServer(), id, true);
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.6f);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SpinStopPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> stopSpin(player.getServer(), player.getUuid()));
        });
    }
    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<UUID> it = activeSpin.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null) { it.remove(); cleanupSpinMaps(id); continue; }
            PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
            if (GrabMechanic.heldBy.containsKey(id)) {
                it.remove(); cleanupSpinMaps(id);
                detachHelicopterRider(server, id);
                broadcastSpinSync(server, id, false);
                PoseNetworking.broadcastAnimState(player, ANIM_NONE);
                continue;
            }
            if (player.isOnGround() || player.isTouchingWater()) {
                it.remove(); cleanupSpinMaps(id);
                detachHelicopterRider(server, id);
                broadcastSpinSync(server, id, false);
                PoseNetworking.broadcastAnimState(player, ANIM_NONE);
                continue;
            }
            long elapsed = now - spinStartTime.getOrDefault(id, now);
            if (elapsed >= MAX_SPIN_MS) {
                it.remove(); cleanupSpinMaps(id);
                detachHelicopterRider(server, id);
                broadcastSpinSync(server, id, false);
                PoseNetworking.broadcastAnimState(player, ANIM_NONE);
                continue;
            }
            boolean hasRider = helicopterRider.containsKey(id);
            float currentYaw = spinYaw.getOrDefault(id, player.getBodyYaw());
            if (!hasRider) {
                float newYaw = currentYaw + YAW_PER_TICK;
                if (newYaw > 180f) newYaw -= 360f;
                spinYaw.put(id, newYaw);
                currentYaw = newYaw;
                player.setYaw(currentYaw);
                player.setBodyYaw(currentYaw);
                player.setHeadYaw(currentYaw);
            }
            Vec3d vel = player.getVelocity();
            if (vel.y < GRAVITY_CAP) {
                player.setVelocity(vel.x, GRAVITY_CAP, vel.z);
                player.velocityModified = true;
            }
            Vec3d pos = player.getPos().add(0, 0.9, 0);
            double angle = Math.toRadians(currentYaw);
            player.getServerWorld().spawnParticles(
                    ParticleTypes.CLOUD,
                    pos.x + Math.cos(angle) * 0.5, pos.y, pos.z + Math.sin(angle) * 0.5,
                    2, 0.05, 0.05, 0.05, 0.01);
            int tck = spinTick.merge(id, 1, Integer::sum);
            if (tck % SOUND_INTERVAL == 0) {
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.5f, 1.4f);
            }
            if (!hasRider) {
                checkHelicopterSweep(server, player);
            }
        }
    }
    private static void checkHelicopterSweep(MinecraftServer server, ServerPlayerEntity spinner) {
        UUID spinnerId = spinner.getUuid();
        Vec3d sPos = spinner.getPos();
        ServerWorld world = spinner.getServerWorld();
        Box sweepBox = new Box(
                sPos.x - HELICOPTER_H_RANGE, sPos.y - 0.5, sPos.z - HELICOPTER_H_RANGE,
                sPos.x + HELICOPTER_H_RANGE, sPos.y + HELICOPTER_V_RANGE, sPos.z + HELICOPTER_H_RANGE
        );
        for (var entity : world.getNonSpectatingEntities(ServerPlayerEntity.class, sweepBox)) {
            if (entity == spinner) continue;
            ServerPlayerEntity target = entity;
            UUID targetId = target.getUuid();
            if (!target.isOnGround()) continue;
            if (helicopterSpinner.containsKey(targetId)) continue;
            if (GrabMechanic.holding.containsKey(spinnerId)) continue;
            if (GrabMechanic.heldBy.containsKey(targetId)) continue;
            target.startRiding(spinner, true);
            helicopterRider.put(spinnerId, targetId);
            helicopterSpinner.put(targetId, spinnerId);
            EntityPassengersSetS2CPacket pkt = new EntityPassengersSetS2CPacket(spinner);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(pkt);
            }
            spinner.setVelocity(0, 4.0, 0);
            spinner.velocityModified = true;
            GroundPoundHandler.markMegaPound(spinnerId);
            HelicopterLaunchPayload launchPkt = new HelicopterLaunchPayload(spinnerId, targetId);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, launchPkt);
            }
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    sPos.x, sPos.y + 1.2, sPos.z, 8, 0.4, 0.2, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    sPos.x, sPos.y + 0.5, sPos.z, 15, 0.5, 0.5, 0.5, 0.3);
            world.spawnParticles(ParticleTypes.EXPLOSION,
                    sPos.x, sPos.y + 0.5, sPos.z, 3, 0.3, 0.3, 0.3, 0);
            world.playSound(null, sPos.x, sPos.y, sPos.z,
                    SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 1.2f, 0.7f);
            world.playSound(null, sPos.x, sPos.y, sPos.z,
                    ModSounds.HELI, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.playSound(null, sPos.x, sPos.y, sPos.z,
                    ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 0.8f, 0.5f);
            spinner.sendMessage(net.minecraft.text.Text.literal("§c§l🚀 HELICOPTER! Press SHIFT for MEGA GROUND POUND!"), true);
            target.sendMessage(net.minecraft.text.Text.literal("§c§l🚀 You're riding the helicopter!"), true);
            break;
        }
    }
    private static void doHelicopterLaunch(MinecraftServer server, ServerPlayerEntity spinner) {
        UUID spinnerId = spinner.getUuid();
        UUID riderId   = helicopterRider.get(spinnerId);
        if (riderId == null) return;
        ServerPlayerEntity rider = server.getPlayerManager().getPlayer(riderId);
        if (rider != null) rider.stopRiding();
        helicopterRider.remove(spinnerId);
        helicopterSpinner.remove(riderId);
        EntityPassengersSetS2CPacket pkt = new EntityPassengersSetS2CPacket(spinner);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(pkt);
        }
        activeSpin.remove(spinnerId);
        cleanupSpinMaps(spinnerId);
        broadcastSpinSync(server, spinnerId, false);
        PoseNetworking.broadcastAnimState(spinner, 0);
        Vec3d upVel = new Vec3d(0, 3.0, 0);
        spinner.setVelocity(upVel);
        spinner.velocityModified = true;
        if (rider != null) {
            rider.setVelocity(upVel.add(0, 0.15, 0));
            rider.velocityModified = true;
            GroundPoundHandler.markMegaPound(spinnerId);
        }
        HelicopterLaunchPayload launchPkt = new HelicopterLaunchPayload(spinnerId, riderId != null ? riderId : spinnerId);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, launchPkt);
        }
        ServerWorld world = spinner.getServerWorld();
        Vec3d pos = spinner.getPos().add(0, 1, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.3, 0.3, 0.3, 0);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 20, 0.5, 0.5, 0.5, 0.3);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 1.2f, 0.7f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 0.8f, 0.5f);
        spinner.sendMessage(net.minecraft.text.Text.literal("§c§l🚀 HELICOPTER LAUNCH!"), true);
        if (rider != null) rider.sendMessage(net.minecraft.text.Text.literal("§c§l🚀 Helicopter launched!"), true);
    }
    private static void detachHelicopterRider(MinecraftServer server, UUID spinnerId) {
        UUID riderId = helicopterRider.remove(spinnerId);
        if (riderId == null) return;
        helicopterSpinner.remove(riderId);
        ServerPlayerEntity rider   = server.getPlayerManager().getPlayer(riderId);
        ServerPlayerEntity spinner = server.getPlayerManager().getPlayer(spinnerId);
        if (rider != null) {
            rider.stopRiding();
            rider.requestTeleport(rider.getX(), rider.getY(), rider.getZ());
        }
        if (spinner != null) {
            EntityPassengersSetS2CPacket pkt = new EntityPassengersSetS2CPacket(spinner);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(pkt);
            }
        }
        if (rider != null) {
            EntityPassengersSetS2CPacket pkt2 = new EntityPassengersSetS2CPacket(rider);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(pkt2);
            }
        }
    }
    static final Map<UUID, UUID> pendingGroundPoundRider = new HashMap<>();
    public static void stopSpinKeepRider(MinecraftServer server, UUID id) {
        if (!activeSpin.remove(id)) return;
        UUID riderId = helicopterRider.remove(id);
        if (riderId != null) {
            helicopterSpinner.remove(riderId);
            pendingGroundPoundRider.put(id, riderId);
        }
        cleanupSpinMaps(id);
        broadcastSpinSync(server, id, false);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
        if (player != null) PoseNetworking.broadcastAnimState(player, ANIM_NONE);
    }
    public static void stopSpin(MinecraftServer server, UUID id) {
        if (!activeSpin.remove(id)) return;
        cleanupSpinMaps(id);
        detachHelicopterRider(server, id);
        broadcastSpinSync(server, id, false);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
        if (player != null) PoseNetworking.broadcastAnimState(player, ANIM_NONE);
    }
    public static void detachRiderByIds(MinecraftServer server, UUID spinnerId, UUID riderId) {
        helicopterSpinner.remove(riderId);
        ServerPlayerEntity rider   = server.getPlayerManager().getPlayer(riderId);
        ServerPlayerEntity spinner = server.getPlayerManager().getPlayer(spinnerId);
        if (rider != null) {
            rider.stopRiding();
            rider.requestTeleport(rider.getX(), rider.getY(), rider.getZ());
        }
        if (spinner != null) {
            EntityPassengersSetS2CPacket pkt = new EntityPassengersSetS2CPacket(spinner);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(pkt);
            }
        }
        if (rider != null) {
            EntityPassengersSetS2CPacket pkt2 = new EntityPassengersSetS2CPacket(rider);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(pkt2);
            }
        }
    }
    public static boolean isSpinning(UUID id) { return activeSpin.contains(id); }
    public static boolean hasHelicopterRider(UUID spinnerId) { return helicopterRider.containsKey(spinnerId); }
    private static void cleanupSpinMaps(UUID id) {
        spinStartTime.remove(id);
        spinYaw.remove(id);
        spinTick.remove(id);
    }
    private static void broadcastSpinSync(MinecraftServer server, UUID id, boolean spinning) {
        SpinSyncPayload pkt = new SpinSyncPayload(id, spinning);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, pkt);
        }
    }
    public static void cleanup(UUID id) {
        activeSpin.remove(id);
        cleanupSpinMaps(id);
        UUID riderId = helicopterRider.remove(id);
        if (riderId != null) helicopterSpinner.remove(riderId);
        helicopterSpinner.remove(id);
        pendingGroundPoundRider.remove(id);
    }
}