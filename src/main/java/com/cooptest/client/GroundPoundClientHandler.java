package com.cooptest.client;
import com.cooptest.GroundPoundHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public class GroundPoundClientHandler {
    private static boolean localDiving = false;
    private static long    diveStartMs = 0L;
    private static final Map<UUID, Boolean> divingPlayers = new HashMap<>();
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GroundPoundHandler.GroundPoundSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    UUID id = payload.playerId();
                    boolean diving = payload.diving();
                    divingPlayers.put(id, diving);
                    MinecraftClient client = context.client();
                    if (client.player == null) return;
                    boolean isLocal = client.player.getUuid().equals(id);
                    if (isLocal) {
                        localDiving = diving;
                        if (diving) {
                            diveStartMs = System.currentTimeMillis();
                            FirstPersonAnimationTest.stop();
                        } else {
                            diveStartMs = 0L;
                        }
                    }
                }));
        HudRenderCallback.EVENT.register(GroundPoundClientHandler::renderHUD);
    }
    private static void renderHUD(DrawContext context, RenderTickCounter tc) {
        if (!localDiving) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        long elapsed = System.currentTimeMillis() - diveStartMs;
        int a = Math.min(220, (int)(elapsed / 8)) << 24;
        String label = "⬇ GROUND POUND";
        int lx = (sw - client.textRenderer.getWidth(label)) / 2;
        context.drawText(client.textRenderer, Text.literal("§c§l" + label), lx, sh / 2 - 30, a | 0xFFFFFF, true);
    }
    public static boolean isLocalPlayerDiving()    { return localDiving; }
    public static boolean isPlayerDiving(UUID id)  { return divingPlayers.getOrDefault(id, false); }
    public static void cleanup(UUID id) {
        divingPlayers.remove(id);
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null && c.player.getUuid().equals(id)) {
            localDiving = false;
            diveStartMs = 0L;
        }
    }
}