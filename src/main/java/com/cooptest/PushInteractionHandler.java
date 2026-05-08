package com.cooptest;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
public class PushInteractionHandler {
    private static final float  PUSH_RANGE       = 2.5f;
    private static final long   HOLD_REQUIRED_MS = 1500L;
    private static final long   READY_WINDOW_MS  = 3000L;
    private static final long   COOLDOWN_MS      = 1500L;
    private static final long   PUSH_IMMUNITY_MS = 500L;
    private static final long   JUMP_WINDOW_MS   = 800L;
    private static final double VEL_LOW    = 0.5;
    private static final double VEL_MEDIUM = 1.8;
    private static final double VEL_HIGH   = 3.5;
    private static final HashMap<UUID, UUID> holdTarget  = new HashMap<>();
    private static final HashMap<UUID, Long> holdStart   = new HashMap<>();
    private static final HashMap<UUID, UUID> readyPushers = new HashMap<>();
    private static final HashMap<UUID, Long> readyStart   = new HashMap<>();
    private static final HashMap<UUID, Long> cooldowns    = new HashMap<>();
    public  static final HashMap<UUID, Long> pushImmunity = new HashMap<>();
    public  static final HashMap<UUID, Long> lastJumpTime = new HashMap<>();
    public static final Identifier PUSH_ANIM_ID = Identifier.of("cooptest", "push_anim");
    public record PushAnimPayload(UUID playerId) implements CustomPayload {
        public static final Id<PushAnimPayload> ID = new Id<>(PUSH_ANIM_ID);
        public static final PacketCodec<PacketByteBuf, PushAnimPayload> CODEC =
                PacketCodec.of((p, buf) -> buf.writeUuid(p.playerId), buf -> new PushAnimPayload(buf.readUuid()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(PushAnimPayload.ID, PushAnimPayload.CODEC);
    }
    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK
                .register(PushInteractionHandler::tick);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> {
                    if (world.isClient) return net.minecraft.util.ActionResult.PASS;
                    if (!(player instanceof ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
                    if (!(entity instanceof ServerPlayerEntity target)) return net.minecraft.util.ActionResult.PASS;
                    if (!CoopMovesConfig.get().enablePush) return net.minecraft.util.ActionResult.PASS;
                    long now = System.currentTimeMillis();
                    if (sp.isSneaking()) {
                        if (HighFiveHandler.isInBlockingState(sp.getUuid())) return net.minecraft.util.ActionResult.PASS;
                        if (isOnCooldown(sp.getUuid(), now)) return net.minecraft.util.ActionResult.PASS;
                        if (readyPushers.containsKey(sp.getUuid())) return net.minecraft.util.ActionResult.PASS;
                        if (sp.distanceTo(target) > PUSH_RANGE) return net.minecraft.util.ActionResult.PASS;
                        UUID prevTarget = holdTarget.get(sp.getUuid());
                        if (!target.getUuid().equals(prevTarget)) {
                            holdTarget.put(sp.getUuid(), target.getUuid());
                            holdStart.put(sp.getUuid(), now);
                        }
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                    UUID intendedTarget = readyPushers.get(target.getUuid());
                    if (intendedTarget == null || !intendedTarget.equals(sp.getUuid())) return net.minecraft.util.ActionResult.PASS;
                    Long rs = readyStart.get(target.getUuid());
                    if (rs == null || now - rs > READY_WINDOW_MS) return net.minecraft.util.ActionResult.PASS;
                    if (isOnCooldown(target.getUuid(), now)) return net.minecraft.util.ActionResult.PASS;
                    double vel;
                    Long jt = lastJumpTime.get(sp.getUuid());
                    boolean recentJump = jt != null && (now - jt) < JUMP_WINDOW_MS;
                    if      (recentJump)     vel = capToCeiling(sp, VEL_HIGH);
                    else if (sp.isSneaking()) vel = capToCeiling(sp, VEL_LOW);
                    else                     vel = capToCeiling(sp, VEL_MEDIUM);
                    readyPushers.remove(target.getUuid());
                    readyStart.remove(target.getUuid());
                    executePush(target, sp, vel, now);
                    return net.minecraft.util.ActionResult.SUCCESS;
                });
    }
    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (var entry : new HashMap<>(holdTarget).entrySet()) {
            UUID pusherId   = entry.getKey();
            UUID targetId   = entry.getValue();
            Long startMs    = holdStart.get(pusherId);
            if (startMs == null) { holdTarget.remove(pusherId); continue; }
            ServerPlayerEntity pusher = server.getPlayerManager().getPlayer(pusherId);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
            if (pusher == null || !pusher.isSneaking() || target == null
                    || pusher.distanceTo(target) > PUSH_RANGE) {
                holdTarget.remove(pusherId);
                holdStart.remove(pusherId);
                continue;
            }
            if (readyPushers.containsKey(pusherId)) {
                holdTarget.remove(pusherId);
                holdStart.remove(pusherId);
                continue;
            }
            if (now - startMs >= HOLD_REQUIRED_MS) {
                holdTarget.remove(pusherId);
                holdStart.remove(pusherId);
                readyPushers.put(pusherId, targetId);
                readyStart.put(pusherId, now);
                Vec3d mid = pusher.getPos().add(target.getPos()).multiply(0.5);
                pusher.getServerWorld().playSound(null, mid.x, mid.y, mid.z,
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.8f);
                pusher.sendMessage(net.minecraft.text.Text.literal("§eTell homie to right-click!"), true);
                target.sendMessage(net.minecraft.text.Text.literal("§e[Right-click to launch!]"), true);
            }
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isOnGround() && p.getVelocity().y > 0.08) lastJumpTime.put(p.getUuid(), now);
        }
        for (var re : new HashMap<>(readyPushers).entrySet()) {
            UUID pusherId = re.getKey();
            UUID intendedTarget = re.getValue();
            Long rs = readyStart.get(pusherId);
            if (rs == null || now - rs > READY_WINDOW_MS) continue;
            ServerPlayerEntity pusher = server.getPlayerManager().getPlayer(pusherId);
            if (pusher == null) continue;
            for (ServerPlayerEntity nearby : server.getPlayerManager().getPlayerList()) {
                if (nearby.getUuid().equals(pusherId)) continue;
                if (pusher.distanceTo(nearby) <= PUSH_RANGE) {
                    UUID nearbyId = nearby.getUuid();
                    if (!nearbyId.equals(intendedTarget)) {
                        readyPushers.put(pusherId, nearbyId);
                        nearby.sendMessage(net.minecraft.text.Text.literal("§e[Right-click to launch!]"), true);
                    }
                    break;
                }
            }
        }
        readyPushers.entrySet().removeIf(e -> { Long t = readyStart.get(e.getKey()); return t == null || now - t > READY_WINDOW_MS; });
        readyStart.entrySet().removeIf(e -> now - e.getValue() > READY_WINDOW_MS);
        holdStart.entrySet().removeIf(e -> now - e.getValue() > 10000L);
        lastJumpTime.entrySet().removeIf(e -> now - e.getValue() > JUMP_WINDOW_MS * 4);
        cooldowns.entrySet().removeIf(e -> now - e.getValue() > COOLDOWN_MS * 2);
    }
    private static void executePush(ServerPlayerEntity pusher, ServerPlayerEntity target, double velocity, long now) {
        PushAnimPayload pkt = new PushAnimPayload(pusher.getUuid());
        for (ServerPlayerEntity p : PlayerLookup.tracking(pusher)) ServerPlayNetworking.send(p, pkt);
        ServerPlayNetworking.send(pusher, pkt);
        target.setVelocity(target.getVelocity().x, 0, target.getVelocity().z);
        target.addVelocity(0, velocity, 0);
        target.velocityModified = true;
        pushImmunity.put(target.getUuid(), now);
        LaunchedPlayerTracker.markPlayerAsLaunched(target.getUuid());
        UUID carried = GrabMechanic.holding.get(target.getUuid());
        if (carried != null) {
            ServerPlayerEntity c = target.getServer().getPlayerManager().getPlayer(carried);
            if (c != null) { c.addVelocity(0, velocity * 0.85, 0); c.velocityModified = true;
                LaunchedPlayerTracker.markPlayerAsLaunched(c.getUuid()); pushImmunity.put(c.getUuid(), now); }
        }
        cooldowns.put(pusher.getUuid(), now);
        PoseNetworking.broadcastPoseChange(Objects.requireNonNull(pusher.getServer()), pusher.getUuid(), PoseState.PUSH_ACTION);
    }
    private static double capToCeiling(ServerPlayerEntity t, double base) {
        BlockPos pos = t.getBlockPos();
        for (int y = 1; y <= 15; y++) {
            BlockPos check = pos.up(y); BlockState state = t.getWorld().getBlockState(check);
            if (!state.isAir() && state.isSolidBlock(t.getWorld(), check)) { return Math.min(base, Math.sqrt(2 * 0.08 * 20 * Math.max(2, y - 1))); }
        }
        return base;
    }
    private static boolean isOnCooldown(UUID uuid, long now) { Long t = cooldowns.get(uuid); return t != null && (now - t) < COOLDOWN_MS; }
    public static boolean hasPushImmunity(UUID uuid) {
        Long t = pushImmunity.get(uuid); if (t == null) return false;
        if (System.currentTimeMillis() - t < PUSH_IMMUNITY_MS) return true;
        pushImmunity.remove(uuid); return false;
    }
    public static void cleanupExpiredImmunity() { long now = System.currentTimeMillis(); pushImmunity.entrySet().removeIf(e -> now - e.getValue() > PUSH_IMMUNITY_MS); }
}