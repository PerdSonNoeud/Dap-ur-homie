package com.cooptest.client;
import com.cooptest.KickHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public class KickClientHandler {
    private static boolean wasHeld    = false;
    private static boolean isCharging = false;
    private static long    chargeStartMs = 0L;
    private static long cooldownEndMs = 0L;
    private static boolean hitFlashActive = false;
    private static boolean hitFlashDrop   = false;
    private static long    hitFlashStart  = 0L;
    private static final long HIT_FLASH_MS = 200L;
    private static final Map<UUID, Float>   otherCharges = new HashMap<>();
    private static final Map<UUID, Boolean> otherActive  = new HashMap<>();
    private static final int BAR_WIDTH    = 36;
    private static final int BAR_HEIGHT   = 2;
    private static final int BAR_Y_OFFSET = 14;
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(KickHandler.KickChargeSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    UUID pid = payload.playerId();
                    MinecraftClient client = MinecraftClient.getInstance();
                    boolean isLocal = client.player != null && client.player.getUuid().equals(pid);
                    if (payload.isCharging()) {
                        otherActive.put(pid, true);
                        otherCharges.put(pid, payload.chargePercent());
                    } else {
                        otherActive.put(pid, false);
                        otherCharges.put(pid, 0f);
                        if (isLocal && isCharging) stopLocalCharge();
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(KickHandler.KickCooldownPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    stopLocalCharge();
                    wasHeld = false;
                    if (payload.cooldownMs() > 0) {
                        cooldownEndMs = System.currentTimeMillis() + payload.cooldownMs();
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(KickHandler.KickResultPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (payload.hit()) {
                        hitFlashActive = true;
                        hitFlashDrop   = payload.isDropKick();
                        hitFlashStart  = System.currentTimeMillis();
                    }
                }));
        HudRenderCallback.EVENT.register(KickClientHandler::renderHUD);
    }
    public static void handleKickTick(MinecraftClient client,
                                      boolean keyHeld,
                                      boolean sprinting) {
        boolean justPressed  = keyHeld  && !wasHeld;
        boolean justReleased = !keyHeld && wasHeld;
        if (justPressed && !isOnCooldown()) {
            if (sprinting) {
                isCharging    = true;
                chargeStartMs = System.currentTimeMillis();
                ClientPlayNetworking.send(new KickHandler.KickStartPayload(true));
            } else {
                ClientPlayNetworking.send(new KickHandler.KickStartPayload(false));
            }
        }
        if (isCharging && !sprinting) {
            stopLocalCharge();
        }
        if (justReleased && isCharging) {
            ClientPlayNetworking.send(new KickHandler.KickReleasePayload());
            stopLocalCharge();
        }
        if (justPressed && isOnCooldown() && client.player != null) {
            long rem = cooldownEndMs - System.currentTimeMillis();
            client.player.sendMessage(
                    Text.literal("§cKick cooldown! " + String.format("%.1f", rem / 1000.0) + "s"),
                    true
            );
        }
        wasHeld = keyHeld;
    }
    public static void cancelIfCharging() {
        if (isCharging) {
            stopLocalCharge();
            wasHeld = false;
        }
    }
    private static void renderHUD(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        int sw      = context.getScaledWindowWidth();
        int sh      = context.getScaledWindowHeight();
        int centreX = sw / 2;
        int barY    = sh / 2 + BAR_Y_OFFSET;
        int barX    = centreX - BAR_WIDTH / 2;
        if (hitFlashActive) {
            long e = System.currentTimeMillis() - hitFlashStart;
            if (e > HIT_FLASH_MS) {
                hitFlashActive = false;
            } else {
                int a = (int)((1f - (float)e / HIT_FLASH_MS) * 90) << 24;
                int c = hitFlashDrop ? (a | 0xFF8800) : (a | 0xFFFFCC);
                context.fill(0, 0, sw, 4, c);
                context.fill(0, sh - 4, sw, sh, c);
            }
        }
        if (isCharging && com.cooptest.CoopMovesConfig.get().enableKick) {
            long elapsed        = System.currentTimeMillis() - chargeStartMs;
            float pct           = Math.min(1f, (float) elapsed / KickHandler.CHARGE_TIME_MS);
            boolean full        = pct >= 0.99f;
            context.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0x55000000);
            int fillColor;
            if (full) {
                long t = System.currentTimeMillis();
                int a = (int)((Math.sin(t / 100.0) * 0.25 + 0.75) * 200);
                fillColor = (a << 24) | 0xFFFFFF;
            } else {
                fillColor = 0xCCFF8800;
            }
            context.fill(barX, barY, barX + (int)(BAR_WIDTH * pct), barY + BAR_HEIGHT, fillColor);
            if (full) {
                String lbl = "DROP KICK";
                int lx = centreX - client.textRenderer.getWidth(lbl) / 2;
                context.drawText(client.textRenderer, Text.literal("§f" + lbl),
                        lx, barY - 9, 0xCCFFFFFF, false);
            }
        }
        else if (isOnCooldown()) {
            float pct = (float)(cooldownEndMs - System.currentTimeMillis()) / KickHandler.KICK_COOLDOWN_MS;
            context.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0x33000000);
            context.fill(barX, barY, barX + (int)(BAR_WIDTH * pct), barY + BAR_HEIGHT, 0x88FF4400);
        }
        for (Map.Entry<UUID, Boolean> entry : otherActive.entrySet()) {
            if (!entry.getValue()) continue;
            UUID pid = entry.getKey();
            if (client.player.getUuid().equals(pid) || client.world == null) continue;
            boolean inRange = false;
            for (var p : client.world.getPlayers()) {
                if (p.getUuid().equals(pid)) { inRange = client.player.distanceTo(p) <= 20.0; break; }
            }
            if (!inRange) continue;
        }
    }
    private static void stopLocalCharge() {
        isCharging    = false;
        chargeStartMs = 0L;
    }
    public static boolean isOnCooldown()              { return System.currentTimeMillis() < cooldownEndMs; }
    public static boolean isLocalPlayerKickCharging() { return isCharging; }
    public static void cleanup() {
        stopLocalCharge();
        wasHeld        = false;
        cooldownEndMs  = 0L;
        hitFlashActive = false;
        otherCharges.clear();
        otherActive.clear();
    }
}