package com.cooptest.client;
import com.cooptest.HeavenDapPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
public class HeavenDapClientHandler {
    private static boolean wasPlayingLastTick = false;
    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(CoopImpactRenderType.createReloadListener());
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            HeavenWhiteOverlay.render(context, tickCounter.getLastFrameDuration());
            if (CoopImpactHandler.playing) {
                int w = context.getScaledWindowWidth();
                int h = context.getScaledWindowHeight();
                if (CoopImpactHandler.whiteFrame) {
                    context.fill(0, 0, w, h, 0xE8FFFFFF);
                } else {
                    context.fill(0, 0, w, h, 0xE8000000);
                }
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean wasPlaying = wasPlayingLastTick;
            wasPlayingLastTick = CoopImpactHandler.playing;
            CoopImpactHandler.tick();
            HeavenWhiteOverlay.tick();
            if (wasPlaying && !CoopImpactHandler.playing) {
                HeavenWhiteOverlay.start();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(
                HeavenDapPayloads.HeavenDapStartPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (!HeavenWhiteOverlay.isActive()) {
                        HeavenWhiteOverlay.start();
                    }
                })
        );
        ClientPlayNetworking.registerGlobalReceiver(
                HeavenDapPayloads.HeavenDapEndPayload.ID,
                (payload, ctx) -> ctx.client().execute(HeavenWhiteOverlay::stop)
        );
        ClientPlayNetworking.registerGlobalReceiver(
                HeavenDapPayloads.HeavenImpactPayload.ID,
                (payload, ctx) -> ctx.client().execute(() ->
                        CoopImpactHandler.start(20, 33L)
                )
        );
        ClientPlayNetworking.registerGlobalReceiver(
                HeavenDapPayloads.RestoreVolumePayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (ctx.client().options != null) {
                        ctx.client().options.getSoundVolumeOption(
                                net.minecraft.sound.SoundCategory.MASTER).setValue(1.0);
                    }
                })
        );
    }
    public static record QTEButtonPressPayload(String button) implements CustomPayload {
        public static final Identifier QTE_BUTTON_PRESS_ID = Identifier.of("cooptest", "qte_button_press");
        public static final Id<QTEButtonPressPayload> ID = new Id<>(QTE_BUTTON_PRESS_ID);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
        public static final PacketCodec<RegistryByteBuf, QTEButtonPressPayload> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, QTEButtonPressPayload::button, QTEButtonPressPayload::new);
    }
}