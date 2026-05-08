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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.*;
public class HighFiveHugHandler {
    private static final double HUG_DISTANCE = 4.0;
    private static final long HUG_HOLD_TIME_MS = 800;
    private static final long HUG_OPPORTUNITY_MS = 3000;
    private static final Map<UUID, Long> hugHoldStart = new HashMap<>();
    private static final Map<UUID, UUID> hugPartner = new HashMap<>();
    private static final Map<UUID, Long> lastHUpdate = new HashMap<>();
    private static final Map<UUID, HugState> hugState = new HashMap<>();
    private static final Map<UUID, Long> hugStartTime = new HashMap<>();
    private enum HugState {
        NONE,
        START,
        HUGGING,
        ENDING
    }
    public static final Identifier HUG_HOLD_ID = Identifier.of("cooptest", "hug_hold");
    public record HugHoldPayload() implements CustomPayload {
        public static final Id<HugHoldPayload> ID = new Id<>(HUG_HOLD_ID);
        public static final PacketCodec<PacketByteBuf, HugHoldPayload> CODEC =
                PacketCodec.unit(new HugHoldPayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(HugHoldPayload.ID, HugHoldPayload.CODEC);
    }
    public static void registerClientPayloads() {
        try { PayloadTypeRegistry.playC2S().register(HugHoldPayload.ID, HugHoldPayload.CODEC); } catch (Exception ignored) {}
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HugHoldPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                onPlayerHoldingH(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server));
    }
    public static void startHugHold(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        long now = System.currentTimeMillis();
        hugHoldStart.put(p1.getUuid(), now);
        hugHoldStart.put(p2.getUuid(), now);
        hugPartner.put(p1.getUuid(), p2.getUuid());
        hugPartner.put(p2.getUuid(), p1.getUuid());
    }
    private static void onPlayerHoldingH(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        long now = System.currentTimeMillis();
        lastHUpdate.put(playerId, now);
        Long holdStart = hugHoldStart.get(playerId);
        if (holdStart == null) return;
        long elapsed = now - holdStart;
        if (elapsed < HUG_HOLD_TIME_MS) {
            return;
        }
        UUID partnerId = hugPartner.get(playerId);
        if (partnerId == null) return;
        ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
        if (partner == null) return;
        Long partnerHoldStart = hugHoldStart.get(partnerId);
        if (partnerHoldStart == null) return;
        long partnerElapsed = now - partnerHoldStart;
        if (partnerElapsed < HUG_HOLD_TIME_MS) {
            return;
        }
        Long partnerLastH = lastHUpdate.get(partnerId);
        if (partnerLastH == null || now - partnerLastH > 1000) {
            return;
        }
        double distance = player.getPos().distanceTo(partner.getPos());
        if (distance > HUG_DISTANCE) {
            player.sendMessage(Text.literal("§c❤ Get closer to hug! ❤"), true);
            return;
        }
        startHug(player, partner);
    }
    private static void startHug(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();
        hugHoldStart.remove(id1);
        hugHoldStart.remove(id2);
        hugState.put(id1, HugState.START);
        hugState.put(id2, HugState.START);
        hugStartTime.put(id1, now);
        hugStartTime.put(id2, now);
        PoseNetworking.broadcastAnimState(p1, 32);
        PoseNetworking.broadcastAnimState(p2, 32);
        p1.sendMessage(Text.literal("§d❤ Hugging... ❤"), true);
        p2.sendMessage(Text.literal("§d❤ Hugging... ❤"), true);
    }
    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> hugHoldIt = hugHoldStart.entrySet().iterator();
        while (hugHoldIt.hasNext()) {
            Map.Entry<UUID, Long> entry = hugHoldIt.next();
            if (now - entry.getValue() > HUG_OPPORTUNITY_MS) {
                hugHoldIt.remove();
                hugPartner.remove(entry.getKey());
            }
        }
        Set<UUID> processedPlayers = new HashSet<>();
        Map<UUID, HugState> hugStateCopy = new HashMap<>(hugState);
        List<Runnable> stateChanges = new ArrayList<>();
        for (Map.Entry<UUID, HugState> entry : hugStateCopy.entrySet()) {
            UUID playerId = entry.getKey();
            HugState state = entry.getValue();
            if (processedPlayers.contains(playerId)) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                hugState.remove(playerId);
                hugPartner.remove(playerId);
                hugStartTime.remove(playerId);
                lastHUpdate.remove(playerId);
                continue;
            }
            UUID partnerId = hugPartner.get(playerId);
            if (partnerId == null) {
                hugState.remove(playerId);
                hugStartTime.remove(playerId);
                lastHUpdate.remove(playerId);
                continue;
            }
            ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
            if (partner == null) {
                hugState.remove(playerId);
                hugPartner.remove(playerId);
                hugStartTime.remove(playerId);
                lastHUpdate.remove(playerId);
                continue;
            }
            Long startTime = hugStartTime.get(playerId);
            if (startTime == null) continue;
            long elapsed = now - startTime;
            processedPlayers.add(playerId);
            processedPlayers.add(partnerId);
            if (state == HugState.START) {
                if (elapsed >= 333) {
                    stateChanges.add(() -> transitionToHugging(player, partner));
                }
            } else if (state == HugState.HUGGING) {
                Long lastH1 = lastHUpdate.get(playerId);
                Long lastH2 = lastHUpdate.get(partnerId);
                if (lastH1 == null || lastH2 == null ||
                        now - lastH1 > 1000 || now - lastH2 > 1000) {
                    stateChanges.add(() -> endHug(player, partner));
                    continue;
                }
                double distance = player.getPos().distanceTo(partner.getPos());
                if (distance > 1.0) {
                    Vec3d playerPos = player.getPos();
                    Vec3d partnerPos = partner.getPos();
                    Vec3d direction = partnerPos.subtract(playerPos).normalize();
                    double targetDistance = 0.8;
                    Vec3d midpoint = playerPos.add(partnerPos).multiply(0.5);
                    Vec3d offset = direction.multiply(targetDistance / 2.0);
                    Vec3d targetPlayer = midpoint.subtract(offset);
                    Vec3d targetPartner = midpoint.add(offset);
                    player.teleport(player.getServerWorld(), targetPlayer.x, targetPlayer.y, targetPlayer.z, player.getYaw(), player.getPitch());
                    partner.teleport(partner.getServerWorld(), targetPartner.x, targetPartner.y, targetPartner.z, partner.getYaw(), partner.getPitch());
                } else if (distance > HUG_DISTANCE + 0.5) {
                    stateChanges.add(() -> endHug(player, partner));
                    continue;
                }
                if (elapsed % 1000 < 50) {
                    applyHugEffects(player, partner);
                }
            } else if (state == HugState.ENDING) {
                if (elapsed >= 542) {
                    hugState.remove(playerId);
                    hugPartner.remove(playerId);
                    hugStartTime.remove(playerId);
                    lastHUpdate.remove(playerId);
                    PoseNetworking.broadcastAnimState(player, 0);
                }
            }
        }
        for (Runnable change : stateChanges) {
            change.run();
        }
    }
    private static void transitionToHugging(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();
        Random random = new Random();
        boolean useHugging2 = random.nextBoolean();
        hugState.put(id1, HugState.HUGGING);
        hugState.put(id2, HugState.HUGGING);
        hugStartTime.put(id1, now);
        hugStartTime.put(id2, now);
        if (useHugging2) {
            PoseNetworking.broadcastAnimState(p1, 33);
            PoseNetworking.broadcastAnimState(p2, 34);
        } else {
            PoseNetworking.broadcastAnimState(p1, 34);
            PoseNetworking.broadcastAnimState(p2, 33);
        }
        applyHugEffects(p1, p2);
    }
    private static void applyHugEffects(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        p1.addStatusEffect(new StatusEffectInstance(
                StatusEffects.REGENERATION, 40, 1, false, false));
        p2.addStatusEffect(new StatusEffectInstance(
                StatusEffects.REGENERATION, 40, 1, false, false));
        Vec3d pos = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 1, 0);
        ServerWorld world = p1.getServerWorld();
        world.spawnParticles(ParticleTypes.HEART,
                pos.x, pos.y, pos.z,
                5, 0.3, 0.3, 0.3, 0.1);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.1f, 1.5f);
    }
    private static void endHug(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();
        hugState.put(id1, HugState.ENDING);
        hugState.put(id2, HugState.ENDING);
        hugStartTime.put(id1, now);
        hugStartTime.put(id2, now);
        PoseNetworking.broadcastAnimState(p1, 35);
        PoseNetworking.broadcastAnimState(p2, 35);
        p1.sendMessage(Text.literal("§e Hug ended "), true);
        p2.sendMessage(Text.literal("§e Hug ended "), true);
    }
    public static boolean isInHugFreeze(UUID playerId) {
        HugState state = hugState.get(playerId);
        if (state == null) return false;
        return state == HugState.START || state == HugState.HUGGING;
    }
}