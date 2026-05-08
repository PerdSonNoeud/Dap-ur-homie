package com.cooptest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.UUID;
public class PoseNetworking {
    public static final HashMap<UUID, PoseState> poseStates = new HashMap<>();
    public static final HashMap<UUID, Float> chargeProgress = new HashMap<>();
    public record PoseSyncPayload(UUID playerId, int poseOrdinal) implements CustomPayload {
        public static final Id<PoseSyncPayload> ID = new Id<>(Identifier.of("cooptest", "pose_sync"));
        public static final PacketCodec<PacketByteBuf, PoseSyncPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeInt(payload.poseOrdinal);
                },
                buf -> new PoseSyncPayload(buf.readUuid(), buf.readInt())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record ChargeSyncPayload(UUID playerId, float progress) implements CustomPayload {
        public static final Id<ChargeSyncPayload> ID = new Id<>(Identifier.of("cooptest", "charge_sync"));
        public static final PacketCodec<PacketByteBuf, ChargeSyncPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeFloat(payload.progress);
                },
                buf -> new ChargeSyncPayload(buf.readUuid(), buf.readFloat())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record ThrowAnimPayload(UUID playerId) implements CustomPayload {
        public static final Id<ThrowAnimPayload> ID = new Id<>(Identifier.of("cooptest", "throw_anim"));
        public static final PacketCodec<PacketByteBuf, ThrowAnimPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeUuid(payload.playerId),
                buf -> new ThrowAnimPayload(buf.readUuid())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record AnimStateSyncPayload(UUID playerId, int animStateOrdinal) implements CustomPayload {
        public static final Id<AnimStateSyncPayload> ID = new Id<>(Identifier.of("cooptest", "anim_state_sync"));
        public static final PacketCodec<PacketByteBuf, AnimStateSyncPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeInt(payload.animStateOrdinal);
                },
                buf -> new AnimStateSyncPayload(buf.readUuid(), buf.readInt())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(PoseSyncPayload.ID, PoseSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PoseSyncPayload.ID, PoseSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChargeSyncPayload.ID, ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargeSyncPayload.ID, ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ThrowAnimPayload.ID, ThrowAnimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ThrowAnimPayload.ID, ThrowAnimPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AnimStateSyncPayload.ID, AnimStateSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnimStateSyncPayload.ID, AnimStateSyncPayload.CODEC);
    }
    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(PoseSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            PoseState state = PoseState.values()[payload.poseOrdinal()];
            context.server().execute(() -> {
                ServerPlayerEntity requester = context.server().getPlayerManager().getPlayer(id);
                if (state == PoseState.GRAB_READY && HighFiveHandler.isInBlockingState(id)) {
                    if (requester != null) {
                        ServerPlayNetworking.send(requester, new PoseSyncPayload(id, PoseState.NONE.ordinal()));
                    }
                    return;
                }
                if (state == PoseState.PUSH_IDLE) {
                    if (requester != null && !requester.getMainHandStack().isEmpty()) {
                        requester.sendMessage(net.minecraft.text.Text.literal("§cHold nothing in your main hand to push!"), true);
                        ServerPlayNetworking.send(requester, new PoseSyncPayload(id, PoseState.NONE.ordinal()));
                        return;
                    }
                }
                poseStates.put(id, state);
                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(player, new PoseSyncPayload(id, state.ordinal()));
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ChargeSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            float progress = payload.progress();
            context.server().execute(() -> {
                chargeProgress.put(id, progress);
                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    if (!player.getUuid().equals(id)) {
                        ServerPlayNetworking.send(player, new ChargeSyncPayload(id, progress));
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ThrowAnimPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            context.server().execute(() -> {
                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    if (!player.getUuid().equals(id)) {
                        ServerPlayNetworking.send(player, new ThrowAnimPayload(id));
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(AnimStateSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            int animState = payload.animStateOrdinal();
            context.server().execute(() -> {
                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    if (!player.getUuid().equals(id)) {
                        ServerPlayNetworking.send(player, new AnimStateSyncPayload(id, animState));
                    }
                }
            });
        });
    }
    public static void registerClientReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(PoseSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            PoseState state = PoseState.values()[payload.poseOrdinal()];
            poseStates.put(id, state);
            context.client().execute(() -> {
                if (context.client().world != null) {
                    for (PlayerEntity player : context.client().world.getPlayers()) {
                        if (player.getUuid().equals(id)) {
                            com.cooptest.client.CoopAnimationHandler.updatePlayerAnimation(player, state);
                            break;
                        }
                    }
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(ChargeSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            float progress = payload.progress();
            chargeProgress.put(id, progress);
        });
        ClientPlayNetworking.registerGlobalReceiver(ThrowAnimPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            ArmPoseTracker.throwAnimationStart.put(id, System.currentTimeMillis());
        });
        ClientPlayNetworking.registerGlobalReceiver(AnimStateSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            int animState = payload.animStateOrdinal();
            context.client().execute(() -> {
                if (context.client().world != null) {
                    var localPlayer = context.client().player;
                    if (animState == 0) {
                        com.cooptest.client.ChargedDapClientHandler.cleanup(id);
                        com.cooptest.client.CoopAnimationHandler.cleanup(id);
                        com.cooptest.client.HighFiveClientHandler.cleanup(id);
                        com.cooptest.client.PushClientHandler.cleanup(id);
                        com.cooptest.client.MahitoClientHandler.cleanup(id);
                        com.cooptest.client.FallDapClientHandler.cleanup(id);
                        ArmPoseTracker.cleanup(id);
                    }
                    PlayerEntity targetPlayer = null;
                    for (PlayerEntity player : context.client().world.getPlayers()) {
                        if (player.getUuid().equals(id)) {
                            targetPlayer = player;
                            break;
                        }
                    }
                    if (targetPlayer != null) {
                        if (localPlayer != null && (animState == 10 || animState == 18)) {
                            boolean isLocalPlayer = id.equals(localPlayer.getUuid());
                            String animName = (animState == 10) ? "DAP_HIT" : "PERFECT_DAP_HIT";
                            String playerName = targetPlayer.getName().getString();
                        }
                        com.cooptest.client.CoopAnimationHandler.setAnimStateFromNetwork(targetPlayer, animState);
                    }
                }
            });
        });
    }
    public static void sendPoseToServer(UUID playerId, PoseState state) {
        ClientPlayNetworking.send(new PoseSyncPayload(playerId, state.ordinal()));
    }
    public static void sendChargeProgress(UUID playerId, float progress) {
        ClientPlayNetworking.send(new ChargeSyncPayload(playerId, progress));
    }
    public static void sendThrowAnimation(UUID playerId) {
        ClientPlayNetworking.send(new ThrowAnimPayload(playerId));
    }
    public static void sendAnimState(UUID playerId, int animStateOrdinal) {
        ClientPlayNetworking.send(new AnimStateSyncPayload(playerId, animStateOrdinal));
    }
    public static void broadcastPoseChange(MinecraftServer server, UUID playerId, PoseState state) {
        poseStates.put(playerId, state);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new PoseSyncPayload(playerId, state.ordinal()));
        }
    }
    public static void broadcastAnimState(ServerPlayerEntity sourcePlayer, int animStateOrdinal) {
        var server = sourcePlayer.getServer();
        if (server == null) return;
        UUID playerId = sourcePlayer.getUuid();
        AnimStateSyncPayload payload = new AnimStateSyncPayload(playerId, animStateOrdinal);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}