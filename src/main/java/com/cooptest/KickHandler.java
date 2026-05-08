package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.*;
public class KickHandler {
    public static final float   KICK_RANGE            = 2.0f;
    public static final float   DROP_KICK_RANGE       = 3.0f;
    public static final float   KICK_DAMAGE           = 2.0f;
    public static final float   DROP_KICK_DAMAGE      = 10.0f;
    public static final double  KICK_KB_STRENGTH      = 1.5;
    public static final double  DROP_KICK_KB_STRENGTH = 3.5;
    public static final long    CHARGE_TIME_MS        = 3000L;
    public static final long    KICK_COOLDOWN_MS      = 2000L;
    private static final double KICK_SLOW_AMOUNT      = -0.4;
    private static final double DROP_KICK_SLOW_AMOUNT = -0.9;
    private static final long   KICK_ANIM_MS          = 1000L;
    private static final long   DROP_KICK_ANIM_MS     = 1750L;
    private static final long   DROP_KICK_SLOW_DELAY  = 830L;
    private static final Identifier KICK_SLOW_ID = Identifier.of("testcoop", "kick_slow");
    private static final int ANIM_KICK      = 61;
    private static final int ANIM_DROP_KICK = 62;
    public record KickStartPayload(boolean dropKickMode) implements CustomPayload {
        public static final Id<KickStartPayload> ID =
                new Id<>(Identifier.of("testcoop", "kick_start"));
        public static final PacketCodec<PacketByteBuf, KickStartPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> buf.writeBoolean(val.dropKickMode()),
                        buf -> new KickStartPayload(buf.readBoolean())
                );
        @Override public Id<KickStartPayload> getId() { return ID; }
    }
    public record KickReleasePayload() implements CustomPayload {
        public static final Id<KickReleasePayload> ID =
                new Id<>(Identifier.of("testcoop", "kick_release"));
        public static final PacketCodec<PacketByteBuf, KickReleasePayload> CODEC =
                PacketCodec.unit(new KickReleasePayload());
        @Override public Id<KickReleasePayload> getId() { return ID; }
    }
    public record KickChargeSyncPayload(UUID playerId, boolean isCharging, float chargePercent)
            implements CustomPayload {
        public static final Id<KickChargeSyncPayload> ID =
                new Id<>(Identifier.of("testcoop", "kick_charge_sync"));
        public static final PacketCodec<PacketByteBuf, KickChargeSyncPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> {
                            buf.writeUuid(val.playerId());
                            buf.writeBoolean(val.isCharging());
                            buf.writeFloat(val.chargePercent());
                        },
                        buf -> new KickChargeSyncPayload(buf.readUuid(), buf.readBoolean(), buf.readFloat())
                );
        @Override public Id<KickChargeSyncPayload> getId() { return ID; }
    }
    public record KickCooldownPayload(long cooldownMs) implements CustomPayload {
        public static final Id<KickCooldownPayload> ID =
                new Id<>(Identifier.of("testcoop", "kick_cooldown"));
        public static final PacketCodec<PacketByteBuf, KickCooldownPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> buf.writeLong(val.cooldownMs()),
                        buf -> new KickCooldownPayload(buf.readLong())
                );
        @Override public Id<KickCooldownPayload> getId() { return ID; }
    }
    public record KickResultPayload(boolean isDropKick, boolean hit) implements CustomPayload {
        public static final Id<KickResultPayload> ID =
                new Id<>(Identifier.of("testcoop", "kick_result"));
        public static final PacketCodec<PacketByteBuf, KickResultPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> {
                            buf.writeBoolean(val.isDropKick());
                            buf.writeBoolean(val.hit());
                        },
                        buf -> new KickResultPayload(buf.readBoolean(), buf.readBoolean())
                );
        @Override public Id<KickResultPayload> getId() { return ID; }
    }
    private static final Map<UUID, Long>    chargeStart        = new HashMap<>();
    private static final Map<UUID, Long>    cooldownEnd        = new HashMap<>();
    private static final Map<UUID, Integer> lastSyncTick       = new HashMap<>();
    private static final Map<UUID, Long>    slowApplyAt        = new HashMap<>();
    private static final Map<UUID, Long>    slowRemoveAt       = new HashMap<>();
    private static final Map<UUID, Double>  slowAmount         = new HashMap<>();
    private static final Map<UUID, Long>    kickPushWindowEnd  = new HashMap<>();
    private static final Map<UUID, Vec3d>   kickPushFwd        = new HashMap<>();
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(KickStartPayload.ID,      KickStartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(KickReleasePayload.ID,    KickReleasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KickChargeSyncPayload.ID, KickChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KickCooldownPayload.ID,   KickCooldownPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KickResultPayload.ID,     KickResultPayload.CODEC);
    }
    public static void register() {
        registerPayloads();
        ServerPlayNetworking.registerGlobalReceiver(KickStartPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            UUID id = player.getUuid();
            if (!CoopMovesConfig.get().enableKick) return;
            if (isOnCooldown(id)) return;
            if (payload.dropKickMode()) {
                if (!CoopMovesConfig.get().enableDropKick) {
                    executeKick(player, false);
                    return;
                }
                chargeStart.put(id, System.currentTimeMillis());
                broadcastChargeSync(player, true, 0f);
            } else {
                executeKick(player, false);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(KickReleasePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            UUID id = player.getUuid();
            Long start = chargeStart.remove(id);
            lastSyncTick.remove(id);
            if (start == null) return;
            boolean fullCharge = (System.currentTimeMillis() - start) >= CHARGE_TIME_MS;
            broadcastChargeSync(player, false, 0f);
            executeKick(player, fullCharge);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            int tick  = server.getTicks();
            Iterator<Map.Entry<UUID, Long>> it = chargeStart.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID id = entry.getKey();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) { it.remove(); lastSyncTick.remove(id); continue; }
                if (!player.isSprinting()) {
                    it.remove();
                    lastSyncTick.remove(id);
                    broadcastChargeSync(player, false, 0f);
                    ServerPlayNetworking.send(player, new KickCooldownPayload(0L));
                    continue;
                }
                int lastTick = lastSyncTick.getOrDefault(id, -999);
                if (tick - lastTick >= 2) {
                    lastSyncTick.put(id, tick);
                    float pct = Math.min(1f, (float)(now - entry.getValue()) / CHARGE_TIME_MS);
                    broadcastChargeSync(player, true, pct);
                }
            }
            Iterator<Map.Entry<UUID, Long>> pushWindowIt = kickPushWindowEnd.entrySet().iterator();
            while (pushWindowIt.hasNext()) {
                Map.Entry<UUID, Long> entry = pushWindowIt.next();
                if (now >= entry.getValue()) { pushWindowIt.remove(); kickPushFwd.remove(entry.getKey()); continue; }
                UUID id = entry.getKey();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                Vec3d fwd = kickPushFwd.get(id);
                if (player == null || fwd == null) { pushWindowIt.remove(); continue; }
                float reach = (float)(KickHandler.KICK_RANGE + 0.5);
                Box box = player.getBoundingBox().expand(reach + 0.3);
                for (var target : player.getServerWorld().getOtherEntities(player, box,
                        e -> e instanceof LivingEntity && !e.isRemoved())) {
                    double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
                    double dist = Math.sqrt(dx*dx + dz*dz);
                    if (dist > reach) continue;
                    double dot = (dist < 0.01) ? 1.0 : (fwd.x*dx + fwd.z*dz) / dist;
                    if (dot < 0.2) continue;
                    Vec3d vel = target.getVelocity();
                    target.setVelocity(vel.add(fwd.x * 0.12, 0.06, fwd.z * 0.12));
                    ((LivingEntity)target).velocityModified = true;
                }
            }
            Iterator<Map.Entry<UUID, Long>> applyIt = slowApplyAt.entrySet().iterator();
            while (applyIt.hasNext()) {
                Map.Entry<UUID, Long> entry = applyIt.next();
                if (now >= entry.getValue()) {
                    applyIt.remove();
                    UUID id = entry.getKey();
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    if (player != null) applySlowdown(player, slowAmount.getOrDefault(id, DROP_KICK_SLOW_AMOUNT));
                }
            }
            Iterator<Map.Entry<UUID, Long>> removeIt = slowRemoveAt.entrySet().iterator();
            while (removeIt.hasNext()) {
                Map.Entry<UUID, Long> entry = removeIt.next();
                if (now >= entry.getValue()) {
                    removeIt.remove();
                    slowAmount.remove(entry.getKey());
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) removeSlowdown(player);
                }
            }
        });
    }
    private static void executeKick(ServerPlayerEntity player, boolean isDropKick) {
        UUID id       = player.getUuid();
        ServerWorld world = player.getServerWorld();
        float  reach      = isDropKick ? DROP_KICK_RANGE       : KICK_RANGE;
        float  damage     = isDropKick ? DROP_KICK_DAMAGE      : KICK_DAMAGE;
        double kbStrength = isDropKick ? DROP_KICK_KB_STRENGTH : KICK_KB_STRENGTH;
        double upwardPop  = isDropKick ? 0.55 : 0.35;
        float  yaw  = player.getYaw();
        double fwdX = -Math.sin(Math.toRadians(yaw));
        double fwdZ =  Math.cos(Math.toRadians(yaw));
        Box searchBox = player.getBoundingBox().expand(reach + 0.5);
        List<Entity> candidates = world.getOtherEntities(player, searchBox,
                e -> e instanceof LivingEntity && !e.isRemoved() && !e.isSpectator());
        boolean hitAny = false;
        List<ServerPlayerEntity> playerHits = new ArrayList<>();
        for (Entity target : candidates) {
            double dx   = target.getX() - player.getX();
            double dz   = target.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > reach) continue;
            double dot = (dist < 0.01) ? 1.0 : (fwdX * dx + fwdZ * dz) / dist;
            if (dot < 0.25) continue;
            target.damage(world.getDamageSources().playerAttack(player), damage);
            if (target instanceof LivingEntity living) {
                if (isDropKick) {
                    living.setVelocity(fwdX * 4.0, 0.8, fwdZ * 4.0);
                } else {
                    living.takeKnockback(kbStrength, -dx, -dz);
                    Vec3d vel2 = living.getVelocity();
                    living.setVelocity(vel2.x, upwardPop, vel2.z);
                }
                living.velocityModified = true;
            }
            if (target instanceof ServerPlayerEntity hitPlayer) {
                playerHits.add(hitPlayer);
            }
            Vec3d hitPos = player.getPos().add(target.getPos()).multiply(0.5)
                    .add(0, player.getHeight() * 0.55, 0);
            if (isDropKick) {
                world.spawnParticles(ParticleTypes.EXPLOSION,     hitPos.x, hitPos.y, hitPos.z, 1,  0.2, 0.2, 0.2, 0.0);
                world.spawnParticles(ParticleTypes.CRIT,          hitPos.x, hitPos.y, hitPos.z, 5,  0.3, 0.3, 0.3, 0.15);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, hitPos.x, hitPos.y, hitPos.z, 4,  0.2, 0.2, 0.2, 0.1);
            } else {
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK,  hitPos.x, hitPos.y, hitPos.z, 5,  0.2, 0.2, 0.2, 0.1);
                world.spawnParticles(ParticleTypes.CRIT,          hitPos.x, hitPos.y, hitPos.z, 8,  0.2, 0.2, 0.2, 0.1);
            }
            hitAny = true;
        }
        if (isDropKick && playerHits.size() == 1) {
            ServerPlayerEntity other = playerHits.get(0);
            Vec3d mid = player.getPos().add(other.getPos()).multiply(0.5).add(0, 1.0, 0);
            world.spawnParticles(ParticleTypes.EXPLOSION,         mid.x, mid.y, mid.z, 2,  0.3, 0.3, 0.3, 0.0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 1,  0.2, 0.2, 0.2, 0.0);
            world.spawnParticles(ParticleTypes.CRIT,              mid.x, mid.y, mid.z, 8,  0.4, 0.4, 0.4, 0.2);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT,     mid.x, mid.y, mid.z, 6,  0.3, 0.3, 0.3, 0.15);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,       mid.x, mid.y, mid.z, 4,  0.3, 0.3, 0.3, 0.02);
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2 / 8) * i;
                double rx = mid.x + Math.cos(angle) * 1.5;
                double rz = mid.z + Math.sin(angle) * 1.5;
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, rx, mid.y - 0.8, rz, 1, 0, 0, 0, 0);
            }
            world.playSound(null, mid.x, mid.y, mid.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 0.8f);
            world.playSound(null, mid.x, mid.y, mid.z,
                    ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.0f, 0.7f);
            hitAny = false;
        }
        if (hitAny && isDropKick) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 0.9f, 0.9f);
        } else if (!hitAny) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.4f, 1.2f);
        }
        PoseNetworking.broadcastAnimState(player, isDropKick ? ANIM_DROP_KICK : ANIM_KICK);
        long now = System.currentTimeMillis();
        kickPushWindowEnd.put(id, now + 540L);
        kickPushFwd.put(id, new Vec3d(fwdX, 0, fwdZ));
        if (!isDropKick) {
            player.addVelocity(fwdX * 0.3, 0, fwdZ * 0.3);
            player.velocityModified = true;
            applySlowdown(player, KICK_SLOW_AMOUNT);
            slowRemoveAt.put(id, now + KICK_ANIM_MS);
        } else {
            player.addVelocity(fwdX * 0.5, 0, fwdZ * 0.5);
            player.velocityModified = true;
            slowApplyAt.put(id, now + DROP_KICK_SLOW_DELAY);
            slowRemoveAt.put(id, now + DROP_KICK_ANIM_MS);
            slowAmount.put(id, DROP_KICK_SLOW_AMOUNT);
        }
        ServerPlayNetworking.send(player, new KickResultPayload(isDropKick, hitAny));
        cooldownEnd.put(id, now + KICK_COOLDOWN_MS);
        ServerPlayNetworking.send(player, new KickCooldownPayload(KICK_COOLDOWN_MS));
    }
    private static void applySlowdown(ServerPlayerEntity player, double amount) {
        var attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(KICK_SLOW_ID);
        attr.addPersistentModifier(new EntityAttributeModifier(
                KICK_SLOW_ID, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }
    private static void removeSlowdown(ServerPlayerEntity player) {
        var attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(KICK_SLOW_ID);
    }
    private static boolean isOnCooldown(UUID id) {
        Long end = cooldownEnd.get(id);
        return end != null && System.currentTimeMillis() < end;
    }
    private static void broadcastChargeSync(ServerPlayerEntity player, boolean isCharging, float pct) {
        KickChargeSyncPayload pkt = new KickChargeSyncPayload(player.getUuid(), isCharging, pct);
        for (ServerPlayerEntity other : PlayerLookup.around(player.getServerWorld(), player.getPos(), 30)) {
            ServerPlayNetworking.send(other, pkt);
        }
        ServerPlayNetworking.send(player, pkt);
    }
    public static void cleanup(UUID id) {
        chargeStart.remove(id);
        cooldownEnd.remove(id);
        lastSyncTick.remove(id);
        slowApplyAt.remove(id);
        slowRemoveAt.remove(id);
        slowAmount.remove(id);
        kickPushWindowEnd.remove(id);
        kickPushFwd.remove(id);
    }
}