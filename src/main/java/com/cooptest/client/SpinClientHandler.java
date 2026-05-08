package com.cooptest.client;
import com.cooptest.SpinHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public class SpinClientHandler {
    private static boolean localSpinning = false;
    private static final Map<UUID, Boolean> spinningPlayers = new HashMap<>();
    private static boolean launchFlashActive = false;
    private static long    launchFlashStart  = 0L;
    private static final long LAUNCH_FLASH_MS = 400L;
    private static boolean localHasRider = false;
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SpinHandler.SpinSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    UUID id = payload.playerId();
                    boolean spinning = payload.spinning();
                    spinningPlayers.put(id, spinning);
                    MinecraftClient client = context.client();
                    if (client.player == null) return;
                    boolean isLocal = client.player.getUuid().equals(id);
                    if (isLocal) {
                        localSpinning = spinning;
                        if (spinning) {
                            FirstPersonAnimationTest.showBothHands();
                        } else {
                            localHasRider = false;
                            FirstPersonAnimationTest.stop();
                        }
                    }
                    if (client.world != null) {
                        for (var player : client.world.getPlayers()) {
                            if (player.getUuid().equals(id)) {
                                if (spinning) {
                                    CoopAnimationHandler.playSpinAnimation(player);
                                }
                                break;
                            }
                        }
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(SpinHandler.HelicopterLaunchPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    MinecraftClient client = context.client();
                    if (client.player == null) return;
                    UUID localId = client.player.getUuid();
                    boolean isSpinner = localId.equals(payload.spinnerId());
                    boolean isRider   = localId.equals(payload.riderId());
                    if (isSpinner || isRider) {
                        launchFlashActive = true;
                        launchFlashStart  = System.currentTimeMillis();
                        if (isSpinner) localHasRider = false;
                    }
                }));
        HudRenderCallback.EVENT.register(SpinClientHandler::renderHUD);
    }
    private static void renderHUD(DrawContext context, RenderTickCounter tc) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        if (launchFlashActive) {
            long e = System.currentTimeMillis() - launchFlashStart;
            if (e > LAUNCH_FLASH_MS) {
                launchFlashActive = false;
            } else {
                int a = (int)((1f - (float)e / LAUNCH_FLASH_MS) * 200) << 24;
                int c = a | 0x00FFFF;
                context.fill(0, 0, sw, 6, c);
                context.fill(0, sh - 6, sw, sh, c);
            }
        }
        if (!localSpinning) return;
        float pulse = (float)(Math.sin(System.currentTimeMillis() / 180.0) * 0.2 + 0.8);
        int a = (int)(pulse * 200) << 24;
        String label = localHasRider ? "↻ SPINNING  [SHIFT] LAUNCH!" : "↻ SPINNING";
        int lx = (sw - client.textRenderer.getWidth(label)) / 2;
        context.drawText(client.textRenderer, Text.literal((localHasRider ? "§e§l" : "§b") + label),
                lx, sh / 2 - 30, a | 0xFFFFFF, true);
    }
    public static void onRiderAttached()                         { localHasRider = true; }
    public static boolean isLocalPlayerSpinning()               { return localSpinning; }
    public static boolean isPlayerSpinning(UUID id)             { return spinningPlayers.getOrDefault(id, false); }
    public static void forceStopLocalSpin()                     { localSpinning = false; }
    public static void cleanup(UUID id) {
        spinningPlayers.remove(id);
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null && c.player.getUuid().equals(id)) {
            localSpinning = false;
            localHasRider = false;
        }
    }
}