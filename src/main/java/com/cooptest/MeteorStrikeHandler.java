package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.*;

// IGNORE THIS IS TRASH WASTE 4 hr ON THIS
public class MeteorStrikeHandler {
    public static final long ABILITY_DURATION_MS = 60_000;
    public static final long COUNTDOWN_MS = 3_000;
    public static final int CRATER_RADIUS = 10;
    public static final int DAMAGE_RADIUS = 20;
    private static final Map<UUID, Long> abilityExpiry = new HashMap<>();
    private static final Map<UUID, PendingMeteor> pendingMeteors = new HashMap<>();
    private static class PendingMeteor {
        final UUID playerId;
        final BlockPos target;
        final long impactTime;
        final ServerWorld world;
        boolean invulnGranted = false;
        PendingMeteor(UUID playerId, BlockPos target, ServerWorld world) {
            this.playerId  = playerId;
            this.target    = target;
            this.world     = world;
            this.impactTime = System.currentTimeMillis() + COUNTDOWN_MS;
        }
    }
    public record MeteorFirePayload() implements CustomPayload {
        public static final Id<MeteorFirePayload> ID =
                new Id<>(Identifier.of("cooptest", "meteor_fire"));
        public static final PacketCodec<PacketByteBuf, MeteorFirePayload> CODEC =
                PacketCodec.of((p, buf) -> {}, buf -> new MeteorFirePayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record MeteorGrantPayload(long expiryMs) implements CustomPayload {
        public static final Id<MeteorGrantPayload> ID =
                new Id<>(Identifier.of("cooptest", "meteor_grant"));
        public static final PacketCodec<PacketByteBuf, MeteorGrantPayload> CODEC =
                PacketCodec.of((p, buf) -> buf.writeLong(p.expiryMs),
                        buf -> new MeteorGrantPayload(buf.readLong()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record MeteorStatusPayload(long remainingAbilityMs, long countdownMs) implements CustomPayload {
        public static final Id<MeteorStatusPayload> ID =
                new Id<>(Identifier.of("cooptest", "meteor_status"));
        public static final PacketCodec<PacketByteBuf, MeteorStatusPayload> CODEC =
                PacketCodec.of((p, buf) -> { buf.writeLong(p.remainingAbilityMs); buf.writeLong(p.countdownMs); },
                        buf -> new MeteorStatusPayload(buf.readLong(), buf.readLong()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record MeteorExpiredPayload() implements CustomPayload {
        public static final Id<MeteorExpiredPayload> ID =
                new Id<>(Identifier.of("cooptest", "meteor_expired"));
        public static final PacketCodec<PacketByteBuf, MeteorExpiredPayload> CODEC =
                PacketCodec.of((p, buf) -> {}, buf -> new MeteorExpiredPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(MeteorFirePayload.ID,    MeteorFirePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MeteorGrantPayload.ID,   MeteorGrantPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MeteorStatusPayload.ID,  MeteorStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MeteorExpiredPayload.ID, MeteorExpiredPayload.CODEC);
    }
    public static void registerClientPayloads() {
        try { PayloadTypeRegistry.playC2S().register(MeteorFirePayload.ID, MeteorFirePayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playS2C().register(MeteorGrantPayload.ID, MeteorGrantPayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playS2C().register(MeteorStatusPayload.ID, MeteorStatusPayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playS2C().register(MeteorExpiredPayload.ID, MeteorExpiredPayload.CODEC); } catch (Exception ignored) {}
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MeteorFirePayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onFire(ctx.player())));
        ServerTickEvents.END_SERVER_TICK.register(MeteorStrikeHandler::tick);
    }
    public static void grantAbility(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        long expiry = System.currentTimeMillis() + ABILITY_DURATION_MS;
        abilityExpiry.put(p1.getUuid(), expiry);
        abilityExpiry.put(p2.getUuid(), expiry);
        try { ServerPlayNetworking.send(p1, new MeteorGrantPayload(expiry)); } catch (Exception ignored) {}
        try { ServerPlayNetworking.send(p2, new MeteorGrantPayload(expiry)); } catch (Exception ignored) {}
        for (ServerPlayerEntity p : p1.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(net.minecraft.text.Text.literal(
                    "§c☄ " + p1.getName().getString() + " §7and §c" +
                            p2.getName().getString() + " §7have unlocked §c§lMETEOR STRIKE§7! Press §lG§7 to fire!"), false);
        }
    }
    public static boolean hasAbility(UUID id) { return abilityExpiry.containsKey(id); }
    public static void cleanup(UUID id) {
        abilityExpiry.remove(id);
        pendingMeteors.remove(id);
    }
    private static void onFire(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (!abilityExpiry.containsKey(id)) return;
        if (pendingMeteors.containsKey(id)) return;
        Vec3d eye  = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        BlockPos target = null;
        for (double d = 1.0; d <= 80.0; d += 0.5) {
            Vec3d point = eye.add(look.multiply(d));
            BlockPos bp = BlockPos.ofFloored(point);
            if (!player.getServerWorld().getBlockState(bp).isAir()) {
                target = bp;
                break;
            }
        }
        if (target == null) {
            Vec3d endpoint = eye.add(look.multiply(80.0));
            target = BlockPos.ofFloored(endpoint);
        }
        pendingMeteors.put(id, new PendingMeteor(id, target, player.getServerWorld()));
        final BlockPos finalTarget = target;
        Vec3d targetCenter = Vec3d.ofCenter(finalTarget);
        for (double d = 0; d < eye.distanceTo(targetCenter); d += 1.0) {
            Vec3d point = eye.add(look.multiply(d));
            player.getServerWorld().spawnParticles(ParticleTypes.CRIT,
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 2.0f, 0.5f);
        player.sendMessage(net.minecraft.text.Text.literal(
                "§c☄ METEOR INCOMING §7— impact in 3 seconds!"), true);
    }
    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        abilityExpiry.entrySet().removeIf(e -> {
            if (now >= e.getValue()) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null) {
                    try { ServerPlayNetworking.send(p, new MeteorExpiredPayload()); } catch (Exception ignored) {}
                }
                pendingMeteors.remove(e.getKey());
                return true;
            }
            return false;
        });
        for (Iterator<PendingMeteor> it = new ArrayList<>(pendingMeteors.values()).iterator(); it.hasNext();) {
            PendingMeteor m = it.next();
            long msLeft = m.impactTime - now;
            if (msLeft <= 500 && !m.invulnGranted) {
                m.invulnGranted = true;
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.playerId);
                if (p != null) p.setInvulnerable(true);
            }
            if (server.getTicks() % 2 == 0) {
                spawnCountdownPillar(m.world, m.target, (float) msLeft / COUNTDOWN_MS);
            }
            if (now >= m.impactTime) {
                pendingMeteors.remove(m.playerId);
                abilityExpiry.remove(m.playerId);
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.playerId);
                impact(m.world, m.target, p);
                if (p != null) {
                    try { ServerPlayNetworking.send(p, new MeteorExpiredPayload()); } catch (Exception ignored) {}
                    final ServerPlayerEntity fp = p;
                    server.execute(() -> {
                        fp.setInvulnerable(false);
                    });
                }
            } else {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.playerId);
                if (p != null) {
                    long abilityLeft = Math.max(0, abilityExpiry.getOrDefault(m.playerId, 0L) - now);
                    try { ServerPlayNetworking.send(p, new MeteorStatusPayload(abilityLeft, msLeft)); } catch (Exception ignored) {}
                }
            }
        }
        for (Map.Entry<UUID, Long> e : abilityExpiry.entrySet()) {
            if (!pendingMeteors.containsKey(e.getKey())) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null && server.getTicks() % 5 == 0) {
                    long abilityLeft = Math.max(0, e.getValue() - now);
                    try { ServerPlayNetworking.send(p, new MeteorStatusPayload(abilityLeft, -1)); } catch (Exception ignored) {}
                }
            }
        }
    }
    private static void spawnCountdownPillar(ServerWorld world, BlockPos target, float progress) {
        double x = target.getX() + 0.5, z = target.getZ() + 0.5;
        int height = (int)(50 * progress) + 5;
        for (int y = 0; y < height; y += 3) {
            world.spawnParticles(ParticleTypes.FLAME,
                    x, target.getY() + y, z, 1, 0.3, 0, 0.3, 0.05);
        }
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30.0 + (System.currentTimeMillis() / 100.0 % 360));
            double radius = 3.0 * progress + 0.5;
            world.spawnParticles(ParticleTypes.CRIT,
                    x + Math.cos(angle) * radius, target.getY() + 0.5, z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
        if (progress < 0.5f) {
            world.playSound(null, x, target.getY(), z,
                    SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    SoundCategory.PLAYERS, 1.5f, 0.5f + (1.0f - progress));
        }
    }
    private static void impact(ServerWorld world, BlockPos target, ServerPlayerEntity shooter) {
        Vec3d center = Vec3d.ofCenter(target);
        int r = CRATER_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x*x + y*y + z*z > r*r) continue;
                    BlockPos bp = target.add(x, y, z);
                    var state = world.getBlockState(bp);
                    if (state.isAir()) continue;
                    if (state.getBlock() == Blocks.BEDROCK) continue;
                    world.breakBlock(bp, false);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45.0);
            double ex = center.x + Math.cos(angle) * 12;
            double ez = center.z + Math.sin(angle) * 12;
            world.createExplosion(shooter, ex, center.y, ez, 12.0f, true,
                    World.ExplosionSourceType.TNT);
        }
        world.createExplosion(shooter, center.x, center.y, center.z, 20.0f, true,
                World.ExplosionSourceType.TNT);
        Box hitBox = new Box(center, center).expand(DAMAGE_RADIUS);
        for (var e : world.getOtherEntities(shooter, hitBox)) {
            if (!(e instanceof net.minecraft.entity.LivingEntity living)) continue;
            double dist = e.getPos().distanceTo(center);
            if (dist > DAMAGE_RADIUS) continue;
            float dmg = (float)(25.0 * (1.0 - dist / DAMAGE_RADIUS));
            living.damage(world.getDamageSources().explosion(null, shooter), dmg);
            Vec3d dir = e.getPos().subtract(center).normalize();
            if (dir.lengthSquared() < 0.001) dir = new Vec3d(0, 1, 0);
            living.addVelocity(dir.x * 3.0, 2.0, dir.z * 3.0);
            living.velocityModified = true;
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 20, 5, 5, 5, 0);
        world.spawnParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 200, 8, 4, 8, 0.5);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 5.0f, 0.3f);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 5.0f, 0.5f);
    }
}