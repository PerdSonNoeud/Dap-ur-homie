package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
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
import static java.util.Collections.emptySet;
public class DapHoldHandler {
    private static final long ANIM_LENGTH_MS    = 1042;
    private static final long J_WINDOW_START_MS = 330;
    private static final long IMPACT_MS         = 420;
    private static final long J_WINDOW_END_MS   = 1330;
    private static final double STOP_DISTANCE   = 1.5;
    private static final double TP_SPEED        = 0.08;
    public record DapHoldStartPayload(UUID playerId, UUID partnerId, int role) implements CustomPayload {
        public static final Id<DapHoldStartPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_start"));
        public static final PacketCodec<PacketByteBuf, DapHoldStartPayload> CODEC = PacketCodec.of(
                (p, buf) -> { buf.writeUuid(p.playerId()); buf.writeUuid(p.partnerId()); buf.writeInt(p.role()); },
                buf -> new DapHoldStartPayload(buf.readUuid(), buf.readUuid(), buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldWindowPayload(boolean open) implements CustomPayload {
        public static final Id<DapHoldWindowPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_window"));
        public static final PacketCodec<PacketByteBuf, DapHoldWindowPayload> CODEC = PacketCodec.of(
                (p, buf) -> buf.writeBoolean(p.open()), buf -> new DapHoldWindowPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldLoopPayload(boolean looping) implements CustomPayload {
        public static final Id<DapHoldLoopPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_loop"));
        public static final PacketCodec<PacketByteBuf, DapHoldLoopPayload> CODEC = PacketCodec.of(
                (p, buf) -> buf.writeBoolean(p.looping()), buf -> new DapHoldLoopPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldEndPayload(boolean wasLooping) implements CustomPayload {
        public static final Id<DapHoldEndPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_end"));
        public static final PacketCodec<PacketByteBuf, DapHoldEndPayload> CODEC = PacketCodec.of(
                (p, buf) -> buf.writeBoolean(p.wasLooping()), buf -> new DapHoldEndPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldFreezePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<DapHoldFreezePayload> ID = new Id<>(Identifier.of("cooptest", "daphold_freeze"));
        public static final PacketCodec<PacketByteBuf, DapHoldFreezePayload> CODEC = PacketCodec.of(
                (p, buf) -> { buf.writeUuid(p.playerId()); buf.writeBoolean(p.frozen()); },
                buf -> new DapHoldFreezePayload(buf.readUuid(), buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldJHoldPayload() implements CustomPayload {
        public static final Id<DapHoldJHoldPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_jhold"));
        public static final PacketCodec<PacketByteBuf, DapHoldJHoldPayload> CODEC = PacketCodec.unit(new DapHoldJHoldPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldJReleasePayload() implements CustomPayload {
        public static final Id<DapHoldJReleasePayload> ID = new Id<>(Identifier.of("cooptest", "daphold_jrelease"));
        public static final PacketCodec<PacketByteBuf, DapHoldJReleasePayload> CODEC = PacketCodec.unit(new DapHoldJReleasePayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record GroupJoinedPayload(UUID joinerId, UUID hfId, int memberCount) implements CustomPayload {
        public static final Id<GroupJoinedPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_group_join"));
        public static final PacketCodec<PacketByteBuf, GroupJoinedPayload> CODEC = PacketCodec.of(
                (p, buf) -> { buf.writeUuid(p.joinerId()); buf.writeUuid(p.hfId()); buf.writeInt(p.memberCount()); },
                buf -> new GroupJoinedPayload(buf.readUuid(), buf.readUuid(), buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record GroupResultPayload(boolean perfect, int memberCount) implements CustomPayload {
        public static final Id<GroupResultPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_group_result"));
        public static final PacketCodec<PacketByteBuf, GroupResultPayload> CODEC = PacketCodec.of(
                (p, buf) -> { buf.writeBoolean(p.perfect()); buf.writeInt(p.memberCount()); },
                buf -> new GroupResultPayload(buf.readBoolean(), buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record GroupJoinPayload() implements CustomPayload {
        public static final Id<GroupJoinPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_group_join_req"));
        public static final PacketCodec<PacketByteBuf, GroupJoinPayload> CODEC = PacketCodec.unit(new GroupJoinPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    private static final Map<UUID, UUID> activePairs   = new HashMap<>();
    private static final Map<UUID, Long> pairStartTime = new HashMap<>();
    private static final Set<UUID> windowOpen          = new HashSet<>();
    private static final Set<UUID> impactFired         = new HashSet<>();
    private static final Set<UUID> looping             = new HashSet<>();
    private static final Set<UUID> endingAnimation     = new HashSet<>();
    private static final Map<UUID, Long> jHoldLastTick = new HashMap<>();
    private static final Map<UUID, Long> loopStartTime = new HashMap<>();
    private static final Map<UUID, ArmorStandEntity> handStands = new HashMap<>();
    private static final Set<UUID> tpComplete          = new HashSet<>();
    private static final Map<UUID, Set<UUID>> groupJoiners  = new HashMap<>();
    private static final Map<UUID, UUID>      joinerGroup   = new HashMap<>();
    private static final Map<UUID, Long>      joinerJLast   = new HashMap<>();
    private static final Map<UUID, Long>      releaseFirst  = new HashMap<>();
    private static final Map<UUID, Set<UUID>> releasedSet   = new HashMap<>();
    private static final double GROUP_JOIN_RADIUS  = 2.5;
    private static final long   RELEASE_WINDOW_MS  = 500L;
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DapHoldStartPayload.ID,    DapHoldStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldWindowPayload.ID,   DapHoldWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldLoopPayload.ID,     DapHoldLoopPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldEndPayload.ID,      DapHoldEndPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldFreezePayload.ID,   DapHoldFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupJoinedPayload.ID,     GroupJoinedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupResultPayload.ID,     GroupResultPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapHoldJHoldPayload.ID,    DapHoldJHoldPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapHoldJReleasePayload.ID, DapHoldJReleasePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GroupJoinPayload.ID,       GroupJoinPayload.CODEC);
    }
    public static void register() {
        registerPayloads();
        ServerPlayNetworking.registerGlobalReceiver(DapHoldJHoldPayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onJHold(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(DapHoldJReleasePayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onJRelease(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(GroupJoinPayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> {
                    ServerPlayerEntity player = ctx.player();
                    UUID id = player.getUuid();
                    if (isInDapHold(id)) return;
                    tryJoinGroup(player, System.currentTimeMillis());
                }));
        ServerTickEvents.END_SERVER_TICK.register(DapHoldHandler::onServerTick);
    }
    private static void makeFaceEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d p1Pos = p1.getPos();
        Vec3d p2Pos = p2.getPos();
        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        System.out.println("[DapHold]  Left click swing - body rotation synced!");
        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawP2 = yawP1 + 180;
        p1.setYaw(yawP1);
        p1.setBodyYaw(yawP1);
        p1.setHeadYaw(yawP1);
        p1.teleport(p1.getServerWorld(), p1Pos.x, p1Pos.y, p1Pos.z, yawP1, 0.0f);
        p2.setYaw(yawP2);
        p2.setBodyYaw(yawP2);
        p2.setHeadYaw(yawP2);
        p2.teleport(p2.getServerWorld(), p2Pos.x, p2Pos.y, p2Pos.z, yawP2, 0.0f);
    }
    private static boolean arePlayersFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        net.minecraft.util.math.Vec3d p1Pos = p1.getPos();
        net.minecraft.util.math.Vec3d p2Pos = p2.getPos();
        net.minecraft.util.math.Vec3d directionTo = p2Pos.subtract(p1Pos).normalize();
        net.minecraft.util.math.Vec3d p1Looking = p1.getRotationVector();
        double dot1 = p1Looking.dotProduct(directionTo);
        if (dot1 < 0.85) return false;
        net.minecraft.util.math.Vec3d directionBack = p1Pos.subtract(p2Pos).normalize();
        net.minecraft.util.math.Vec3d p2Looking = p2.getRotationVector();
        double dot2 = p2Looking.dotProduct(directionBack);
        return dot2 >= 0.85;
    }
    public static void startDapHold(ServerPlayerEntity hfPlayer, ServerPlayerEntity dapPlayer) {
        UUID hfId = hfPlayer.getUuid();
        UUID dapId = dapPlayer.getUuid();
        if (isInDapHold(hfId) || isInDapHold(dapId)) return;
        if (!arePlayersFacingEachOther(hfPlayer, dapPlayer)) {
            hfPlayer.sendMessage(net.minecraft.text.Text.literal("§cNot facing each other!"), true);
            dapPlayer.sendMessage(net.minecraft.text.Text.literal("§cNot facing each other!"), true);
            System.out.println("[DapHold]  FAILED - Players not facing each other!");
            return;
        }
        System.out.println("[DapHold]  Facing check passed! START! HF=" + hfPlayer.getName().getString() + " DAP=" + dapPlayer.getName().getString());
        HighFiveHandler.handRaisedTime.remove(hfId);
        HighFiveHandler.startAnimTime.remove(hfId);
        HighFiveHandler.syncHandRaised(hfPlayer, false);
        System.out.println("[DapHold] Removed HF player from HighFiveHandler control");
        com.cooptest.DapSession session = com.cooptest.DapSessionManager.createSession(
                hfId, dapId,
                1.5,
                com.cooptest.DapSession.DapType.PERFECT_DAP
        );
        activePairs.put(hfId, dapId);
        pairStartTime.put(hfId, System.currentTimeMillis());
        sendFreeze(hfPlayer.getServer(), hfId,  true);
        sendFreeze(hfPlayer.getServer(), dapId, true);
        System.out.println("[DapHold] Sent freeze to both players");
        spawnHandStand(hfPlayer, dapPlayer);
        System.out.println("[DapHold] Sending DapHoldStartPayload:");
        System.out.println("  - HF player (" + hfPlayer.getName().getString() + "): role=0 (highfive_dap)");
        System.out.println("  - DAP player (" + dapPlayer.getName().getString() + "): role=1 (dap_high)");
        sendToAll(hfPlayer.getServer(), new DapHoldStartPayload(hfId,  dapId, 0));
        sendToAll(hfPlayer.getServer(), new DapHoldStartPayload(dapId, hfId,  1));
        PoseNetworking.broadcastAnimState(hfPlayer, 38);
        PoseNetworking.broadcastAnimState(dapPlayer, 39);
    }
    private static void onServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Set<UUID> toCleanup = new HashSet<>();
        for (Map.Entry<UUID, UUID> entry : activePairs.entrySet()) {
            UUID hfId  = entry.getKey();
            UUID dapId = entry.getValue();
            ServerPlayerEntity hfPlayer  = server.getPlayerManager().getPlayer(hfId);
            ServerPlayerEntity dapPlayer = server.getPlayerManager().getPlayer(dapId);
            if (hfPlayer == null || dapPlayer == null) { toCleanup.add(hfId); continue; }
            Long startMs = pairStartTime.get(hfId);
            if (startMs == null) { toCleanup.add(hfId); continue; }
            long elapsed = now - startMs;
            if (!tpComplete.contains(hfId)) {
                tpComplete.add(hfId);
            }
            updateHandStand(hfPlayer, dapPlayer, hfId);
            if (elapsed % 500 < 50) {
                hfPlayer.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                dapPlayer.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }
            if (!windowOpen.contains(hfId) && elapsed >= J_WINDOW_START_MS) {
                windowOpen.add(hfId);
                sendToAll(server, new DapHoldWindowPayload(true));
                hfPlayer.sendMessage(net.minecraft.text.Text.literal("§e⚡ HOLD J "), true);
                dapPlayer.sendMessage(net.minecraft.text.Text.literal("§e⚡ HOLD J "), true);
            }
            if (!impactFired.contains(hfId) && elapsed >= IMPACT_MS) {
                impactFired.add(hfId);
                spawnImpactParticles(hfPlayer, dapPlayer, hfId);
            }
            if (windowOpen.contains(hfId) && !looping.contains(hfId)
                    && !endingAnimation.contains(hfId) && elapsed >= J_WINDOW_END_MS) {
                if (isHoldingJ(hfId, now) && isHoldingJ(dapId, now)) {
                    looping.add(hfId);
                    loopStartTime.put(hfId, now);
                    com.cooptest.DapSessionManager.removeSession(hfId);
                    sendToAll(server, new DapHoldLoopPayload(true));
                    if (hfPlayer != null && dapPlayer != null) {
                        PoseNetworking.broadcastAnimState(hfPlayer, 40);
                        PoseNetworking.broadcastAnimState(dapPlayer, 40);
                    }
                    System.out.println("[DapHold] BOTH HELD J → DAPPING LOOP!");
                } else {
                    endingAnimation.add(hfId);
                    sendToAll(server, new DapHoldWindowPayload(false));
                    doUnfreeze(server, hfId, dapId);
                }
            }
            if (endingAnimation.contains(hfId) && elapsed >= ANIM_LENGTH_MS) {
                sendToAll(server, new DapHoldEndPayload(false));
                toCleanup.add(hfId);
            }
        }
        Set<UUID> groupResultNeeded = new HashSet<>();
        for (UUID hfId : looping) {
            ServerPlayerEntity hfPlayer = server.getPlayerManager().getPlayer(hfId);
            UUID dapId = activePairs.get(hfId);
            ServerPlayerEntity dapPlayer = server.getPlayerManager().getPlayer(dapId);
            if (hfPlayer == null || dapPlayer == null) continue;
            ServerWorld world = hfPlayer.getServerWorld();
            ArmorStandEntity stand = handStands.get(hfId);
            if (stand != null && !stand.isRemoved()) {
                Vec3d impactPos = stand.getPos();
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                        impactPos.x, impactPos.y, impactPos.z, 2, 0.1, 0.1, 0.1, 0.02);
            }
            Set<UUID> joiners = groupJoiners.get(hfId);
            if (joiners != null && !joiners.isEmpty()) {
                Long first = releaseFirst.get(hfId);
                if (first != null && now - first > RELEASE_WINDOW_MS) {
                    groupResultNeeded.add(hfId);
                    continue;
                }
                Set<UUID> toEvict = new HashSet<>();
                for (UUID jId : joiners) {
                    Long lastJ = joinerJLast.get(jId);
                    if (lastJ == null || now - lastJ > 300) toEvict.add(jId);
                }
                for (UUID jId : toEvict) {
                    joiners.remove(jId);
                    joinerGroup.remove(jId);
                    joinerJLast.remove(jId);
                    sendFreeze(server, jId, false);
                    ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
                    if (jp != null) {
                        PoseNetworking.broadcastAnimState(jp, 41);
                        jp.sendMessage(net.minecraft.text.Text.literal("§7Left the group"), true);
                    }
                }
                if (server.getTicks() % 4 == 0) {
                    faceGroupCenter(hfId, server);
                    ServerPlayerEntity hfP2 = server.getPlayerManager().getPlayer(hfId);
                    UUID dapId2 = activePairs.get(hfId);
                    ServerPlayerEntity dapP2 = server.getPlayerManager().getPlayer(dapId2);
                    if (hfP2 != null)  hfP2.setHeadYaw(hfP2.getBodyYaw());
                    if (dapP2 != null) dapP2.setHeadYaw(dapP2.getBodyYaw());
                    for (UUID jId : joiners) {
                        ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
                        if (jp != null) jp.setHeadYaw(jp.getBodyYaw());
                    }
                }
                Vec3d mid = getGroupMidpoint(hfId, server);
                int chargeParticles = joiners.size() + 1;
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                        mid.x, mid.y + 1.2, mid.z, chargeParticles, 0.3, 0.2, 0.3, 0.05);
            }
        }
        for (UUID hfId : groupResultNeeded) {
            if (looping.contains(hfId)) doGroupResult(hfId, server, false);
        }
        toCleanup.forEach(hfId -> cleanupPair(hfId, server));
    }
    private static void onJHold(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        long now = System.currentTimeMillis();
        UUID hfId = getPairHfId(id);
        if (hfId != null && windowOpen.contains(hfId)) {
            jHoldLastTick.put(id, now);
            return;
        }
        if (joinerGroup.containsKey(id)) {
            joinerJLast.put(id, now);
            return;
        }
        if (hfId == null) tryJoinGroup(player, now);
    }
    private static void onJRelease(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        jHoldLastTick.remove(id);
        joinerJLast.remove(id);
        UUID joinerHfId = joinerGroup.get(id);
        if (joinerHfId != null) {
            logGroupRelease(id, joinerHfId, player.getServer());
            return;
        }
        UUID hfId = getPairHfId(id);
        if (hfId == null || !looping.contains(hfId)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (groupJoiners.containsKey(hfId) && !groupJoiners.get(hfId).isEmpty()) {
            logGroupRelease(id, hfId, server);
            return;
        }
        UUID dapId = activePairs.get(hfId);
        looping.remove(hfId);
        loopStartTime.remove(hfId);
        doUnfreeze(server, hfId, dapId);
        sendToAll(server, new DapHoldEndPayload(true));
        ServerPlayerEntity hfP = server.getPlayerManager().getPlayer(hfId);
        ServerPlayerEntity dapP = server.getPlayerManager().getPlayer(dapId);
        if (hfP  != null) PoseNetworking.broadcastAnimState(hfP,  41);
        if (dapP != null) PoseNetworking.broadcastAnimState(dapP, 41);
        pairStartTime.put(hfId, System.currentTimeMillis() + 100 - 1042L);
    }
    private static void tryJoinGroup(ServerPlayerEntity player, long now) {
        UUID id = player.getUuid();
        for (UUID hfId : looping) {
            UUID dapId = activePairs.get(hfId);
            if (hfId.equals(id) || (dapId != null && dapId.equals(id))) continue;
            Vec3d mid = getGroupMidpoint(hfId, player.getServer());
            if (player.getPos().distanceTo(mid) > GROUP_JOIN_RADIUS) continue;
            addGroupJoiner(player, hfId);
            return;
        }
    }
    private static void addGroupJoiner(ServerPlayerEntity joiner, UUID hfId) {
        UUID id = joiner.getUuid();
        MinecraftServer server = joiner.getServer();
        groupJoiners.computeIfAbsent(hfId, k -> new HashSet<>()).add(id);
        joinerGroup.put(id, hfId);
        joinerJLast.put(id, System.currentTimeMillis());
        sendFreeze(server, id, true);
        PoseNetworking.broadcastAnimState(joiner, 38);
        int total = 2 + groupJoiners.get(hfId).size();
        GroupJoinedPayload pkt = new GroupJoinedPayload(id, hfId, total);
        sendToAll(server, pkt);
        faceGroupCenter(hfId, server);
        joiner.sendMessage(net.minecraft.text.Text.literal("§a§l⚡ JOINED GROUP DAP! (" + total + " players)"), true);
        ServerPlayerEntity hfP = server.getPlayerManager().getPlayer(hfId);
        if (hfP != null) hfP.sendMessage(net.minecraft.text.Text.literal("§e§l+" + joiner.getName().getString() + " joined! (" + total + " total)"), true);
    }
    private static void logGroupRelease(UUID id, UUID hfId, MinecraftServer server) {
        if (server == null) return;
        releasedSet.computeIfAbsent(hfId, k -> new HashSet<>()).add(id);
        if (!releaseFirst.containsKey(hfId)) releaseFirst.put(hfId, System.currentTimeMillis());
        checkGroupRelease(hfId, server);
    }
    private static void checkGroupRelease(UUID hfId, MinecraftServer server) {
        Set<UUID> joiners = groupJoiners.getOrDefault(hfId, emptySet());
        int total = 2 + joiners.size();
        int released = releasedSet.getOrDefault(hfId, emptySet()).size();
        long elapsed = System.currentTimeMillis() - releaseFirst.getOrDefault(hfId, Long.MAX_VALUE);
        if (released >= total) {
            doGroupResult(hfId, server, elapsed <= RELEASE_WINDOW_MS);
        }
    }
    private static void doGroupResult(UUID hfId, MinecraftServer server, boolean perfect) {
        UUID dapId = activePairs.get(hfId);
        Set<UUID> joiners = new HashSet<>(groupJoiners.getOrDefault(hfId, emptySet()));
        int memberCount = 2 + joiners.size();
        java.util.List<ServerPlayerEntity> all = new java.util.ArrayList<>();
        ServerPlayerEntity hfP  = server.getPlayerManager().getPlayer(hfId);
        ServerPlayerEntity dapP = server.getPlayerManager().getPlayer(dapId);
        if (hfP  != null) all.add(hfP);
        if (dapP != null) all.add(dapP);
        for (UUID jId : joiners) {
            ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
            if (jp != null) all.add(jp);
        }
        Vec3d center = all.stream().map(ServerPlayerEntity::getPos)
                .reduce(Vec3d.ZERO, Vec3d::add)
                .multiply(1.0 / Math.max(1, all.size()));
        ServerWorld world = hfP != null ? hfP.getServerWorld() : server.getOverworld();
        if (perfect) {
            for (ServerPlayerEntity p : all) {
                PoseNetworking.broadcastAnimState(p, 68);
            }
            for (ServerPlayerEntity p : all) sendFreeze(server, p.getUuid(), false);
            final java.util.List<ServerPlayerEntity> allFinal = all;
            final Vec3d centerFinal = center;
            final ServerWorld worldFinal = world;
            final int mc = memberCount;
            new Thread(() -> {
                try { Thread.sleep(1670); } catch (InterruptedException ignored) {}
                server.execute(() -> {
                    for (ServerPlayerEntity p : allFinal) {
                        if (!p.isAlive()) continue;
                        p.addVelocity(0, 0.4 + mc * 0.1, 0);
                        p.velocityModified = true;
                        p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.SPEED, 120, Math.min(2, mc - 1)));
                        p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.JUMP_BOOST, 120, 0));
                        p.sendMessage(net.minecraft.text.Text.literal("§6§l✨ PERFECT GROUP DAP! §e" + mc + " players!"), true);
                    }
                    for (int i = 0; i < mc * 3; i++) {
                        double ox = (worldFinal.random.nextDouble() - 0.5) * 3;
                        double oz = (worldFinal.random.nextDouble() - 0.5) * 3;
                        worldFinal.spawnParticles(ParticleTypes.FIREWORK,
                                centerFinal.x + ox, centerFinal.y + 2 + i * 0.5, centerFinal.z + oz,
                                6, 0.3, 0.1, 0.3, 0.12);
                    }
                    worldFinal.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                            centerFinal.x, centerFinal.y + 1.5, centerFinal.z, mc * 5, 0.6, 0.6, 0.6, 0.3);
                    worldFinal.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                            centerFinal.x, centerFinal.y + 1, centerFinal.z, mc, 0.4, 0.3, 0.4, 0);
                    worldFinal.playSound(null, centerFinal.x, centerFinal.y, centerFinal.z,
                            ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 1.5f, 0.9f + mc * 0.05f);
                    worldFinal.playSound(null, centerFinal.x, centerFinal.y, centerFinal.z,
                            net.minecraft.sound.SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST,
                            SoundCategory.PLAYERS, 1.2f, 0.8f);
                });
            }).start();
        } else {
            for (ServerPlayerEntity p : all) {
                Vec3d dir = p.getPos().subtract(center).normalize();
                if (dir.lengthSquared() < 0.01) dir = new Vec3d(1, 0, 0);
                p.addVelocity(dir.x * 0.9, 0.3, dir.z * 0.9);
                p.velocityModified = true;
                p.sendMessage(net.minecraft.text.Text.literal("§c❌ Release not synced!"), true);
            }
            world.spawnParticles(ParticleTypes.POOF,
                    center.x, center.y + 1, center.z, 12, 0.4, 0.3, 0.4, 0.05);
            world.playSound(null, center.x, center.y, center.z,
                    net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6f, 0.8f);
        }
        sendToAll(server, new GroupResultPayload(perfect, memberCount));
        if (!perfect) {
            sendToAll(server, new DapHoldEndPayload(false));
            if (hfP  != null) PoseNetworking.broadcastAnimState(hfP,  41);
            if (dapP != null) PoseNetworking.broadcastAnimState(dapP, 41);
            for (UUID jId : joiners) {
                ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
                if (jp != null) PoseNetworking.broadcastAnimState(jp, 41);
            }
        }
        for (UUID jId : joiners) {
            sendFreeze(server, jId, false);
            joinerGroup.remove(jId);
            joinerJLast.remove(jId);
        }
        groupJoiners.remove(hfId);
        releaseFirst.remove(hfId);
        releasedSet.remove(hfId);
        looping.remove(hfId);
        loopStartTime.remove(hfId);
        if (dapId != null) doUnfreeze(server, hfId, dapId);
        pairStartTime.put(hfId, System.currentTimeMillis() + 100 - 1042L);
    }
    private static Vec3d getGroupMidpoint(UUID hfId, MinecraftServer server) {
        java.util.List<Vec3d> positions = new java.util.ArrayList<>();
        ServerPlayerEntity hfP  = server.getPlayerManager().getPlayer(hfId);
        UUID dapId = activePairs.get(hfId);
        ServerPlayerEntity dapP = server.getPlayerManager().getPlayer(dapId);
        if (hfP  != null) positions.add(hfP.getPos());
        if (dapP != null) positions.add(dapP.getPos());
        for (UUID jId : groupJoiners.getOrDefault(hfId, emptySet())) {
            ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
            if (jp != null) positions.add(jp.getPos());
        }
        if (positions.isEmpty()) return Vec3d.ZERO;
        return positions.stream().reduce(Vec3d.ZERO, Vec3d::add)
                .multiply(1.0 / positions.size());
    }
    private static void faceGroupCenter(UUID hfId, MinecraftServer server) {
        java.util.List<ServerPlayerEntity> members = new java.util.ArrayList<>();
        ServerPlayerEntity hfP  = server.getPlayerManager().getPlayer(hfId);
        UUID dapId = activePairs.get(hfId);
        ServerPlayerEntity dapP = server.getPlayerManager().getPlayer(dapId);
        if (hfP  != null) members.add(hfP);
        if (dapP != null) members.add(dapP);
        for (UUID jId : groupJoiners.getOrDefault(hfId, emptySet())) {
            ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
            if (jp != null) members.add(jp);
        }
        if (members.size() < 2) return;
        Vec3d center = members.stream().map(ServerPlayerEntity::getPos)
                .reduce(Vec3d.ZERO, Vec3d::add).multiply(1.0 / members.size());
        for (ServerPlayerEntity p : members) {
            Vec3d diff = center.subtract(p.getPos());
            if (diff.horizontalLengthSquared() < 0.001) continue;
            float yaw = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x))) - 90f;
            p.setYaw(yaw); p.setBodyYaw(yaw); p.setHeadYaw(yaw);
        }
    }
    public static void forceUnfreeze(MinecraftServer server, UUID id) {
        sendFreeze(server, id, false);
    }
    public static UUID getPairHfId(UUID id) {
        if (activePairs.containsKey(id)) return id;
        for (Map.Entry<UUID, UUID> e : activePairs.entrySet())
            if (e.getValue().equals(id)) return e.getKey();
        return null;
    }
    private static UUID getPairHfIdPrivate(UUID id) { return getPairHfId(id); }
    private static boolean isHoldingJ(UUID id, long now) {
        Long last = jHoldLastTick.get(id);
        return last != null && (now - last) < 200;
    }
    private static void smoothTP(ServerPlayerEntity hf, ServerPlayerEntity dap, UUID hfId) {
        double dist = hf.getPos().distanceTo(dap.getPos());
        if (dist <= STOP_DISTANCE) {
            tpComplete.add(hfId);
            faceEachOther(hf, dap);
            return;
        }
        double move = Math.min(TP_SPEED, (dist - STOP_DISTANCE) / 2.0);
        Vec3d dir   = dap.getPos().subtract(hf.getPos()).normalize();
        Vec3d newHf  = hf.getPos().add(dir.multiply(move));
        Vec3d newDap = dap.getPos().add(dir.negate().multiply(move));
        hf.teleport(hf.getServerWorld(),   newHf.x,  newHf.y,  newHf.z,  hf.getYaw(),  hf.getPitch());
        dap.teleport(dap.getServerWorld(), newDap.x, newDap.y, newDap.z, dap.getYaw(), dap.getPitch());
    }
    private static void faceEachOther(ServerPlayerEntity a, ServerPlayerEntity b) {
        Vec3d diff = b.getPos().subtract(a.getPos());
        float yawA = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x))) - 90f;
        a.teleport(a.getServerWorld(), a.getX(), a.getY(), a.getZ(), yawA,        a.getPitch());
        b.teleport(b.getServerWorld(), b.getX(), b.getY(), b.getZ(), yawA + 180f, b.getPitch());
    }
    private static void spawnHandStand(ServerPlayerEntity hf, ServerPlayerEntity dap) {
        ServerWorld world = hf.getServerWorld();
        Vec3d mid = hf.getPos().add(0, 1.4, 0).add(dap.getPos().add(0, 1.4, 0)).multiply(0.5);
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.setPosition(mid.x, mid.y, mid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        world.spawnEntity(stand);
        handStands.put(hf.getUuid(), stand);
    }
    private static void updateHandStand(ServerPlayerEntity hf, ServerPlayerEntity dap, UUID hfId) {
        ArmorStandEntity stand = handStands.get(hfId);
        if (stand == null || stand.isRemoved()) return;
        Vec3d mid = hf.getPos().add(0, 1.4, 0).add(dap.getPos().add(0, 1.4, 0)).multiply(0.5);
        stand.setPosition(mid.x, mid.y, mid.z);
    }
    private static void spawnImpactParticles(ServerPlayerEntity hf, ServerPlayerEntity dap, UUID hfId) {
        ServerWorld world = hf.getServerWorld();
        ArmorStandEntity stand = handStands.get(hfId);
        double x, y, z;
        if (stand != null && !stand.isRemoved()) {
            x = stand.getX(); y = stand.getY(); z = stand.getZ();
        } else {
            Vec3d mid = hf.getPos().add(dap.getPos()).multiply(0.5).add(0, 1.4, 0);
            x = mid.x; y = mid.y; z = mid.z;
        }
        world.spawnParticles(ParticleTypes.FLASH,     x, y, z, 3,  0,   0,   0,   0);
        world.spawnParticles(ParticleTypes.END_ROD,   x, y, z, 40, 0.4, 0.4, 0.4, 0.15);
        world.spawnParticles(ParticleTypes.WHITE_ASH, x, y, z, 80, 0.6, 0.6, 0.6, 0.08);
        world.spawnParticles(ParticleTypes.CLOUD,     x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 5,  0.3, 0.3, 0.3, 0);
        double groundY = hf.getY() + 0.1;
        for (double angle = 0; angle < 360; angle += 8) {
            double rad = Math.toRadians(angle);
            for (double r = 0.5; r <= 3.0; r += 0.5) {
                world.spawnParticles(ParticleTypes.END_ROD,
                        x + Math.cos(rad) * r, groundY, z + Math.sin(rad) * r,
                        2, 0.05, 0.05, 0.05, 0.02);
            }
        }
        world.playSound(null, x, y, z, ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
    private static void sendFreeze(MinecraftServer server, UUID targetId, boolean freeze) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(p, new DapHoldFreezePayload(targetId, freeze));
    }
    private static void doUnfreeze(MinecraftServer server, UUID hfId, UUID dapId) {
        sendFreeze(server, hfId, false);
        sendFreeze(server, dapId, false);
        com.cooptest.DapSessionManager.removeSession(hfId);
        ArmorStandEntity stand = handStands.remove(hfId);
        if (stand != null && !stand.isRemoved()) stand.discard();
    }
    private static void sendToAll(MinecraftServer server, CustomPayload payload) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(p, payload);
    }
    private static void cleanupPair(UUID hfId, MinecraftServer server) {
        UUID dapId = activePairs.remove(hfId);
        pairStartTime.remove(hfId); windowOpen.remove(hfId); impactFired.remove(hfId);
        looping.remove(hfId); endingAnimation.remove(hfId); tpComplete.remove(hfId);
        loopStartTime.remove(hfId);
        jHoldLastTick.remove(hfId);
        if (dapId != null) jHoldLastTick.remove(dapId);
        ArmorStandEntity stand = handStands.remove(hfId);
        if (stand != null && !stand.isRemoved()) stand.discard();
        Set<UUID> joiners = groupJoiners.remove(hfId);
        if (joiners != null) {
            for (UUID jId : joiners) {
                joinerGroup.remove(jId);
                joinerJLast.remove(jId);
                sendFreeze(server, jId, false);
                ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
                if (jp != null) PoseNetworking.broadcastAnimState(jp, 41);
            }
        }
        releaseFirst.remove(hfId);
        releasedSet.remove(hfId);
        com.cooptest.DapSessionManager.removeSession(hfId);
        sendFreeze(server, hfId, false);
        if (dapId != null) sendFreeze(server, dapId, false);
        long now = System.currentTimeMillis();
        ChargedDapHandler.cooldowns.put(hfId, now + 1000);
        if (dapId != null) ChargedDapHandler.cooldowns.put(dapId, now + 1000);
        HighFiveHandler.highFiveCooldown.put(hfId, now);
        if (dapId != null) HighFiveHandler.highFiveCooldown.put(dapId, now);
        System.out.println("[DapHold] Cleaned up: " + hfId + " (1s cooldown applied)");
    }
    public static boolean tryDetect(ServerPlayerEntity player, ServerPlayerEntity partner) {
        boolean playerHF  = HighFiveHandler.hasHandRaised(player.getUuid());
        boolean partnerHF = HighFiveHandler.hasHandRaised(partner.getUuid());
        if (playerHF && !partnerHF)  { startDapHold(player,  partner); return true; }
        if (partnerHF && !playerHF)  { startDapHold(partner, player);  return true; }
        return false;
    }
    public static boolean isInDapHold(UUID playerId) {
        return activePairs.containsKey(playerId)
                || activePairs.containsValue(playerId)
                || joinerGroup.containsKey(playerId);
    }
}