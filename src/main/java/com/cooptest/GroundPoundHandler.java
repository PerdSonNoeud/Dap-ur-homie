package com.cooptest;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
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
public class GroundPoundHandler {
    private static final double DIVE_SPEED         = -3.5;
    private static final double AOE_RADIUS         = 8.0;
    private static final double KB_STRENGTH        = 2.2;
    private static final long   LAND_STUN_MS       = 500L;
    private static final int    MIN_HEIGHT_BLOCKS  = 3;
    private static final int    ANIM_DIVE          = 65;
    private static final int    ANIM_LAND          = 66;
    public record GroundPoundStartPayload() implements CustomPayload {
        public static final Id<GroundPoundStartPayload> ID =
                new Id<>(Identifier.of("testcoop", "ground_pound_start"));
        public static final PacketCodec<PacketByteBuf, GroundPoundStartPayload> CODEC =
                PacketCodec.unit(new GroundPoundStartPayload());
        @Override public Id<GroundPoundStartPayload> getId() { return ID; }
    }//gooidnfa crazyfabehfjafsf
    public record GroundPoundSyncPayload(UUID playerId, boolean diving) implements CustomPayload {
        public static final Id<GroundPoundSyncPayload> ID =
                new Id<>(Identifier.of("testcoop", "ground_pound_sync"));
        public static final PacketCodec<PacketByteBuf, GroundPoundSyncPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> { buf.writeUuid(val.playerId()); buf.writeBoolean(val.diving()); },
                        buf -> new GroundPoundSyncPayload(buf.readUuid(), buf.readBoolean())
                );
        @Override public Id<GroundPoundSyncPayload> getId() { return ID; }
    }
    private static final Set<UUID>          diving         = new HashSet<>();
    private static final Map<UUID, Double>  diveStartY     = new HashMap<>();
    private static final Map<UUID, Long>    landStunEnd    = new HashMap<>();
    private static final Map<UUID, Long>    diveStartTime  = new HashMap<>();
    private static final long               MAX_DIVE_MS    = 15_000L;
    static final Set<UUID>                  megaPound      = new HashSet<>();
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(GroundPoundStartPayload.ID, GroundPoundStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroundPoundSyncPayload.ID,  GroundPoundSyncPayload.CODEC);
    }
    public static void register() {
        registerPayloads();
        ServerPlayNetworking.registerGlobalReceiver(GroundPoundStartPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!CoopMovesConfig.get().enableGroundPound) return;
                UUID id = player.getUuid();
                if (diving.contains(id)) return;
                if (GrabMechanic.heldBy.containsKey(id)) return;
                if (player.isOnGround() && !SpinHandler.isSpinning(id)) return;
                if (SpinHandler.isSpinning(id)) {
                    SpinHandler.stopSpinKeepRider(player.getServer(), id);
                }
                diving.add(id);
                diveStartY.put(id, player.getY());
                diveStartTime.put(id, System.currentTimeMillis());
                player.setVelocity(0, DIVE_SPEED, 0);
                player.velocityModified = true;
                PoseNetworking.broadcastAnimState(player, ANIM_DIVE);
                broadcastDiveSync(player.getServer(), id, true);
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.0f, 0.6f);
            });
        });
    }
    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> stunIt = landStunEnd.entrySet().iterator();
        while (stunIt.hasNext()) {
            Map.Entry<UUID, Long> e = stunIt.next();
            if (now >= e.getValue()) {
                stunIt.remove();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null) {
                }
            }
        }
        Iterator<UUID> it = diving.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null) { it.remove(); cleanupDiveMaps(id); continue; }
            player.fallDistance = 0f;
            Long dStart = diveStartTime.get(id);
            if (dStart != null && System.currentTimeMillis() - dStart > MAX_DIVE_MS) {
                it.remove(); cleanupDiveMaps(id);
                broadcastDiveSync(server, id, false);
                PoseNetworking.broadcastAnimState(player, 0);
                continue;
            }
            boolean landed = player.isOnGround()
                    || player.isTouchingWater()
                    || isCloseToGroundFalling(player);
            if (landed) {
                it.remove();
                double heightFallen = Math.max(0, diveStartY.getOrDefault(id, player.getY()) - player.getY());
                cleanupDiveMaps(id);
                UUID riderId = SpinHandler.pendingGroundPoundRider.remove(id);
                if (riderId != null) {
                    SpinHandler.detachRiderByIds(player.getServer(), id, riderId);
                }
                executeImpact(player, heightFallen);
                broadcastDiveSync(server, id, false);
                continue;
            }
            Vec3d vel = player.getVelocity();
            player.setVelocity(vel.x * 0.1, DIVE_SPEED, vel.z * 0.1);
            player.velocityModified = true;
            player.setYaw(player.getYaw());
        }
    }
    private static void executeImpact(ServerPlayerEntity player, double heightFallen) {
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();
        UUID id   = player.getUuid();
        boolean isMega = megaPound.remove(id);
        double rawPower = Math.min(1.0, heightFallen / 10.0);
        double scaledPower = 1.0 - Math.exp(-rawPower * 2.5);
        if (heightFallen < MIN_HEIGHT_BLOCKS) scaledPower *= 0.3;
        double megaMult   = isMega ? 2.5 : 1.0;
        double aoeRadius  = (isMega ? 14.0 : AOE_RADIUS);
        double kbMult     = (0.5 + scaledPower * 1.5) * megaMult;
        double upwardPop  = (0.3 + scaledPower * 0.5) * (isMega ? 1.8 : 1.0);
        Box aoeBox = player.getBoundingBox().expand(aoeRadius);
        for (var entity : world.getOtherEntities(player, aoeBox,
                e -> e instanceof LivingEntity && !e.isRemoved())) {
            double dx = entity.getX() - pos.x, dz = entity.getZ() - pos.z;
            double dist = Math.sqrt(dx*dx + dz*dz);
            if (dist > aoeRadius) continue;
            double falloff = 1.0 - (dist / aoeRadius);
            if (entity instanceof LivingEntity living) {
                double nx = dist > 0.01 ? dx/dist : 0, nz = dist > 0.01 ? dz/dist : 0;
                living.setVelocity(nx * KB_STRENGTH * falloff * kbMult,
                        upwardPop * falloff,
                        nz * KB_STRENGTH * falloff * kbMult);
                living.velocityModified = true;
                double dmg = scaledPower * (isMega ? 8.0 : 4.0) * falloff;
                if (dmg > 0.5) {
                    living.damage(world.getDamageSources().playerAttack(player), (float)dmg);
                }
            }
        }
        float explosionPower = isMega ? 6.0f : 3.5f;
        world.createExplosion(player, pos.x, pos.y, pos.z, explosionPower,
                false, ServerWorld.ExplosionSourceType.TNT);
        int rings = (int)(scaledPower * 3) + 1;
        for (int ring = 1; ring <= rings; ring++) {
            double radius = ring * 2.0;
            int count     = ring * 12;
            for (int i = 0; i < count; i++) {
                double angle  = (Math.PI * 2 / count) * i;
                double rx = pos.x + Math.cos(angle) * radius;
                double rz = pos.z + Math.sin(angle) * radius;
                world.spawnParticles(ParticleTypes.EXPLOSION, rx, pos.y + 0.1, rz, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, rx, pos.y + 0.1, rz, 1, 0, 0, 0, 0);
            }
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.3, pos.z, 2, 0.3, 0, 0.3, 0);
        world.spawnParticles(ParticleTypes.CRIT,              pos.x, pos.y + 0.5, pos.z, 20, 0.8, 0.5, 0.8, 0.2);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,       pos.x, pos.y + 0.2, pos.z, 8,  0.4, 0.1, 0.4, 0.02);
        for (int i = 0; i < 24; i++) {
            double a = (Math.PI * 2 / 24) * i;
            double r = 1.5 + scaledPower;
            world.spawnParticles(ParticleTypes.CLOUD,
                    pos.x + Math.cos(a)*r, pos.y + 0.1, pos.z + Math.sin(a)*r,
                    1, Math.cos(a)*0.2, 0.1, Math.sin(a)*0.2, 0.02);
        }
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS,
                0.8f + (float)scaledPower * 0.4f, 0.7f - (float)scaledPower * 0.2f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.9f, 0.6f);
        if (scaledPower > 0.5) {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
        PoseNetworking.broadcastAnimState(player, ANIM_LAND);
        landStunEnd.put(player.getUuid(), System.currentTimeMillis() + LAND_STUN_MS);
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        if (scaledPower >= 0.6) {
            player.sendMessage(net.minecraft.text.Text.literal("§c§l GROUND POUND!"), true);
        } else {
            player.sendMessage(net.minecraft.text.Text.literal("§e Ground Pound"), true);
        }
    }
    private static boolean isCloseToGroundFalling(ServerPlayerEntity player) {
        if (player.getVelocity().y >= 0) return false;
        Vec3d pos = player.getPos();
        for (int i = 1; i <= 3; i++) {
            var block = player.getServerWorld().getBlockState(
                    net.minecraft.util.math.BlockPos.ofFloored(pos.x, pos.y - i * 0.5, pos.z));
            if (!block.isAir()) return true;
        }
        return false;
    }
    private static void broadcastDiveSync(MinecraftServer server, UUID id, boolean divingState) {
        GroundPoundSyncPayload pkt = new GroundPoundSyncPayload(id, divingState);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, pkt);
        }
    }/*
    ⠀⠀⠀⠀⠀⠀⠀⠀⢀⣠⠤⠖⠒⠛⠋⠉⠙⠛⠒⠶⢤⣄⡀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⣠⠶⠋⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⢄⡀⠀⠉⠳⣤⡀⠀⠀⠀⠀
⠀⠀⠀⠀⢀⡼⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠘⢆⠀⠀⠈⠻⣄⠀⠀⠀
⠀⠀⠀⢀⡞⠀⠀⠀⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠠⡀⠀⠈⡄⠀⠀⠀⠹⡄⠀⠀
⠀⠀⠀⡼⠀⠀⠀⠀⠐⡄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠘⡄⠀⡅⠀⠀⠀⠀⢧⠀⠀
⠀⠀⢀⡇⠀⠀⠀⠀⢠⠟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⡇⠀⠳⠀⠀⠀⠀⠀⢧⠀
⠀⠀⢸⡇⠀⠀⠀⠀⢸⡀⠀⠀⠀⠀⠀⠀⠀⠀⡠⠊⠀⠀⠀⣢⣀⣀⡤⣢⠟⠀
⠀⠀⠈⡇⠀⠀⠀⠀⠀⢑⡤⠀⠀⠈⡖⠒⣻⣭⣬⢆⠀⢸⠏⠲⣿⡗⡏⠣⣄⠀
⠀⠀⠀⢹⡄⠀⠀⠀⢀⡏⠀⠈⠵⠂⢳⣁⠀⢙⣡⢎⠀⠈⠙⠲⡤⠚⠀⠀⠈⢦
⠀⠀⠀⠀⠹⣄⠀⠀⠸⡄⠀⠀⠀⠀⠀⠀⠉⠁⠤⠊⠀⠀⠀⠀⡏⠁⠀⠀⠀⢸
⠀⠀⠀⠀⠀⠈⠙⡖⠤⠻⢤⣀⣀⣀⣀⠀⠀⠀⡠⠤⠀⠀⠀⣸⡁⠀⠀⢀⡴⠃
⠀⠀⠀⠀⠀⠀⠀⣿⠀⠀⢰⠇⠀⠀⠈⡇⢀⠔⢇⠠⡄⠀⠀⢏⢃⠒⡞⠉⠀⠀
⠀⠀⠀⠀⠀⠀⢀⡏⠀⠀⣾⠀⠀⠀⠀⠁⠎⠀⠀⠀⠘⠦⣤⡼⠈⣸⠀⠀⠀⠀
⠀⠀⠀⠀⠀⢠⡞⠀⠀⠀⡇⠀⠀⠀⠀⠀⠀⠀⢀⡔⠉⠳⠊⠢⠀⡏⠀⠀⠀⠀
⠀⠀⠀⣀⡴⠋⠀⠀⠀⠀⣧⠀⠀⠀⠀⠀⠀⠀⢻⠐⠒⠤⠔⡹⠀⣇⠀⠀⠀⠀
⢀⡴⠞⠁⠀⠀⠀⠀⢰⠀⢹⣀⠀⠀⠀⠀⠀⠀⠀⠑⠤⠤⠊⠀⠀⠹⣄⠀⠀⠀
⠉⠳⣄⠀⠀⠀⠀⠀⠀⡇⠀⠉⠳⣄⡀⠀⠀⠀⠀⠀⠐⠢⠊⠀⢂⡀⠈⠳⣄⠀
⠀⠀⠈⢳⡄⠀⠀⠀⠀⢰⡀⠀⠀⠈⠙⢦⡀⠀⠀⠀⠀⠀⠀⠀⠀⢱⡀⠀⣸⠀
⠀⠀⠀⠀⠙⢦⡔⠥⡀⠀⣇⠀⣄⠀⢀⠀⠙⠦⣄⣀⠀⠀⠀⠀⣀⡼⠤⠖⠃⠀
⠀⠀⠀⠀⠀⠀⠙⢾⣐⠐⢪⣖⡓⡤⢿⠧⡔⡉⢄⡽⠏⠙⠛⠉⠁⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠉⠓⠧⣴⣵⣁⣛⣴⠵⠞⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
    */
    private static void cleanupDiveMaps(UUID id) {
        diveStartY.remove(id);
        diveStartTime.remove(id);
    }
    public static boolean isDiving(UUID id) {
        return diving.contains(id);
    }
    public static void markMegaPound(UUID id) {
        megaPound.add(id);
    }
    public static void cleanup(UUID id) {
        diving.remove(id);
        cleanupDiveMaps(id);
        landStunEnd.remove(id);
        megaPound.remove(id);
        SpinHandler.pendingGroundPoundRider.remove(id);
    }
}