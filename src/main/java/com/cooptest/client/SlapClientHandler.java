package com.cooptest.client;
import com.cooptest.SlapHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
public class SlapClientHandler {
    private static long slapCooldownEnd = 0;
    public static boolean isOnSlapCooldown() { return System.currentTimeMillis() < slapCooldownEnd; }
    public static void triggerSlapCooldown() { slapCooldownEnd = System.currentTimeMillis() + 500L; }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.CameraFlickPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    MinecraftClient client = context.client();
                    if (client.player == null) return;
                    if (!client.player.getUuid().equals(payload.playerId())) return;
                    float newPitch = Math.min(90f, client.player.getPitch() + payload.pitchDelta());
                    client.player.setPitch(newPitch);
                    triggerSlapCooldown();
                }));
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.CameraYawFlickPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    var client = context.client();
                    if (client.player == null) return;
                    if (!client.player.getUuid().equals(payload.playerId())) return;
                    client.player.setYaw(client.player.getYaw() + payload.yawDelta());
                }));
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.ScreenClosePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    MinecraftClient client = context.client();
                    if (client.player == null) return;
                    if (!client.player.getUuid().equals(payload.playerId())) return;
                    if (client.currentScreen != null) {
                        client.setScreen(null);
                    }
                }));
    }
}