package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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

import java.util.*;
import java.util.Random;

public class ChargedDapHandler {
    public static final float DAP_RANGE = 1.6f;

    // public static final long CHARGE_TIME_MS = 250;
    // public static final long RELEASE_WINDOW_MS = 500;
    // public static final long PERFECT_WINDOW_MS = 85;
    // public static final long GOOD_SYNC_MS = 300;
    // public static final long COOLDOWN_MS = 1500;
    // public static final long WHIFF_COOLDOWN_MS = 800;
    // public static final long FIRE_DELAY_MS = 2000;
    // public static final long FIRE_BUILD_TIME_MS = 2000;

    public static long chargeTimeMs() {
        return CoopMovesConfig.get().dapChargeWindowMs;
    }

    public static long releaseWindowMs() {
        return CoopMovesConfig.get().dapReleaseWindowMs;
    }

    public static long perfectWindowMs() {
        return CoopMovesConfig.get().dapPerfectWindowMs;
    }

    public static long cooldownMs() {
        return CoopMovesConfig.get().dapCooldownMs;
    }

    public static long whiffCooldownMs() {
        return CoopMovesConfig.get().dapWhiffCooldownMs;
    }

    public static long fireDelayMs() {
        return CoopMovesConfig.get().dapFireDelayMs;
    }

    public static long fireBuildTimeMs() {
        return CoopMovesConfig.get().dapFireBuildTimeMs;
    }

    public static final double MIN_MOVEMENT_SPEED = 1.5;
    public static final long FIRE_GRACE_PERIOD_MS = 500;

    public static final double SPEED_BONUS_THRESHOLD = 8.0;
    public static final double SPEED_TIER_4_THRESHOLD = 25.0;
    public static final double PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED = 10.0;

    public static final int SPEED_HISTORY_TICKS = 40;

    public static final Map<UUID, Long> chargeStartTime = new HashMap<>();
    public static final Map<UUID, Long> releaseTime = new HashMap<>();
    public static final Map<UUID, UUID> waitingForPartner = new HashMap<>();
    public static final Map<UUID, Long> cooldowns = new HashMap<>();

    public static final Map<UUID, Long> fireStartTime = new HashMap<>();
    public static final Map<UUID, Long> fireGraceTime = new HashMap<>();
    public static final Map<UUID, Float> fireLevel = new HashMap<>();

    public static final Map<UUID, Long> fireMaxedStartTime = new HashMap<>();
    public static final Set<UUID> heavenReady = new HashSet<>();
    public static final long HEAVEN_READY_TIME_MS = 5000;

    private static class HeavenParticleSpawner {
        final ServerWorld world;
        final Vec3d pos;
        final long startTime;
        final long endTime;

        HeavenParticleSpawner(ServerWorld world, Vec3d pos, long durationMs) {
            this.world = world;
            this.pos = pos;
            this.startTime = System.currentTimeMillis();
            this.endTime = this.startTime + durationMs;
        }
    }

    private static final List<HeavenParticleSpawner> activeHeavenParticles = new ArrayList<>();

    public static final Map<UUID, LinkedList<Double>> speedHistory = new HashMap<>();

    public static final Map<UUID, Integer> impactFreezeTicks = new HashMap<>();

    private static final Map<UUID, Long> perfectDapStartTime = new HashMap<>();
    private static final Map<UUID, UUID> perfectDapPartner = new HashMap<>();
    private static final Map<UUID, Long> perfectDapFreezeEnd = new HashMap<>();
    private static final Map<UUID, Boolean> perfectDapImpactSent = new HashMap<>();

    private static final Map<UUID, Long> comboCooldown = new HashMap<>();

    private static final Map<UUID, Boolean> fireComboActive = new HashMap<>();

    private static final Map<UUID, Long> underwaterRemovalStart = new HashMap<>();
    private static final Map<UUID, Vec3d> underwaterRemovalPos = new HashMap<>();
    private static final Map<UUID, ServerWorld> underwaterRemovalWorld = new HashMap<>();

    private static final long FIRE_DAP_HIT_LENGTH = 2292;
    private static final long FIRE_IMPACT_TIME = 210;
    private static final long FIRE_J_WINDOW_START = 830;
    private static final long FIRE_J_WINDOW_END = 2200;
    // private static final long FIRE_WINDOW_START = 710;
    // private static final long FIRE_WINDOW_END = 1420;

    private static final long FUSION_G_WINDOW_START_MS = DapFusionHandler.FUSION_G_WINDOW_START;

    private static final long FIRE_COMBO_FREEZE_MS = 4000;
    private static final long FIRE_COMBO_ARM_IMPACT = 1330;
    private static final long FIRE_COMBO_TORNADO = 1460;

    private static final Map<UUID, Long> fireDapStartTime = new HashMap<>();
    private static final Map<UUID, UUID> fireDapPartner = new HashMap<>();
    private static final Map<UUID, Boolean> inFireDapHit = new HashMap<>();
    private static final Map<UUID, Boolean> fireCircleSpawned = new HashMap<>();

    private static final Map<UUID, Long> fireDapComboRequestTime = new HashMap<>();
    private static final Map<UUID, Long> fireDapComboFreezeEnd = new HashMap<>();

    private static final Map<UUID, HeavenDapData> heavenPlayers = new HashMap<>();

    static class HeavenDapData {
        Vec3d originalMidpoint;
        ServerWorld world;
        long startTime;
        UUID partnerId;

        HeavenDapData(Vec3d originalMidpoint, ServerWorld world, long startTime, UUID partnerId) {
            this.originalMidpoint = originalMidpoint;
            this.world = world;
            this.startTime = startTime;
            this.partnerId = partnerId;
        }
    }

    private static class TornadoSwirlEntity {
        final Vec3d startCenter;
        final ServerWorld world;
        double angle;
        double height;
        double radius;
        int age;

        TornadoSwirlEntity(ServerWorld world, Vec3d center, double startAngle, double startRadius) {
            this.world = world;
            this.startCenter = center;
            this.angle = startAngle;
            this.height = 0;
            this.radius = startRadius;
            this.age = 0;
        }

        boolean tick() {

            height += 0.2;

            angle += 18;

            if (height < 30) {
                radius += 0.08;
            } else {
                radius -= 0.05;
            }

            double x = startCenter.x + Math.cos(Math.toRadians(angle)) * radius;
            double z = startCenter.z + Math.sin(Math.toRadians(angle)) * radius;
            double y = startCenter.y + height;

            world.spawnParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.08);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 4, 0.2, 0.2, 0.2, 0.05);

            if (height % 5 < 0.5) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.4, 0.4, 0.4, 0.06);
                world.spawnParticles(ParticleTypes.LAVA, x, y, z, 3, 0.2, 0.2, 0.2, 0.02);
            }

            age++;

            return height < 60 && age < 60;
        }
    }

    private static final List<TornadoSwirlEntity> activeTornadoSwirls = new ArrayList<>();
    private static long tornadoStartTime = 0;
    private static boolean tornadoActive = false;
    private static Vec3d tornadoCenter = null;
    private static ServerWorld tornadoWorld = null;

    private static long auraBeamStartTime = 0;
    private static boolean auraBeamsActive = false;
    private static UUID auraBeamPlayer1 = null;
    private static UUID auraBeamPlayer2 = null;

    private static final Map<UUID, Vec3d> smoothTPTarget = new HashMap<>();
    private static final Map<UUID, Integer> smoothTPProgress = new HashMap<>();

    private static final Map<UUID, net.minecraft.entity.decoration.ArmorStandEntity> fireDapArmorStands = new HashMap<>();

    private static final Map<UUID, net.minecraft.entity.decoration.ArmorStandEntity> perfectDapArmorStands = new HashMap<>();

    private record FireDapScheduledEvent(ServerPlayerEntity p1, ServerPlayerEntity p2, long executeTime) {}

    private static final Map<UUID, FireDapScheduledEvent> pendingFireArmImpacts = new HashMap<>();
    private static final Map<UUID, FireDapScheduledEvent> pendingFireTornadoSpawns = new HashMap<>();

    private static class SaturnRing {
        final Vec3d center;
        final long startTime;
        final long endTime;

        SaturnRing(Vec3d center, long startTime) {
            this.center = center;
            this.startTime = startTime;
            this.endTime = startTime + 20000;
        }
    }

    private static final List<SaturnRing> activeSaturnRings = new ArrayList<>();

    // private static final Map<UUID, Long> impactFreezeEnd = new HashMap<>();
    // private static final long IMPACT_FREEZE_MS = 90;

    // public static void freezeOnImpact(UUID playerId) {
    //     long now = System.currentTimeMillis();
    //     impactFreezeEnd.put(playerId, now + IMPACT_FREEZE_MS);
    // }

    // public static boolean isInImpactFreeze(UUID playerId) {
    //     Long freezeEnd = impactFreezeEnd.get(playerId);
    //     if (freezeEnd == null)
    //         return false;

    //     if (System.currentTimeMillis() >= freezeEnd) {
    //         impactFreezeEnd.remove(playerId);
    //         return false;
    //     }
    //     return true;
    // }

    public static final Map<UUID, Long> blockingAnimEndTime = new HashMap<>();

    private static final List<ScheduledParticles> scheduledParticles = new ArrayList<>();

    private static final Set<UUID> highFivePartners = new HashSet<>();

    private static final Map<UUID, Long> perfectFriendshipLevitation = new HashMap<>();
    private static final Map<UUID, UUID> perfectFriendshipPartner = new HashMap<>();

    private record ScheduledParticles(ServerWorld world, double x, double y, double z, long spawnTime) {
    }

    private record ScheduledPerfectDapEffect(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
                                             long effectTime) {
    }

    private static final List<ScheduledPerfectDapEffect> scheduledPerfectDapEffects = new ArrayList<>();

    public static final Identifier CHARGE_START_ID = Identifier.of("cooptest", "charged_dap_start");
    public static final Identifier CHARGE_RELEASE_ID = Identifier.of("cooptest", "charged_dap_release");
    public static final Identifier CHARGE_SYNC_ID = Identifier.of("cooptest", "charged_dap_sync");
    public static final Identifier DAP_RESULT_ID = Identifier.of("cooptest", "charged_dap_result");
    public static final Identifier WHIFF_COOLDOWN_ID = Identifier.of("cooptest", "whiff_cooldown");
    public static final Identifier IMPACT_FRAME_ID = Identifier.of("cooptest", "impact_frame");

    public record ChargeStartPayload() implements CustomPayload {
        public static final Id<ChargeStartPayload> ID = new Id<>(CHARGE_START_ID);
        public static final PacketCodec<PacketByteBuf, ChargeStartPayload> CODEC = PacketCodec
                .unit(new ChargeStartPayload());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ChargeReleasePayload() implements CustomPayload {
        public static final Id<ChargeReleasePayload> ID = new Id<>(CHARGE_RELEASE_ID);
        public static final PacketCodec<PacketByteBuf, ChargeReleasePayload> CODEC = PacketCodec
                .unit(new ChargeReleasePayload());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record WhiffCooldownPayload(long cooldownDurationMs) implements CustomPayload {
        public static final Id<WhiffCooldownPayload> ID = new Id<>(WHIFF_COOLDOWN_ID);
        public static final PacketCodec<PacketByteBuf, WhiffCooldownPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeLong(payload.cooldownDurationMs),
                buf -> new WhiffCooldownPayload(buf.readLong()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ImpactFramePayload(int durationMs, boolean grayscale) implements CustomPayload {
        public static final Id<ImpactFramePayload> ID = new Id<>(IMPACT_FRAME_ID);
        public static final PacketCodec<PacketByteBuf, ImpactFramePayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeInt(payload.durationMs);
                    buf.writeBoolean(payload.grayscale);
                },
                buf -> new ImpactFramePayload(buf.readInt(), buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static final Identifier PERFECT_DAP_FREEZE_ID = Identifier.of("cooptest", "perfect_dap_freeze");

    public record PerfectDapFreezePayload(boolean frozen) implements CustomPayload {
        public static final Id<PerfectDapFreezePayload> ID = new Id<>(PERFECT_DAP_FREEZE_ID);
        public static final PacketCodec<PacketByteBuf, PerfectDapFreezePayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeBoolean(payload.frozen),
                buf -> new PerfectDapFreezePayload(buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static final Identifier PERFECT_DAP_IMPACT_FRAME_ID = Identifier.of("cooptest", "perfect_dap_impact_frame");
    public static final Identifier FACING_DAP_IMPACT_ID = Identifier.of("cooptest", "facing_dap_impact");

    public record FacingDapImpactPayload() implements CustomPayload {
        public static final Id<FacingDapImpactPayload> ID = new Id<>(FACING_DAP_IMPACT_ID);
        public static final PacketCodec<PacketByteBuf, FacingDapImpactPayload> CODEC = PacketCodec
                .unit(new FacingDapImpactPayload());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PerfectDapImpactFramePayload(int frameIndex) implements CustomPayload {
        public static final Id<PerfectDapImpactFramePayload> ID = new Id<>(PERFECT_DAP_IMPACT_FRAME_ID);
        public static final PacketCodec<PacketByteBuf, PerfectDapImpactFramePayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeInt(payload.frameIndex),
                buf -> new PerfectDapImpactFramePayload(buf.readInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static final Identifier FIRE_DAP_J_PRESS_ID = Identifier.of("cooptest", "fire_dap_j_press");
    public static final Identifier FIRE_DAP_WINDOW_ID = Identifier.of("cooptest", "fire_dap_window");
    public static final Identifier FIRE_DAP_FREEZE_ID = Identifier.of("cooptest", "fire_dap_freeze");
    public static final Identifier FIRE_DAP_FP_ID = Identifier.of("cooptest", "fire_dap_fp");

    public record FireDapJPressPayload() implements CustomPayload {
        public static final Id<FireDapJPressPayload> ID = new Id<>(FIRE_DAP_J_PRESS_ID);
        public static final PacketCodec<PacketByteBuf, FireDapJPressPayload> CODEC = PacketCodec
                .unit(new FireDapJPressPayload());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FireDapWindowPayload() implements CustomPayload {
        public static final Id<FireDapWindowPayload> ID = new Id<>(FIRE_DAP_WINDOW_ID);
        public static final PacketCodec<PacketByteBuf, FireDapWindowPayload> CODEC = PacketCodec
                .unit(new FireDapWindowPayload());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FireDapFreezePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<FireDapFreezePayload> ID = new Id<>(FIRE_DAP_FREEZE_ID);
        public static final PacketCodec<PacketByteBuf, FireDapFreezePayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeBoolean(payload.frozen);
                },
                buf -> new FireDapFreezePayload(buf.readUuid(), buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FireDapFirstPersonPayload(UUID playerId, boolean showBothHands) implements CustomPayload {
        public static final Id<FireDapFirstPersonPayload> ID = new Id<>(FIRE_DAP_FP_ID);
        public static final PacketCodec<PacketByteBuf, FireDapFirstPersonPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeBoolean(payload.showBothHands);
                },
                buf -> new FireDapFirstPersonPayload(buf.readUuid(), buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HeavenReadyPayload(UUID playerId, boolean ready) implements CustomPayload {
        public static final Id<HeavenReadyPayload> ID = new Id<>(Identifier.of("cooptest", "heaven_ready"));
        public static final PacketCodec<PacketByteBuf, HeavenReadyPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeBoolean(payload.ready);
                },
                buf -> new HeavenReadyPayload(buf.readUuid(), buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ChargeSyncPayload(UUID playerId, float chargePercent, float firePercent, boolean isCharging)
            implements CustomPayload {
        public static final Id<ChargeSyncPayload> ID = new Id<>(CHARGE_SYNC_ID);
        public static final PacketCodec<PacketByteBuf, ChargeSyncPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeFloat(payload.chargePercent);
                    buf.writeFloat(payload.firePercent);
                    buf.writeBoolean(payload.isCharging);
                },
                buf -> new ChargeSyncPayload(buf.readUuid(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DapResultPayload(double x, double y, double z, UUID player1, UUID player2,
                                   int tier, boolean perfectHit) implements CustomPayload {
        public static final Id<DapResultPayload> ID = new Id<>(DAP_RESULT_ID);
        public static final PacketCodec<PacketByteBuf, DapResultPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeDouble(payload.x);
                    buf.writeDouble(payload.y);
                    buf.writeDouble(payload.z);
                    buf.writeUuid(payload.player1);
                    buf.writeUuid(payload.player2);
                    buf.writeInt(payload.tier);
                    buf.writeBoolean(payload.perfectHit);
                },
                buf -> new DapResultPayload(
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readUuid(), buf.readUuid(), buf.readInt(), buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static void broadcastHeavenReadyStatus(MinecraftServer server, UUID playerId) {
        HeavenReadyPayload payload = new HeavenReadyPayload(playerId, false);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ChargeStartPayload.ID, ChargeStartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChargeReleasePayload.ID, ChargeReleasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HeavenReadyPayload.ID, HeavenReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargeSyncPayload.ID, ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapResultPayload.ID, DapResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WhiffCooldownPayload.ID, WhiffCooldownPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ImpactFramePayload.ID, ImpactFramePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PerfectDapFreezePayload.ID, PerfectDapFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PerfectDapImpactFramePayload.ID, PerfectDapImpactFramePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FacingDapImpactPayload.ID, FacingDapImpactPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(FireDapJPressPayload.ID, FireDapJPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FireDapWindowPayload.ID, FireDapWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FireDapFreezePayload.ID, FireDapFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FireDapFirstPersonPayload.ID, FireDapFirstPersonPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(QTEButtonPressPayload.ID, QTEButtonPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QTEWindowPayload.ID, QTEWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QTEClearPayload.ID, QTEClearPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ChargeStartPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {

                if (!CoopMovesConfig.get().enableDap) {
                    return;
                }
                onChargeStart(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ChargeReleasePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {

                if (!CoopMovesConfig.get().enableDap) {
                    return;
                }
                onChargeRelease(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(FireDapJPressPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onFireDapJPress(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(QTEButtonPressPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String button = payload.button();
            context.server().execute(() -> {

                if (PerfectDapComboHandler.onButtonPress(player, button))
                    return;

                if (DapFusionHandler.onQTEButtonPress(player, button)) {
                    return;
                }

                if (HighFiveQTEHugHandler.onButtonPress(player, button)) {
                    return;
                }

                if (HuddleHandler.onButtonPress(player, button)) {
                    return;
                }

                if (DapComboChain.onButtonPress(player, button)) {
                    return;
                }
                QTEManager.onButtonPress(player, button);
            });
        });

        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> {
                    if (world.isClient)
                        return net.minecraft.util.ActionResult.PASS;
                    if (!(player instanceof ServerPlayerEntity sp))
                        return net.minecraft.util.ActionResult.PASS;
                    if (!(entity instanceof ServerPlayerEntity target))
                        return net.minecraft.util.ActionResult.PASS;
                    if (sp.isSneaking())
                        return net.minecraft.util.ActionResult.PASS;
                    if (getChargePercent(sp.getUuid()) < 0.95f)
                        return net.minecraft.util.ActionResult.PASS;

                    if (getChargePercent(target.getUuid()) < 0.95f) {

                        NormalFacingDapHandler.recordRightClick(sp, target);
                        return net.minecraft.util.ActionResult.PASS;
                    }

                    if (NormalFacingDapHandler.isConfirmed(sp.getUuid(), target.getUuid())
                            || NormalFacingDapHandler.isConfirmedOneSide(target.getUuid(), sp.getUuid())) {

                        NormalFacingDapHandler.clearConfirm(sp.getUuid(), target.getUuid());
                        chargeStartTime.remove(sp.getUuid());
                        chargeStartTime.remove(target.getUuid());
                        broadcastChargeCancel(sp);
                        broadcastChargeCancel(target);
                        NormalFacingDapHandler.start(sp, target);
                    } else {
                        NormalFacingDapHandler.recordRightClick(sp, target);
                    }
                    return net.minecraft.util.ActionResult.PASS;
                });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            Iterator<ScheduledParticles> particleIt = scheduledParticles.iterator();
            while (particleIt.hasNext()) {
                ScheduledParticles sp = particleIt.next();
                if (now >= sp.spawnTime()) {
                    sp.world().spawnParticles(ParticleTypes.CRIT, sp.x(), sp.y(), sp.z(), 15, 0.3, 0.3, 0.3, 0.15);
                    sp.world().spawnParticles(ParticleTypes.ENCHANT, sp.x(), sp.y(), sp.z(), 10, 0.2, 0.2, 0.2, 0.1);
                    particleIt.remove();
                }
            }

            Iterator<ScheduledPerfectDapEffect> effectIt = scheduledPerfectDapEffects.iterator();
            while (effectIt.hasNext()) {
                ScheduledPerfectDapEffect effect = effectIt.next();
                if (now >= effect.effectTime()) {

                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands
                            .get(effect.p1().getUuid());
                    Vec3d pos;
                    if (stand != null && !stand.isRemoved()) {
                        pos = stand.getPos();
                    } else {
                        pos = effect.pos();
                    }
                    ServerWorld world = effect.world();

                    ServerPlayNetworking.send(effect.p1(), new PerfectDapImpactFramePayload(1));
                    ServerPlayNetworking.send(effect.p2(), new PerfectDapImpactFramePayload(1));

                    world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0);
                    world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 40, 0.4, 0.4, 0.4, 0.12);
                    world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 50, 0.5, 0.5, 0.5, 0.15);

                    world.playSound(null, pos.x, pos.y, pos.z,
                            ModSounds.DAP_HIT, SoundCategory.PLAYERS, 1.5f, 1.0f);
                    world.playSound(null, pos.x, pos.y, pos.z,
                            ModSounds.IMPACT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    world.playSound(null, pos.x, pos.y, pos.z,
                            SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2f, 1.0f);

                    if (!CoopMovesConfig.get().noGriefMode) {
                        // createExplosion(world, pos, effect.p1(), effect.p2(), 3.5, 6.0f);
                        createShockwave(world, pos, effect.p1(), effect.p2());
                    }

                    handleUnderwaterPerfectDap(world, pos, effect.p1(), effect.p2());

                    effectIt.remove();
                }
            }

            Iterator<Map.Entry<UUID, Long>> underwaterIt = underwaterRemovalStart.entrySet().iterator();
            while (underwaterIt.hasNext()) {
                Map.Entry<UUID, Long> entry = underwaterIt.next();
                UUID trackId = entry.getKey();
                long startTime = entry.getValue();
                long elapsed = now - startTime;

                if (elapsed >= 5000) {
                    underwaterIt.remove();
                    underwaterRemovalPos.remove(trackId);
                    underwaterRemovalWorld.remove(trackId);
                    continue;
                }

                Vec3d pos = underwaterRemovalPos.get(trackId);
                ServerWorld world = underwaterRemovalWorld.get(trackId);
                if (pos == null || world == null)
                    continue;

                BlockPos centerPos = BlockPos.ofFloored(pos);
                double radius = 3.0;
                int radiusInt = (int) Math.ceil(radius);

                for (int x = -radiusInt; x <= radiusInt; x++) {
                    for (int y = -radiusInt; y <= radiusInt; y++) {
                        for (int z = -radiusInt; z <= radiusInt; z++) {
                            double distance = Math.sqrt(x * x + y * y + z * z);
                            if (distance <= radius) {
                                BlockPos blockPos = centerPos.add(x, y, z);
                                if (world.getBlockState(blockPos).isOf(net.minecraft.block.Blocks.WATER)) {
                                    world.setBlockState(blockPos, net.minecraft.block.Blocks.AIR.getDefaultState());
                                }
                            }
                        }
                    }
                }
            }

            Iterator<HeavenParticleSpawner> heavenParticleIt = activeHeavenParticles.iterator();
            while (heavenParticleIt.hasNext()) {
                HeavenParticleSpawner spawner = heavenParticleIt.next();

                if (now >= spawner.endTime) {

                    heavenParticleIt.remove();
                    continue;
                }

                ServerWorld world = spawner.world;
                Vec3d pos = spawner.pos;

                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 3, 3, 3, 0);
                world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 10, 5, 5, 5, 0.3);
                world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, 4, 4, 4, 0.2);
                world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }

            Iterator<Map.Entry<UUID, Long>> perfectDapIt = perfectDapStartTime.entrySet().iterator();
            while (perfectDapIt.hasNext()) {
                Map.Entry<UUID, Long> entry = perfectDapIt.next();
                UUID playerId = entry.getKey();
                long startTime = entry.getValue();
                long elapsed = now - startTime;

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {

                    perfectDapIt.remove();
                    perfectDapPartner.remove(playerId);
                    perfectDapFreezeEnd.remove(playerId);
                    perfectDapImpactSent.remove(playerId);

                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands.remove(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        stand.discard();
                    }
                    continue;
                }

                UUID partnerId = perfectDapPartner.get(playerId);
                ServerPlayerEntity partner = partnerId != null ? server.getPlayerManager().getPlayer(partnerId) : null;

                if (partner != null) {
                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands.get(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        Vec3d p1Hand = player.getPos().add(0, 1.4, 0);
                        Vec3d p2Hand = partner.getPos().add(0, 1.4, 0);
                        Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);
                        stand.setPosition(handMid.x, handMid.y, handMid.z);
                        stand.setFireTicks(0);
                    }

                    smoothDapDescent(player, stand);
                }

                if (elapsed >= 150 && elapsed <= 1330 && partner != null) {
                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands.get(playerId);
                    if (stand == null || stand.isRemoved())
                        player.getPos().add(partner.getPos()).multiply(0.5).add(0, 1.4, 0);
                }

                if (elapsed >= 290 && elapsed < 310 && partner != null) {
                    if (!perfectDapImpactSent.getOrDefault(playerId, false)) {
                        ServerPlayNetworking.send(player, new PerfectDapImpactFramePayload(1));
                        perfectDapImpactSent.put(playerId, true);
                    }
                }

                if (elapsed >= 812 && perfectDapFreezeEnd.containsKey(playerId)) {
                    perfectDapFreezeEnd.remove(playerId);
                    ServerPlayNetworking.send(player, new PerfectDapFreezePayload(false));

                    if (partnerId != null) {
                        ServerPlayerEntity partner2 = server.getPlayerManager().getPlayer(partnerId);
                        if (partner2 != null) {
                            perfectDapFreezeEnd.remove(partnerId);
                            ServerPlayNetworking.send(partner2, new PerfectDapFreezePayload(false));
                        }
                    }
                }

                if (elapsed >= 1625) {
                    perfectDapIt.remove();
                    perfectDapPartner.remove(playerId);
                    perfectDapFreezeEnd.remove(playerId);
                    perfectDapImpactSent.remove(playerId);

                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands.remove(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        stand.discard();
                    }
                }
            }

            Iterator<Map.Entry<UUID, HeavenDapData>> heavenIt = heavenPlayers.entrySet().iterator();
            while (heavenIt.hasNext()) {
                Map.Entry<UUID, HeavenDapData> entry = heavenIt.next();
                UUID playerId = entry.getKey();
                HeavenDapData data = entry.getValue();
                long elapsed = now - data.startTime;

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {
                    heavenIt.remove();
                    continue;
                }

                // missed timing
                if (elapsed >= 3000 && elapsed <= 9000) {
                    Vec3d pos = player.getPos();

                    data.world.spawnParticles(ParticleTypes.WHITE_ASH,
                            pos.x, pos.y + 1, pos.z,
                            5, 1.0, 1.0, 1.0, 0.02);
                    data.world.spawnParticles(ParticleTypes.CLOUD,
                            pos.x, pos.y, pos.z,
                            3, 0.5, 0.5, 0.5, 0.01);
                }

                // slap
                if (elapsed >= 9500 && elapsed < 9600) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));
                }

                if (elapsed >= 11500) {
                    Vec3d returnPos = data.originalMidpoint;
                    UUID partnerId = data.partnerId;
                    ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);

                    // Found someone to dap with
                    if (partner != null) {
                        double dx = partner.getX() - player.getX();
                        double dz = partner.getZ() - player.getZ();
                        float yawTowardsPartner = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                        float yawAwayFromPartner = yawTowardsPartner + 180;

                        player.teleport(data.world, returnPos.x, returnPos.y, returnPos.z, yawAwayFromPartner, 0);
                        player.stopFallFlying();
                        player.setVelocity(Vec3d.ZERO);
                        player.velocityModified = true;

                        ServerPlayNetworking.send(player, new PerfectDapFreezePayload(false));
                        PoseNetworking.broadcastAnimState(player, 0);

                        player.removeStatusEffect(StatusEffects.NAUSEA);

                        if (heavenPlayers.containsKey(partnerId)) {
                            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                    net.minecraft.entity.effect.StatusEffects.RESISTANCE, 80, 255, false, false));
                            partner.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                    net.minecraft.entity.effect.StatusEffects.RESISTANCE, 80, 255, false, false));
                            // final ServerWorld _rw = data.world;
                            // final Vec3d _rp = returnPos;
                            // for (int i = 0; i < 8; i++) {
                            //     double a = Math.toRadians(i * 45.0);
                            //     for (double dy : new double[] { 0, 20, -20 }) {
                            //         _rw.createExplosion(null,
                            //                 _rp.x + Math.cos(a) * 5, _rp.y + dy,
                            //                 _rp.z + Math.sin(a) * 5, 8f,
                            //                 false, net.minecraft.world.World.ExplosionSourceType.NONE);
                            //     }
                            // }
                            // new Thread(() -> {
                            //     try {
                            //         Thread.sleep(150);
                            //     } catch (InterruptedException ignored) {
                            //     }
                            //     _rw.getServer().execute(() -> {
                            //         for (int i = 0; i < 8; i++) {
                            //             double a = Math.toRadians(i * 45.0 + 22.5);
                            //             for (double dy : new double[] { 0, 20, -20 }) {
                            //                 _rw.createExplosion(null,
                            //                         _rp.x + Math.cos(a) * 25, _rp.y + dy,
                            //                         _rp.z + Math.sin(a) * 25, 20f,
                            //                         true, net.minecraft.world.World.ExplosionSourceType.TNT);
                            //             }
                            //         }
                            //     });
                            // }).start();
                            // new Thread(() -> {
                            //     try {
                            //         Thread.sleep(350);
                            //     } catch (InterruptedException ignored) {
                            //     }
                            //     _rw.getServer().execute(() -> {
                            //         for (int i = 0; i < 8; i++) {
                            //             double a = Math.toRadians(i * 45.0);
                            //             for (double dy : new double[] { 0, 20, -20 }) {
                            //                 _rw.createExplosion(null,
                            //                         _rp.x + Math.cos(a) * 60, _rp.y + dy,
                            //                         _rp.z + Math.sin(a) * 60, 15f,
                            //                         true, net.minecraft.world.World.ExplosionSourceType.TNT);
                            //             }
                            //         }
                            //     });
                            // }).start();
                            // new Thread(() -> {
                            //     try {
                            //         Thread.sleep(600);
                            //     } catch (InterruptedException ignored) {
                            //     }
                            //     _rw.getServer().execute(() -> {
                            //         for (int i = 0; i < 8; i++) {
                            //             double a = Math.toRadians(i * 45.0 + 22.5);
                            //             _rw.createExplosion(null,
                            //                     _rp.x + Math.cos(a) * 100, _rp.y,
                            //                     _rp.z + Math.sin(a) * 100, 12f,
                            //                     true, net.minecraft.world.World.ExplosionSourceType.TNT);
                            //         }
                            //     });
                            // }).start();
                        }

                        data.world.spawnParticles(ParticleTypes.FLASH,
                                returnPos.x, returnPos.y + 1, returnPos.z,
                                5, 0.5, 0.5, 0.5, 0);
                        data.world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                                returnPos.x, returnPos.y, returnPos.z,
                                10, 3, 3, 3, 0.5);

                        final ServerPlayerEntity finalPlayer = player;
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                                Objects.requireNonNull(Objects.requireNonNull(finalPlayer.getServer())).execute(() ->
                                ServerPlayNetworking.send(finalPlayer, new HeavenDapPayloads.RestoreVolumePayload()));
                            } catch (InterruptedException e) {
                                // e.printStackTrace();
                            }
                        }).start();

                        if (heavenPlayers.containsKey(partnerId)) {
                            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                p.sendMessage(net.minecraft.text.Text.literal(
                                                "§d§l✨ " + player.getName().getString() + " §7and §d§l" +
                                                        partner.getName().getString()
                                                        + " §7have achieved §d§lPERFECT FRIENDSHIP! ✨"),
                                        false);
                            }

                            server.getOverworld().playSound(null, returnPos.x, returnPos.y, returnPos.z,
                                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 3.0f, 1.0f);
                            server.getOverworld().playSound(null, returnPos.x, returnPos.y, returnPos.z,
                                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 2.0f, 1.5f);
                        }
                    }

                    heavenIt.remove();
                }
            }

            Iterator<Map.Entry<UUID, Long>> fireDapIt = fireDapStartTime.entrySet().iterator();
            while (fireDapIt.hasNext()) {
                Map.Entry<UUID, Long> entry = fireDapIt.next();
                UUID playerId = entry.getKey();
                long startTime = entry.getValue();
                long elapsed = now - startTime;

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {
                    fireDapIt.remove();
                    inFireDapHit.remove(playerId);
                    fireDapPartner.remove(playerId);
                    continue;
                }

                if (elapsed >= FIRE_IMPACT_TIME && !fireCircleSpawned.getOrDefault(playerId, true)
                        && inFireDapHit.getOrDefault(playerId, false)) {
                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null && fireDapStartTime.containsKey(partnerId)) {
                        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                        if (partner != null && !fireCircleSpawned.getOrDefault(partnerId, true)) {
                            spawnFireCircle(player, partner);

                            long freezeEnd = now + (FIRE_DAP_HIT_LENGTH - elapsed);
                            fireDapComboFreezeEnd.put(playerId, freezeEnd);
                            fireDapComboFreezeEnd.put(partnerId, freezeEnd);

                            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                ServerPlayNetworking.send(p, new FireDapFreezePayload(playerId, true));
                                ServerPlayNetworking.send(p, new FireDapFreezePayload(partnerId, true));
                            }

                            fireCircleSpawned.put(playerId, true);
                            fireCircleSpawned.put(partnerId, true);
                        }
                    }
                }

                if (elapsed >= FIRE_IMPACT_TIME && inFireDapHit.getOrDefault(playerId, false)) {
                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null) {
                        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                        if (partner != null) {

                            net.minecraft.entity.decoration.ArmorStandEntity stand = fireDapArmorStands.get(playerId);
                            if (stand != null && !stand.isRemoved()) {
                                Vec3d p1Hand = player.getPos().add(0, 1.4, 0);
                                Vec3d p2Hand = partner.getPos().add(0, 1.4, 0);
                                Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);
                                stand.setPosition(handMid.x, handMid.y, handMid.z);
                                stand.setFireTicks(0);

                                smoothDapDescent(player, stand);
                                smoothDapDescent(partner, stand);
                            }
                        }
                    }
                }

                if (elapsed >= FIRE_DAP_HIT_LENGTH && inFireDapHit.getOrDefault(playerId, false)) {
                    Long jpressTime = fireDapComboRequestTime.get(playerId);
                    if (jpressTime != null && (now - jpressTime) < 2000) {
                        if (now - jpressTime >= 1000) {
                            UUID partnerId = fireDapPartner.get(playerId);
                            if (partnerId != null) {
                                ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                                if (partner != null) {
                                    partner.sendMessage(net.minecraft.text.Text
                                                    .literal("§c✗ You missed the combo! " + player.getName().getString()
                                                            + " pressed J!"),
                                            true);
                                    player.sendMessage(
                                            net.minecraft.text.Text.literal(
                                                    "§c✗ " + partner.getName().getString() + " missed the combo!"),
                                            true);
                                }
                            }
                        } else {
                            continue;
                        }
                    }

                    fireDapIt.remove();
                    inFireDapHit.remove(playerId);
                    fireDapComboRequestTime.remove(playerId);

                    DapSessionManager.removeSessionForPlayer(playerId);

                    if (fireDapComboFreezeEnd.containsKey(playerId)) {
                        fireDapComboFreezeEnd.remove(playerId);
                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                            ServerPlayNetworking.send(p, new FireDapFreezePayload(playerId, false));
                        }
                        PoseNetworking.broadcastAnimState(player, 0);
                    }
                }
            }

            for (Map.Entry<UUID, Long> entry : new HashMap<>(fireDapComboFreezeEnd).entrySet()) {
                UUID playerId = entry.getKey();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null && !inFireDapHit.getOrDefault(playerId, false)) {
                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null) {
                        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                        if (partner != null) {
                            teleportFireDapFacingEachOther(player, partner);
                        }
                    }
                }
            }

            Iterator<Map.Entry<UUID, FireDapScheduledEvent>> armIt = pendingFireArmImpacts.entrySet().iterator();
            while (armIt.hasNext()) {
                Map.Entry<UUID, FireDapScheduledEvent> entry = armIt.next();
                FireDapScheduledEvent event = entry.getValue();
                if (now >= event.executeTime) {
                    executeFireArmImpact(event.p1, event.p2);
                    armIt.remove();
                }
            }

            Iterator<Map.Entry<UUID, FireDapScheduledEvent>> tornadoIt = pendingFireTornadoSpawns.entrySet().iterator();
            while (tornadoIt.hasNext()) {
                Map.Entry<UUID, FireDapScheduledEvent> entry = tornadoIt.next();
                FireDapScheduledEvent event = entry.getValue();
                if (now >= event.executeTime) {
                    spawnFireTornado(event.p1, event.p2);
                    tornadoIt.remove();
                }
            }

            if (auraBeamsActive && server != null) {
                long elapsed = now - auraBeamStartTime;

                if (elapsed < 4000) {
                    ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(auraBeamPlayer1);
                    ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(auraBeamPlayer2);

                    if (p1 != null && p2 != null) {
                        spawnAnimatedAuraBeam(p1, elapsed);
                        spawnAnimatedAuraBeam(p2, elapsed);
                    }
                } else {
                    auraBeamsActive = false;
                }
            }

            Iterator<Map.Entry<UUID, Vec3d>> tpIt = smoothTPTarget.entrySet().iterator();
            while (tpIt.hasNext()) {
                Map.Entry<UUID, Vec3d> entry = tpIt.next();
                UUID playerId = entry.getKey();
                Vec3d target = entry.getValue();

                assert server != null;
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {
                    tpIt.remove();
                    smoothTPProgress.remove(playerId);
                    continue;
                }

                int progress = smoothTPProgress.getOrDefault(playerId, 0);

                if (progress < 10) {
                    Vec3d current = player.getPos();
                    double t = (progress + 1) / 10.0;

                    Vec3d newPos = new Vec3d(
                            current.x + (target.x - current.x) * t,
                            current.y + (target.y - current.y) * t,
                            current.z + (target.z - current.z) * t);

                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null) {
                        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                        if (partner != null) {
                            double dx = partner.getPos().x - newPos.x;
                            double dz = partner.getPos().z - newPos.z;
                            float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;

                            player.teleport(player.getServerWorld(), newPos.x, newPos.y, newPos.z, yaw,
                                    player.getPitch());
                        }
                    }

                    smoothTPProgress.put(playerId, progress + 1);
                } else {
                    tpIt.remove();
                    smoothTPProgress.remove(playerId);
                }
            }

            if (tornadoActive && tornadoCenter != null && tornadoWorld != null) {
                long elapsed = now - tornadoStartTime;

                if (elapsed < 5000) {

                    if (elapsed % 40 == 0) {

                        for (int i = 0; i < 12; i++) {
                            double startAngle = i * 30;
                            double startRadius = 3.0 + (Math.random() * 2);

                            TornadoSwirlEntity swirl = new TornadoSwirlEntity(tornadoWorld, tornadoCenter, startAngle,
                                    startRadius);
                            activeTornadoSwirls.add(swirl);
                        }
                    }
                } else {

                    tornadoActive = false;
                    activeTornadoSwirls.clear();
                }

                Iterator<TornadoSwirlEntity> swirlIt = activeTornadoSwirls.iterator();
                while (swirlIt.hasNext()) {
                    TornadoSwirlEntity swirl = swirlIt.next();
                    boolean stillAlive = swirl.tick();
                    if (!stillAlive) {
                        swirlIt.remove();
                    }
                }

                if (tornadoCenter != null && tornadoWorld != null) {
                    double tornadoRadius = 30;

                    UUID shieldPlayer1 = auraBeamPlayer1;
                    UUID shieldPlayer2 = auraBeamPlayer2;

                    Box searchBox = new Box(
                            tornadoCenter.x - tornadoRadius, tornadoCenter.y, tornadoCenter.z - tornadoRadius,
                            tornadoCenter.x + tornadoRadius, tornadoCenter.y + 70, tornadoCenter.z + tornadoRadius);

                    for (Entity entity : tornadoWorld.getOtherEntities(null, searchBox)) {

                        if (entity.getUuid().equals(shieldPlayer1) || entity.getUuid().equals(shieldPlayer2)) {
                            continue;
                        }

                        Vec3d entityPos = entity.getPos();
                        double dx = entityPos.x - tornadoCenter.x;
                        double dz = entityPos.z - tornadoCenter.z;
                        double distanceToCenter = Math.sqrt(dx * dx + dz * dz);

                        if (distanceToCenter >= tornadoRadius - 2 && distanceToCenter <= tornadoRadius + 2) {

                            Vec3d direction = new Vec3d(dx, 0, dz).normalize();

                            entity.setVelocity(
                                    direction.x * 2.0,
                                    0.5,
                                    direction.z * 2.0);
                            entity.velocityModified = true;

                            if (entity instanceof net.minecraft.entity.LivingEntity living) {
                                living.damage(living.getDamageSources().magic(), 5.0f);
                            }
                        }

                        if (entity instanceof net.minecraft.entity.projectile.ProjectileEntity) {

                            if (distanceToCenter >= tornadoRadius - 3) {
                                entity.discard();
                            }
                        }
                    }
                }
            }

            Iterator<Map.Entry<UUID, Long>> freezeIt = fireDapComboFreezeEnd.entrySet().iterator();
            while (freezeIt.hasNext()) {
                Map.Entry<UUID, Long> entry = freezeIt.next();
                if (now >= entry.getValue()) {
                    UUID playerId = entry.getKey();

                    assert server != null;
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(player, new FireDapFreezePayload(playerId, false));
                    }

                    freezeIt.remove();
                    fireDapPartner.remove(playerId);

                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                    if (player != null) {
                        PoseNetworking.broadcastAnimState(player, 0);
                    }

                }
            }

            Iterator<SaturnRing> ringIt = activeSaturnRings.iterator();
            while (ringIt.hasNext()) {
                SaturnRing ring = ringIt.next();
                if (now >= ring.endTime) {
                    ringIt.remove();
                    continue;
                }

                long elapsed = now - ring.startTime;
                double rotationAngle = (elapsed / 100.0) * 360.0;
                double radius = 50.0;

                assert server != null;
                ServerWorld world = server.getOverworld();

                for (double angle = 0; angle < 360; angle += 5) {
                    double rad = Math.toRadians(angle + rotationAngle);
                    double x = ring.center.x + Math.cos(rad) * radius;
                    double z = ring.center.z + Math.sin(rad) * radius;
                    double y = ring.center.y;

                    world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.1, 0.1, 0.1, 0.01);
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, 1, 0.05, 0.05, 0.05, 0);
                }
            }

            assert server != null;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();

                if (impactFreezeTicks.containsKey(id)) {
                    int remaining = impactFreezeTicks.get(id);
                    if (remaining > 0) {
                        player.setVelocity(0, Math.min(0, player.getVelocity().y), 0);
                        player.velocityModified = true;
                        impactFreezeTicks.put(id, remaining - 1);
                    } else {
                        impactFreezeTicks.remove(id);
                    }
                }
                Vec3d velocity = getEffectiveVelocity(player);
                double speed = velocity.length() * 20.0;

                LinkedList<Double> history = speedHistory.computeIfAbsent(id, k -> new LinkedList<>());
                history.addLast(speed);
                while (history.size() > SPEED_HISTORY_TICKS) {
                    history.removeFirst();
                }

                // Charging handler
                if (chargeStartTime.containsKey(id)) {
                    float charge = getChargePercent(id);
                    double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
                    if (charge >= 0.99f) {
                        boolean isMoving = horizontalSpeed >= MIN_MOVEMENT_SPEED;
                        if (isMoving) {
                            fireGraceTime.remove(id);

                            if (!CoopMovesConfig.get().enableFireDap) {
                                fireStartTime.remove(id);
                                fireLevel.put(id, 0f);
                            } else {
                                if (!fireStartTime.containsKey(id)) {
                                    fireStartTime.put(id, now);
                                }
                                long timeAtFullCharge = now - fireStartTime.get(id);

                                if (timeAtFullCharge >= fireDelayMs()) {
                                    long fireBuildTime = timeAtFullCharge - fireDelayMs();
                                    float fire = Math.min(1.0f, (float) fireBuildTime / fireBuildTimeMs());
                                    fireLevel.put(id, fire);

                                    // fire particles around the arm while running
                                    spawnFireHandParticles(player, fire);

                                    if (fire >= 0.99f) {
                                        if (!fireMaxedStartTime.containsKey(id)) {
                                            fireMaxedStartTime.put(id, now);
                                        }
                                        long timeAtMaxFire = now - fireMaxedStartTime.get(id);
                                        if (timeAtMaxFire >= HEAVEN_READY_TIME_MS && !heavenReady.contains(id)) {
                                            heavenReady.add(id);

                                            player.getServerWorld().playSound(null, player.getX(), player.getY(),
                                                    player.getZ(),
                                                    net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK,
                                                    net.minecraft.sound.SoundCategory.PLAYERS,
                                                    1.0f, 0.8f);
                                            player.sendMessage(
                                                    net.minecraft.text.Text
                                                            .literal("§d§l✨ HEAVEN READY! ✨ §7(Fire UI broken!)"),
                                                    true);

                                            HeavenReadyPayload payload = new HeavenReadyPayload(id, true);
                                            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                                ServerPlayNetworking.send(p, payload);
                                            }
                                        }
                                    } else {
                                        fireMaxedStartTime.remove(id);
                                    }
                                } else {
                                    fireLevel.put(id, 0f);
                                }
                            }
                        } else {
                            if (!fireGraceTime.containsKey(id)) {
                                fireGraceTime.put(id, now);
                            }

                            long graceDuration = now - fireGraceTime.get(id);
                            if (graceDuration > FIRE_GRACE_PERIOD_MS) {
                                fireStartTime.remove(id);
                                fireLevel.put(id, 0f);
                                fireMaxedStartTime.remove(id);

                                if (heavenReady.remove(id)) {
                                    broadcastHeavenReadyStatus(server, id);
                                }
                            }
                        }
                    } else {
                        fireStartTime.remove(id);
                        fireGraceTime.remove(id);
                        fireLevel.put(id, 0f);
                        fireMaxedStartTime.remove(id);

                        if (heavenReady.remove(id)) {
                            broadcastHeavenReadyStatus(server, id);
                        }
                    }
                } else {
                    fireStartTime.remove(id);
                    fireGraceTime.remove(id);
                    fireLevel.remove(id);
                    fireMaxedStartTime.remove(id);

                    if (heavenReady.remove(id)) {
                        broadcastHeavenReadyStatus(server, id);
                    }
                }
            }

            Iterator<Map.Entry<UUID, UUID>> it = waitingForPartner.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, UUID> entry = it.next();
                UUID waiterId = entry.getKey();
                Long releaseT = releaseTime.get(waiterId);

                if (releaseT != null && now - releaseT > releaseWindowMs()) {
                    ServerPlayerEntity waiter = server.getPlayerManager().getPlayer(waiterId);
                    ServerPlayerEntity partner = server.getPlayerManager().getPlayer(entry.getValue());

                    if (waiter != null && partner != null) {
                        executeFizzle(waiter, partner);
                    }

                    it.remove();
                    releaseTime.remove(waiterId);
                    chargeStartTime.remove(waiterId);
                    chargeStartTime.remove(entry.getValue());
                    broadcastChargeCancel(waiter);
                    if (partner != null)
                        broadcastChargeCancel(partner);
                }
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!chargeStartTime.containsKey(player.getUuid()))
                    continue;

                float charge = getChargePercent(player.getUuid());
                float fire = fireLevel.getOrDefault(player.getUuid(), 0f);
                ChargeSyncPayload syncPayload = new ChargeSyncPayload(player.getUuid(), charge, fire, true);

                for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
                    ServerPlayNetworking.send(other, syncPayload);
                }
                ServerPlayNetworking.send(player, syncPayload);
            }
        });
    }

    private static void spawnFireHandParticles(ServerPlayerEntity player, float fireLevel) {

        if (Math.random() > 0.33)
            return;

        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();

        float yaw = player.getYaw();
        double yawRad = Math.toRadians(yaw);

        double rightX = -Math.cos(yawRad) * 0.4;
        double rightZ = -Math.sin(yawRad) * 0.4;

        double handX = pos.x + rightX;
        double handY = pos.y + 1.3;
        double handZ = pos.z + rightZ;

        world.spawnParticles(ParticleTypes.FLAME, handX, handY, handZ, 1, 0.06, 0.06, 0.06, 0.005);
        if (fireLevel > 0.6f) {
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, handX, handY, handZ, 1, 0.05, 0.05, 0.05, 0.003);
        }
    }

    private static void onChargeStart(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (HighFiveHandler.hasHandRaised(uuid)) {

            broadcastChargeCancel(player);
            return;
        }
        if (HighFiveHandler.isInBlockingAnimation(uuid)) {

            broadcastChargeCancel(player);
            return;
        }

        if (isInComboCooldown(uuid)) {
            player.sendMessage(net.minecraft.text.Text.literal("§cWait 1 second after combo!"), true);
            broadcastChargeCancel(player);
            return;
        }

        if (FallCatchHandler.isInCatchReadyMode(uuid))
            return;

        if (isOnCooldown(uuid))
            return;

        if (!player.getMainHandStack().isEmpty())
            return;

        chargeStartTime.put(uuid, System.currentTimeMillis());
        fireLevel.put(uuid, 0f);

        ChargeSyncPayload payload = new ChargeSyncPayload(uuid, 0f, 0f, true);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(other, payload);
        }

        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.5f, 0.8f);
    }

    private static void onChargeRelease(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (!chargeStartTime.containsKey(uuid))
            return;

        long now = System.currentTimeMillis();
        float myCharge = getChargePercent(uuid);
        float myFire = fireLevel.getOrDefault(uuid, 0f);

        if (myCharge >= 0.95f) {
            boolean partnerAlsoCharging = chargeStartTime.keySet().stream()
                    .filter(uid -> !uid.equals(uuid))
                    .anyMatch(uid -> {
                        ServerPlayerEntity nearby = player.getServerWorld().getPlayers().stream()
                                .filter(p -> p.getUuid().equals(uid)
                                        && player.distanceTo(p) < 2.5f)
                                .findFirst().orElse(null);
                        return nearby != null;
                    });
            if (!partnerAlsoCharging && SlapHandler.checkSlapOnRelease(player)) {
                chargeStartTime.remove(uuid);
                fireLevel.remove(uuid);
                fireStartTime.remove(uuid);
                MinecraftServer server = player.getServer();
                if (server != null && heavenReady.remove(uuid)) broadcastHeavenReadyStatus(server, uuid);
                broadcastChargeCancel(player);
                return;
            }
        }

        java.util.List<ServerPlayerEntity> allPartners = findAllDapPartners(player);
        if (allPartners.size() >= 2) {
            ServerPlayerEntity tp2 = allPartners.get(0);
            ServerPlayerEntity tp3 = allPartners.get(1);

            Long t2 = chargeStartTime.get(tp2.getUuid());
            Long t3 = chargeStartTime.get(tp3.getUuid());
            if (t2 != null && t3 != null) {

                long maxDiff = Math.max(Math.abs(now - t2), Math.max(Math.abs(now - t3), Math.abs(t2 - t3)));
                if (maxDiff <= releaseWindowMs() * 2) {
                    executeTripleDap(player, tp2, tp3);
                    for (ServerPlayerEntity tp : new ServerPlayerEntity[] { player, tp2, tp3 }) {
                        UUID tid = tp.getUuid();
                        chargeStartTime.remove(tid);
                        fireLevel.remove(tid);
                        fireStartTime.remove(tid);
                        MinecraftServer server = tp.getServer();
                        if (server != null && heavenReady.remove(tid)) broadcastHeavenReadyStatus(server, tid);
                        broadcastChargeCancel(tp);
                    }
                    return;
                }
            }
        }
        ServerPlayerEntity partner = findAnyDapPartner(player);

        if (partner == null) {
            executeWhiff(player);
            chargeStartTime.remove(uuid);
            fireLevel.remove(uuid);
            fireStartTime.remove(uuid);

            MinecraftServer server = player.getServer();
            if (server != null && heavenReady.remove(uuid)) broadcastHeavenReadyStatus(server, uuid);
            broadcastChargeCancel(player);

            long cooldownEnd = now + whiffCooldownMs();
            cooldowns.put(uuid, cooldownEnd);

            broadcastWhiffCooldown(player);

            player.sendMessage(net.minecraft.text.Text.literal("§c✗ Whiff! 0.8s cooldown"), true);
            return;
        }
        UUID partnerId = partner.getUuid();

        if (HighFiveHandler.hasHandRaised(partnerId) && !chargeStartTime.containsKey(partnerId)) {
            if (DapHoldHandler.tryDetect(player, partner)) {
                chargeStartTime.remove(uuid);
                fireLevel.remove(uuid);
                fireStartTime.remove(uuid);

                MinecraftServer server = player.getServer();
                if (server != null && heavenReady.remove(uuid)) broadcastHeavenReadyStatus(server, uuid);
                broadcastChargeCancel(player);
                return;
            }
            highFivePartners.add(partnerId);
            executeDap(player, partner, myCharge, myCharge, myFire, myFire, now, now);
            highFivePartners.remove(partnerId);

            HighFiveHandler.handRaisedTime.remove(partnerId);
            HighFiveHandler.startAnimTime.remove(partnerId);
            HighFiveHandler.highFiveCooldown.put(partnerId, now);
            HighFiveHandler.syncHandRaised(partner, false);

            chargeStartTime.remove(uuid);
            fireLevel.remove(uuid);
            fireStartTime.remove(uuid);

            MinecraftServer server = player.getServer();
            if (server != null && heavenReady.remove(uuid)) {
                broadcastHeavenReadyStatus(server, uuid);
            }

            broadcastChargeCancel(player);
            return;
        }

        if (waitingForPartner.containsKey(partnerId) && waitingForPartner.get(partnerId).equals(uuid)) {
            long partnerReleaseTime = releaseTime.get(partnerId);
            float partnerCharge = getChargePercent(partnerId);
            float partnerFire = fireLevel.getOrDefault(partnerId, 0f);

            executeDap(player, partner, myCharge, partnerCharge, myFire, partnerFire, partnerReleaseTime, now);

            waitingForPartner.remove(partnerId);
            releaseTime.remove(partnerId);
            chargeStartTime.remove(uuid);
            chargeStartTime.remove(partnerId);
            fireLevel.remove(uuid);
            fireLevel.remove(partnerId);
            fireStartTime.remove(uuid);
            fireStartTime.remove(partnerId);

            MinecraftServer server = player.getServer();
            if (server != null && heavenReady.remove(uuid)) {
                broadcastHeavenReadyStatus(server, uuid);
            }
            if (server != null && heavenReady.remove(partnerId)) {
                broadcastHeavenReadyStatus(server, partnerId);
            }

            broadcastChargeCancel(player);
            broadcastChargeCancel(partner);

        } else {
            releaseTime.put(uuid, now);
            waitingForPartner.put(uuid, partnerId);

            player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.7f, 1.2f);
        }
    }

    private static ServerPlayerEntity findAnyDapPartner(ServerPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(DAP_RANGE);
        Vec3d look = player.getRotationVec(1.0f);

        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other == player)
                continue;
            if (isOnCooldown(other.getUuid()))
                continue;

            boolean isReady = chargeStartTime.containsKey(other.getUuid()) ||
                    HighFiveHandler.hasHandRaised(other.getUuid());

            if (!isReady)
                continue;
            if (!searchBox.intersects(other.getBoundingBox()))
                continue;

            Vec3d toOther = other.getPos().subtract(player.getPos()).normalize();
            if (look.dotProduct(toOther) <= 0.0)
                continue;

            return other;
        }
        return null;
    }

    private static java.util.List<ServerPlayerEntity> findAllDapPartners(ServerPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(DAP_RANGE);
        java.util.List<ServerPlayerEntity> result = new java.util.ArrayList<>();
        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other == player)
                continue;
            if (isOnCooldown(other.getUuid()))
                continue;
            if (!chargeStartTime.containsKey(other.getUuid()))
                continue;
            if (searchBox.intersects(other.getBoundingBox()))
                result.add(other);
            if (result.size() >= 2)
                break;
        }
        return result;
    }

    private static void executeTripleDap(ServerPlayerEntity p1, ServerPlayerEntity p2, ServerPlayerEntity p3) {
        ServerWorld world = p1.getServerWorld();
        long now = System.currentTimeMillis();

        Vec3d center = p1.getPos().add(p2.getPos()).add(p3.getPos()).multiply(1.0 / 3.0);
        net.minecraft.entity.decoration.ArmorStandEntity stand = new net.minecraft.entity.decoration.ArmorStandEntity(
                world,
                center.x, center.y, center.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        world.spawnEntity(stand);

        double radius = 0.7;
        ServerPlayerEntity[] trio = { p1, p2, p3 };
        for (int i = 0; i < 3; i++) {

            double angle = Math.PI * 2 * i / 3;
            double px = center.x + radius * Math.cos(angle);
            double pz = center.z + radius * Math.sin(angle);

            float yaw = (float) (-Math.toDegrees(Math.atan2(center.x - px, center.z - pz)));
            trio[i].teleport(world, px, p1.getY(), pz, java.util.Set.of(), yaw, 0);
            trio[i].setYaw(yaw);
            trio[i].setBodyYaw(yaw);
            trio[i].setHeadYaw(yaw);
            trio[i].prevBodyYaw = yaw;
            trio[i].swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }

        stand.discard();

        for (ServerPlayerEntity p : trio)
            cooldowns.put(p.getUuid(), now + cooldownMs());

        for (ServerPlayerEntity p : trio) {
            PoseNetworking.broadcastAnimState(p,
                    com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
        }

        Vec3d cTop = center.add(0, 1.4, 0);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                cTop.x, cTop.y, cTop.z, 40, 0.4, 0.4, 0.4, 0.15);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH,
                cTop.x, cTop.y, cTop.z, 3, 0.1, 0.1, 0.1, 0);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING,
                cTop.x, cTop.y, cTop.z, 15, 0.5, 0.5, 0.5, 0.2);

        world.playSound(null, cTop.x, cTop.y, cTop.z,
                ModSounds.EPIC_DAP, net.minecraft.sound.SoundCategory.PLAYERS, 1.3f, 1.1f);

        net.minecraft.text.Text msg = net.minecraft.text.Text.literal("§6§l⚡ TRIPLE DAP!");
        for (ServerPlayerEntity p : trio)
            p.sendMessage(msg, true);
    }

    private static void executeDap(ServerPlayerEntity p1, ServerPlayerEntity p2,
                                   float charge1, float charge2, float fire1, float fire2,
                                   long releaseTime1, long releaseTime2) {
        if (DapHoldHandler.isInDapHold(p1.getUuid()) || DapHoldHandler.isInDapHold(p2.getUuid())) return;

        long now = System.currentTimeMillis();
        cooldowns.put(p1.getUuid(), now + cooldownMs());
        cooldowns.put(p2.getUuid(), now + cooldownMs());

        float avgCharge = (charge1 + charge2) / 2.0f;

        double speed1 = getMaxRecentSpeed(p1.getUuid());
        double speed2 = getMaxRecentSpeed(p2.getUuid());
        double combinedSpeed = speed1 + speed2;

        long timeDiff = Math.abs(releaseTime1 - releaseTime2);
        boolean perfectHit = timeDiff <= perfectWindowMs();
        boolean bothCharging = chargeStartTime.containsKey(p1.getUuid()) && chargeStartTime.containsKey(p2.getUuid());

        int tier = calculateTier(avgCharge, combinedSpeed, fire1, fire2);
        if (tier >= 3 && bothCharging && !perfectHit) {
            if (timeDiff > releaseWindowMs()) {
                return;
            }
        }

        boolean isPerfectDap = (tier >= 3 && bothCharging && perfectHit);
        boolean isHighTierDap = (tier >= 4);
        if (!isPerfectDap && !isHighTierDap) {
            if (!arePlayersFacingEachOther(p1, p2)) {
                p1.sendMessage(net.minecraft.text.Text.literal("§c§lKeep eye contact!"), true);
                p2.sendMessage(net.minecraft.text.Text.literal("§c§lKeep eye contact!"), true);
                cooldowns.put(p1.getUuid(), now + 300);
                cooldowns.put(p2.getUuid(), now + 300);

                broadcastChargeCancel(p1);
                broadcastChargeCancel(p2);
                PoseNetworking.broadcastAnimState(p1, 0);
                PoseNetworking.broadcastAnimState(p2, 0);
                return;
            }
        }

        Vec3d pos1 = p1.getPos();
        Vec3d pos2 = p2.getPos();
        Vec3d dapPos = pos1.add(pos2).multiply(0.5).add(0, 0.5, 0);

        ServerWorld world = p1.getServerWorld();

        if (NormalFacingDapHandler.isConfirmed(p1.getUuid(), p2.getUuid())) {
            NormalFacingDapHandler.clearConfirm(p1.getUuid(), p2.getUuid());
            NormalFacingDapHandler.start(p1, p2);
            return;
        }

        switch (tier) {
            case 0 -> executeTier0(world, dapPos, p1, p2);
            case 1 -> executeTier1(world, dapPos, p1, p2);
            case 2 -> executeTier2(world, dapPos, p1, p2);
            case 3 -> executeTier3Great(world, dapPos, p1, p2, perfectHit, bothCharging);
            case 4 -> executeTier4Legendary(world, dapPos, p1, p2, perfectHit, bothCharging);
            case 5 -> executeTier5FireDap(world, dapPos, p1, p2, perfectHit);
        }
        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);

        MahitoTrollHandler.checkForMahitoTroll(p1, p2);

        speedHistory.remove(p1.getUuid());
        speedHistory.remove(p2.getUuid());
        chargeStartTime.remove(p1.getUuid());
        chargeStartTime.remove(p2.getUuid());

        broadcastChargeCancel(p1);
        broadcastChargeCancel(p2);

        boolean facingActive = FacingDapHandler.isActive(p1.getUuid()) || FacingDapHandler.isActive(p2.getUuid());
        if (!facingActive) {
            if (highFivePartners.contains(p1.getUuid())) {
                PoseNetworking.broadcastAnimState(p1,
                        com.cooptest.client.CoopAnimationHandler.AnimState.HIGHFIVE_HIT.ordinal());
                PoseNetworking.broadcastAnimState(p2,
                        com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
                comboCooldown.put(p1.getUuid(), now + 1000);
                comboCooldown.put(p2.getUuid(), now + 1000);
            } else if (highFivePartners.contains(p2.getUuid())) {
                PoseNetworking.broadcastAnimState(p2,
                        com.cooptest.client.CoopAnimationHandler.AnimState.HIGHFIVE_HIT.ordinal());
                PoseNetworking.broadcastAnimState(p1,
                        com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
                comboCooldown.put(p1.getUuid(), now + 1000);
                comboCooldown.put(p2.getUuid(), now + 1000);
            } else {

                int animOrd = tier <= 2
                        ? com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT_BAD.ordinal()
                        : com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal();
                PoseNetworking.broadcastAnimState(p1, animOrd);
                PoseNetworking.broadcastAnimState(p2, animOrd);
            }
        }

        long particleSpawnTime = System.currentTimeMillis() + 800;
        scheduledParticles.add(new ScheduledParticles(world, dapPos.x, dapPos.y, dapPos.z, particleSpawnTime));

        DapResultPayload result = new DapResultPayload(
                dapPos.x, dapPos.y, dapPos.z,
                p1.getUuid(), p2.getUuid(), tier, perfectHit);
        MinecraftServer server = p1.getServer();
        if (server != null)
            for (ServerPlayerEntity other : PlayerLookup.all(p1.getServer())) {
                ServerPlayNetworking.send(other, result);
            }
    }

    private static int calculateTier(float avgCharge, double combinedSpeed, float fire1, float fire2) {
        if (fire1 >= 0.90f && fire2 >= 0.90f) {
            return 5;
        }
        if (avgCharge >= 0.8f && combinedSpeed >= SPEED_TIER_4_THRESHOLD) {
            return 4;
        }

        float chargeScore = avgCharge * 100;
        float speedBonus = 0;
        if (combinedSpeed >= SPEED_BONUS_THRESHOLD) {
            speedBonus = (float) ((combinedSpeed - SPEED_BONUS_THRESHOLD)
                    / (SPEED_TIER_4_THRESHOLD - SPEED_BONUS_THRESHOLD)
                    * 30);
        }
        float finalScore = chargeScore + speedBonus;

        if (finalScore >= 100)
            return 3;
        if (finalScore >= 70)
            return 2;
        if (finalScore >= 40)
            return 1;
        return 0;
    }

    private static void executeFizzle(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        Vec3d pos = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1.4, 0);

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8f, 0.5f);

        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 12, 0.4, 0.3, 0.4, 0.03);
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 8, 0.3, 0.3, 0.3, 0.02);

        p1.sendMessage(net.minecraft.text.Text.literal("§7*missed!* timing off..."), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§7*missed!* timing off..."), true);

        chargeStartTime.remove(p1.getUuid());
        chargeStartTime.remove(p2.getUuid());
        fireLevel.remove(p1.getUuid());
        fireLevel.remove(p2.getUuid());

        MinecraftServer server = p1.getServer();
        if (server != null) {
            if (heavenReady.remove(p1.getUuid())) {
                broadcastHeavenReadyStatus(server, p1.getUuid());
            }
            if (heavenReady.remove(p2.getUuid())) {
                broadcastHeavenReadyStatus(server, p2.getUuid());
            }
        }

        broadcastChargeCancel(p1);
        broadcastChargeCancel(p2);

        PoseNetworking.broadcastAnimState(p1, 0);
        PoseNetworking.broadcastAnimState(p2, 0);

        UUID p1Id = p1.getUuid();
        UUID p2Id = p2.getUuid();
        for (ServerPlayerEntity p : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new FireDapFirstPersonPayload(p1Id, false));
            ServerPlayNetworking.send(p, new FireDapFirstPersonPayload(p2Id, false));
        }

        long now = System.currentTimeMillis();
        cooldowns.put(p1.getUuid(), now + 500);
        cooldowns.put(p2.getUuid(), now + 500);
    }

    public static void executeWhiff(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d pos = player.getPos().add(0, 1.4, 0);
        Vec3d look = player.getRotationVector();
        pos = pos.add(look.multiply(0.5));

        if (Math.random() < 0.1) {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_MISS, SoundCategory.PLAYERS, 1.0f, 1.0f);
        } else {
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.6f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.5f, 1.5f);
        }

        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 5, 0.15, 0.15, 0.15, 0.01);

        setBlockingAnimation(player.getUuid(), 330);

        PoseNetworking.broadcastAnimState(player, 0);

        UUID playerId = player.getUuid();
        MinecraftServer server = player.getServer();
        if (server != null)
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, new FireDapFirstPersonPayload(playerId, false));
            }
        player.sendMessage(net.minecraft.text.Text.literal("§7*whoosh*"), true);
    }

    private static void executeTier0(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        DapSession session = DapSessionManager.createSession( id1, id2, 1.4, DapSession.DapType.NORMAL_DAP);

        if (session == null) {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 0);
            p1.sendMessage(net.minecraft.text.Text.literal("§7Weak dap..."), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§7Weak dap..."), true);
            return;
        }

        session.onComplete(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(710);
                } catch (InterruptedException ignored) {
                }
                world.getServer().execute(() -> {
                    Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1.3, 0);
                    world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                            SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.9f, 1.1f);
                    spawnPrecisionDapParticles(world, midpoint, 0);
                });
            }).start();

            new Thread(() -> {
                try {
                    Thread.sleep(1667);
                } catch (InterruptedException ignored) {
                }
                world.getServer().execute(() -> {
                    PoseNetworking.broadcastAnimState(p1, 0);
                    PoseNetworking.broadcastAnimState(p2, 0);
                });
            }).start();
            applyKnockback(p1, p2);
        });
    }

    private static void executeTier1(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,
                DapSession.DapType.NORMAL_DAP);

        if (session == null) {

            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 1);
            p1.sendMessage(net.minecraft.text.Text.literal("§e✋ Decent Dap!"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§e✋ Decent Dap!"), true);
            return;
        }

        session.onComplete(() -> {

            new Thread(() -> {
                try {
                    Thread.sleep(710);
                } catch (InterruptedException ignored) {
                }
                world.getServer().execute(() -> {
                    Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1.3, 0);
                    world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                            SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.9f, 1.1f);
                    spawnPrecisionDapParticles(world, midpoint, 1);
                });
            }).start();

            new Thread(() -> {
                try {
                    Thread.sleep(1667);
                } catch (InterruptedException ignored) {
                }
                world.getServer().execute(() -> {
                    PoseNetworking.broadcastAnimState(p1, 0);
                    PoseNetworking.broadcastAnimState(p2, 0);
                });
            }).start();
            applyKnockback(p1, p2);
        });
    }

    private static void executeTier2(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,
                DapSession.DapType.NORMAL_DAP);

        if (session == null) {

            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 2);
            p1.sendMessage(net.minecraft.text.Text.literal("§a✋ Good Dap! ✋"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§a✋ Good Dap! ✋"), true);
            return;
        }

        session.onComplete(() -> {

            new Thread(() -> {
                try {
                    Thread.sleep(710);
                } catch (InterruptedException ignored) {
                }
                world.getServer().execute(() -> {
                    Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1.3, 0);
                    world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                            SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.9f, 1.1f);
                    spawnPrecisionDapParticles(world, midpoint, 2);
                });
            }).start();

            new Thread(() -> {
                try {
                    Thread.sleep(1667);
                } catch (InterruptedException ignored) {
                }
                world.getServer().execute(() -> {
                    PoseNetworking.broadcastAnimState(p1, 0);
                    PoseNetworking.broadcastAnimState(p2, 0);
                });
            }).start();
            applyKnockback(p1, p2);
        });
    }

    private static void executeTier3Great(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
                                          boolean perfectHit, boolean bothCharging) {
        if (bothCharging && perfectHit) {
            rotateBothPlayersToFaceEachOther(p1, p2);

            if (FacingDapHandler.areFacingEachOther(p1, p2) && !FacingDapHandler.isActive(p1.getUuid())) {
                FacingDapHandler.start(p1, p2);
                return;
            }
            long now = System.currentTimeMillis();
            UUID id1 = p1.getUuid();
            UUID id2 = p2.getUuid();

            DapSession session = DapSessionManager.createSession(id1, id2, 1.5,
                    DapSession.DapType.PERFECT_DAP);

            if (session == null) {
                executeTier3Normal(world, pos, p1, p2);
                return;
            }

            perfectDapStartTime.put(id1, now);
            perfectDapStartTime.put(id2, now);
            perfectDapPartner.put(id1, id2);
            perfectDapPartner.put(id2, id1);

            session.onComplete(() -> startPerfectDapTier3Animation(world, pos, p1, p2));
        } else {
            executeTier3Normal(world, pos, p1, p2);
        }
    }

    private static void startPerfectDapTier3Animation(ServerWorld world, Vec3d pos, ServerPlayerEntity p1,
                                                      ServerPlayerEntity p2) {
        long now = System.currentTimeMillis();
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        Vec3d diff12 = p2.getPos().subtract(p1.getPos());
        float yaw1 = (float) (Math.toDegrees(Math.atan2(diff12.z, diff12.x))) - 90f;
        float yaw2 = yaw1 + 180f;
        p1.setYaw(yaw1);
        p1.setBodyYaw(yaw1);
        p1.setHeadYaw(yaw1);
        p2.setYaw(yaw2);
        p2.setBodyYaw(yaw2);
        p2.setHeadYaw(yaw2);
        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        Vec3d p1Hand = p1.getPos().add(0, 1.4, 0);
        Vec3d p2Hand = p2.getPos().add(0, 1.4, 0);
        p1Hand.add(p2Hand).multiply(0.5);

        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_HIT.ordinal());
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_HIT.ordinal());

        perfectDapFreezeEnd.put(id1, now + 1290);
        perfectDapFreezeEnd.put(id2, now + 1290);

        ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(true));

        setBlockingAnimation(id1, 1625);
        setBlockingAnimation(id2, 1625);

        long effectTime = now + 150;
        scheduledPerfectDapEffects.add(new ScheduledPerfectDapEffect(
                world, pos, p1, p2, effectTime));

        p1.sendMessage(net.minecraft.text.Text.literal("§6§l✋ PERFECT GREAT DAP! ✋"), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§6§l✋ PERFECT GREAT DAP! ✋"), true);

        DapSessionManager.removeSession(id1);
    }

    private static void executeTier3Normal(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        DapSession session = DapSessionManager.createSession(id1, id2, 1.4, DapSession.DapType.NORMAL_DAP);

        if (session == null) {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 3);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            // createExplosion(world, pos, p1, p2, 3.5, 6.0f);
            applyKnockback(p1, p2);
            p1.sendMessage(net.minecraft.text.Text.literal("§6§l✋ GREAT DAP! ✋"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§6§l✋ GREAT DAP! ✋"), true);
            return;
        }

        session.onComplete(() -> {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);

            spawnPrecisionDapParticles(world, pos, 3);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            // createExplosion(world, pos, p1, p2, 3.5, 6.0f);
            applyKnockback(p1, p2);
            p1.sendMessage(net.minecraft.text.Text.literal("§6§l✋ GREAT DAP! ✋"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§6§l✋ GREAT DAP! ✋"), true);

            if (CoopMovesConfig.get().enableDapCombo)
                DapComboChain.startCombo(p1, p2, pos);
        });
    }

    // private static void startStage2Extender(ServerPlayerEntity p1, ServerPlayerEntity p2) {
    //     UUID id1 = p1.getUuid();
    //     UUID id2 = p2.getUuid();
    //     ServerWorld world = p1.getServerWorld();
    //     long now = System.currentTimeMillis();

    //     ServerPlayNetworking.send(p1, new QTEClearPayload(id1));
    //     ServerPlayNetworking.send(p2, new QTEClearPayload(id2));

    //     ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(true));
    //     ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(true));

    //     perfectDapFreezeEnd.put(id1, now + 4500);
    //     perfectDapFreezeEnd.put(id2, now + 4500);

    //     PoseNetworking.broadcastAnimState(p1,
    //             com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P1.ordinal());

    //     PoseNetworking.broadcastAnimState(p2,
    //             com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P2.ordinal());

    //     p1.sendMessage(net.minecraft.text.Text.literal("§d§l★ EXTENDER DAP! ★"), true);
    //     p2.sendMessage(net.minecraft.text.Text.literal("§d§l★ EXTENDER DAP! ★"), true);

    //     new Thread(() -> {
    //         try {
    //             Thread.sleep(4500);
    //             world.getServer().execute(() -> {

    //                 ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(false));
    //                 ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(false));

    //                 perfectDapFreezeEnd.remove(id1);
    //                 perfectDapFreezeEnd.remove(id2);

    //                 PoseNetworking.broadcastAnimState(p1,
    //                         com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
    //                 PoseNetworking.broadcastAnimState(p2,
    //                         com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());

    //                 HighFiveHandler.cleanup(id1);
    //                 HighFiveHandler.cleanup(id2);

    //                 long cooldownTime = System.currentTimeMillis() + 1000;
    //                 comboCooldown.put(id1, cooldownTime);
    //                 comboCooldown.put(id2, cooldownTime);

    //             });
    //         } catch (InterruptedException e) {
    //         }
    //     }).start();
    // }

    // public static boolean isInQTE(UUID playerId) {
    //     return QTEManager.isInQTE(playerId) || DapComboChain.isInCombo(playerId);
    // }

    private static long tickSpeedRestoreTime = 0;

    private static void executeTier4Legendary(ServerWorld world, Vec3d pos, ServerPlayerEntity p1,
                                              ServerPlayerEntity p2, boolean perfectHit, boolean bothCharging) {
        var server = world.getServer();

        UUID p1Id = p1.getUuid();
        UUID p2Id = p2.getUuid();
        boolean bothHeavenReady = heavenReady.contains(p1Id) && heavenReady.contains(p2Id);

        if (bothHeavenReady && perfectHit) {
            startHeavenDap(p1, p2, pos, world);
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    world.getServer().execute(() -> {
                        p1.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.RESISTANCE, 80, 255, false, false));
                        p2.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.RESISTANCE, 80, 255, false, false));
                        // boolean _d = !CoopMovesConfig.get().noGriefMode;
                        // for (int i = 0; i < 8; i++) {
                        //     double a = Math.toRadians(i * 45.0);
                        //     for (double dy : new double[] { 0, 20, -20 }) {
                        //         world.createExplosion(null,
                        //                 pos.x + Math.cos(a) * 5, pos.y + dy,
                        //                 pos.z + Math.sin(a) * 5, 8f,
                        //                 false, net.minecraft.world.World.ExplosionSourceType.NONE);
                        //     }
                        // }
                        // new Thread(() -> {
                        //     try {
                        //         Thread.sleep(150);
                        //     } catch (InterruptedException ignored) {
                        //     }
                        //     world.getServer().execute(() -> {
                        //         for (int i = 0; i < 8; i++) {
                        //             double a = Math.toRadians(i * 45.0 + 22.5);
                        //             for (double dy : new double[] { 0, 20, -20 }) {
                        //                 world.createExplosion(null,
                        //                         pos.x + Math.cos(a) * 25, pos.y + dy,
                        //                         pos.z + Math.sin(a) * 25, 20f,
                        //                         _d, net.minecraft.world.World.ExplosionSourceType.TNT);
                        //             }
                        //         }
                        //     });
                        // }).start();
                        // new Thread(() -> {
                        //     try {
                        //         Thread.sleep(350);
                        //     } catch (InterruptedException ignored) {
                        //     }
                        //     world.getServer().execute(() -> {
                        //         for (int i = 0; i < 8; i++) {
                        //             double a = Math.toRadians(i * 45.0);
                        //             for (double dy : new double[] { 0, 20, -20 }) {
                        //                 world.createExplosion(null,
                        //                         pos.x + Math.cos(a) * 60, pos.y + dy,
                        //                         pos.z + Math.sin(a) * 60, 15f,
                        //                         _d, net.minecraft.world.World.ExplosionSourceType.TNT);
                        //             }
                        //         }
                        //     });
                        // }).start();
                        // new Thread(() -> {
                        //     try {
                        //         Thread.sleep(600);
                        //     } catch (InterruptedException ignored) {
                        //     }
                        //     world.getServer().execute(() -> {
                        //         for (int i = 0; i < 8; i++) {
                        //             double a = Math.toRadians(i * 45.0 + 22.5);
                        //             world.createExplosion(null,
                        //                     pos.x + Math.cos(a) * 100, pos.y,
                        //                     pos.z + Math.sin(a) * 100, 12f,
                        //                     _d, net.minecraft.world.World.ExplosionSourceType.TNT);
                        //         }
                        //     });
                        // }).start();

                        spawnExpandingLegendarySonicBoom(world, pos);

                        activeHeavenParticles.add(new HeavenParticleSpawner(world, pos, 30000));

                        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 20, 5, 5, 5, 0);
                        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 300, 10, 10, 10, 0.5);
                        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 200, 8, 8, 8, 0.4);
                        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 10, 0, 0, 0, 0);

                        spawnStarBurst(world, pos, 100, 5.0);
                        createMassiveShockwave(world, pos, p1, p2);

                        activeSaturnRings.add(new SaturnRing(pos, System.currentTimeMillis()));

                    });
                } catch (InterruptedException e) {
                    // e.printStackTrace();
                }
            }).start();
        } else if (bothCharging) {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0f, 0.5f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 0.7f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 2.0f, 0.8f);

            spawnStarBurst(world, pos, 40, 1.5);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 3, 1, 1, 1, 0);
            world.spawnParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 50, 0.5, 0.5, 0.5, 0.2);
            world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 100, 1.0, 1.0, 1.0, 0.1);

            // world.createExplosion(null, pos.x, pos.y, pos.z, 6.0f, !CoopMovesConfig.get().noGriefMode,
            //         net.minecraft.world.World.ExplosionSourceType.MOB);

            removeTotem(p1);
            removeTotem(p2);

            p1.setHealth(0);
            p2.setHealth(0);
            p1.onDeath(world.getDamageSources().magic());
            p2.onDeath(world.getDamageSources().magic());

            p1.sendMessage(net.minecraft.text.Text.literal("§4§l☠ THE POWER WAS TOO GREAT! ☠"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§4§l☠ THE POWER WAS TOO GREAT! ☠"), true);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(net.minecraft.text.Text.literal(
                                "§4§l☠ " + p1.getName().getString() + " §7and §4" + p2.getName().getString() +
                                        " §7failed to achieve Perfect Friendship... §c§lTHEY PERISHED!"),
                        false);
            }
        } else {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0f, 0.9f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 1.0f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 2.0f, 0.9f);

            spawnStarBurst(world, pos, 30, 1.0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.5, 0.5, 0.5, 0.25);

            // world.createExplosion(null, pos.x, pos.y, pos.z, 5.0f, !CoopMovesConfig.get().noGriefMode,
            //         net.minecraft.world.World.ExplosionSourceType.MOB);
            applyKnockback(p1, p2);

            p1.sendMessage(net.minecraft.text.Text.literal("§d§l⚡ LEGENDARY DAP! ⚡"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§d§l⚡ LEGENDARY DAP! ⚡"), true);
        }
    }

    private static void removeTotem(ServerPlayerEntity player) {
        if (player.getMainHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING))
            player.getMainHandStack().setCount(0);
        if (player.getOffHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING))
            player.getOffHandStack().setCount(0);
    }

    public static void checkTickSpeedRestore(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();

        if (tickSpeedRestoreTime > 0 && now >= tickSpeedRestoreTime) {
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
                    "tick rate 20");
            tickSpeedRestoreTime = 0;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(net.minecraft.text.Text.literal("§7Time returns to normal..."), false);
            }
        }

        Iterator<Map.Entry<UUID, Long>> it = perfectFriendshipLevitation.entrySet().iterator();
        Set<UUID> processed = new HashSet<>();

        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID playerId = entry.getKey();
            long endTime = entry.getValue();

            if (now >= endTime && !processed.contains(playerId)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                UUID partnerId = perfectFriendshipPartner.get(playerId);
                ServerPlayerEntity partner = partnerId != null ? server.getPlayerManager().getPlayer(partnerId) : null;

                if (player != null) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0, false, true));
                    processed.add(playerId);
                }

                if (player != null && partner != null && !processed.contains(partnerId)) {
                    partner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0, false, true));
                    processed.add(partnerId);

                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        p.sendMessage(net.minecraft.text.Text.literal(
                                        "§d§l✨ " + player.getName().getString() + " §7and §d§l" + partner.getName().getString()
                                                +
                                                " §7have achieved §b§lPERFECT FRIENDSHIP§7! §d§l✨"),
                                false);
                    }
                }
                it.remove();
            }
        }

        for (UUID id : processed) {
            perfectFriendshipPartner.remove(id);
            perfectFriendshipLevitation.remove(id);
        }
    }

    public static void startHeavenDap(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d midpoint, ServerWorld world) {
        long now = System.currentTimeMillis();
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        ServerPlayNetworking.send(p1, new HeavenDapPayloads.HeavenImpactPayload());
        ServerPlayNetworking.send(p2, new HeavenDapPayloads.HeavenImpactPayload());

        ServerPlayNetworking.send(p1, new PerfectDapImpactFramePayload(1));
        ServerPlayNetworking.send(p2, new PerfectDapImpactFramePayload(1));

        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 3.0f, 1.2f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 2.0f, 1.0f);

        // final double RING_1_RADIUS = 100;
        // final double RING_2_RADIUS = 25;
        // final double RING_3_RADIUS = 60;
        // final double RING_4_RADIUS = 100;
        // final float RING_1_POWER = 8f;
        // final float RING_2_POWER = 20f;
        // final float RING_3_POWER = 15f;
        // final float RING_4_POWER = 12f;
        // final double VERTICAL_SPREAD = 200;

        p1.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.RESISTANCE, 80, 255, false, false));
        p2.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.RESISTANCE, 80, 255, false, false));

        // for (int i = 0; i < 8; i++) {
        //     double a = Math.toRadians(i * 45.0);
        //     for (double dy : new double[] { 0, VERTICAL_SPREAD, -VERTICAL_SPREAD }) {
        //         world.createExplosion(null,
        //                 midpoint.x + Math.cos(a) * RING_1_RADIUS, midpoint.y + dy,
        //                 midpoint.z + Math.sin(a) * RING_1_RADIUS, RING_1_POWER,
        //                 false, net.minecraft.world.World.ExplosionSourceType.NONE);
        //     }
        // }

        // new Thread(() -> {
        //     try {
        //         Thread.sleep(150);
        //     } catch (InterruptedException ignored) {
        //     }
        //     world.getServer().execute(() -> {
        //         for (int i = 0; i < 8; i++) {
        //             double a = Math.toRadians(i * 45.0 + 22.5);
        //             for (double dy : new double[] { 0, VERTICAL_SPREAD, -VERTICAL_SPREAD }) {
        //                 world.createExplosion(null,
        //                         midpoint.x + Math.cos(a) * RING_2_RADIUS, midpoint.y + dy,
        //                         midpoint.z + Math.sin(a) * RING_2_RADIUS, RING_2_POWER,
        //                         true, net.minecraft.world.World.ExplosionSourceType.TNT);
        //             }
        //         }
        //     });
        // }).start();

        // new Thread(() -> {
        //     try {
        //         Thread.sleep(350);
        //     } catch (InterruptedException ignored) {
        //     }
        //     world.getServer().execute(() -> {
        //         for (int i = 0; i < 8; i++) {
        //             double a = Math.toRadians(i * 45.0);
        //             for (double dy : new double[] { 0, VERTICAL_SPREAD, -VERTICAL_SPREAD }) {
        //                 world.createExplosion(null,
        //                         midpoint.x + Math.cos(a) * RING_3_RADIUS, midpoint.y + dy,
        //                         midpoint.z + Math.sin(a) * RING_3_RADIUS, RING_3_POWER,
        //                         true, net.minecraft.world.World.ExplosionSourceType.TNT);
        //             }
        //         }
        //     });
        // }).start();

        // new Thread(() -> {
        //     try {
        //         Thread.sleep(600);
        //     } catch (InterruptedException ignored) {
        //     }
        //     world.getServer().execute(() -> {
        //         for (int i = 0; i < 8; i++) {
        //             double a = Math.toRadians(i * 45.0 + 22.5);
        //             world.createExplosion(null,
        //                     midpoint.x + Math.cos(a) * RING_4_RADIUS, midpoint.y,
        //                     midpoint.z + Math.sin(a) * RING_4_RADIUS, RING_4_POWER,
        //                     true, net.minecraft.world.World.ExplosionSourceType.TNT);
        //         }
        //     });
        // }).start();

        spawnSonicBoomCircles(world, midpoint);

        world.spawnParticles(ParticleTypes.FLASH, midpoint.x, midpoint.y, midpoint.z, 5, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, midpoint.x, midpoint.y, midpoint.z, 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticles(ParticleTypes.END_ROD, midpoint.x, midpoint.y, midpoint.z, 50, 1.0, 1.0, 1.0, 0.3);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, midpoint.x, midpoint.y, midpoint.z, 40, 0.8, 0.8, 0.8, 0.2);

        heavenPlayers.put(id1, new HeavenDapData(midpoint, world, now, id2));
        heavenPlayers.put(id2, new HeavenDapData(midpoint, world, now, id1));

        new Thread(() -> {
            try {
                Thread.sleep(700);

                MinecraftServer server = p1.getServer();
                assert server != null;
                server.execute(() -> {
                    if (p1.isRemoved() || p2.isRemoved())
                        return;

                    double heavenY = 500.0;
                    Vec3d heavenMid = new Vec3d(midpoint.x, heavenY, midpoint.z);

                    Vec3d dir = p2.getPos().subtract(p1.getPos()).normalize();

                    Vec3d pos1 = heavenMid.add(dir.multiply(-2.5));
                    Vec3d pos2 = heavenMid.add(dir.multiply(2.5));

                    double dx = pos2.x - pos1.x;
                    double dz = pos2.z - pos1.z;
                    float yaw1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                    float yaw2 = yaw1 + 180;

                    p1.teleport(world, pos1.x, pos1.y, pos1.z, yaw1, 0);
                    p2.teleport(world, pos2.x, pos2.y, pos2.z, yaw2, 0);

                    p1.stopFallFlying();
                    p2.stopFallFlying();
                    p1.setVelocity(Vec3d.ZERO);
                    p2.setVelocity(Vec3d.ZERO);
                    p1.velocityModified = true;
                    p2.velocityModified = true;

                    ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(true));
                    ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(true));

                    PoseNetworking.broadcastAnimState(p1,
                            com.cooptest.client.CoopAnimationHandler.AnimState.HEAVEN_DAP.ordinal());
                    PoseNetworking.broadcastAnimState(p2,
                            com.cooptest.client.CoopAnimationHandler.AnimState.HEAVEN_DAP.ordinal());

                    p1.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0, false, false));
                    p2.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0, false, false));

                    p1.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));
                    p2.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));

                    ServerPlayNetworking.send(p1, new HeavenDapPayloads.HeavenDapStartPayload());
                    ServerPlayNetworking.send(p2, new HeavenDapPayloads.HeavenDapStartPayload());

                });

            } catch (InterruptedException e) {
                // e.printStackTrace();
            }
        }).start();
    }

    private static void spawnSonicBoomCircles(ServerWorld world, Vec3d pos) {

        new Thread(() -> {
            try {
                for (int circle = 0; circle < 3; circle++) {
                    final double baseRadius = 2.0 + (circle * 2.0);

                    world.getServer().execute(() -> {

                        for (int i = 0; i < 60; i++) {
                            double angle = Math.toRadians(i * 6);
                            double x = pos.x + Math.cos(angle) * baseRadius;
                            double z = pos.z + Math.sin(angle) * baseRadius;

                            world.spawnParticles(ParticleTypes.WHITE_ASH,
                                    x, pos.y + 0.5, z,
                                    3, 0.1, 0.3, 0.1, 0.05);

                            world.spawnParticles(ParticleTypes.CLOUD,
                                    x, pos.y + 0.5, z,
                                    2, 0.05, 0.2, 0.05, 0.02);
                        }
                    });

                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                // e.printStackTrace();
            }
        }).start();
    }

    // private static void spawnPerfectDapSonicBoom(ServerWorld world, Vec3d center) {
    //     spawnExpandingRing(world, center, 0.5, 3.0, 200);
    // }

    private static void spawnFireDapSonicBoom(ServerWorld world, Vec3d pos) {
        new Thread(() -> {
            try {
                spawnExpandingFireRing(world, pos, 2.0, 10.0, 250);
                Thread.sleep(100);
                spawnExpandingFireRing(world, pos, 5.0, 15.0, 350);
            } catch (InterruptedException e) {
                // e.printStackTrace();
            }
        }).start();
    }

    private static void spawnExpandingFireRing(ServerWorld world, Vec3d center, double startRadius, double endRadius,
                                               int durationMs) {
        int steps = 15;
        double radiusStep = (endRadius - startRadius) / steps;
        long stepDuration = durationMs / steps;

        new Thread(() -> {
            try {
                for (int step = 0; step < steps; step++) {
                    final double currentRadius = startRadius + (radiusStep * step);

                    world.getServer().execute(() -> {
                        int particleCount = (int) (currentRadius * 15);
                        for (int i = 0; i < particleCount; i++) {
                            double angle = Math.toRadians((360.0 / particleCount) * i);

                            double x = center.x + Math.cos(angle) * currentRadius;
                            double z = center.z + Math.sin(angle) * currentRadius;
                            double y = center.y + 0.5;

                            double vx = Math.cos(angle) * 0.35;
                            double vz = Math.sin(angle) * 0.35;

                            world.spawnParticles(ParticleTypes.FLAME,
                                    x, y, z, 0, vx, 0.0, vz, 0.5);
                            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                    x, y, z, 0, vx * 0.8, 0.0, vz * 0.8, 0.4);

                            if (i % 3 == 0) {
                                world.spawnParticles(ParticleTypes.LAVA,
                                        x, y, z, 1, 0, 0, 0, 0);
                            }
                        }
                    });
                    Thread.sleep(stepDuration);
                }
            } catch (InterruptedException e) {
                // e.printStackTrace();
            }
        }).start();
    }

    private static void spawnExpandingLegendarySonicBoom(ServerWorld world, Vec3d center) {
        new Thread(() -> {
            try {
                spawnExpandingRing(world, center, 1.0, 6.0, 300);
                Thread.sleep(200);
                spawnExpandingRing(world, center, 6.0, 15.0, 400);
                Thread.sleep(200);
                spawnExpandingRing(world, center, 15.0, 30.0, 500);
            } catch (InterruptedException e) {
                // e.printStackTrace();
            }
        }).start();
    }

    private static void spawnExpandingRing(ServerWorld world, Vec3d center, double startRadius, double endRadius,
                                           int durationMs) {
        int steps = 20;
        double radiusStep = (endRadius - startRadius) / steps;
        long stepDuration = durationMs / steps;

        new Thread(() -> {
            try {
                for (int step = 0; step < steps; step++) {
                    final double currentRadius = startRadius + (radiusStep * step);

                    world.getServer().execute(() -> {
                        int particleCount = (int) (currentRadius * 20);
                        for (int i = 0; i < particleCount; i++) {
                            double angle = Math.toRadians((360.0 / particleCount) * i);

                            double x = center.x + Math.cos(angle) * currentRadius;
                            double z = center.z + Math.sin(angle) * currentRadius;
                            double y = center.y + 0.5;

                            double vx = Math.cos(angle) * 0.4;
                            double vz = Math.sin(angle) * 0.4;

                            world.spawnParticles(ParticleTypes.CLOUD,
                                    x, y, z, 0, vx, 0.0, vz, 0.6);
                            world.spawnParticles(ParticleTypes.WHITE_ASH,
                                    x, y, z, 0, vx, 0.0, vz, 0.5);

                            if (i % (particleCount / 24) == 0) {
                                world.spawnParticles(ParticleTypes.END_ROD,
                                        x, y, z, 1, 0, 0, 0, 0);
                            }
                        }
                    });
                    Thread.sleep(stepDuration);
                }
            } catch (InterruptedException e) {
                // e.printStackTrace();
            }
        }).start();
    }

    private static void createMassiveShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1,
                                               ServerPlayerEntity p2) {
        Box shockwaveBox = new Box(
                pos.x - 50.0, pos.y - 50.0, pos.z - 50.0,
                pos.x + 50.0, pos.y + 50.0, pos.z + 50.0);

        for (Entity entity : world.getOtherEntities(null, shockwaveBox)) {
            if (entity == p1 || entity == p2)
                continue;
            double dist = entity.getPos().distanceTo(pos);
            if (dist > 50.0 || dist < 0.5)
                continue;

            double knockbackStrength = (1.0 - dist / 50.0) * 10.0;
            Vec3d knockDir = entity.getPos().subtract(pos).normalize();

            entity.addVelocity(
                    knockDir.x * knockbackStrength * 2.0,
                    knockbackStrength * 1.5,
                    knockDir.z * knockbackStrength * 2.0);
            entity.velocityModified = true;
        }

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 3.0f, 0.6f);
    }

    private static void executeTier5FireDap(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
                                            boolean perfectHit) {

        UUID p1Id = p1.getUuid(), p2Id = p2.getUuid();
        boolean bothHeavenReady = heavenReady.contains(p1Id) && heavenReady.contains(p2Id);
        double speed1 = getMaxRecentSpeed(p1Id);
        double speed2 = getMaxRecentSpeed(p2Id);
        boolean bothMovingFast = speed1 >= PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED
                && speed2 >= PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED;

        if (bothHeavenReady && bothMovingFast) {
            startHeavenDap(p1, p2, pos, world);
            return;
        }

        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.FIRE_IMPACT, SoundCategory.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5f, 1.3f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 2.0f, 0.8f);

        spawnFireDapSonicBoom(world, pos);

        Random rand = new Random();
        int sphereParticles = 24;
        for (int i = 0; i < sphereParticles; i++) {

            double theta = rand.nextDouble() * 2 * Math.PI;
            double phi = Math.acos(2 * rand.nextDouble() - 1);
            double radius = 0.5 + rand.nextDouble() * 0.5;

            double dx = Math.sin(phi) * Math.cos(theta) * radius;
            double dy = Math.cos(phi) * radius;
            double dz = Math.sin(phi) * Math.sin(theta) * radius;

            double speed = 0.15 + rand.nextDouble() * 0.15;
            world.spawnParticles(ParticleTypes.FLAME,
                    pos.x + dx, pos.y + dy + 1.0, pos.z + dz,
                    1, dx * speed, dy * speed, dz * speed, 0.1);
        }

        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.0, pos.z, 30, 0.3, 0.3, 0.3, 0.2);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0, pos.z, 15, 0.2, 0.2, 0.2, 0.15);
        world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y + 1.0, pos.z, 8, 0.3, 0.3, 0.3, 0);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 20, 0.5, 0.5, 0.5, 0.15);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z, 25, 0.5, 0.5, 0.5, 0.3);

        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 22.5);
            for (double r = 1; r <= 2.5; r += 0.5) {
                double ringX = Math.cos(angle) * r;
                double ringZ = Math.sin(angle) * r;
                world.spawnParticles(ParticleTypes.FLAME, pos.x + ringX, pos.y, pos.z + ringZ, 1, 0.05, 0.1, 0.05,
                        0.02);
            }
        }

        createFireShockwave(world, pos, p1, p2);

        if (perfectHit) {

            world.spawnParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y, pos.z, 40, 0.8, 0.8, 0.8, 0.15);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 50, 0.6, 0.6, 0.6, 0.3);

            p1.sendMessage(net.minecraft.text.Text.literal("§c§l🔥 PERFECT FIRE DAP! 🔥"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§c§l🔥 PERFECT FIRE DAP! 🔥"), true);
        } else {

            p1.sendMessage(net.minecraft.text.Text.literal("§c§l🔥 FIRE DAP! 🔥"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§c§l🔥 FIRE DAP! 🔥"), true);
        }

        startFireDap(p1, p2);

        DapFusionHandler.openFusionWindow(p1, p2);

        new Thread(() -> {
            try {
                Thread.sleep(FUSION_G_WINDOW_START_MS);
            } catch (InterruptedException ignored) {
            }
            MinecraftServer server = p1.getServer();
            assert server != null;
            p1.getServer().execute(() -> {
                if (inFireDapHit.getOrDefault(p1.getUuid(), false)) {
                    p1.sendMessage(
                            net.minecraft.text.Text
                                    .literal("§6Press §lG §r§6to FUSE  §7|  §cPress §lJ §r§cfor Fire Combo"),
                            true);
                    p2.sendMessage(
                            net.minecraft.text.Text
                                    .literal("§6Press §lG §r§6to FUSE  §7|  §cPress §lJ §r§cfor Fire Combo"),
                            true);
                }
            });
        }).start();

        for (ServerPlayerEntity nearby : PlayerLookup.around(world, pos, 50)) {
            if (nearby != p1 && nearby != p2) {
                String prefix = perfectHit ? "§c§lPERFECT " : "§c§l";
                nearby.sendMessage(net.minecraft.text.Text.literal(
                                prefix + "🔥 " + p1.getName().getString() + " §7and §c" + p2.getName().getString() +
                                        " §7unleashed a §c§lFIRE DAP§7!"),
                        false);
            }
        }
    }

    private static void spawnPrecisionDapParticles(ServerWorld world, Vec3d pos, int tier) {

        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(false);

        world.spawnEntity(stand);
        Vec3d exactPos = stand.getPos().add(0, 1.0, 0);
        int particleCount = 15 + (tier * 5);
        world.spawnParticles(
                ParticleTypes.CRIT,
                exactPos.x, exactPos.y, exactPos.z,
                particleCount,
                0.3, 0.3, 0.3,
                0.15);

        if (tier >= 3) {
            world.spawnParticles(
                    ParticleTypes.ENCHANTED_HIT,
                    exactPos.x, exactPos.y, exactPos.z,
                    particleCount / 2,
                    0.4, 0.4, 0.4,
                    0.2);
        }

        new Thread(() -> {
            try {
                Thread.sleep(50);
                world.getServer().execute(stand::discard);
            } catch (InterruptedException e) {
                world.getServer().execute(stand::discard);
            }
        }).start();
    }

    private static float calculateYawToFace(ServerPlayerEntity from, ServerPlayerEntity to) {
        Vec3d fromPos = from.getPos();
        Vec3d toPos = to.getPos();
        double dx = toPos.x - fromPos.x;
        double dz = toPos.z - fromPos.z;
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return (float) angle;
    }

    private static void smoothRotateToFacePartner(ServerPlayerEntity player, ServerPlayerEntity partner) {
        float targetYaw = calculateYawToFace(player, partner);
        float currentYaw = player.getYaw();

        targetYaw = ((targetYaw % 360) + 540) % 360 - 180;
        currentYaw = ((currentYaw % 360) + 540) % 360 - 180;

        float diff = targetYaw - currentYaw;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;

        final float finalCurrentYaw = currentYaw;
        final float finalDiff = diff;

        final int steps = 10;
        final long delayPerStep = 50;

        new Thread(() -> {
            for (int i = 1; i <= steps; i++) {
                final int step = i;
                try {
                    Thread.sleep(delayPerStep);
                    MinecraftServer server = player.getServer();
                    assert server != null;
                    server.execute(() -> {
                        float progress = (float) step / steps;
                        float newYaw = finalCurrentYaw + (finalDiff * progress);
                        player.setYaw(newYaw);
                        player.networkHandler.sendPacket(
                                new net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket(player));
                    });
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private static void rotateBothPlayersToFaceEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        smoothRotateToFacePartner(p1, p2);
        smoothRotateToFacePartner(p2, p1);
    }

    private static void spawnStarBurst(ServerWorld world, Vec3d pos, int rays, double spread) {
        for (int i = 0; i < rays; i++) {
            double angle = (2 * Math.PI * i) / rays;
            double dx = Math.cos(angle) * spread;
            double dz = Math.sin(angle) * spread;
            world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, dx, 0.2, dz, 0.15);
        }
    }

    private static void applyKnockback(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        p1.setVelocity(0, 0, 0);
        p2.setVelocity(0, 0, 0);

        p1.velocityModified = true;
        p2.velocityModified = true;
    }

    public static void applyImpactFreeze(ServerPlayerEntity p1, ServerPlayerEntity p2, int ticks) {
        if (ticks <= 0)
            return;

        p1.setVelocity(0, 0, 0);
        p2.setVelocity(0, 0, 0);
        p1.velocityModified = true;
        p2.velocityModified = true;

        impactFreezeTicks.put(p1.getUuid(), ticks);
        impactFreezeTicks.put(p2.getUuid(), ticks);
    }

    // private static void createExplosion(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
    //                                     double radius, float maxDamage) {
    //     Box damageBox = new Box(
    //             pos.x - radius, pos.y - radius, pos.z - radius,
    //             pos.x + radius, pos.y + radius, pos.z + radius);

    //     for (Entity entity : world.getOtherEntities(null, damageBox)) {
    //         if (entity == p1 || entity == p2)
    //             continue;

    //         double dist = entity.getPos().distanceTo(pos);
    //         if (dist > radius)
    //             continue;

    //         double knockbackStrength = (1.0 - dist / radius) * 2.0;
    //         Vec3d knockDir = entity.getPos().subtract(pos).normalize();
    //         entity.addVelocity(knockDir.x * knockbackStrength, knockbackStrength * 0.5, knockDir.z * knockbackStrength);
    //         entity.velocityModified = true;

    //         if (entity instanceof ServerPlayerEntity target) {
    //             float damage = (float) ((1.0 - dist / radius) * maxDamage);
    //             target.damage(world.getDamageSources().explosion(null, null), damage);
    //         }
    //     }
    // }

    private static void createShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Box shockwaveBox = new Box(
                pos.x - 10.0, pos.y - 10.0, pos.z - 10.0,
                pos.x + 10.0, pos.y + 10.0, pos.z + 10.0);

        for (int i = 0; i < 36; i++) {
            double angle = (i / 36.0) * Math.PI * 2;
            for (double r = 1; r <= 10.0; r += 2) {
                double px = pos.x + Math.cos(angle) * r;
                double pz = pos.z + Math.sin(angle) * r;
                world.spawnParticles(ParticleTypes.CLOUD, px, pos.y, pz, 1, 0, 0.1, 0, 0.02);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, px, pos.y + 0.5, pz, 1, 0, 0, 0, 0);
            }
        }

        for (Entity entity : world.getOtherEntities(null, shockwaveBox)) {
            if (entity == p1 || entity == p2)
                continue;

            double dist = entity.getPos().distanceTo(pos);
            if (dist > 10.0 || dist < 0.5)
                continue;

            double knockbackStrength = (1.0 - dist / 10.0) * 2.0;
            Vec3d knockDir = entity.getPos().subtract(pos).normalize();

            entity.addVelocity(
                    knockDir.x * knockbackStrength * 1.5,
                    knockbackStrength * 0.6,
                    knockDir.z * knockbackStrength * 1.5);
            entity.velocityModified = true;

            if (entity instanceof ServerPlayerEntity target) {
                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 0.8f);
            }
        }

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.6f, 1.5f);
    }

    private static void handleUnderwaterPerfectDap(ServerWorld world, Vec3d pos, ServerPlayerEntity p1,
                                                   ServerPlayerEntity p2) {

        if (!p1.isSubmergedInWater() && !p2.isSubmergedInWater()) {
            return;
        }

        p1.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.WATER_BREATHING, 120, 0, false, false));
        p2.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.WATER_BREATHING, 120, 0, false, false));

        world.spawnParticles(ParticleTypes.SPLASH,
                pos.x, pos.y, pos.z,
                80, 2.0, 2.0, 2.0, 0.4);
        world.spawnParticles(ParticleTypes.BUBBLE_POP,
                pos.x, pos.y, pos.z,
                40, 1.5, 1.5, 1.5, 0.3);

        UUID trackId = UUID.randomUUID();
        underwaterRemovalStart.put(trackId, System.currentTimeMillis());
        underwaterRemovalPos.put(trackId, pos);
        underwaterRemovalWorld.put(trackId, world);

    }

    // private static void createLegendaryExplosion(ServerWorld world, Vec3d pos, ServerPlayerEntity p1,
    //                                              ServerPlayerEntity p2) {
    //     double radius = 6.0;
    //     Box damageBox = new Box(
    //             pos.x - radius, pos.y - radius, pos.z - radius,
    //             pos.x + radius, pos.y + radius, pos.z + radius);

    //     for (Entity entity : world.getOtherEntities(null, damageBox)) {
    //         if (entity == p1 || entity == p2)
    //             continue;

    //         double dist = entity.getPos().distanceTo(pos);
    //         if (dist > radius)
    //             continue;

    //         double knockbackStrength = (1.0 - dist / radius) * 3.0;
    //         Vec3d knockDir = entity.getPos().subtract(pos).normalize();
    //         entity.addVelocity(knockDir.x * knockbackStrength, knockbackStrength * 0.7, knockDir.z * knockbackStrength);
    //         entity.velocityModified = true;

    //         float damage;
    //         if (entity instanceof ServerPlayerEntity) {
    //             damage = (float) ((1.0 - dist / radius) * 8.0);
    //         } else {
    //             damage = (float) ((1.0 - dist / radius) * 20.0);
    //         }
    //         entity.damage(world.getDamageSources().explosion(null, null), damage);
    //     }
    // }

    private static void createFireShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1,
                                            ServerPlayerEntity p2) {
        double radius = 50.0;
        Box damageBox = new Box(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);

        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 1, false, false, true));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 1, false, false, true));

        for (Entity entity : world.getOtherEntities(null, damageBox)) {
            if (entity == p1 || entity == p2)
                continue;

            double dist = entity.getPos().distanceTo(pos);
            if (dist > radius)
                continue;

            double knockbackStrength = (1.0 - dist / radius) * 15.0;
            Vec3d knockDir = entity.getPos().subtract(pos).normalize();

            entity.addVelocity(
                    knockDir.x * knockbackStrength,
                    knockbackStrength * 1.5,
                    knockDir.z * knockbackStrength);
            entity.velocityModified = true;
        }

        Random rand = new Random();
        for (int i = 0; i < 200; i++) {
            double particleRadius = rand.nextDouble() * 20.0;
            double particleAngle = rand.nextDouble() * 2 * Math.PI;
            double px = pos.x + Math.cos(particleAngle) * particleRadius;
            double pz = pos.z + Math.sin(particleAngle) * particleRadius;
            double py = pos.y + rand.nextDouble() * 3.0;

            world.spawnParticles(ParticleTypes.FLAME, px, py, pz, 1, 0, 0.5, 0, 0.05);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, 0.3, 0, 0.03);
        }
    }

    private static void broadcastChargeCancel(ServerPlayerEntity player) {
        if (player == null) return;
        NormalFacingDapHandler.clearConfirm(player.getUuid(), null);
        ChargeSyncPayload payload = new ChargeSyncPayload(player.getUuid(), 0f, 0f, false);
        MinecraftServer server = player.getServer();
        if (server == null) return; // Should never happen, but just in case
        for (ServerPlayerEntity other : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(other, payload);
        }

    }

    public static void broadcastWhiffCooldown(ServerPlayerEntity player) {
        if (player == null)
            return;
        WhiffCooldownPayload payload = new WhiffCooldownPayload(whiffCooldownMs());
        ServerPlayNetworking.send(player, payload);

        PoseNetworking.broadcastAnimState(player,
                com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
    }

    // public static void broadcastImpactFrame(ServerPlayerEntity p1, ServerPlayerEntity p2, int durationMs,
    //                                         boolean grayscale) {
    //     ImpactFramePayload payload = new ImpactFramePayload(durationMs, grayscale);
    //     if (p1 != null)
    //         ServerPlayNetworking.send(p1, payload);
    //     if (p2 != null)
    //         ServerPlayNetworking.send(p2, payload);

    //     if (p1 != null) {
    //         for (ServerPlayerEntity nearby : PlayerLookup.around(p1.getServerWorld(), p1.getPos(), 20)) {
    //             if (nearby != p1 && nearby != p2) {
    //                 ServerPlayNetworking.send(nearby, payload);
    //             }
    //         }
    //     }
    // }

    private static boolean arePlayersFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d p1Pos = p1.getPos();
        Vec3d p2Pos = p2.getPos();
        Vec3d p1ToP2 = new Vec3d(p2Pos.x - p1Pos.x, 0, p2Pos.z - p1Pos.z).normalize();
        Vec3d p2ToP1 = p1ToP2.negate();

        double yaw1Rad = Math.toRadians(p1.getYaw());
        Vec3d look1 = new Vec3d(-Math.sin(yaw1Rad), 0, Math.cos(yaw1Rad));
        double yaw2Rad = Math.toRadians(p2.getYaw());
        Vec3d look2 = new Vec3d(-Math.sin(yaw2Rad), 0, Math.cos(yaw2Rad));

        double dot1 = look1.dotProduct(p1ToP2);
        double dot2 = look2.dotProduct(p2ToP1);

        return dot1 > -0.3 && dot2 > -0.3;
    }

    private static double getMaxRecentSpeed(UUID playerId) {
        LinkedList<Double> history = speedHistory.get(playerId);
        if (history == null || history.isEmpty())
            return 0.0;

        double maxSpeed = 0.0;
        for (Double speed : history) {
            if (speed > maxSpeed)
                maxSpeed = speed;
        }
        return maxSpeed;
    }

    private static Vec3d getEffectiveVelocity(ServerPlayerEntity player) {
        if (player.hasVehicle()) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null) return vehicle.getVelocity();
        }
        return player.getVelocity();
    }

    public static float getChargePercent(UUID playerId) {
        Long startTime = chargeStartTime.get(playerId);
        if (startTime == null) return 0f;

        long elapsed = System.currentTimeMillis() - startTime;
        return Math.min(1.0f, (float) elapsed / chargeTimeMs());
    }

    // public static float getFireLevel(UUID playerId) {
    //     return fireLevel.getOrDefault(playerId, 0f);
    // }

    public static boolean isCharging(UUID playerId) {
        return chargeStartTime.containsKey(playerId);
    }

    public static boolean isFullyCharged(UUID playerId) {
        Long startTime = chargeStartTime.get(playerId);
        if (startTime == null)
            return false;
        long chargeTime = System.currentTimeMillis() - startTime;
        float chargePercent = Math.min(chargeTime / (float) chargeTimeMs(), 1.0f);
        return chargePercent >= 0.8f;
    }

    // public static Long getChargeStartTime(UUID playerId) {
    //     return chargeStartTime.get(playerId);
    // }

    // public static void resetChargeStartTime(UUID playerId) {
    //     chargeStartTime.put(playerId, System.currentTimeMillis());
    // }

    private static boolean isOnCooldown(UUID uuid) {
        Long cooldownEnd = cooldowns.get(uuid);
        if (cooldownEnd == null)
            return false;

        long now = System.currentTimeMillis();

        if (cooldownEnd - now > 10000) {
            cooldowns.remove(uuid);
            return false;
        }

        return now < cooldownEnd;
    }

    public static void cleanup(UUID uuid) {
        chargeStartTime.remove(uuid);
        releaseTime.remove(uuid);
        waitingForPartner.remove(uuid);
        cooldowns.remove(uuid);
        speedHistory.remove(uuid);
        fireStartTime.remove(uuid);
        fireGraceTime.remove(uuid);
        fireLevel.remove(uuid);
        impactFreezeTicks.remove(uuid);
        blockingAnimEndTime.remove(uuid);

        perfectDapStartTime.remove(uuid);
        perfectDapPartner.remove(uuid);
        perfectDapFreezeEnd.remove(uuid);
        perfectDapImpactSent.remove(uuid);
        comboCooldown.remove(uuid);
        net.minecraft.entity.decoration.ArmorStandEntity pStand = perfectDapArmorStands.remove(uuid);
        if (pStand != null && !pStand.isRemoved())
            pStand.discard();

        fireDapStartTime.remove(uuid);
        fireDapPartner.remove(uuid);
        inFireDapHit.remove(uuid);
        fireCircleSpawned.remove(uuid);
        fireDapComboRequestTime.remove(uuid);
        fireDapComboFreezeEnd.remove(uuid);
        pendingFireArmImpacts.remove(uuid);
        pendingFireTornadoSpawns.remove(uuid);
        fireComboActive.remove(uuid);
        net.minecraft.entity.decoration.ArmorStandEntity fStand = fireDapArmorStands.remove(uuid);
        if (fStand != null && !fStand.isRemoved())
            fStand.discard();

        heavenPlayers.remove(uuid);
        heavenReady.remove(uuid);
        fireMaxedStartTime.remove(uuid);

        DapSessionManager.removeSessionForPlayer(uuid);

        DapComboChain.cancelCombo(uuid);

        PerfectDapComboHandler.cancelCombo(uuid);
    }

    public static boolean isInBlockingAnimation(UUID uuid) {
        Long endTime = blockingAnimEndTime.get(uuid);
        if (endTime == null)
            return false;
        if (System.currentTimeMillis() >= endTime) {
            blockingAnimEndTime.remove(uuid);
            return false;
        }
        return true;
    }

    public static void setBlockingAnimation(UUID uuid, long durationMs) {
        blockingAnimEndTime.put(uuid, System.currentTimeMillis() + durationMs);
    }

    // public static boolean isPerfectDapFrozen(UUID playerId) {
    //     return perfectDapFreezeEnd.containsKey(playerId);
    // }

    // public static boolean isFireDapFrozen(UUID playerId) {
    //     return fireDapComboFreezeEnd.containsKey(playerId);
    // }

    // public static boolean isInFireDapBlockingState(UUID playerId) {
    //     return inFireDapHit.getOrDefault(playerId, false) || fireDapComboFreezeEnd.containsKey(playerId);
    // }

    public static boolean isInComboCooldown(UUID playerId) {
        Long cooldownEnd = comboCooldown.get(playerId);
        if (cooldownEnd == null) return false;

        long now = System.currentTimeMillis();
        if (now < cooldownEnd) return true;
        else {
            comboCooldown.remove(playerId);
            return false;
        }
    }

    public static void startFireDap(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();

        fireComboActive.put(id1, true);
        fireComboActive.put(id2, true);

        fireDapStartTime.put(id1, now);
        fireDapStartTime.put(id2, now);
        fireCircleSpawned.put(id1, false);
        fireCircleSpawned.put(id2, false);
        fireDapPartner.put(id1, id2);
        fireDapPartner.put(id2, id1);
        inFireDapHit.put(id1, true);
        inFireDapHit.put(id2, true);

        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.2,
                DapSession.DapType.FIRE_DAP);

        if (session == null) {

            fireComboActive.remove(id1);
            fireComboActive.remove(id2);
            fireDapStartTime.remove(id1);
            fireDapStartTime.remove(id2);
            return;
        }

        session.onComplete(() -> startFireDapAnimation(p1, p2));

    }

    private static void startFireDapAnimation(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        ServerWorld world = p1.getServerWorld();

        long now = System.currentTimeMillis();
        fireDapStartTime.put(id1, now);
        fireDapStartTime.put(id2, now);

        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        Vec3d p1Hand = p1.getPos().add(0, 1.4, 0);
        Vec3d p2Hand = p2.getPos().add(0, 1.4, 0);
        Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);

        net.minecraft.entity.decoration.ArmorStandEntity stand = new net.minecraft.entity.decoration.ArmorStandEntity(
                net.minecraft.entity.EntityType.ARMOR_STAND, world);
        stand.setPosition(handMid.x, handMid.y, handMid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setFireTicks(0);
        world.spawnEntity(stand);
        fireDapArmorStands.put(id1, stand);

        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_HIT.ordinal());
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_HIT.ordinal());

        MinecraftServer server = p1.getServer();
        if (server != null)
            for (ServerPlayerEntity player : p1.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id1, true));
                ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id2, true));
            }

        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 255, false, false));
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false));

        ServerPlayNetworking.send(p1, new FireDapWindowPayload());
        ServerPlayNetworking.send(p2, new FireDapWindowPayload());

    }

    private static void onFireDapJPress(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        if (!inFireDapHit.getOrDefault(playerId, false)) return;
        long now = System.currentTimeMillis();
        Long startTime = fireDapStartTime.get(playerId);
        if (startTime == null) return;

        long elapsed = now - startTime;
        if (elapsed < FIRE_J_WINDOW_START || elapsed > FIRE_J_WINDOW_END) {
            player.sendMessage(net.minecraft.text.Text.literal("§cToo early/late for combo!"), true);
            return;
        }
        DapFusionHandler.cancelForJCombo(playerId);

        UUID partnerId = fireDapPartner.get(playerId);
        if (partnerId == null) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        if (partnerId.equals(playerId)) {
            fireDapComboRequestTime.put(playerId, now);
            ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
            if (partner != null) executeFireDapCombo(player, partner);
            return;
        }
        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
        if (partner == null) return;

        fireDapComboRequestTime.put(playerId, now);
        Long partnerRequestTime = fireDapComboRequestTime.get(partnerId);
        if (partnerRequestTime != null) {
            long diff = Math.abs(now - partnerRequestTime);
            if (diff < 1000) executeFireDapCombo(player, partner);
        }
    }

    private static void executeFireDapCombo(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();

        inFireDapHit.remove(id1);
        inFireDapHit.remove(id2);
        fireDapComboRequestTime.remove(id1);
        fireDapComboRequestTime.remove(id2);

        DapSessionManager.removeSessionForPlayer(id1);

        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        fireDapComboFreezeEnd.put(id1, now + FIRE_COMBO_FREEZE_MS);
        fireDapComboFreezeEnd.put(id2, now + FIRE_COMBO_FREEZE_MS);

        MinecraftServer server = p1.getServer();
        if (server != null)
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, new FireDapFreezePayload(id1, true));
                ServerPlayNetworking.send(player, new FireDapFreezePayload(id2, true));
            }

        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_COMBO_P1.ordinal());
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_COMBO_P2.ordinal());

        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);
        p1.getServerWorld().playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 2.0f, 0.8f);

        for (ServerPlayerEntity player : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id1, true));
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id2, true));
        }

        p1.sendMessage(net.minecraft.text.Text.literal("§c§l🔥 DIVINE FLAME COMBO! 🔥"), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§c§l🔥 DIVINE FLAME COMBO! 🔥"), true);

        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 255, false, false));
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 255, false, false));

        spawnVerticalFireWalls(p1, p2);

        auraBeamsActive = true;
        auraBeamStartTime = now;
        auraBeamPlayer1 = id1;
        auraBeamPlayer2 = id2;

        pendingFireArmImpacts.put(id1, new FireDapScheduledEvent(p1, p2, now + FIRE_COMBO_ARM_IMPACT));

        pendingFireTornadoSpawns.put(id1, new FireDapScheduledEvent(p1, p2, now + FIRE_COMBO_TORNADO));

    }

    private static void spawnAnimatedAuraBeam(ServerPlayerEntity player, long elapsed) {
        ServerWorld world = player.getServerWorld();
        Vec3d playerPos = player.getPos();

        double phase = (elapsed % 1000) / 1000.0;

        for (int y = 0; y < 70; y++) {
            double currentY = playerPos.y + y;

            double coreAngle = (y * 20 + elapsed * 0.5) % 360;
            double coreRad = Math.toRadians(coreAngle);
            double coreRadius = 0.3;

            double coreX = playerPos.x + Math.cos(coreRad) * coreRadius;
            double coreZ = playerPos.z + Math.sin(coreRad) * coreRadius;

            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, coreX, currentY, coreZ, 1, 0.05, 0.05, 0.05, 0.01);
            if (y % 3 == 0) {
                world.spawnParticles(ParticleTypes.ENCHANT, coreX, currentY, coreZ, 2, 0.1, 0.1, 0.1, 0.5);
            }

            double midAngle = (y * 15 - elapsed * 0.3) % 360;
            double midRad = Math.toRadians(midAngle);
            double midRadius = 0.6;

            double midX = playerPos.x + Math.cos(midRad) * midRadius;
            double midZ = playerPos.z + Math.sin(midRad) * midRadius;

            world.spawnParticles(ParticleTypes.FLAME, midX, currentY, midZ, 2, 0.1, 0.1, 0.1, 0.02);

            if (y % 2 == 0) {
                double outerAngle = (y * 10 + elapsed * 0.2) % 360;
                for (int i = 0; i < 4; i++) {
                    double angle = outerAngle + (i * 90);
                    double rad = Math.toRadians(angle);
                    double outerRadius = 0.9 + Math.sin(phase * Math.PI * 2) * 0.2;

                    double x = playerPos.x + Math.cos(rad) * outerRadius;
                    double z = playerPos.z + Math.sin(rad) * outerRadius;

                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, currentY, z, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }

            if (y % 5 == 0) {
                double waveOffset = Math.sin((y / 70.0 + phase) * Math.PI * 2) * 0.5;
                world.spawnParticles(ParticleTypes.END_ROD,
                        playerPos.x + waveOffset, currentY, playerPos.z,
                        1, 0.1, 0.1, 0.1, 0.02);
            }
        }

        for (double angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle + elapsed * 0.5);
            double radius = 3.0;

            double x = playerPos.x + Math.cos(rad) * radius;
            double z = playerPos.z + Math.sin(rad) * radius;

            world.spawnParticles(ParticleTypes.FLAME, x, playerPos.y + 0.1, z, 3, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, playerPos.y + 0.1, z, 2, 0.1, 0.1, 0.1, 0.03);

            if (angle % 30 == 0) {
                world.spawnParticles(ParticleTypes.END_ROD, x, playerPos.y + 0.1, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void spawnVerticalFireWalls(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);

        double wallDistance = 5.0;
        int wallHeight = 50;
        int wallLength = 10;

        for (int x = -wallLength / 2; x <= wallLength / 2; x++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + x;
                double wy = midpoint.y + y;
                double wz = midpoint.z + wallDistance;

                if (Math.abs(x) <= 1.5)
                    continue;

                world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }

        for (int x = -wallLength / 2; x <= wallLength / 2; x++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + x;
                double wy = midpoint.y + y;
                double wz = midpoint.z - wallDistance;

                if (Math.abs(x) <= 1.5)
                    continue;

                world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }

        for (int z = -wallLength / 2; z <= wallLength / 2; z++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + wallDistance;
                double wy = midpoint.y + y;
                double wz = midpoint.z + z;

                if (Math.abs(z) <= 1.5)
                    continue;

                world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }

        for (int z = -wallLength / 2; z <= wallLength / 2; z++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x - wallDistance;
                double wy = midpoint.y + y;
                double wz = midpoint.z + z;

                if (Math.abs(z) <= 1.5)
                    continue;

                world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }

    }

    private static void spawnFireCircle(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, midpoint.x, midpoint.y + 1, midpoint.z, 3, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION, midpoint.x, midpoint.y + 1, midpoint.z, 20, 2.0, 2.0, 2.0, 0);

        int radius = 20;
        for (double angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            double x = midpoint.x + radius * Math.cos(rad);
            double z = midpoint.z + radius * Math.sin(rad);

            world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + 0.1, z, 20, 0.4, 1.2, 0.4, 0.05);
            world.spawnParticles(ParticleTypes.LAVA, x, midpoint.y + 0.1, z, 10, 0.3, 0.6, 0.3, 0.02);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, midpoint.y + 0.1, z, 15, 0.5, 1.8, 0.5, 0.1);

            if (angle % 20 == 0) {
                for (int h = 0; h < 10; h++) {
                    world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + h, z, 25, 0.6, 0.6, 0.6, 0.12);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y + h, z, 12, 0.4, 0.4, 0.4, 0.06);
                }
            }
        }

        for (int height = 0; height < 15; height++) {
            for (double angle = 0; angle < 360; angle += 10) {
                double rad = Math.toRadians(angle + height * 25);
                double distance = 3 + height * 0.4;
                double x = midpoint.x + distance * Math.cos(rad);
                double z = midpoint.z + distance * Math.sin(rad);

                world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + height, z, 5, 0.3, 0.3, 0.3, 0.04);
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, midpoint.y + height, z, 3, 0.2, 0.2, 0.2, 0.02);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y + height, z, 2, 0.15, 0.15, 0.15,
                        0.01);
            }
        }

        for (int h = 0; h < 20; h++) {
            world.spawnParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y + h, midpoint.z, 40, 2.0, 0.5, 2.0, 0.15);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y + h, midpoint.z, 20, 1.5, 0.3,
                    1.5,
                    0.1);
            world.spawnParticles(ParticleTypes.LAVA, midpoint.x, midpoint.y + h, midpoint.z, 15, 1.0, 0.2, 1.0, 0.05);
        }

        Random random = new Random();
        // int fireCount = 0;
        for (int i = 0; i < 300; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 4 + random.nextDouble() * 16;

            double x = midpoint.x + Math.cos(angle) * distance;
            double z = midpoint.z + Math.sin(angle) * distance;

            BlockPos pos = new BlockPos((int) x, (int) midpoint.y, (int) z);
            BlockPos above = pos.up();

            if (world.getBlockState(pos).isSolidBlock(world, pos) && world.getBlockState(above).isAir()) {
                // world.setBlockState(above, net.minecraft.block.Blocks.FIRE.getDefaultState());
                // fireCount++;

                world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + 0.5, z, 20, 0.5, 1.0, 0.5, 0.08);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, midpoint.y + 0.5, z, 10, 0.4, 0.8, 0.4, 0.06);
                world.spawnParticles(ParticleTypes.LAVA, x, midpoint.y + 0.1, z, 5, 0.3, 0.2, 0.3, 0.02);
            }
        }

        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 2.5f, 0.5f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 3.5f, 0.6f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 2.0f, 0.7f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0f, 1.2f);

    }

    private static void executeFireArmImpact(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();

        Vec3d midpoint;
        net.minecraft.entity.decoration.ArmorStandEntity stand = fireDapArmorStands.get(p1.getUuid());
        if (stand != null && !stand.isRemoved()) {
            midpoint = stand.getPos();
        } else {

            midpoint = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1.2, 0);
        }

        world.spawnParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y, midpoint.z,
                3, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y, midpoint.z,
                2, 0.2, 0.2, 0.2, 0.08);

        Vec3d underArms = new Vec3d(midpoint.x, midpoint.y - 0.5, midpoint.z);
        world.spawnParticles(ParticleTypes.FLAME, underArms.x, underArms.y, underArms.z,
                8, 0.5, 0.2, 0.5, 0.12);
        world.spawnParticles(ParticleTypes.LAVA, underArms.x, underArms.y, underArms.z,
                4, 0.4, 0.15, 0.4, 0.05);

        for (int ring = 1; ring <= 15; ring++) {
            double radius = ring * 1.5;
            for (double angle = 0; angle < 360; angle += 8) {
                double rad = Math.toRadians(angle);
                double x = midpoint.x + radius * Math.cos(rad);
                double z = midpoint.z + radius * Math.sin(rad);

                world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y, z, 3, 0.2, 0.5, 0.2, 0.05);
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, midpoint.y, z, 2, 0.15, 0.3, 0.15, 0.03);
            }
        }

        double pillarStartY = midpoint.y + 2.0;
        for (int h = 0; h < 30; h++) {

            world.spawnParticles(ParticleTypes.FLAME, midpoint.x, pillarStartY + h, midpoint.z,
                    25, 1.5, 0.5, 1.5, 0.15);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, pillarStartY + h, midpoint.z,
                    15, 1.2, 0.4, 1.2, 0.1);
            world.spawnParticles(ParticleTypes.LAVA, midpoint.x, pillarStartY + h, midpoint.z,
                    10, 1.0, 0.3, 1.0, 0.08);

            if (h < 5) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, midpoint.x, pillarStartY + h, midpoint.z,
                        20, 2.0, 0.5, 2.0, 0.12);
            }
        }

        for (double angle = 0; angle < 360; angle += 3) {
            double rad = Math.toRadians(angle);
            double radius = 3.0;

            double x = midpoint.x + Math.cos(rad) * radius;
            double z = midpoint.z + Math.sin(rad) * radius;

            world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y, z, 5, 0.1, 0.1, 0.1, 0.08);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y, z, 3, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.END_ROD, x, midpoint.y, z, 1, 0, 0, 0, 0);

            if (angle % 15 == 0) {
                for (int h = 0; h < 10; h++) {
                    world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + h, z, 2, 0.05, 0.05, 0.05, 0.02);
                }
            }
        }

        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        Box searchBox = new Box(
                midpoint.x - 30, midpoint.y - 30, midpoint.z - 30,
                midpoint.x + 30, midpoint.y + 30, midpoint.z + 30);

        for (Entity entity : world.getOtherEntities(null, searchBox)) {

            if (entity.getUuid().equals(id1) || entity.getUuid().equals(id2)) {
                continue;
            }

            Vec3d entityPos = entity.getPos();
            double distance = entityPos.distanceTo(midpoint);

            if (distance < 30 && distance > 0.1) {

                Vec3d direction = entityPos.subtract(midpoint).normalize();

                double strength = (30 - distance) / 30.0 * 3.0;

                entity.setVelocity(
                        direction.x * strength,
                        0.8 + (strength * 0.5),
                        direction.z * strength);
                entity.velocityModified = true;

                if (entity instanceof net.minecraft.entity.LivingEntity living) {
                    float damage = (float) ((30 - distance) / 30.0 * 20.0);
                    living.damage(living.getDamageSources().explosion(null, null), damage);
                }
            }
        }

        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.GALACTIC_DAP, SoundCategory.PLAYERS, 3.0f, 1.0f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.5f, 0.8f);

    }

    private static void spawnFireTornado(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);

        tornadoStartTime = System.currentTimeMillis();
        tornadoActive = true;
        tornadoCenter = midpoint;
        tornadoWorld = world;
        activeTornadoSwirls.clear();

        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 4.0f, 0.4f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 3.0f, 0.6f);
    }

    private static void teleportFireDapFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d p1Pos = p1.getPos();
        Vec3d p2Pos = p2.getPos();
        double distance = p1Pos.distanceTo(p2Pos);

        Vec3d direction = p2Pos.subtract(p1Pos).normalize();
        Vec3d midpoint = p1Pos.add(p2Pos).multiply(0.5);

        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawP2 = yawP1 + 180;

        if (Math.abs(distance - 1.2) > 0.05) {
            Vec3d offset = direction.multiply(1.2 / 2.0);
            Vec3d targetP1 = midpoint.subtract(offset);
            Vec3d targetP2 = midpoint.add(offset);

            p1.teleport(p1.getServerWorld(), targetP1.x, targetP1.y, targetP1.z, yawP1, 0.0f);
            p2.teleport(p2.getServerWorld(), targetP2.x, targetP2.y, targetP2.z, yawP2, 0.0f);
        } else {
            p1.teleport(p1.getServerWorld(), p1Pos.x, p1Pos.y, p1Pos.z, yawP1, 0.0f);
            p2.teleport(p2.getServerWorld(), p2Pos.x, p2Pos.y, p2Pos.z, yawP2, 0.0f);
        }
        p1.setYaw(yawP1);
        p1.setBodyYaw(yawP1);
        p1.setHeadYaw(yawP1);
        p2.setYaw(yawP2);
        p2.setBodyYaw(yawP2);
        p2.setHeadYaw(yawP2);
    }

    // private static void teleportPerfectDapFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2,
    //                                                       double targetDistance) {
    //     Vec3d p1Pos = p1.getPos();
    //     Vec3d p2Pos = p2.getPos();
    //     double distance = p1Pos.distanceTo(p2Pos);

    //     Vec3d direction = p2Pos.subtract(p1Pos).normalize();
    //     Vec3d midpoint = p1Pos.add(p2Pos).multiply(0.5);

    //     double dx = p2Pos.x - p1Pos.x;
    //     double dz = p2Pos.z - p1Pos.z;
    //     float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
    //     float yawP2 = yawP1 + 180;

    //     if (Math.abs(distance - targetDistance) > 0.05) {
    //         Vec3d offset = direction.multiply(targetDistance / 2.0);
    //         Vec3d targetP1 = midpoint.subtract(offset);
    //         Vec3d targetP2 = midpoint.add(offset);

    //         p1.teleport(p1.getServerWorld(), targetP1.x, targetP1.y, targetP1.z, yawP1, 0.0f);
    //         p2.teleport(p2.getServerWorld(), targetP2.x, targetP2.y, targetP2.z, yawP2, 0.0f);

    //         p1.setYaw(yawP1);
    //         p1.setBodyYaw(yawP1);
    //         p1.setHeadYaw(yawP1);
    //         p2.setYaw(yawP2);
    //         p2.setBodyYaw(yawP2);
    //         p2.setHeadYaw(yawP2);
    //     } else {

    //         p1.teleport(p1.getServerWorld(), p1Pos.x, p1Pos.y, p1Pos.z, yawP1, 0.0f);
    //         p2.teleport(p2.getServerWorld(), p2Pos.x, p2Pos.y, p2Pos.z, yawP2, 0.0f);

    //         p1.setYaw(yawP1);
    //         p1.setBodyYaw(yawP1);
    //         p1.setHeadYaw(yawP1);
    //         p2.setYaw(yawP2);
    //         p2.setBodyYaw(yawP2);
    //         p2.setHeadYaw(yawP2);
    //     }
    // }

    private static void smoothDapDescent(ServerPlayerEntity player,
                                         net.minecraft.entity.decoration.ArmorStandEntity stand) {
        if (stand == null || stand.isRemoved())
            return;

        if (fireComboActive.getOrDefault(player.getUuid(), false)) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        Vec3d playerPos = player.getPos();
        Vec3d standPos = stand.getPos();

        double distanceToPlayer = standPos.distanceTo(playerPos);
        if (distanceToPlayer < 0.8) {

            Vec3d direction = standPos.subtract(playerPos).normalize();

            Vec3d newStandPos = playerPos.add(direction.multiply(1.2));
            stand.setPosition(newStandPos.x, newStandPos.y, newStandPos.z);

            return;
        }

        BlockPos feetPos = BlockPos.ofFloored(playerPos.x, playerPos.y - 0.1, playerPos.z);
        boolean solidBlockBelow = !world.getBlockState(feetPos).isAir() &&
                world.getBlockState(feetPos).isSolidBlock(world, feetPos);

        if (solidBlockBelow) {

            return;
        }

        BlockPos checkPos = BlockPos.ofFloored(playerPos.x, playerPos.y - 1.0, playerPos.z);
        int blocksChecked = 0;

        while (blocksChecked < 10) {
            if (!world.getBlockState(checkPos).isAir() &&
                    world.getBlockState(checkPos).isSolidBlock(world, checkPos)) {
                break;
            }
            checkPos = checkPos.down();
            blocksChecked++;
        }

        double groundY = checkPos.getY() + 1.0;

        if (playerPos.y <= groundY + 0.1) {
            return;
        }

        double newY = playerPos.y - 0.12;

        newY = Math.max(newY, groundY);

        if (newY < playerPos.y && newY >= groundY) {
            player.setPosition(playerPos.x, newY, playerPos.z);

            stand.setPosition(standPos.x, newY + 1.4, standPos.z);
        }
    }
}
