package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.util.math.Vec3d;
import java.util.*;
public class DapFusionHandler {
    public static final long FUSION_G_WINDOW_START = 830;
    public static final long FUSION_G_WINDOW_END   = 2200;
    private static final long WALK_QTE_WINDOW_STAGE_1 = 450;
    private static final long WALK_QTE_WINDOW_STAGE_2 = 300;
    private static final long WALK_QTE_WINDOW_STAGE_3 = 200;
    public static final long TIMING_BAR_TOTAL_MS = 1800;
    public static final long TIMING_BAR_TOTAL_MS_CLIENT = TIMING_BAR_TOTAL_MS;
    private static final long TIMING_HIT_WINDOW_WALK   = 350;
    private static final long[] TIMING_HIT_WINDOW_FUSION = {300, 260, 220};
    private static final long TIMING_GRACE_MS = 80;
    private static final double WALK_START_DISTANCE = 6.0;
    private static final double WALK_STEP_DISTANCE = 1.5;
    private static final double WALK_STOP_DISTANCE = 1.3;
    private static final int SMOOTH_TP_TICKS = 5;
    private static final long[] FUSION_QTE_WINDOWS = {
            500, 450, 400, 350, 300, 250, 200, 175, 150, 100
    };
    private static final long FUSION_STAGE_GAP_MS = 600;
    public enum FusionPhase {
        AWAITING_G,
        WALK_QTE,
        FUSION_QTE,
        FUSED,
        FAILED
    }
    public static class FusionSession {
        public final UUID p1Id, p2Id;
        public ServerPlayerEntity p1Ref, p2Ref;
        public final ServerWorld world;
        public FusionPhase phase = FusionPhase.AWAITING_G;
        public boolean p1PressedG = false;
        public boolean p2PressedG = false;
        public long gWindowOpenTime;
        public int walkStage = 0;
        public Vec3d p1WalkPos;
        public Vec3d p2WalkPos;
        public Vec3d p1SmoothStart;
        public Vec3d p2SmoothStart;
        public int smoothTpTick = 0;
        public boolean walkQteOpen = false;
        public boolean p1WalkPressed = false;
        public boolean p2WalkPressed = false;
        public long walkQteOpenTime = 0;
        public String walkExpectedButton;
        public int fusionStage = 0;
        public boolean p1FusionPressed = false;
        public boolean p2FusionPressed = false;
        public long fusionQteOpenTime = 0;
        public String fusionExpectedButton;
        public boolean fusionQteOpen = false;
        public long lastFusionStageEnd = 0;
        public long walkHitWindowStart = 0;
        public long walkHitWindowEnd   = 0;
        public long fusionHitWindowStart = 0;
        public long fusionHitWindowEnd   = 0;
        public boolean walkIsTimingBar   = false;
        public boolean fusionIsTimingBar = false;
        public boolean isSolo() { return p1Id.equals(p2Id); }
        private static final String[] BUTTONS = {"G", "H"}; // WAHT SIGMA
        private static final Random RNG = new Random();
        FusionSession(ServerPlayerEntity p1, ServerPlayerEntity p2, long now) {
            this.p1Id = p1.getUuid();
            this.p2Id = p2.getUuid();
            this.p1Ref = p1;
            this.p2Ref = p2;
            this.world = p1.getServerWorld();
            this.gWindowOpenTime = now;
        }
        String randomButton() {
            if (CoopMovesConfig.get().easyFusionTest) return "G";
            return BUTTONS[RNG.nextInt(2)];
        }
        long randomGreenZoneStart(long hitWindowMs) {
            long minStart = (long)(TIMING_BAR_TOTAL_MS * 0.20);
            long maxStart = (long)(TIMING_BAR_TOTAL_MS * 0.60);
            long range = maxStart - minStart - hitWindowMs;
            if (range <= 0) return minStart;
            return minStart + (long)(RNG.nextDouble() * range);
        }
        long walkQteWindow() {
            if (CoopMovesConfig.get().easyFusionTest) return 800;
            return switch (walkStage) {
                case 0 -> WALK_QTE_WINDOW_STAGE_1;
                case 1 -> TIMING_BAR_TOTAL_MS + 300;
                default -> WALK_QTE_WINDOW_STAGE_3;
            };
        }
        long fusionQteWindow() {
            if (CoopMovesConfig.get().easyFusionTest) return 800;
            return FUSION_QTE_WINDOWS[Math.min(fusionStage, 9)];
        }
    }
    private static final Map<UUID, FusionSession> sessions = new HashMap<>();
    private static final Map<UUID, UUID> fusedPairs = new HashMap<>();
    private static final Map<UUID, Vec3d[]> smoothTpTargets = new HashMap<>();
    private static final Map<UUID, Integer> smoothTpProgress = new HashMap<>();
    public record FusionPhasePayload(UUID p1, UUID p2, int phase) implements CustomPayload {
        public static final Id<FusionPhasePayload> ID =
                new Id<>(Identifier.of("cooptest", "fusion_phase"));
        public static final PacketCodec<PacketByteBuf, FusionPhasePayload> CODEC = PacketCodec.of(
                (payload, buf) -> { buf.writeUuid(payload.p1); buf.writeUuid(payload.p2); buf.writeInt(payload.phase); },
                buf -> new FusionPhasePayload(buf.readUuid(), buf.readUuid(), buf.readInt())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record FusionQTEPayload(UUID playerId, String button, int stage, long windowStartMs, long windowEndMs, boolean open, int type) implements CustomPayload {
        public static final Id<FusionQTEPayload> ID =
                new Id<>(Identifier.of("cooptest", "fusion_qte"));
        public static final PacketCodec<PacketByteBuf, FusionQTEPayload> CODEC = PacketCodec.of(
                (p, buf) -> { buf.writeUuid(p.playerId); buf.writeString(p.button);
                    buf.writeInt(p.stage); buf.writeLong(p.windowStartMs); buf.writeLong(p.windowEndMs);
                    buf.writeBoolean(p.open); buf.writeInt(p.type); },
                buf -> new FusionQTEPayload(buf.readUuid(), buf.readString(),
                        buf.readInt(), buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readInt())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record FusionGPressPayload() implements CustomPayload {
        public static final Id<FusionGPressPayload> ID =
                new Id<>(Identifier.of("cooptest", "fusion_g_press"));
        public static final PacketCodec<PacketByteBuf, FusionGPressPayload> CODEC =
                PacketCodec.of((p, buf) -> {}, buf -> new FusionGPressPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record FusionFusedPayload(boolean fused) implements CustomPayload {
        public static final Id<FusionFusedPayload> ID =
                new Id<>(Identifier.of("cooptest", "fusion_fused"));
        public static final PacketCodec<PacketByteBuf, FusionFusedPayload> CODEC =
                PacketCodec.of((p, buf) -> buf.writeBoolean(p.fused),
                        buf -> new FusionFusedPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record FusionUnfusePayload() implements CustomPayload {
        public static final Id<FusionUnfusePayload> ID =
                new Id<>(Identifier.of("cooptest", "fusion_unfuse"));
        public static final PacketCodec<PacketByteBuf, FusionUnfusePayload> CODEC =
                PacketCodec.of((p, buf) -> {}, buf -> new FusionUnfusePayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record FusionBlackScreenPayload(boolean active) implements CustomPayload {
        public static final Id<FusionBlackScreenPayload> ID =
                new Id<>(Identifier.of("cooptest", "fusion_black_screen"));
        public static final PacketCodec<PacketByteBuf, FusionBlackScreenPayload> CODEC =
                PacketCodec.of((p, buf) -> buf.writeBoolean(p.active),
                        buf -> new FusionBlackScreenPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(FusionPhasePayload.ID, FusionPhasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionQTEPayload.ID, FusionQTEPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionBlackScreenPayload.ID, FusionBlackScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionFusedPayload.ID, FusionFusedPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FusionGPressPayload.ID, FusionGPressPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FusionUnfusePayload.ID, FusionUnfusePayload.CODEC);
    }
    public static void registerClientPayloads() {
        try { PayloadTypeRegistry.playS2C().register(FusionPhasePayload.ID, FusionPhasePayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playS2C().register(FusionQTEPayload.ID, FusionQTEPayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playS2C().register(FusionBlackScreenPayload.ID, FusionBlackScreenPayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playS2C().register(FusionFusedPayload.ID, FusionFusedPayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playC2S().register(FusionGPressPayload.ID, FusionGPressPayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playC2S().register(FusionUnfusePayload.ID, FusionUnfusePayload.CODEC); } catch (Exception ignored) {}
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(FusionGPressPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleGPressFromClient(context.player()));
        });
        ServerPlayNetworking.registerGlobalReceiver(FusionUnfusePayload.ID, (payload, context) -> {
            context.server().execute(() -> handleUnfuseRequest(context.player()));
        });
        ServerTickEvents.END_SERVER_TICK.register(DapFusionHandler::tick);
    }
    public static void openFusionWindow(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid(), id2 = p2.getUuid();
        if (sessions.containsKey(id1) || sessions.containsKey(id2)) return;
        long now = System.currentTimeMillis();
        FusionSession session = new FusionSession(p1, p2, now);
        sessions.put(id1, session);
        sessions.put(id2, session);
        broadcast(session, new FusionPhasePayload(id1, id2, 0));
    }
    public static void cancelForJCombo(UUID playerId) {
        FusionSession s = sessions.get(playerId);
        if (s == null) return;
        if (s.phase != FusionPhase.AWAITING_G) return;
        cleanupSession(s);
    }
    public static boolean onQTEButtonPress(ServerPlayerEntity player, String button) {
        FusionSession s = sessions.get(player.getUuid());
        if (s == null) return false;
        if (s.phase == FusionPhase.WALK_QTE && s.walkQteOpen) {
            handleWalkQTEPress(s, player.getUuid(), button);
            return true;
        }
        if (s.phase == FusionPhase.FUSION_QTE && s.fusionQteOpen) {
            handleFusionQTEPress(s, player.getUuid(), button);
            return true;
        }
        return false;
    }
    public static boolean isInFusion(UUID playerId) {
        return sessions.containsKey(playerId);
    }
    public static void cleanup(UUID playerId) {
        FusionSession s = sessions.get(playerId);
        if (s != null) cleanupSession(s);
    }
    public static void handleGPressFromClient(ServerPlayerEntity player) {
        onGPress(player);
    }
    private static void onGPress(ServerPlayerEntity player) {
        FusionSession s = sessions.get(player.getUuid());
        if (s == null || s.phase != FusionPhase.AWAITING_G) return;
        long now = System.currentTimeMillis();
        long elapsed = now - s.gWindowOpenTime;
        if (elapsed < FUSION_G_WINDOW_START || elapsed > FUSION_G_WINDOW_END) {
            player.sendMessage(net.minecraft.text.Text.literal("§cToo early/late for fusion!"), true);
            return;
        }
        if (player.getUuid().equals(s.p1Id)) {
            s.p1PressedG = true;
            if (s.isSolo()) s.p2PressedG = true;
        } else {
            s.p2PressedG = true;
        }
        if (s.p1PressedG && s.p2PressedG) {
            startWalkPhase(s);
        }
    }
    private static void startWalkPhase(FusionSession s) {
        s.phase = FusionPhase.WALK_QTE;
        s.walkStage = 0;
        DapSessionManager.removeSessionForPlayer(s.p1Id);
        DapSessionManager.removeSessionForPlayer(s.p2Id);
        PoseNetworking.poseStates.put(s.p1Id, PoseState.NONE);
        PoseNetworking.poseStates.put(s.p2Id, PoseState.NONE);
        Vec3d p1Start;
        Vec3d p2Start;
        if (s.isSolo()) {
            Vec3d base = s.p1Ref.getPos();
            p1Start = base.add(2, 0, 0);
            p2Start = base.add(-2, 0, 0);
        } else {
            Vec3d mid = s.p1Ref.getPos().add(s.p2Ref.getPos()).multiply(0.5);
            Vec3d rawDir = s.p1Ref.getPos().subtract(s.p2Ref.getPos()).normalize();
            if (rawDir.lengthSquared() < 0.001) rawDir = new Vec3d(1, 0, 0);
            p1Start = mid.add(rawDir.multiply(3.0));
            p2Start = mid.subtract(rawDir.multiply(3.0));
        }
        s.p1WalkPos = p1Start;
        s.p2WalkPos = p2Start;
        facePlayers(s.p1Ref, s.p2Ref, s.p1WalkPos, s.p2WalkPos);
        Vec3d mid = s.p1WalkPos.add(s.p2WalkPos).multiply(0.5);
        s.world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y + 1, mid.z, 2, 0, 0, 0, 0);
        s.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y + 1, mid.z, 20, 0.5, 0.5, 0.5, 0.3);
        s.world.playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5f, 1.5f);
        freezeBoth(s, true);
        broadcast(s, new FusionPhasePayload(s.p1Id, s.p2Id, 1));
        int auraOrdinal = com.cooptest.client.CoopAnimationHandler.AnimState.AURA_WALK.ordinal();
        try { ServerPlayNetworking.send(s.p1Ref,
                new PoseNetworking.AnimStateSyncPayload(s.p1Id, auraOrdinal)); } catch (Exception ignored) {}
        try { ServerPlayNetworking.send(s.p2Ref,
                new PoseNetworking.AnimStateSyncPayload(s.p2Id, auraOrdinal)); } catch (Exception ignored) {}
        s.world.playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.5f, 1.8f);
        broadcastServer(s, "§d§l✨ FUSION RITUAL BEGUN! §7Hit the QTE to walk forward!");
        scheduleWalkQTE(s);
    }
    private static void facePlayers(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d pos1, Vec3d pos2) {
        double dx = pos2.x - pos1.x;
        double dz = pos2.z - pos1.z;
        float yaw1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float yaw2 = yaw1 + 180f;
        p1.setYaw(yaw1); p1.setBodyYaw(yaw1); p1.setHeadYaw(yaw1);
        p1.prevYaw = yaw1; p1.prevBodyYaw = yaw1; p1.prevHeadYaw = yaw1;
        p2.setYaw(yaw2); p2.setBodyYaw(yaw2); p2.setHeadYaw(yaw2);
        p2.prevYaw = yaw2; p2.prevBodyYaw = yaw2; p2.prevHeadYaw = yaw2;
        p1.teleport(p1.getServerWorld(), pos1.x, pos1.y, pos1.z, yaw1, 0);
        p2.teleport(p2.getServerWorld(), pos2.x, pos2.y, pos2.z, yaw2, 0);
    }
    private static void scheduleWalkQTE(FusionSession s) {
        new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            if (s.p1Ref.getServer() == null) return;
            s.p1Ref.getServer().execute(() -> openWalkQTE(s));
        }).start();
    }
    private static void openWalkQTE(FusionSession s) {
        if (s.phase != FusionPhase.WALK_QTE) return;
        long now = System.currentTimeMillis();
        s.walkQteOpen = true;
        s.p1WalkPressed = false;
        s.p2WalkPressed = false;
        s.walkQteOpenTime = now;
        s.walkExpectedButton = s.randomButton();
        boolean isTimingBar = (s.walkStage == 1) && !CoopMovesConfig.get().easyFusionTest;
        s.walkIsTimingBar = isTimingBar;
        long windowStartMs, windowEndMs;
        int type;
        if (isTimingBar) {
            windowStartMs = s.randomGreenZoneStart(TIMING_HIT_WINDOW_WALK);
            windowEndMs   = windowStartMs + TIMING_HIT_WINDOW_WALK;
            type = 1;
        } else {
            windowStartMs = 0;
            windowEndMs   = s.walkQteWindow();
            type = 0;
        }
        s.walkHitWindowStart = now + windowStartMs - TIMING_GRACE_MS;
        s.walkHitWindowEnd   = now + windowEndMs   + TIMING_GRACE_MS;
        sendFusionQTE(s.p1Ref, s.walkExpectedButton, s.walkStage + 1, windowStartMs, windowEndMs, true, type);
        sendFusionQTE(s.p2Ref, s.walkExpectedButton, s.walkStage + 1, windowStartMs, windowEndMs, true, type);
    }
    private static void handleWalkQTEPress(FusionSession s, UUID presserId, String button) {
        if (!s.walkQteOpen) return;
        if (!button.equals(s.walkExpectedButton)) {
            failWalkPhase(s, "§cWrong button! Fusion cancelled!");
            return;
        }
        if (s.walkIsTimingBar) {
            long now = System.currentTimeMillis();
            if (now < s.walkHitWindowStart) {
                failWalkPhase(s, "§c✗ Too early! Fusion cancelled!");
                return;
            }
            if (now > s.walkHitWindowEnd) {
                failWalkPhase(s, "§c✗ Too late! Fusion cancelled!");
                return;
            }
        }
        if (presserId.equals(s.p1Id)) {
            s.p1WalkPressed = true;
            if (s.isSolo()) s.p2WalkPressed = true;
        } else {
            s.p2WalkPressed = true;
        }
        if (s.p1WalkPressed && s.p2WalkPressed) {
            s.walkQteOpen = false;
            closeFusionQTE(s.p1Ref, s.walkExpectedButton, s.walkStage + 1);
            closeFusionQTE(s.p2Ref, s.walkExpectedButton, s.walkStage + 1);
            walkSuccess(s);
        }
    }
    private static void walkSuccess(FusionSession s) {
        s.walkStage++;
        Vec3d mid = s.p1WalkPos.add(s.p2WalkPos).multiply(0.5);
        Vec3d dirP1 = mid.subtract(s.p1WalkPos);
        Vec3d dirP2 = mid.subtract(s.p2WalkPos);
        if (dirP1.lengthSquared() < 0.001) dirP1 = new Vec3d(-1, 0, 0);
        if (dirP2.lengthSquared() < 0.001) dirP2 = new Vec3d(1, 0, 0);
        dirP1 = dirP1.normalize();
        dirP2 = dirP2.normalize();
        double currentDist = s.p1WalkPos.distanceTo(s.p2WalkPos);
        double step = Math.min(WALK_STEP_DISTANCE, Math.max(0.1, (currentDist - WALK_STOP_DISTANCE) / 2.0));
        Vec3d newP1 = s.p1WalkPos.add(dirP1.multiply(step));
        Vec3d newP2 = s.p2WalkPos.add(dirP2.multiply(step));
        smoothTpTargets.put(s.p1Id, new Vec3d[]{s.p1WalkPos, newP1});
        smoothTpTargets.put(s.p2Id, new Vec3d[]{s.p2WalkPos, newP2});
        smoothTpProgress.put(s.p1Id, 0);
        smoothTpProgress.put(s.p2Id, 0);
        s.p1WalkPos = newP1;
        s.p2WalkPos = newP2;
        facePlayers(s.p1Ref, s.p2Ref, s.p1WalkPos, s.p2WalkPos);
        spawnWalkAura(s, s.walkStage);
        if (s.walkStage >= 3) {
            PoseNetworking.broadcastAnimState(s.p1Ref,
                    com.cooptest.client.CoopAnimationHandler.AnimState.FUSION_START_P1.ordinal());
            PoseNetworking.broadcastAnimState(s.p2Ref,
                    com.cooptest.client.CoopAnimationHandler.AnimState.FUSION_START_P2.ordinal());
            new Thread(() -> {
                try { Thread.sleep(SMOOTH_TP_TICKS * 50 + 420L); } catch (InterruptedException ignored) {}
                if (s.p1Ref.getServer() == null) return;
                s.p1Ref.getServer().execute(() -> triggerMeetupExplosion(s));
            }).start();
        } else {
            scheduleWalkQTE(s);
        }
    }
    private static void failWalkPhase(FusionSession s, String reason) {
        s.phase = FusionPhase.FAILED;
        s.walkQteOpen = false;
        closeFusionQTE(s.p1Ref, "", 0);
        closeFusionQTE(s.p2Ref, "", 0);
        PoseNetworking.broadcastAnimState(s.p1Ref, 0);
        PoseNetworking.broadcastAnimState(s.p2Ref, 0);
        freezeBoth(s, false);
        Vec3d mid = s.p1Ref.getPos().add(s.p2Ref.getPos()).multiply(0.5);
        Vec3d away1 = s.p1Ref.getPos().subtract(mid).normalize().multiply(4.0).add(0, 0.8, 0);
        Vec3d away2 = s.p2Ref.getPos().subtract(mid).normalize().multiply(4.0).add(0, 0.8, 0);
        s.p1Ref.addVelocity(away1.x, away1.y, away1.z);
        s.p2Ref.addVelocity(away2.x, away2.y, away2.z);
        s.p1Ref.velocityModified = true;
        s.p2Ref.velocityModified = true;
        broadcast(s, new FusionPhasePayload(s.p1Id, s.p2Id, 99));
        s.p1Ref.sendMessage(net.minecraft.text.Text.literal(reason), true);
        s.p2Ref.sendMessage(net.minecraft.text.Text.literal(reason), true);
        cleanupSession(s);
    }
    private static void triggerMeetupExplosion(FusionSession s) {
        if (s.phase != FusionPhase.WALK_QTE) return;
        PoseNetworking.broadcastAnimState(s.p1Ref,
                com.cooptest.client.CoopAnimationHandler.AnimState.FUSION_HIT_P1.ordinal());
        PoseNetworking.broadcastAnimState(s.p2Ref,
                com.cooptest.client.CoopAnimationHandler.AnimState.FUSION_HIT_P2.ordinal());
        new Thread(() -> {
            try { Thread.sleep(350); } catch (InterruptedException ignored) {}
            if (s.p1Ref.getServer() == null) return;
            s.p1Ref.getServer().execute(() -> {
                if (s.phase == FusionPhase.WALK_QTE) {
                    Vec3d mid = s.p1Ref.getPos().add(s.p2Ref.getPos()).multiply(0.5).add(0, 1, 0);
                    s.world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, 60, 0.8, 0.8, 0.8, 0.3);
                    s.world.spawnParticles(ParticleTypes.FLASH, mid.x, mid.y, mid.z, 5, 0, 0, 0, 0);
                    s.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 30, 0.4, 0.4, 0.4, 0.3);
                    s.world.playSound(null, mid.x, mid.y, mid.z,
                            ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 3.0f, 0.8f);
                    s.world.playSound(null, mid.x, mid.y, mid.z,
                            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 0.6f);
                }
            });
        }).start();
        new Thread(() -> {
            try { Thread.sleep(620); } catch (InterruptedException ignored) {}
            if (s.p1Ref.getServer() == null) return;
            s.p1Ref.getServer().execute(() -> {
                if (s.phase != FusionPhase.WALK_QTE) return;
                broadcastServer(s, "§6§l⚡ THE FUSION BEGINS! ⚡ §7Complete the 10-stage QTE!");
                s.phase = FusionPhase.FUSION_QTE;
                s.fusionStage = 0;
                s.lastFusionStageEnd = System.currentTimeMillis();
                broadcast(s, new FusionPhasePayload(s.p1Id, s.p2Id, 2));
                PoseNetworking.broadcastAnimState(s.p1Ref,
                        com.cooptest.client.CoopAnimationHandler.AnimState.FUSION_IDLE_P1.ordinal());
                PoseNetworking.broadcastAnimState(s.p2Ref,
                        com.cooptest.client.CoopAnimationHandler.AnimState.FUSION_IDLE_P2.ordinal());
                openNextFusionQTE(s);
            });
        }).start();
    }
    private static void openNextFusionQTE(FusionSession s) {
        if (s.phase != FusionPhase.FUSION_QTE) return;
        if (s.fusionStage >= 10) {
            triggerFusion(s);
            return;
        }
        long now = System.currentTimeMillis();
        s.fusionQteOpen = true;
        s.p1FusionPressed = false;
        s.p2FusionPressed = false;
        s.fusionQteOpenTime = now;
        s.fusionExpectedButton = s.randomButton();
        boolean isTimingBar = (s.fusionStage == 3 || s.fusionStage == 6 || s.fusionStage == 9);
        s.fusionIsTimingBar = isTimingBar;
        long windowStartMs, windowEndMs;
        int type;
        if (isTimingBar) {
            int timingIdx = s.fusionStage == 3 ? 0 : s.fusionStage == 6 ? 1 : 2;
            long hitWindow = TIMING_HIT_WINDOW_FUSION[timingIdx];
            windowStartMs = s.randomGreenZoneStart(hitWindow);
            windowEndMs   = windowStartMs + hitWindow;
            type = 1;
        } else {
            windowStartMs = 0;
            windowEndMs   = s.fusionQteWindow();
            type = 0;
        }
        s.fusionHitWindowStart = now + windowStartMs - TIMING_GRACE_MS;
        s.fusionHitWindowEnd   = now + windowEndMs   + TIMING_GRACE_MS;
        sendFusionQTE(s.p1Ref, s.fusionExpectedButton, s.fusionStage + 1, windowStartMs, windowEndMs, true, type);
        sendFusionQTE(s.p2Ref, s.fusionExpectedButton, s.fusionStage + 1, windowStartMs, windowEndMs, true, type);
        spawnFusionAura(s, s.fusionStage);
        Vec3d mid2 = s.p1Ref.getPos().add(s.p2Ref.getPos()).multiply(0.5);
        if (s.fusionStage == 7) {
            s.world.playSound(null, mid2.x, mid2.y, mid2.z,
                    ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 1.5f, 1.4f);
        } else if (s.fusionStage == 8) {
            s.world.playSound(null, mid2.x, mid2.y, mid2.z,
                    ModSounds.FIRE_IMPACT, SoundCategory.PLAYERS, 1.5f, 1.2f);
        } else if (s.fusionStage == 9) {
            s.world.playSound(null, mid2.x, mid2.y, mid2.z,
                    ModSounds.GALACTIC_DAP, SoundCategory.PLAYERS, 2.0f, 0.9f);
        }
    }
    private static void handleFusionQTEPress(FusionSession s, UUID presserId, String button) {
        if (!s.fusionQteOpen) return;
        if (!button.equals(s.fusionExpectedButton)) {
            failFusion(s, "§c✗ Wrong button! FUSION FAILED!");
            return;
        }
        if (s.fusionIsTimingBar) {
            long now = System.currentTimeMillis();
            if (now < s.fusionHitWindowStart) {
                failFusion(s, "§c✗ Too early! FUSION FAILED!");
                return;
            }
            if (now > s.fusionHitWindowEnd) {
                failFusion(s, "§c✗ Too late! FUSION FAILED!");
                return;
            }
        }
        if (presserId.equals(s.p1Id)) {
            s.p1FusionPressed = true;
            if (s.isSolo()) s.p2FusionPressed = true;
        } else {
            s.p2FusionPressed = true;
        }
        if (s.p1FusionPressed && s.p2FusionPressed) {
            s.fusionQteOpen = false;
            closeFusionQTE(s.p1Ref, s.fusionExpectedButton, s.fusionStage + 1);
            closeFusionQTE(s.p2Ref, s.fusionExpectedButton, s.fusionStage + 1);
            s.fusionStage++;
            s.lastFusionStageEnd = System.currentTimeMillis();
            String progress = s.fusionStage >= 10
                    ? "§6§l★ 10/10 ★"
                    : "§a" + s.fusionStage + "/10 §7— §6Keep going!";
            s.p1Ref.sendMessage(net.minecraft.text.Text.literal(progress), true);
            if (!s.isSolo()) s.p2Ref.sendMessage(net.minecraft.text.Text.literal(progress), true);
            if (s.fusionStage >= 10) {
                triggerFusion(s);
            } else {
                ServerPlayerEntity p1 = s.p1Ref;
                new Thread(() -> {
                    try { Thread.sleep(FUSION_STAGE_GAP_MS); } catch (InterruptedException ignored) {}
                    if (p1.getServer() == null) return;
                    p1.getServer().execute(() -> openNextFusionQTE(s));
                }).start();
            }
        }
    }
    private static void failFusion(FusionSession s, String reason) {
        s.phase = FusionPhase.FAILED;
        s.fusionQteOpen = false;
        closeFusionQTE(s.p1Ref, "", 0);
        closeFusionQTE(s.p2Ref, "", 0);
        PoseNetworking.broadcastAnimState(s.p1Ref, 0);
        PoseNetworking.broadcastAnimState(s.p2Ref, 0);
        freezeBoth(s, false);
        Vec3d mid = s.p1Ref.getPos().add(s.p2Ref.getPos()).multiply(0.5);
        Vec3d away1 = s.p1Ref.getPos().subtract(mid).normalize().multiply(4.0).add(0, 1.0, 0);
        Vec3d away2 = s.p2Ref.getPos().subtract(mid).normalize().multiply(4.0).add(0, 1.0, 0);
        s.p1Ref.addVelocity(away1.x, away1.y, away1.z);
        s.p2Ref.addVelocity(away2.x, away2.y, away2.z);
        s.p1Ref.velocityModified = true;
        s.p2Ref.velocityModified = true;
        broadcast(s, new FusionPhasePayload(s.p1Id, s.p2Id, 99));
        s.p1Ref.sendMessage(net.minecraft.text.Text.literal(reason), false);
        s.p2Ref.sendMessage(net.minecraft.text.Text.literal(reason), false);
        for (ServerPlayerEntity p : s.p1Ref.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(net.minecraft.text.Text.literal(
                    "§c✗ " + s.p1Ref.getName().getString() + " §7and §c" +
                            s.p2Ref.getName().getString() + " §7failed the fusion!"), false);
        }
        cleanupSession(s);
    }
    private static void triggerFusion(FusionSession s) {
        s.phase = FusionPhase.FUSED;
        PoseNetworking.broadcastAnimState(s.p1Ref, 0);
        PoseNetworking.broadcastAnimState(s.p2Ref, 0);
        freezeBoth(s, false);
        net.minecraft.entity.effect.StatusEffectInstance invuln1 = new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.RESISTANCE, 300, 255, false, false);
        net.minecraft.entity.effect.StatusEffectInstance invuln2 = new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.RESISTANCE, 300, 255, false, false);
        s.p1Ref.addStatusEffect(invuln1);
        s.p2Ref.addStatusEffect(invuln2);
        s.p1Ref.setInvulnerable(true);
        s.p2Ref.setInvulnerable(true);
        fusedPairs.put(s.p1Id, s.p2Id);
        fusedPairs.put(s.p2Id, s.p1Id);
        try { ServerPlayNetworking.send(s.p1Ref, new FusionFusedPayload(true)); } catch (Exception ignored) {}
        try { ServerPlayNetworking.send(s.p2Ref, new FusionFusedPayload(true)); } catch (Exception ignored) {}
        try { ServerPlayNetworking.send(s.p1Ref, new FusionBlackScreenPayload(true)); } catch (Exception ignored) {}
        try { ServerPlayNetworking.send(s.p2Ref, new FusionBlackScreenPayload(true)); } catch (Exception ignored) {}
        broadcast(s, new FusionPhasePayload(s.p1Id, s.p2Id, 3));
        Vec3d mid = s.p1Ref.getPos().add(s.p2Ref.getPos()).multiply(0.5);
        new Thread(() -> {
            try {
                s.p1Ref.getServer().execute(() -> {
                    if (!CoopMovesConfig.get().noGriefMode) {
                        float power = 10.0f;
                        for (int dx = -8; dx <= 8; dx += 8) {
                            for (int dz = -8; dz <= 8; dz += 8) {
                                s.world.createExplosion(null,
                                        mid.x + dx, mid.y, mid.z + dz, power, true,
                                        net.minecraft.world.World.ExplosionSourceType.MOB);
                            }
                        }
                    } else {
                        s.world.createExplosion(null, mid.x, mid.y, mid.z, 10.0f, false,
                                net.minecraft.world.World.ExplosionSourceType.MOB);
                    }
                    s.p1Ref.setVelocity(Vec3d.ZERO);
                    s.p2Ref.setVelocity(Vec3d.ZERO);
                    s.p1Ref.velocityModified = true;
                    s.p2Ref.velocityModified = true;
                });
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(500);
                    final int burst = i;
                    if (s.p1Ref.getServer() == null) return;
                    s.p1Ref.getServer().execute(() -> {
                        float spread = 3.0f + burst * 1.5f;
                        int count = 60 + burst * 20;
                        for (int p = 0; p < 5; p++) {
                            double ox = (Math.random() - 0.5) * spread * 2;
                            double oz = (Math.random() - 0.5) * spread * 2;
                            double oy = Math.random() * 4;
                            s.world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                                    mid.x + ox, mid.y + oy, mid.z + oz,
                                    1, 0, 0, 0, 0);
                        }
                        s.world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                                mid.x, mid.y + 2, mid.z,
                                count, spread, spread, spread, 0.5);
                        s.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                                mid.x, mid.y + 2, mid.z,
                                count / 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.6);
                        s.world.spawnParticles(ParticleTypes.DRAGON_BREATH,
                                mid.x, mid.y + 1, mid.z,
                                count / 3, spread, spread, spread, 0.3);
                        s.world.spawnParticles(ParticleTypes.END_ROD,
                                mid.x, mid.y + 1, mid.z,
                                count / 2, spread, spread, spread, 0.4);
                        s.world.playSound(null, mid.x, mid.y, mid.z,
                                SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                                SoundCategory.PLAYERS, 2.0f,
                                0.4f + (float)(Math.random() * 0.4f));
                        if (burst % 3 == 0) {
                            s.world.playSound(null, mid.x, mid.y, mid.z,
                                    ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.5f, 0.6f);
                        }
                    });
                }
            } catch (InterruptedException ignored) {}
            if (s.p1Ref.getServer() == null) return;
            s.p1Ref.getServer().execute(() -> {
                s.world.spawnParticles(ParticleTypes.FLASH, mid.x, mid.y + 1, mid.z, 20, 0, 0, 0, 0);
                s.world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y + 1, mid.z, 200, 4, 4, 4, 0.6);
                s.world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y + 1, mid.z, 8, 3, 3, 3, 0);
                s.world.playSound(null, mid.x, mid.y, mid.z,
                        ModSounds.GALACTIC_DAP, SoundCategory.PLAYERS, 4.0f, 0.8f);
                s.world.playSound(null, mid.x, mid.y, mid.z,
                        ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 3.0f, 0.5f);
                s.p1Ref.setInvulnerable(false);
                s.p2Ref.setInvulnerable(false);
                try { ServerPlayNetworking.send(s.p1Ref, new FusionBlackScreenPayload(false)); } catch (Exception ignored) {}
                try { ServerPlayNetworking.send(s.p2Ref, new FusionBlackScreenPayload(false)); } catch (Exception ignored) {}
                try { broadcast(s, new FusionPhasePayload(s.p1Id, s.p2Id, 4)); } catch (Exception ignored) {}
                for (ServerPlayerEntity p : s.p1Ref.getServer().getPlayerManager().getPlayerList()) {
                    p.sendMessage(net.minecraft.text.Text.literal(
                            "§c§l☄ " + s.p1Ref.getName().getString() +
                                    " §eand §c" + s.p2Ref.getName().getString() +
                                    " §c§lUNLOCKED METEOR STRIKE! §7Press G to fire!"), false);
                }
                ServerPlayerEntity freshP1 = s.p1Ref.getServer().getPlayerManager().getPlayer(s.p1Id);
                ServerPlayerEntity freshP2 = s.p1Ref.getServer().getPlayerManager().getPlayer(s.p2Id);
                if (freshP1 != null && freshP2 != null) {
                    MeteorStrikeHandler.grantAbility(freshP1, freshP2);
                }
                silentCleanup(s);
            });
        }).start();
    }
    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Set<FusionSession> processed = new HashSet<>();
        for (FusionSession s : new ArrayList<>(sessions.values())) {
            if (processed.contains(s)) continue;
            processed.add(s);
            s.p1Ref = server.getPlayerManager().getPlayer(s.p1Id);
            s.p2Ref = server.getPlayerManager().getPlayer(s.p2Id);
            if (s.p1Ref == null || s.p2Ref == null) {
                cleanupSession(s);
                continue;
            }
            tickSmoothTP(s, server);
            if (s.phase == FusionPhase.WALK_QTE) {
                faceEachOther(s.p1Ref, s.p2Ref);
            }
            if ((s.phase == FusionPhase.WALK_QTE || s.phase == FusionPhase.FUSION_QTE)
                    && server.getTicks() % 7 == 0) {
                sendSwingToOthers(server, s.p1Ref);
                sendSwingToOthers(server, s.p2Ref);
            }
            if (s.phase == FusionPhase.AWAITING_G) {
                if (now - s.gWindowOpenTime > FUSION_G_WINDOW_END + 500) {
                    cleanupSession(s);
                }
            }
            if (s.phase == FusionPhase.WALK_QTE && s.walkQteOpen) {
                if (now - s.walkQteOpenTime > s.walkQteWindow() + 200) {
                    failWalkPhase(s, "§c✗ Time's up! Fusion cancelled!");
                }
            }
            if (s.phase == FusionPhase.FUSION_QTE && s.fusionQteOpen) {
                long timeout = s.fusionIsTimingBar
                        ? TIMING_BAR_TOTAL_MS + 500
                        : s.fusionQteWindow() + 200;
                if (now - s.fusionQteOpenTime > timeout) {
                    failFusion(s, "§c✗ Too slow! FUSION FAILED!");
                }
            }
            if (s.phase == FusionPhase.WALK_QTE && s.walkQteOpen) {
                boolean oneMissed = (s.p1WalkPressed != s.p2WalkPressed);
                if (oneMissed && now - s.walkQteOpenTime > s.walkQteWindow()) {
                    String missName = !s.p1WalkPressed
                            ? s.p1Ref.getName().getString()
                            : s.p2Ref.getName().getString();
                    failWalkPhase(s, "§c✗ " + missName + " missed! Fusion cancelled!");
                }
            }
            if (s.phase == FusionPhase.FUSION_QTE && s.fusionQteOpen) {
                boolean oneMissed = (s.p1FusionPressed != s.p2FusionPressed);
                if (oneMissed && now - s.fusionQteOpenTime > s.fusionQteWindow()) {
                    String missName = !s.p1FusionPressed
                            ? s.p1Ref.getName().getString()
                            : s.p2Ref.getName().getString();
                    failFusion(s, "§c✗ " + missName + " missed! FUSION FAILED!");
                }
            }
        }
    }
    private static void tickSmoothTP(FusionSession s, MinecraftServer server) {
        tickPlayerSmoothTP(s.p1Id, server);
        tickPlayerSmoothTP(s.p2Id, server);
    }
    private static void tickPlayerSmoothTP(UUID id, MinecraftServer server) {
        Vec3d[] targets = smoothTpTargets.get(id);
        Integer progress = smoothTpProgress.get(id);
        if (targets == null || progress == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
        if (player == null) { smoothTpTargets.remove(id); smoothTpProgress.remove(id); return; }
        int tick = progress + 1;
        float t = (float) tick / SMOOTH_TP_TICKS;
        t = Math.min(1.0f, t);
        Vec3d start = targets[0], end = targets[1];
        double x = start.x + (end.x - start.x) * t;
        double y = start.y + (end.y - start.y) * t;
        double z = start.z + (end.z - start.z) * t;
        player.teleport(player.getServerWorld(), x, y, z, player.getYaw(), player.getPitch());
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        if (tick >= SMOOTH_TP_TICKS) {
            smoothTpTargets.remove(id);
            smoothTpProgress.remove(id);
        } else {
            smoothTpProgress.put(id, tick);
        }
    }
    private static void freezeBoth(FusionSession s, boolean freeze) {
        ServerPlayNetworking.send(s.p1Ref, new ChargedDapHandler.PerfectDapFreezePayload(freeze));
        ServerPlayNetworking.send(s.p2Ref, new ChargedDapHandler.PerfectDapFreezePayload(freeze));
    }
    private static void faceEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d pos1 = p1.getPos(), pos2 = p2.getPos();
        double dx = pos2.x - pos1.x, dz = pos2.z - pos1.z;
        if (dx * dx + dz * dz < 0.001) return;
        float yaw1 = (float)(Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yaw2 = yaw1 + 180;
        p1.setYaw(yaw1); p1.setBodyYaw(yaw1); p1.setHeadYaw(yaw1);
        p1.prevYaw = yaw1; p1.prevBodyYaw = yaw1; p1.prevHeadYaw = yaw1;
        p2.setYaw(yaw2); p2.setBodyYaw(yaw2); p2.setHeadYaw(yaw2);
        p2.prevYaw = yaw2; p2.prevBodyYaw = yaw2; p2.prevHeadYaw = yaw2;
    }
    private static void sendSwingToOthers(MinecraftServer server, ServerPlayerEntity player) {
        net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket swingPacket =
                new net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket(
                        player, net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket.SWING_MAIN_HAND);
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (!other.getUuid().equals(player.getUuid())) {
                other.networkHandler.sendPacket(swingPacket);
            }
        }
        player.setBodyYaw(player.getHeadYaw());
        player.prevBodyYaw = player.getHeadYaw();
    }
    private static void snapBodyToHead(ServerPlayerEntity player) {
        float headYaw = player.getHeadYaw();
        player.setBodyYaw(headYaw);
        player.prevBodyYaw = headYaw;
        player.setYaw(headYaw);
        player.prevYaw = headYaw;
    }
    private static void spawnWalkAura(FusionSession s, int stage) {
        Vec3d mid = s.p1Ref.getPos().add(s.p2Ref.getPos()).multiply(0.5).add(0, 1, 0);
        switch (stage) {
            case 1 -> {
                s.world.spawnParticles(ParticleTypes.FLAME, mid.x, mid.y, mid.z, 20, 0.5, 0.5, 0.5, 0.05);
                s.world.playSound(null, mid.x, mid.y, mid.z,
                        SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
            }
            case 2 -> {
                s.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, mid.x, mid.y, mid.z, 30, 0.5, 0.5, 0.5, 0.1);
                s.world.playSound(null, mid.x, mid.y, mid.z,
                        SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.2f, 1.2f);
            }
            case 3 -> {
                s.world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, 40, 0.6, 0.6, 0.6, 0.2);
                s.world.playSound(null, mid.x, mid.y, mid.z,
                        ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 1.5f, 1.3f);
            }
        }
    }
    private static void spawnFusionAura(FusionSession s, int stage) {
        Vec3d mid = s.p1Ref.getPos().add(s.p2Ref.getPos()).multiply(0.5).add(0, 1, 0);
        int count = 5 + stage * 3;
        float intensity = 0.1f + stage * 0.05f;
        s.world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, count, intensity, intensity, intensity, 0.1 + stage * 0.02);
        s.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, count / 2, intensity, intensity, intensity, 0.2);
        if (stage >= 7) {
            s.world.spawnParticles(ParticleTypes.DRAGON_BREATH, mid.x, mid.y, mid.z, count, intensity, intensity, intensity, 0.15);
        }
    }
    private static void sendFusionQTE(ServerPlayerEntity player, String button, int stage, long windowStartMs, long windowEndMs, boolean open, int type) {
        if (player == null) return;
        try {
            ServerPlayNetworking.send(player, new FusionQTEPayload(player.getUuid(), button, stage, windowStartMs, windowEndMs, open, type));
        } catch (Exception ignored) {}
    }
    private static void closeFusionQTE(ServerPlayerEntity player, String button, int stage) {
        sendFusionQTE(player, button, stage, 0, 0, false, 0);
    }
    private static void broadcast(FusionSession s, CustomPayload payload) {
        for (ServerPlayerEntity p : s.p1Ref.getServer().getPlayerManager().getPlayerList()) {
            try { ServerPlayNetworking.send(p, payload); } catch (Exception ignored) {}
        }
    }
    private static void broadcastServer(FusionSession s, String msg) {
        for (ServerPlayerEntity p : s.p1Ref.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(net.minecraft.text.Text.literal(msg), false);
        }
    }
    public static void autoPressBothCorrect(ServerPlayerEntity player) {
        FusionSession s = sessions.get(player.getUuid());
        if (s == null) { player.sendMessage(net.minecraft.text.Text.literal("§cNo active fusion session."), true); return; }
        if (s.phase == FusionPhase.WALK_QTE && s.walkQteOpen) {
            handleWalkQTEPress(s, s.p1Id, s.walkExpectedButton);
            if (s.walkQteOpen) handleWalkQTEPress(s, s.p2Id, s.walkExpectedButton);
        } else if (s.phase == FusionPhase.FUSION_QTE && s.fusionQteOpen) {
            handleFusionQTEPress(s, s.p1Id, s.fusionExpectedButton);
            if (s.fusionQteOpen) handleFusionQTEPress(s, s.p2Id, s.fusionExpectedButton);
        } else if (s.phase == FusionPhase.AWAITING_G) {
            handleGPressFromClient(player);
            handleGPressFromClient(player);
        } else {
            player.sendMessage(net.minecraft.text.Text.literal("§cNo QTE window currently open. Phase: " + s.phase), true);
        }
    }
    public static void debugSkipToFusionQTE(ServerPlayerEntity player) {
        cleanup(player.getUuid());
        FusionSession s = new FusionSession(player, player, System.currentTimeMillis());
        sessions.put(player.getUuid(), s);
        s.p1WalkPos = player.getPos().add(2, 0, 0);
        s.p2WalkPos = player.getPos().add(-2, 0, 0);
        freezeBoth(s, true);
        broadcast(s, new FusionPhasePayload(s.p1Id, s.p2Id, 2));
        s.phase = FusionPhase.FUSION_QTE;
        s.fusionStage = 0;
        s.lastFusionStageEnd = System.currentTimeMillis();
        try { ServerPlayNetworking.send(player,
                new PoseNetworking.AnimStateSyncPayload(player.getUuid(),
                        com.cooptest.client.CoopAnimationHandler.AnimState.FUSION_IDLE_P1.ordinal()));
        } catch (Exception ignored) {}
        openNextFusionQTE(s);
    }
    public static String getDebugStatus(UUID playerId) {
        FusionSession s = sessions.get(playerId);
        if (s == null) return "No active fusion session.";
        String extra = switch (s.phase) {
            case WALK_QTE -> " | Walk stage " + s.walkStage + "/3 | QTE open=" + s.walkQteOpen
                    + (s.walkQteOpen ? " button=" + s.walkExpectedButton : "");
            case FUSION_QTE -> " | Fusion stage " + s.fusionStage + "/10 | QTE open=" + s.fusionQteOpen
                    + (s.fusionQteOpen ? " button=" + s.fusionExpectedButton : "");
            default -> "";
        };
        return "Phase=" + s.phase + extra;
    }
    private static void handleUnfuseRequest(ServerPlayerEntity player) {
        UUID partnerId = fusedPairs.get(player.getUuid());
        if (partnerId == null) return;
        ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
        defuse(player, partner);
    }
    public static void defuse(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (p1 == null) return;
        UUID id1 = p1.getUuid();
        UUID id2 = p2 != null ? p2.getUuid() : id1;
        fusedPairs.remove(id1);
        fusedPairs.remove(id2);
        p1.setInvulnerable(false);
        if (p2 != null) p2.setInvulnerable(false);
        Vec3d mid = p2 != null
                ? p1.getPos().add(p2.getPos()).multiply(0.5)
                : p1.getPos();
        Vec3d away1 = p1.getPos().subtract(mid).normalize();
        if (away1.lengthSquared() < 0.001) away1 = new Vec3d(1, 0, 0);
        away1 = away1.multiply(2.5).add(0, 0.6, 0);
        p1.addVelocity(away1.x, away1.y, away1.z);
        p1.velocityModified = true;
        if (p2 != null) {
            Vec3d away2 = p2.getPos().subtract(mid).normalize();
            if (away2.lengthSquared() < 0.001) away2 = new Vec3d(-1, 0, 0);
            away2 = away2.multiply(2.5).add(0, 0.6, 0);
            p2.addVelocity(away2.x, away2.y, away2.z);
            p2.velocityModified = true;
        }
        p1.getServerWorld().playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5f, 1.5f);
        p1.getServerWorld().spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                mid.x, mid.y + 1, mid.z, 2, 0.5, 0.5, 0.5, 0);
        try { ServerPlayNetworking.send(p1, new FusionFusedPayload(false)); } catch (Exception ignored) {}
        if (p2 != null) {
            try { ServerPlayNetworking.send(p2, new FusionFusedPayload(false)); } catch (Exception ignored) {}
        }
        p1.sendMessage(net.minecraft.text.Text.literal("§7Fusion dissolved."), true);
        if (p2 != null) p2.sendMessage(net.minecraft.text.Text.literal("§7Fusion dissolved."), true);
        String name1 = p1.getName().getString();
        String name2 = p2 != null ? p2.getName().getString() : name1;
        for (ServerPlayerEntity p : p1.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(net.minecraft.text.Text.literal(
                    "§7" + name1 + " and " + name2 + " have defused."), false);
        }
    }
    public static void cleanupFused(UUID playerId) {
        UUID partnerId = fusedPairs.remove(playerId);
        if (partnerId != null) {
            fusedPairs.remove(partnerId);
        }
    }
    private static void cleanupSession(FusionSession s) {
        if (s.p1Ref != null && s.p2Ref != null) {
            try {
                broadcast(s, new FusionPhasePayload(s.p1Id, s.p2Id, 99));
            } catch (Exception ignored) {}
        }
        removeSessionData(s);
    }
    private static void silentCleanup(FusionSession s) {
        removeSessionData(s);
    }
    private static void removeSessionData(FusionSession s) {
        sessions.remove(s.p1Id);
        sessions.remove(s.p2Id);
        smoothTpTargets.remove(s.p1Id);
        smoothTpTargets.remove(s.p2Id);
        smoothTpProgress.remove(s.p1Id);
        smoothTpProgress.remove(s.p2Id);
    }
}