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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.*;


public class BonkHandler {

    // ── Config ────────────────────────────────────────────────────────────────
    public static boolean ENABLED = true;  // toggled from BonkConfig on init

    // ── AnimState ordinals ────────────────────────────────────────────────────
    private static final int ANIM_LAY_DOWN = 79;  // plays lay_down.json via GeckoLib
    private static final int ANIM_BONK     = 80;  // plays bonk.json via GeckoLib
    private static final int ANIM_NONE     = 0;

    // ── Positioning ───────────────────────────────────────────────────────────
    // *** CHANGE THESE to adjust bonker position on top of victim ***
    private static final double BONKER_Y_OFFSET   = 0.6;   // how high above victim (+= up)
    // *** PUSH BACK: negative = toward feet, positive = toward head. Change this one value ***
    private static final double BONKER_XZ_OFFSET = 0.6;  // currently 0.2 blocks toward feet
    private static final double VICTIM_Y_SINK     = 1.0;   // how far victim sinks into ground
    // *********************************************************************

    // ── Bonk animation timing (loop = 3125ms) ────────────────────────────────
    private static final long LOOP_MS     = 3125L;
    // *** Impact timestamps — match bonk.json keyframe peaks. Adjust if sounds/particles feel off ***
    private static final long HIT_RIGHT_1 = 375L;   // 0.375s right arm peak        → camera RIGHT
    private static final long HIT_LEFT_1  = 667L;   // 0.667s left arm extended     → camera LEFT
    private static final long HIT_RIGHT_2 = 1417L;  // 1.417s right arm second peak → camera RIGHT
    private static final long HIT_ANVIL   = 2542L;  // 2.542s both arms slam        → camera UP
    private static final long HIT_LEFT_2  = 2917L;  // 2.917s left arm reset        → camera LEFT
    // *** Widen HIT_WINDOW (ms) if impacts still feel late ***
    private static final long HIT_WINDOW  = 100L;

    // ── Camera jerk amounts (degrees) ────────────────────────────────────────
    private static final float CAM_SIDE   = 40f;    // left/right jerk
    private static final float CAM_UP     = -55f;   // up flick (negative pitch = look up)

    // ── Range for bonker to join ──────────────────────────────────────────────
    private static final double JOIN_RANGE = 2.5;

    // ── Payloads ──────────────────────────────────────────────────────────────
    public static final Identifier BONK_CAMERA_ID   = Identifier.of("cooptest", "bonk_camera");
    public static final Identifier BONK_MOVE_LOCK_ID = Identifier.of("cooptest", "bonk_move_lock");

    /** S2C — forces victim's camera to delta yaw/pitch */
    public record BonkCameraPayload(float deltaYaw, float deltaPitch) implements CustomPayload {
        public static final Id<BonkCameraPayload> ID = new Id<>(BONK_CAMERA_ID);
        public static final PacketCodec<PacketByteBuf, BonkCameraPayload> CODEC =
                PacketCodec.of((v, b) -> { b.writeFloat(v.deltaYaw); b.writeFloat(v.deltaPitch); },
                        b -> new BonkCameraPayload(b.readFloat(), b.readFloat()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S2C — locks/unlocks victim movement (but NOT camera) */
    public record BonkMoveLockPayload(boolean locked) implements CustomPayload {
        public static final Id<BonkMoveLockPayload> ID = new Id<>(BONK_MOVE_LOCK_ID);
        public static final PacketCodec<PacketByteBuf, BonkMoveLockPayload> CODEC =
                PacketCodec.of((v, b) -> b.writeBoolean(v.locked),
                        b -> new BonkMoveLockPayload(b.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** C2S — client sends when player presses L */
    public static final Identifier BONK_L_KEY_ID = Identifier.of("cooptest", "bonk_l_key");
    public record BonkLKeyPayload() implements CustomPayload {
        public static final Id<BonkLKeyPayload> ID = new Id<>(BONK_L_KEY_ID);
        public static final PacketCodec<PacketByteBuf, BonkLKeyPayload> CODEC =
                PacketCodec.unit(new BonkLKeyPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(BonkCameraPayload.ID,   BonkCameraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BonkMoveLockPayload.ID, BonkMoveLockPayload.CODEC);
        // C2S — must be registered server-side so the client can send it
        PayloadTypeRegistry.playC2S().register(BonkLKeyPayload.ID, BonkLKeyPayload.CODEC);
    }

    // ── State ─────────────────────────────────────────────────────────────────
    // Players currently laying down (victim UUID → their yaw at lay time)
    private static final Map<UUID, Float>  layingPlayers = new HashMap<>();
    // Active bonk sessions: bonker UUID → victim UUID
    private static final Map<UUID, UUID>   bonkSessions   = new HashMap<>();
    // Reverse: victim UUID → bonker UUID
    private static final Map<UUID, UUID>   victimMap      = new HashMap<>();
    // When each bonk session started (ms) — used for animation-relative timing
    private static final Map<UUID, Long>   sessionStartMs  = new HashMap<>();
    // Victim's yaw locked at bonk start — bonker position uses this, NOT victim.getYaw()
    // This prevents the bonker from swinging when the victim's camera jerks left/right
    private static final Map<UUID, Float>  victimLockedYaw = new HashMap<>();

    // ── Registration ──────────────────────────────────────────────────────────
    public static void register() {
        // C2S receiver for L key press
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                BonkLKeyPayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onLKey(ctx.player())));
        ServerTickEvents.END_SERVER_TICK.register(BonkHandler::tick);
    }

    // ── L key press handler ───────────────────────────────────────────────────
    public static void onLKey(ServerPlayerEntity player) {
        if (!ENABLED) return;
        UUID id = player.getUuid();

        // If this player is currently bonking → stop
        if (bonkSessions.containsKey(id)) {
            stopBonk(id, player.getServer());
            return;
        }

        // If this player is currently laying → get up
        if (layingPlayers.containsKey(id)) {
            stopLaying(id, player.getServer());
            return;
        }

        // If nearby someone is laying → become the bonker
        UUID victim = findNearbyLaying(player);
        if (victim != null) {
            startBonk(player, victim);
            return;
        }

        // Otherwise → lay down
        startLaying(player);
    }

    // ── Lay down ──────────────────────────────────────────────────────────────
    private static void startLaying(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        layingPlayers.put(id, player.getYaw());

        // *** Teleport down so lay_down animation sits on ground surface ***
        // *** Adjust VICTIM_Y_SINK above if player floats or clips too deep ***
        player.teleport(player.getServerWorld(),
                player.getX(), player.getY() - VICTIM_Y_SINK, player.getZ(),
                java.util.Set.of(), player.getYaw(), 0);

        // Play lay_down.json animation (hold_on_last_frame — stays until server sends NONE)
        PoseNetworking.broadcastAnimState(player, ANIM_LAY_DOWN);

        // Lock movement only — camera stays completely free for impact jerks
        ServerPlayNetworking.send(player, new BonkMoveLockPayload(true));
        player.sendMessage(net.minecraft.text.Text.literal("§7[Laying down — press L to get up]"), true);
    }

    private static void stopLaying(UUID id, MinecraftServer server) {
        layingPlayers.remove(id);
        sessionStartMs.remove(id);
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p != null) {
            // Restore Y position
            p.teleport(p.getServerWorld(),
                    p.getX(), p.getY() + VICTIM_Y_SINK, p.getZ(),
                    java.util.Set.of(), p.getYaw(), 0);
            PoseNetworking.broadcastAnimState(p, ANIM_NONE);
            ServerPlayNetworking.send(p, new BonkMoveLockPayload(false));
        }
    }

    // ── Start bonk session ────────────────────────────────────────────────────
    private static void startBonk(ServerPlayerEntity bonker, UUID victimId) {
        MinecraftServer server = bonker.getServer();
        if (server == null) return;
        ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
        if (victim == null) return;

        UUID bonkerId = bonker.getUuid();
        bonkSessions.put(bonkerId, victimId);
        victimMap.put(victimId, bonkerId);
        sessionStartMs.put(bonkerId, System.currentTimeMillis());
        victimLockedYaw.put(bonkerId, victim.getYaw());  // lock yaw — bonker won't move when victim camera jerks

        // ════════════════════════════════════════════════════════════
        // BONKER POSITIONING — tweak these values to adjust placement
        // ════════════════════════════════════════════════════════════
        Vec3d vPos = victim.getPos();
        float lockedYaw = victim.getYaw();  // captured once — used for all future tick repositioning
        double fwdX = -Math.sin(Math.toRadians(lockedYaw));
        double fwdZ =  Math.cos(Math.toRadians(lockedYaw));

        double targetX = vPos.x + fwdX * BONKER_XZ_OFFSET;
        double targetZ = vPos.z + fwdZ * BONKER_XZ_OFFSET;
        double targetY = vPos.y + BONKER_Y_OFFSET;

        float yaw   = lockedYaw + 180f;  // *** +180 = faces victim body ***
        float pitch = 30f;               // *** positive = look down ***

        bonker.teleport(bonker.getServerWorld(), targetX, targetY, targetZ,
                java.util.Set.of(), yaw, pitch);
        bonker.setYaw(yaw); bonker.setBodyYaw(yaw); bonker.setHeadYaw(yaw);
        // ════════════════════════════════════════════════════════════

        // Prevent suffocation damage while sunk into ground
        bonker.setInvulnerable(true);

        // Play BONK looping anim on bonker
        PoseNetworking.broadcastAnimState(bonker, ANIM_BONK);

        // Lock bonker movement too (they stay in place)
        ServerPlayNetworking.send(bonker, new BonkMoveLockPayload(true));

        bonker.sendMessage(net.minecraft.text.Text.literal("§c§lBONK! §7Press L to stop"), true);
        victim.sendMessage(net.minecraft.text.Text.literal("§c§lYou're getting bonked! §7Press L to escape"), true);
    }

    private static void stopBonk(UUID bonkerId, MinecraftServer server) {
        UUID victimId = bonkSessions.remove(bonkerId);
        if (victimId != null) victimMap.remove(victimId);
        sessionStartMs.remove(bonkerId);
        victimLockedYaw.remove(bonkerId);

        ServerPlayerEntity bonker = server.getPlayerManager().getPlayer(bonkerId);
        if (bonker != null) {
            bonker.setInvulnerable(false);
            PoseNetworking.broadcastAnimState(bonker, ANIM_NONE);
            ServerPlayNetworking.send(bonker, new BonkMoveLockPayload(false));
            bonker.addVelocity(0, 0.3, 0);
            bonker.velocityModified = true;
        }

        if (victimId != null) {
            layingPlayers.remove(victimId);
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
            if (victim != null) {
                // Restore victim's Y position (they were sunk VICTIM_Y_SINK blocks)
                victim.teleport(victim.getServerWorld(),
                        victim.getX(), victim.getY() + VICTIM_Y_SINK, victim.getZ(),
                        java.util.Set.of(), victim.getYaw(), 0);
                PoseNetworking.broadcastAnimState(victim, ANIM_NONE);
                ServerPlayNetworking.send(victim, new BonkMoveLockPayload(false));
            }
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        // ── Re-apply sleeping state for players waiting to be bonked ─────────
        // Even with the mixin, we re-apply every tick as belt-and-suspenders
        for (UUID id : new ArrayList<>(layingPlayers.keySet())) {
            if (victimMap.containsKey(id)) continue; // already in a bonk session, handled below
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) { stopLaying(id, server); continue; }
            p.setVelocity(0, 0, 0);
            p.velocityModified = true;
        }

        for (UUID bonkerId : new ArrayList<>(bonkSessions.keySet())) {
            UUID victimId = bonkSessions.get(bonkerId);
            ServerPlayerEntity bonker = server.getPlayerManager().getPlayer(bonkerId);
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);

            if (bonker == null || victim == null) {
                stopBonk(bonkerId, server);
                continue;
            }

            // Keep bonker locked above victim every tick
            // *** To change position: edit headOffset / BONKER_Y_OFFSET / bonkerYaw / bonkerPitch ***
            Vec3d vPos = victim.getPos();
            // Use LOCKED yaw (captured at session start) — NOT victim.getYaw()
            // This prevents the bonker from swinging when victim's camera jerks
            float lockedYaw = victimLockedYaw.getOrDefault(bonkerId, victim.getYaw());
            double fwdX = -Math.sin(Math.toRadians(lockedYaw));
            double fwdZ =  Math.cos(Math.toRadians(lockedYaw));
            double bx = vPos.x + fwdX * BONKER_XZ_OFFSET;
            double bz = vPos.z + fwdZ * BONKER_XZ_OFFSET;
            float bonkerYaw   = lockedYaw + 180f;  // *** +180 = faces victim body ***
            float bonkerPitch = 30f;               // *** positive = look down ***
            bonker.teleport(bonker.getServerWorld(), bx, vPos.y + BONKER_Y_OFFSET, bz,
                    java.util.Set.of(), bonkerYaw, bonkerPitch);
            bonker.setInvulnerable(true);

            // Pin victim in place
            victim.setVelocity(0, 0, 0);
            victim.velocityModified = true;

            // ── Impact detection — (now - sessionStart) % LOOP_MS syncs to animation loop ──
            Long startMs = sessionStartMs.get(bonkerId);
            if (startMs == null) continue;
            // loopElapsed 0..3124ms matches bonk.json timeline exactly
            long loopElapsed = (now - startMs) % LOOP_MS;

            ServerWorld world = bonker.getServerWorld();
            Vec3d handPos = bonker.getPos().add(0, 1.4, 0); // approx right-hand height

            fireIfInWindow(loopElapsed, HIT_RIGHT_1, bonkerId, () -> {
                // Right arm impact → victim camera RIGHT
                spawnImpactParticles(world, handPos);
                world.playSound(null, handPos.x, handPos.y, handPos.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.2f);
                world.playSound(null, handPos.x, handPos.y, handPos.z,
                        ModSounds.DAP_HIT, SoundCategory.PLAYERS, 0.9f, 1.1f);
                ServerPlayNetworking.send(victim, new BonkCameraPayload(CAM_SIDE, 0));
            });

            fireIfInWindow(loopElapsed, HIT_LEFT_1, bonkerId, () -> {
                // Left arm → victim camera LEFT
                spawnImpactParticles(world, handPos);
                world.playSound(null, handPos.x, handPos.y, handPos.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                world.playSound(null, handPos.x, handPos.y, handPos.z,
                        ModSounds.DAP_HIT, SoundCategory.PLAYERS, 0.9f, 0.9f);
                ServerPlayNetworking.send(victim, new BonkCameraPayload(-CAM_SIDE, 0));
            });

            fireIfInWindow(loopElapsed, HIT_RIGHT_2, bonkerId, () -> {
                spawnImpactParticles(world, handPos);
                world.playSound(null, handPos.x, handPos.y, handPos.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.3f);
                ServerPlayNetworking.send(victim, new BonkCameraPayload(CAM_SIDE, 0));
            });

            fireIfInWindow(loopElapsed, HIT_ANVIL, bonkerId, () -> {
                // Anvil slam — both arms, camera UP
                Vec3d mid = victim.getPos().add(0, 1.0, 0);
                world.playSound(null, mid.x, mid.y, mid.z,
                        SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.5f, 0.8f);
                world.playSound(null, mid.x, mid.y, mid.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2f, 0.6f);
                world.spawnParticles(ParticleTypes.CRIT,           mid.x, mid.y, mid.z, 20, 0.4, 0.3, 0.4, 0.15);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT,  mid.x, mid.y, mid.z, 12, 0.3, 0.2, 0.3, 0.10);
                world.spawnParticles(ParticleTypes.FLASH,          mid.x, mid.y, mid.z,  2,   0,   0,   0,    0);
                world.spawnParticles(ParticleTypes.EXPLOSION,      mid.x, mid.y, mid.z,  3, 0.2, 0.1, 0.2, 0.05);
                ServerPlayNetworking.send(victim, new BonkCameraPayload(0, CAM_UP));
            });

            fireIfInWindow(loopElapsed, HIT_LEFT_2, bonkerId, () -> {
                spawnImpactParticles(world, handPos);
                world.playSound(null, handPos.x, handPos.y, handPos.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.9f, 1.1f);
                ServerPlayNetworking.send(victim, new BonkCameraPayload(-CAM_SIDE, 0));
            });
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Fires the action once per loop when loopElapsed is within HIT_WINDOW of the target. */
    private static final Map<UUID, Set<Long>> firedThisCycle = new HashMap<>();

    private static void fireIfInWindow(long loopElapsed, long target, UUID bonkerId, Runnable action) {
        if (loopElapsed >= target && loopElapsed < target + HIT_WINDOW) {
            Set<Long> fired = firedThisCycle.computeIfAbsent(bonkerId, k -> new HashSet<>());
            // Use floor(loopElapsed / LOOP_MS) * LOOP_MS + target as unique key per cycle
            // Since loopElapsed = now % LOOP_MS, the cycle index is now / LOOP_MS
            // Cycle key: which loop iteration is this? Use target as the unique hit ID within cycle
            Long sStart = sessionStartMs.get(bonkerId);
            long cycleIndex = sStart != null ? ((System.currentTimeMillis() - sStart) / LOOP_MS) : 0;
            long cycleKey = cycleIndex * 100000L + target;
            if (fired.add(cycleKey)) {
                action.run();
                // Clean up old cycle keys
                fired.removeIf(k -> k < cycleKey - LOOP_MS);
            }
        }
    }

    private static void spawnImpactParticles(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.CRIT,         pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.1);
        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 4, 0.15, 0.15, 0.15, 0.06);
    }

    private static UUID findNearbyLaying(ServerPlayerEntity bonker) {
        for (UUID id : layingPlayers.keySet()) {
            if (victimMap.containsKey(id)) continue; // already being bonked
            ServerPlayerEntity victim = bonker.getServer().getPlayerManager().getPlayer(id);
            if (victim != null && bonker.distanceTo(victim) <= JOIN_RANGE) return id;
        }
        return null;
    }

    public static boolean isLaying(UUID id) {
        return layingPlayers.containsKey(id) || victimMap.containsKey(id);
    }

    public static boolean isInBonkSession(UUID id) {
        return bonkSessions.containsKey(id) || victimMap.containsKey(id) || layingPlayers.containsKey(id);
    }

    public static void cleanup(UUID id) {
        layingPlayers.remove(id);
        firedThisCycle.remove(id);
        UUID victim = bonkSessions.remove(id);
        if (victim != null) victimMap.remove(victim);
        UUID bonker = victimMap.remove(id);
        if (bonker != null) bonkSessions.remove(bonker);
        sessionStartMs.remove(id);
        victimLockedYaw.remove(id);
    }
}