package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.util.math.Vec3d;
import java.util.*;
public class HuddleHandler {
    private static final double HUDDLE_RANGE       = 2.0;
    private static final double HUDDLE_RADIUS      = 1.0;
    private static final long   HOLD_REQUIRED_MS   = 800;
    private static final long   F_RELEASE_GRACE_MS = 1200;
    private static final long   QTE_WINDOW_MS       = 1400;
    private static final long   QTE_ANIM_PRE_MS     = 400;
    private static final long   HUDDLE_MAX_MS       = 20_000;
    private static final long   COOLDOWN_MS         = 8_000;
    private static final int ANIM_HUDDLE_START = 70;
    private static final int ANIM_HUDDLE_IDLE  = 71;
    private static final int ANIM_HUDDLE_QTE1  = 72;
    private static final int ANIM_HUDDLE_END   = 73;
    private static final int ANIM_HUDDLE_QTE2  = 77;
    private static final int ANIM_HUDDLE_QTE3  = 78;
    private static final int ANIM_NONE         = 0;
    private static final long HUDDLE_START_MS   = 542;
    private static final java.util.Random RANDOM = new java.util.Random();
    private enum HuddleStage { ENTERING, IDLE, RELEASING, QTE, ENDING, DONE }
    private static class HuddleSession {
        final List<UUID> players;
        final UUID p1, p2;
        HuddleStage stage = HuddleStage.ENTERING;
        long stageStart   = System.currentTimeMillis();
        long huddleStart  = System.currentTimeMillis();
        Set<UUID> holdsF;
        Long firstRelease = null;
        int  qteStep        = 0;
        String qteBtn1      = "G";
        String qteBtn2      = "H";
        String qteBtn3      = "G";
        Set<UUID> qteHits   = new java.util.HashSet<>();
        boolean qteOpen        = false;
        boolean qteAnimStarted = false;
        long    qteAnimStart   = 0;
        HuddleSession(List<UUID> playerList) {
            this.players = new ArrayList<>(playerList);
            this.p1 = playerList.get(0);
            this.p2 = playerList.get(1);
            this.holdsF = new java.util.HashSet<>(playerList);
            qteBtn1 = RANDOM.nextBoolean() ? "G" : "H";
            qteBtn2 = qteBtn1.equals("G") ? "H" : "G";
            qteBtn3 = RANDOM.nextBoolean() ? "G" : "H";
        }
        void resetQTE() { qteHits.clear(); qteOpen = false; qteAnimStarted = false; }
        boolean allHit() { return qteHits.containsAll(players); }
        boolean allHoldF() { return holdsF.containsAll(players); }
        boolean anyReleasedF() { return !holdsF.containsAll(players); }
        long elapsed() { return System.currentTimeMillis() - stageStart; }
        long lastAuraTick = 0;
        double auraAngle  = 0.0;
        int stepsHit      = 0;
        String expectedButton() {
            return switch (qteStep) {
                case 1 -> qteBtn1;
                case 2 -> qteBtn2;
                case 3 -> qteBtn3;
                default -> "G";
            };
        }
    }
    private static final Map<String, HuddleSession> sessions      = new HashMap<>();
    private static final Map<UUID, String>          playerSession = new HashMap<>();
    private static final Map<UUID, Long>            fHoldStart    = new HashMap<>();
    private static final Map<String, Long>          cooldowns     = new HashMap<>();
    private static final Map<String, net.minecraft.entity.decoration.ArmorStandEntity> centerStands = new HashMap<>();
    private static final Map<UUID, Long> joinerEnterMs = new HashMap<>();
    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
    public record HuddleFHoldPayload(boolean holding) implements CustomPayload {
        public static final Id<HuddleFHoldPayload> ID =
                new Id<>(Identifier.of("testcoop", "huddle_f_hold"));
        public static final PacketCodec<PacketByteBuf, HuddleFHoldPayload> CODEC =
                PacketCodec.of((val, buf) -> buf.writeBoolean(val.holding()),
                        buf -> new HuddleFHoldPayload(buf.readBoolean()));
        @Override public Id<HuddleFHoldPayload> getId() { return ID; }
    }
    public record HuddleEndPayload(UUID p1, UUID p2, boolean success) implements CustomPayload {
        public static final Id<HuddleEndPayload> ID =
                new Id<>(Identifier.of("testcoop", "huddle_end_result"));
        public static final PacketCodec<PacketByteBuf, HuddleEndPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> { buf.writeUuid(val.p1()); buf.writeUuid(val.p2()); buf.writeBoolean(val.success()); },
                        buf -> new HuddleEndPayload(buf.readUuid(), buf.readUuid(), buf.readBoolean()));
        @Override public Id<HuddleEndPayload> getId() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(HuddleFHoldPayload.ID, HuddleFHoldPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HuddleEndPayload.ID,   HuddleEndPayload.CODEC);
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HuddleFHoldPayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onFHold(ctx.player(), payload.holding())));
        ServerTickEvents.END_SERVER_TICK.register(HuddleHandler::tick);
    }
    private static void onFHold(ServerPlayerEntity player, boolean holding) {
        UUID id = player.getUuid();
        String sk = playerSession.get(id);
        if (holding && sk == null && HighFiveHandler.isInBlockingState(id)) {
            return;
        }
        if (sk != null) {
            HuddleSession s = sessions.get(sk);
            if (s != null) {
                if (holding) s.holdsF.add(id);
                else s.holdsF.remove(id);
                if (s.stage == HuddleStage.IDLE && !holding && s.firstRelease == null)
                    s.firstRelease = System.currentTimeMillis();
                return;
            }
        }
        PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
        if (pose == PoseState.GRAB_READY || pose == PoseState.GRAB_HOLDING
                || pose == PoseState.GRABBED || pose == PoseState.PUSH_IDLE
                || pose == PoseState.PUSH_ACTION) return;
        if (holding) fHoldStart.putIfAbsent(id, System.currentTimeMillis());
        else          fHoldStart.remove(id);
    }
    public static boolean onButtonPress(ServerPlayerEntity player, String button) {
        UUID id = player.getUuid();
        String sk = playerSession.get(id);
        if (sk == null) return false;
        HuddleSession s = sessions.get(sk);
        if (s == null || s.stage != HuddleStage.QTE || !s.qteOpen) {
            System.out.println("[HUDDLE] onButtonPress IGNORED btn=" + button
                    + " player=" + player.getName().getString()
                    + " inSession=" + (sk != null)
                    + " stage=" + (s != null ? s.stage : "null")
                    + " qteOpen=" + (s != null ? s.qteOpen : "null"));
            return false;
        }
        if ("FAIL".equals(button)) {
            failHuddle(s, player.getServer());
            return true;
        }
        if (!"G".equals(button) && !"H".equals(button)) return false;
        String expected = s.expectedButton();
        if (!button.equals(expected)) {
            failHuddle(s, player.getServer());
            return true;
        }
        if (id.equals(s.p1)) s.qteHits.add(s.p1);
        else if (s.players.contains(id)) s.qteHits.add(id);
        return true;
    }
    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (server.getTicks() % 4 == 0) detectNewHuddles(server, now);
        joinerEnterMs.entrySet().removeIf(entry -> {
            if (now - entry.getValue() >= HUDDLE_START_MS) {
                ServerPlayerEntity jp = server.getPlayerManager().getPlayer(entry.getKey());
                if (jp != null) PoseNetworking.broadcastAnimState(jp, ANIM_HUDDLE_IDLE);
                return true;
            }
            return false;
        });
        Set<HuddleSession> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (HuddleSession s : new ArrayList<>(sessions.values())) {
            if (!seen.add(s)) continue;
            tickSession(s, server, now);
        }
        cooldowns.entrySet().removeIf(e -> now - e.getValue() > COOLDOWN_MS);
    }
    private static void detectNewHuddles(MinecraftServer server, long now) {
        List<UUID> holders = new ArrayList<>(fHoldStart.keySet());
        for (UUID joiner : holders) {
            if (playerSession.containsKey(joiner)) continue;
            Long startJ = fHoldStart.get(joiner);
            if (startJ == null || now - startJ < HOLD_REQUIRED_MS) continue;
            ServerPlayerEntity pj = server.getPlayerManager().getPlayer(joiner);
            if (pj == null) continue;
            for (HuddleSession s : new ArrayList<>(sessions.values())) {
                if (s.stage != HuddleStage.IDLE) continue;
                if (s.players.contains(joiner)) continue;
                net.minecraft.entity.decoration.ArmorStandEntity stand =
                        centerStands.get(key(s.p1, s.p2));
                if (stand == null) continue;
                if (pj.distanceTo(stand) > HUDDLE_RANGE * 1.5) continue;
                s.players.add(joiner);
                s.holdsF.add(joiner);
                playerSession.put(joiner, key(s.p1, s.p2));
                fHoldStart.remove(joiner);
                if (stand != null) {
                    Vec3d center = stand.getPos();
                    int n = s.players.size();
                    ServerPlayerEntity p1ref = server.getPlayerManager().getPlayer(s.p1);
                    double baseAngle = 0;
                    if (p1ref != null) {
                        Vec3d toP1 = p1ref.getPos().subtract(center);
                        if (toP1.horizontalLengthSquared() > 0.001) baseAngle = Math.atan2(toP1.z, toP1.x);
                    }
                    int ci = n - 1;
                    double angle = baseAngle + (Math.PI * 2 * ci / n);
                    double targetX = center.x + HUDDLE_RADIUS * Math.cos(angle);
                    double targetZ = center.z + HUDDLE_RADIUS * Math.sin(angle);
                    float targetYaw = (float)(-Math.toDegrees(Math.atan2(center.x - targetX, center.z - targetZ)));
                    Vec3d startPos = pj.getPos();
                    final float fYaw = targetYaw;
                    for (int step = 1; step <= 4; step++) {
                        final double frac = step / 4.0;
                        final double stepX = startPos.x + (targetX - startPos.x) * frac;
                        final double stepZ = startPos.z + (targetZ - startPos.z) * frac;
                        final int delay = step * 50;
                        new java.util.Timer().schedule(new java.util.TimerTask() {
                            @Override public void run() {
                                server.execute(() -> {
                                    if (!pj.isAlive()) return;
                                    pj.teleport(pj.getServerWorld(), stepX, pj.getY(), stepZ,
                                            java.util.Set.of(), fYaw, 0);
                                    if (frac >= 1.0) {
                                        pj.setYaw(fYaw); pj.setBodyYaw(fYaw); pj.setHeadYaw(fYaw);
                                    }
                                });
                            }
                        }, delay);
                    }
                } else {
                    repositionCircle(s, server);
                }
                PoseNetworking.broadcastAnimState(pj, ANIM_HUDDLE_START);
                joinerEnterMs.put(joiner, now);
                for (UUID uid : s.players) {
                    ServerPlayerEntity pp = server.getPlayerManager().getPlayer(uid);
                    if (pp != null) pp.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                }
                final List<UUID> allNow = new java.util.ArrayList<>(s.players);
                final ServerPlayerEntity fpjRef = pj;
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override public void run() {
                        server.execute(() -> {
                            for (UUID uid : allNow) {
                                ServerPlayerEntity pp = server.getPlayerManager().getPlayer(uid);
                                if (pp != null) pp.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                            }
                        });
                    }
                }, 120L);
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override public void run() {
                        server.execute(() -> {
                            for (UUID uid : allNow) {
                                ServerPlayerEntity pp = server.getPlayerManager().getPlayer(uid);
                                if (pp != null) pp.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                            }
                        });
                    }
                }, 260L);
                final ServerPlayerEntity fpj = pj;
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override public void run() {
                        server.execute(() -> {
                            if (!fpj.isAlive()) return;
                            Vec3d arm = fpj.getPos().add(0, 1.4, 0)
                                    .add(fpj.getRotationVec(1.0f).multiply(0.4));
                            fpj.getServerWorld().spawnParticles(ParticleTypes.CRIT,
                                    arm.x, arm.y, arm.z, 6, 0.08, 0.08, 0.08, 0.06);
                            fpj.getServerWorld().spawnParticles(ParticleTypes.ENCHANTED_HIT,
                                    arm.x, arm.y, arm.z, 4, 0.06, 0.06, 0.06, 0.04);
                            fpj.getServerWorld().playSound(null, arm.x, arm.y, arm.z,
                                    SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                                    SoundCategory.PLAYERS, 0.8f, 2.0f);
                        });
                    }
                }, 420L);
                Vec3d sPos = stand.getPos();
                pj.getServerWorld().playSound(null, sPos.x, sPos.y, sPos.z,
                        SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.7f);
                pj.sendMessage(net.minecraft.text.Text.literal("§aYou joined the huddle!"), true);
                for (UUID pid : s.players) {
                    if (pid.equals(joiner)) continue;
                    ServerPlayerEntity pp = server.getPlayerManager().getPlayer(pid);
                    if (pp != null) pp.sendMessage(
                            net.minecraft.text.Text.literal("§a" + pj.getName().getString() + " joined the huddle!"), true);
                }
                break;
            }
        }
        for (int i = 0; i < holders.size(); i++) {
            UUID a = holders.get(i);
            if (playerSession.containsKey(a)) continue;
            Long startA = fHoldStart.get(a);
            if (startA == null || now - startA < HOLD_REQUIRED_MS) continue;
            ServerPlayerEntity pa = server.getPlayerManager().getPlayer(a);
            if (pa == null) continue;
            for (int j = i + 1; j < holders.size(); j++) {
                UUID b = holders.get(j);
                if (playerSession.containsKey(b)) continue;
                Long startB = fHoldStart.get(b);
                if (startB == null || now - startB < HOLD_REQUIRED_MS) continue;
                ServerPlayerEntity pb = server.getPlayerManager().getPlayer(b);
                if (pb == null || pa.distanceTo(pb) > HUDDLE_RANGE) continue;
                String k = key(a, b);
                if (cooldowns.containsKey(k)) continue;
                List<UUID> initPlayers = new ArrayList<>(java.util.List.of(a, b));
                HuddleSession s = new HuddleSession(initPlayers);
                sessions.put(k, s);
                playerSession.put(a, k);
                playerSession.put(b, k);
                fHoldStart.remove(a);
                fHoldStart.remove(b);
                positionAndSpawnStand(s, pa, pb, server);
                break;
            }
        }
    }
    private static void positionAndSpawnStand(HuddleSession s,
                                              ServerPlayerEntity pa, ServerPlayerEntity pb,
                                              MinecraftServer server) {
        Vec3d mid = pa.getPos().add(pb.getPos()).multiply(0.5);
        net.minecraft.server.world.ServerWorld world = pa.getServerWorld();
        net.minecraft.entity.decoration.ArmorStandEntity stand =
                new net.minecraft.entity.decoration.ArmorStandEntity(world, mid.x, mid.y, mid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        world.spawnEntity(stand);
        centerStands.put(key(s.p1, s.p2), stand);
        repositionCircle(s, server);
        for (UUID uid : s.players) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uid);
            if (p != null) {
                PoseNetworking.broadcastAnimState(p, ANIM_HUDDLE_START);
                p.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
            }
        }
        final List<UUID> foundersSnap = new java.util.ArrayList<>(s.players);
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                pa.getServer().execute(() -> {
                    for (UUID uid : foundersSnap) {
                        ServerPlayerEntity p = pa.getServer().getPlayerManager().getPlayer(uid);
                        if (p != null) p.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                    }
                });
            }
        }, 120L);
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                pa.getServer().execute(() -> {
                    for (UUID uid : foundersSnap) {
                        ServerPlayerEntity p = pa.getServer().getPlayerManager().getPlayer(uid);
                        if (p != null) p.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                    }
                });
            }
        }, 260L);
        world.playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
        world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                mid.x, mid.y + 1, mid.z, 12, 0.4, 0.4, 0.4, 0.06);
    }
    private static void repositionCircle(HuddleSession s, MinecraftServer server) {
        net.minecraft.entity.decoration.ArmorStandEntity stand = centerStands.get(key(s.p1, s.p2));
        if (stand == null) return;
        Vec3d center = stand.getPos();
        int n = s.players.size();
        ServerPlayerEntity p1p = server.getPlayerManager().getPlayer(s.p1);
        double baseAngle = 0;
        if (p1p != null) {
            Vec3d toP1 = p1p.getPos().subtract(center);
            if (toP1.horizontalLengthSquared() > 0.001) {
                baseAngle = Math.atan2(toP1.z, toP1.x);
            }
        }
        for (int ci = 0; ci < n; ci++) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(s.players.get(ci));
            if (p == null) continue;
            double angle = baseAngle + (Math.PI * 2 * ci / n);
            double px = center.x + HUDDLE_RADIUS * Math.cos(angle);
            double pz = center.z + HUDDLE_RADIUS * Math.sin(angle);
            float yaw = (float)(-Math.toDegrees(Math.atan2(center.x - px, center.z - pz)));
            p.teleport(p.getServerWorld(), px, p.getY(), pz, java.util.Set.of(), yaw, 0);
            p.setYaw(yaw); p.setBodyYaw(yaw); p.setHeadYaw(yaw);
        }
    }
    private static void tickSession(HuddleSession s, MinecraftServer server, long now) {
        ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(s.p1);
        ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(s.p2);
        if (p1 == null || p2 == null) { failHuddle(s, server); return; }
        List<ServerPlayerEntity> live = new ArrayList<>();
        for (UUID uid : s.players) {
            ServerPlayerEntity lp = server.getPlayerManager().getPlayer(uid);
            if (lp == null) { failHuddle(s, server); return; }
            live.add(lp);
        }
        if (now - s.huddleStart > HUDDLE_MAX_MS) { failHuddle(s, server); return; }
        switch (s.stage) {
            case ENTERING -> {
                if (s.elapsed() >= HUDDLE_START_MS) {
                    s.stage = HuddleStage.IDLE;
                    s.stageStart = now;
                    PoseNetworking.broadcastAnimState(p1, ANIM_HUDDLE_IDLE);
                    PoseNetworking.broadcastAnimState(p2, ANIM_HUDDLE_IDLE);
                }
            }
            case IDLE -> {
                if (now - s.lastAuraTick >= 80) {
                    s.lastAuraTick = now;
                    s.auraAngle += 0.35;
                    Vec3d center = p1.getPos().add(p2.getPos()).multiply(0.5);
                    double r = 1.4;
                    for (int i = 0; i < 4; i++) {
                        double a = s.auraAngle + (Math.PI / 2 * i);
                        double px = center.x + r * Math.cos(a);
                        double pz = center.z + r * Math.sin(a);
                        p1.getServerWorld().spawnParticles(ParticleTypes.END_ROD,
                                px, center.y + 0.05, pz, 1, 0, 0, 0, 0.01);
                    }
                    double r2 = 0.7;
                    for (int i = 0; i < 3; i++) {
                        double a = -s.auraAngle * 1.5 + (Math.PI * 2 / 3 * i);
                        double px = center.x + r2 * Math.cos(a);
                        double pz = center.z + r2 * Math.sin(a);
                        p1.getServerWorld().spawnParticles(ParticleTypes.ENCHANTED_HIT,
                                px, center.y + 0.3, pz, 1, 0, 0, 0, 0.005);
                    }
                }
                if (s.anyReleasedF()) {
                    if (s.firstRelease == null) {
                        s.firstRelease = now;
                        Vec3d alertMid = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1, 0);
                        p1.getServerWorld().playSound(null, alertMid.x, alertMid.y, alertMid.z,
                                SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.2f, 1.8f);
                        p1.getServerWorld().spawnParticles(ParticleTypes.NOTE,
                                alertMid.x, alertMid.y + 0.5, alertMid.z, 5, 0.3, 0.2, 0.3, 0.1);
                    }
                    boolean bothReleased = s.holdsF.isEmpty();
                    boolean graceExpired = (now - s.firstRelease) > F_RELEASE_GRACE_MS;
                    if (bothReleased) {
                        Vec3d mid = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1, 0);
                        p1.getServerWorld().playSound(null, mid.x, mid.y, mid.z,
                                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.3f);
                        p1.getServerWorld().playSound(null, mid.x, mid.y, mid.z,
                                ModSounds.DAP_HIT, SoundCategory.PLAYERS, 0.9f, 1.4f);
                        p1.getServerWorld().spawnParticles(ParticleTypes.CRIT,
                                mid.x, mid.y, mid.z, 12, 0.25, 0.25, 0.25, 0.1);
                        p1.getServerWorld().spawnParticles(ParticleTypes.ENCHANTED_HIT,
                                mid.x, mid.y, mid.z, 6, 0.2, 0.2, 0.2, 0.06);
                        startQTEStep(s, server, p1, p2, now);
                    } else if (graceExpired) {
                        failHuddle(s, server);
                    }
                } else {
                    s.firstRelease = null;
                }
            }
            case QTE -> {
                if (!s.qteAnimStarted) return;
                long animElapsed = now - s.qteAnimStart;
                if (!s.qteOpen && animElapsed >= QTE_ANIM_PRE_MS) {
                    s.qteOpen = true;
                    s.stageStart = now;
                    String btn = s.expectedButton();
                    for (ServerPlayerEntity lp : live) {
                        ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(
                                lp.getUuid(), btn, s.qteStep, 0L, QTE_WINDOW_MS, true, 0));
                    }
                    Vec3d mid = p1.getPos().add(p2.getPos()).multiply(0.5);
                    p1.getServerWorld().playSound(null, mid.x, mid.y, mid.z,
                            SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS,
                            0.9f, 1.4f + s.qteStep * 0.1f);
                }
                if (!s.qteOpen) return;
                if (s.allHit()) {
                    s.stepsHit++;
                    for (ServerPlayerEntity lp : live) {
                        lp.addStatusEffect(new StatusEffectInstance(
                                StatusEffects.SPEED, 400, s.stepsHit, false, true));
                        lp.addStatusEffect(new StatusEffectInstance(
                                StatusEffects.STRENGTH, 400, s.stepsHit - 1, false, true));
                    }
                    Vec3d flashMid = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1, 0);
                    int flashCount = 4 + s.stepsHit * 4;
                    p1.getServerWorld().spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                            flashMid.x, flashMid.y, flashMid.z, flashCount, 0.4, 0.4, 0.4, 0.2);
                    p1.getServerWorld().playSound(null, flashMid.x, flashMid.y, flashMid.z,
                            SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS,
                            1.0f, 1.4f + s.qteStep * 0.15f);
                    if (s.qteStep == 3) {
                        s.qteStep = 0;
                        s.resetQTE();
                        for (ServerPlayerEntity lp : live) {
                            PoseNetworking.broadcastAnimState(lp, ANIM_HUDDLE_END);
                            ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(
                                    lp.getUuid(), "G", 0, 0L, 0L, false, 0));
                        }
                        successHuddle(s, server, live, now);
                    } else {
                        s.qteStep++;
                        s.resetQTE();
                        s.stageStart = now;
                        s.qteAnimStarted = true;
                        s.qteAnimStart   = now;
                        int nextAnim = switch (s.qteStep) {
                            case 2 -> ANIM_HUDDLE_QTE2;
                            case 3 -> ANIM_HUDDLE_QTE3;
                            default -> ANIM_HUDDLE_QTE1;
                        };
                        for (ServerPlayerEntity lp : live) PoseNetworking.broadcastAnimState(lp, nextAnim);
                    }
                    return;
                }
                if (s.elapsed() > QTE_WINDOW_MS + QTE_ANIM_PRE_MS) {
                    failHuddle(s, server);
                }
            }
            case ENDING -> {
                if (s.elapsed() >= 1375) {
                    for (ServerPlayerEntity lp : live) {
                        ServerPlayNetworking.send(lp, new ChargedDapHandler.PerfectDapFreezePayload(false));
                    }
                    cleanupSession(s, server);
                }
            }
            case DONE -> cleanupSession(s, server);
        }
    }
    private static void startQTEStep(HuddleSession s, MinecraftServer server,
                                     ServerPlayerEntity p1, ServerPlayerEntity p2, long now) {
        s.qteStep = 1;
        s.stage = HuddleStage.QTE;
        s.stageStart = now;
        s.resetQTE();
        s.qteAnimStarted = true;
        s.qteAnimStart   = now;
        for (UUID uid : s.players) {
            ServerPlayerEntity lp = server.getPlayerManager().getPlayer(uid);
            if (lp == null) continue;
            PoseNetworking.broadcastAnimState(lp, ANIM_HUDDLE_QTE1);
        }
    }
    private static void successHuddle(HuddleSession s, MinecraftServer server,
                                      List<ServerPlayerEntity> live, long now) {
        s.stage = HuddleStage.ENDING;
        s.stageStart = now;
        cooldowns.put(key(s.p1, s.p2), now);
        for (ServerPlayerEntity lp : live) {
            ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(
                    lp.getUuid(), "G", 0, 0L, 0L, false, 0));
        }
        for (ServerPlayerEntity lp : live) {
            lp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 2));
            lp.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,        400, 2));
            lp.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH,     400, 1));
            lp.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,   200, 0));
            lp.experienceLevel += 2;
            lp.sendMessage(net.minecraft.text.Text.literal(
                    "§d§l✦ HUDDLE! ✦ §7+Regen III, Speed III, Strength II, Resistance I"), true);
        }
        ServerPlayerEntity p1 = live.get(0), p2 = live.get(1);
        Vec3d center = p1.getPos().add(p2.getPos()).multiply(0.5);
        ServerWorld flashWorld = p1.getServerWorld();
        flashWorld.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.PLAYERS, 2.0f, 0.6f);
        flashWorld.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 2.0f, 0.8f);
        flashWorld.playSound(null, center.x, center.y, center.z,
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.5f, 1.0f);
        final List<ServerPlayerEntity> liveFinal = new ArrayList<>(live);
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                server.execute(() -> {
                    if (liveFinal.stream().noneMatch(net.minecraft.entity.LivingEntity::isAlive)) return;
                    Vec3d sum = Vec3d.ZERO;
                    for (ServerPlayerEntity lp : liveFinal) sum = sum.add(lp.getPos());
                    Vec3d mid = sum.multiply(1.0 / liveFinal.size()).add(0, 1, 0);
                    ServerWorld world = liveFinal.get(0).getServerWorld();
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,   mid.x, mid.y, mid.z, 80, 0.8, 0.8, 0.8, 0.5);
                    world.spawnParticles(ParticleTypes.COMPOSTER,        mid.x, mid.y, mid.z, 60, 0.6, 0.6, 0.6, 0.4);
                    world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, 40, 0.6, 0.6, 0.6, 0.3);
                    world.spawnParticles(ParticleTypes.FLASH,            mid.x, mid.y, mid.z,  3, 0.1, 0.1, 0.1,   0);
                    world.spawnParticles(ParticleTypes.FIREWORK,         mid.x, mid.y, mid.z, 40, 0.5, 0.6, 0.5, 0.25);
                    world.spawnParticles(ParticleTypes.CRIT,             mid.x, mid.y - 0.5, mid.z, 20, 1.0, 0, 1.0, 0.05);
                    world.spawnParticles(ParticleTypes.HEART,            mid.x, mid.y, mid.z, 15, 0.6, 0.4, 0.6, 0.1);
                    for (double a = 0; a < Math.PI * 2; a += 0.4) {
                        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                                mid.x + Math.cos(a) * 1.5, mid.y - 0.9, mid.z + Math.sin(a) * 1.5,
                                2, 0, 0.1, 0, 0.05);
                    }
                    world.playSound(null, mid.x, mid.y, mid.z,
                            SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 2.0f, 1.2f);
                    world.playSound(null, mid.x, mid.y, mid.z,
                            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.5f, 1.2f);
                    world.playSound(null, mid.x, mid.y, mid.z,
                            SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.5f, 2.0f);
                    net.minecraft.item.ItemStack fw = new net.minecraft.item.ItemStack(net.minecraft.item.Items.FIREWORK_ROCKET);
                    for (int ri = 0; ri < 5; ri++) {
                        double ox = (RANDOM.nextDouble() - 0.5) * 1.2;
                        double oz = (RANDOM.nextDouble() - 0.5) * 1.2;
                        world.spawnEntity(new net.minecraft.entity.projectile.FireworkRocketEntity(
                                world, mid.x + ox, mid.y + 0.5, mid.z + oz, fw));
                    }
                });
            }
        }, 500L);
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                server.execute(() -> {
                    if (liveFinal.isEmpty() || !liveFinal.get(0).isAlive()) return;
                    Vec3d sum2 = Vec3d.ZERO;
                    for (ServerPlayerEntity lp : liveFinal) sum2 = sum2.add(lp.getPos());
                    Vec3d mid = sum2.multiply(1.0 / liveFinal.size()).add(0, 1, 0);
                    ServerWorld world = liveFinal.get(0).getServerWorld();
                    for (int pulse = 0; pulse < 4; pulse++) {
                        final int pp = pulse;
                        new java.util.Timer().schedule(new java.util.TimerTask() {
                            @Override public void run() {
                                server.execute(() -> {
                                    float fade = 1f - pp * 0.25f;
                                    int cnt = (int)(20 * fade);
                                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                                            mid.x, mid.y, mid.z, cnt, 0.9 * fade, 0.7 * fade, 0.9 * fade, 0.15);
                                    world.spawnParticles(ParticleTypes.COMPOSTER,
                                            mid.x, mid.y, mid.z, cnt / 2, 0.7 * fade, 0.6 * fade, 0.7 * fade, 0.1);
                                });
                            }
                        }, (long)(pp * 250));
                    }
                });
            }
        }, 1875L);
        for (ServerPlayerEntity lp : live) {
            ServerPlayNetworking.send(lp, new HuddleEndPayload(s.p1, s.p2, true));
        }
    }
    private static void failHuddle(HuddleSession s, MinecraftServer server) {
        cooldowns.put(key(s.p1, s.p2), System.currentTimeMillis());
        if (server == null) { cleanupSession(s, server); return; }
        List<ServerPlayerEntity> live = new ArrayList<>();
        for (UUID uid : s.players) {
            ServerPlayerEntity lp = server.getPlayerManager().getPlayer(uid);
            if (lp != null) live.add(lp);
        }
        for (ServerPlayerEntity lp : live) {
            ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(
                    lp.getUuid(), "G", 0, 0L, 0L, false, 0));
            PoseNetworking.broadcastAnimState(lp, ANIM_NONE);
            lp.removeStatusEffect(StatusEffects.SPEED);
            lp.removeStatusEffect(StatusEffects.STRENGTH);
            lp.sendMessage(net.minecraft.text.Text.literal("§c✗ Huddle failed!"), true);
            ServerPlayNetworking.send(lp, new HuddleEndPayload(s.p1, s.p2, false));
        }
        if (live.size() >= 2) {
            net.minecraft.entity.decoration.ArmorStandEntity stand = centerStands.get(key(s.p1, s.p2));
            Vec3d center = stand != null ? stand.getPos()
                    : live.get(0).getPos().add(live.get(1).getPos()).multiply(0.5);
            for (ServerPlayerEntity lp : live) {
                Vec3d dir = lp.getPos().subtract(center).normalize();
                lp.addVelocity(dir.x * 0.6, 0.4, dir.z * 0.6);
                lp.velocityModified = true;
            }
            live.get(0).getServerWorld().spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                    center.x, center.y + 1, center.z, 8, 0.3, 0.3, 0.3, 0.05);
            live.get(0).getServerWorld().playSound(null, center.x, center.y, center.z,
                    SoundEvents.ENTITY_ZOMBIE_INFECT, SoundCategory.PLAYERS, 0.7f, 1.5f);
        }
        cleanupSession(s, server);
    }
    private static void cleanupSession(HuddleSession s, MinecraftServer server) {
        String k = key(s.p1, s.p2);
        sessions.remove(k);
        for (UUID uid : s.players) {
            playerSession.remove(uid);
            joinerEnterMs.remove(uid);
            if (server != null) {
                ServerPlayerEntity lp = server.getPlayerManager().getPlayer(uid);
                if (lp != null) ServerPlayNetworking.send(lp, new ChargedDapHandler.PerfectDapFreezePayload(false));
            }
        }
        net.minecraft.entity.decoration.ArmorStandEntity stand = centerStands.remove(k);
        if (stand != null && !stand.isRemoved()) stand.discard();
    }
    public static boolean isInHuddle(UUID id) { return playerSession.containsKey(id); }
    public static void cleanup(UUID id) {
        fHoldStart.remove(id);
        String k = playerSession.remove(id);
        if (k != null) {
            HuddleSession s = sessions.remove(k);
            if (s != null) playerSession.remove(s.p1.equals(id) ? s.p2 : s.p1);
        }
    }
}