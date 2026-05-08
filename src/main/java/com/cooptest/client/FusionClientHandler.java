package com.cooptest.client;
import com.cooptest.DapFusionHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import java.util.UUID;
public class FusionClientHandler {
    private static int currentPhase = -1;
    private static String phaseLabel = "";
    private static boolean blackScreenActive = false;
    private static long blackScreenStartTime = 0;
    private static boolean gWindowActive = false;
    private static long gWindowStart = 0, gWindowEnd = 0;
    private static boolean gPressed = false;
    private static boolean qteActive = false;
    private static long greenZoneStart = 0, greenZoneEnd = 0;
    private static String expectedButton = null;
    private static int currentStage = 0, maxStages = 3;
    private static long windowStart = 0, windowEnd = 0;
    private static boolean pressedThisWindow = false;
    private static int qteType = 0;
    private static long receiveTime = 0;
    private static long  bouncePeriodMs      = 1800;
    private static float greenCenterFrac     = 0.5f;
    private static boolean waitingForGreenUpdate = false;
    private static long flashEndTime = 0, failFlashEndTime = 0;
    public static boolean isFused = false;
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DapFusionHandler.FusionPhasePayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> onPhase(payload, ctx.client())));
        ClientPlayNetworking.registerGlobalReceiver(DapFusionHandler.FusionQTEPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> onQTE(payload, ctx.client())));
        ClientPlayNetworking.registerGlobalReceiver(DapFusionHandler.FusionBlackScreenPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    blackScreenActive = payload.active();
                    if (payload.active()) blackScreenStartTime = System.currentTimeMillis();
                    if (!payload.active() && currentPhase == 4) resetState();
                }));
        HudRenderCallback.EVENT.register(FusionClientHandler::renderHUD);
    }
    private static void onPhase(DapFusionHandler.FusionPhasePayload p, MinecraftClient client) {
        if (client.player == null) return;
        UUID myId = client.player.getUuid();
        if (!myId.equals(p.p1()) && !myId.equals(p.p2())) return;
        currentPhase = p.phase();
        switch (p.phase()) {
            case 0 -> {
                long now = System.currentTimeMillis();
                gWindowActive = true;
                gWindowStart = now + DapFusionHandler.FUSION_G_WINDOW_START;
                gWindowEnd   = now + DapFusionHandler.FUSION_G_WINDOW_END;
                gPressed = false;
                phaseLabel = "§6FUSION AVAILABLE";
                maxStages = 3;
                qteActive = false;
            }
            case 1 -> { phaseLabel = "§eWALK FORWARD";  gWindowActive = false; maxStages = 3; }
            case 2 -> { phaseLabel = "§c§lFUSION QTE";  maxStages = 10; }
            case 3 -> {
                gWindowActive = false; qteActive = false;
                phaseLabel = "";
                flashEndTime = System.currentTimeMillis() + 800;
            }
            case 4 -> {
                gWindowActive = false; qteActive = false;
                phaseLabel = "";
            }
            case 99 -> {
                gWindowActive = false; qteActive = false;
                phaseLabel = "§c✗ FAILED";
                failFlashEndTime = System.currentTimeMillis() + 500;
                scheduleReset(1200);
            }
        }
    }
    private static void onQTE(DapFusionHandler.FusionQTEPayload p, MinecraftClient client) {
        if (client.player == null) return;
        if (!p.playerId().equals(client.player.getUuid())) return;
        if (!p.open()) {
            qteActive = false; expectedButton = null;
            waitingForGreenUpdate = false;
            if (currentPhase == 0 && !gWindowActive) currentPhase = -1;
            return;
        }
        long now = System.currentTimeMillis();
        qteActive = true;
        expectedButton = p.button();
        currentStage = p.stage();
        qteType = p.type();
        receiveTime = now;
        pressedThisWindow = false;
        if (currentPhase < 0) currentPhase = 0;
        if (p.type() == 1) {
            greenZoneStart = now + p.windowStartMs();
            greenZoneEnd   = now + p.windowEndMs();
            windowStart = now;
            windowEnd   = now + DapFusionHandler.TIMING_BAR_TOTAL_MS_CLIENT;
        } else if (p.type() == 2) {
            long wEnd = p.windowEndMs();
            if (wEnd < 0) {
                greenZoneStart      = p.windowStartMs();
                bouncePeriodMs      = Math.abs(wEnd);
                greenCenterFrac     = Math.max(0.05f, Math.min(0.95f, p.stage() / 100f));
                waitingForGreenUpdate = false;
                expectedButton      = p.button();
            } else {
                greenZoneStart      = p.windowStartMs();
                greenZoneEnd        = 0;
                bouncePeriodMs      = wEnd;
                greenCenterFrac     = Math.max(0.05f, Math.min(0.95f, p.stage() / 100f));
                windowStart         = now;
                windowEnd           = now + bouncePeriodMs * 20L;
                waitingForGreenUpdate = false;
                pressedThisWindow   = false;
            }
        } else {
            greenZoneStart = 0; greenZoneEnd = 0;
            windowStart = now;
            windowEnd   = now + p.windowEndMs();
        }
    }
    public static boolean handleGKeyPress() {
        if (!gWindowActive) return false;
        long now = System.currentTimeMillis();
        if (now < gWindowStart || now > gWindowEnd) return false;
        ClientPlayNetworking.send(new DapFusionHandler.FusionGPressPayload());
        gPressed = true;
        gWindowActive = false;
        return true;
    }
    public static boolean handleQTEGPress() {
        if (!qteActive) return false;
        long now = System.currentTimeMillis();
        if (qteType == 1) {
            boolean inGreen = now >= greenZoneStart && now <= greenZoneEnd;
            qteActive = false;
            if (inGreen) {
                flashEndTime = now + 200;
                pressedThisWindow = true;
                ClientPlayNetworking.send(new com.cooptest.QTEButtonPressPayload("G"));
            } else {
                failFlashEndTime = now + 200;
                ClientPlayNetworking.send(new com.cooptest.QTEButtonPressPayload("FAIL"));
            }
        } else if (qteType == 2) {
            if (waitingForGreenUpdate) return true;
            float dotPos  = getBouncePos(now);
            float halfFrac = bouncePeriodMs > 0 ? (float) greenZoneStart / bouncePeriodMs : 0.1f;
            boolean inGreen = Math.abs(dotPos - greenCenterFrac) <= halfFrac;
            if (inGreen) {
                flashEndTime = now + 200;
                pressedThisWindow = true;
                waitingForGreenUpdate = true;
                ClientPlayNetworking.send(new com.cooptest.QTEButtonPressPayload("G"));
            } else {
                failFlashEndTime = now + 200;
                ClientPlayNetworking.send(new com.cooptest.QTEButtonPressPayload("FAIL"));
            }
        } else {
            registerPress();
            ClientPlayNetworking.send(new com.cooptest.QTEButtonPressPayload("G"));
        }
        return true;
    }
    private static float getBouncePos(long now) {
        if (bouncePeriodMs <= 0) return 0.5f;
        long elapsed = now - windowStart;
        double t = (double)(elapsed % bouncePeriodMs) / bouncePeriodMs;
        return (float)(t < 0.5 ? t * 2.0 : (1.0 - t) * 2.0);
    }
    public static boolean handleQTEHPress() {
        if (!qteActive) return false;
        registerPress();
        ClientPlayNetworking.send(new com.cooptest.QTEButtonPressPayload("H"));
        return true;
    }
    private static void registerPress() {
        long now = System.currentTimeMillis();
        boolean inWindow = qteType == 1
                ? (now >= greenZoneStart && now <= greenZoneEnd)
                : (now >= windowStart && now <= windowEnd);
        if (inWindow) {
            flashEndTime = now + 200;
            pressedThisWindow = true;
        } else {
            failFlashEndTime = now + 200;
        }
    }
    public static boolean isActive()      { return currentPhase >= 0; }
    public static boolean isQTEOpen()     { return qteActive; }
    public static boolean isGWindowOpen() { return gWindowActive; }
    private static void renderHUD(DrawContext ctx, RenderTickCounter ticker) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (blackScreenActive) {
            long elapsed = System.currentTimeMillis() - blackScreenStartTime;
            float alpha = clamp((float) elapsed / 500f);
            int a = (int)(alpha * 255);
            int sw = client.getWindow().getScaledWidth();
            int sh = client.getWindow().getScaledHeight();
            var mat = ctx.getMatrices();
            mat.push();
            mat.translate(0, 0, 10000);
            ctx.fill(0, 0, sw, sh, (a << 24) | 0x000000);
            mat.pop();
            return;
        }
        if (currentPhase < 0) return;
        long now = System.currentTimeMillis();
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        var mat = ctx.getMatrices();
        mat.push();
        mat.translate(0, 0, 900);
        if (now < flashEndTime) {
            float p = 1f - (float)(now - (flashEndTime - 800)) / 800f;
            int a = (int)(clamp(p) * 120);
            ctx.fill(0, 0, sw, sh, (a << 24) | 0xFFAA00);
        } else if (now < failFlashEndTime) {
            float p = 1f - (float)(now - (failFlashEndTime - 500)) / 500f;
            int a = (int)(clamp(p) * 100);
            ctx.fill(0, 0, sw, sh, (a << 24) | 0xFF2222);
        }
        if (gWindowActive) {
            long gsNow = System.currentTimeMillis();
            if (gsNow >= gWindowStart && gsNow <= gWindowEnd) {
                float rem = 1f - (float)(gsNow - gWindowStart) / (gWindowEnd - gWindowStart);
                int bw = 50, bh = 3, bx = (sw - bw) / 2, by = sh - 55;
                ctx.fill(bx-1, by-1, bx+bw+1, by+bh+1, 0xAA000000);
                ctx.fill(bx, by, bx+bw, by+bh, 0xFF333333);
                int fw = (int)(bw * clamp(rem));
                if (fw > 0) ctx.fill(bx, by, bx+fw, by+bh, gPressed ? 0xFF44BB44 : 0xFFFFAA00);
                String lbl = gPressed ? "§a✓" : "§6[G]";
                int lw = client.textRenderer.getWidth(lbl);
                ctx.drawText(client.textRenderer, lbl, (sw-lw)/2, by - 9, 0xFFFFFFFF, true);
            }
        }
        if (qteActive) {
            int bw = 60, bh = 4, bx = (sw-bw)/2, by = sh - 85;
            ctx.fill(bx-1, by-1, bx+bw+1, by+bh+1, 0xAA000000);
            ctx.fill(bx, by, bx+bw, by+bh, 0xFF222222);
            if (qteType == 1) {
                long elapsed = now - windowStart;
                float dotPos = clamp((float) elapsed / DapFusionHandler.TIMING_BAR_TOTAL_MS_CLIENT);
                float gzStartFrac = clamp((float)(greenZoneStart - windowStart) / DapFusionHandler.TIMING_BAR_TOTAL_MS_CLIENT);
                float gzEndFrac   = clamp((float)(greenZoneEnd   - windowStart) / DapFusionHandler.TIMING_BAR_TOTAL_MS_CLIENT);
                int gzX1 = bx + (int)(gzStartFrac * bw);
                int gzX2 = bx + (int)(gzEndFrac   * bw);
                ctx.fill(gzX1, by, gzX2, by + bh, 0xFF00BB00);
                int dotX = Math.max(bx, Math.min(bx + bw - 2, bx + (int)(dotPos * bw) - 1));
                ctx.fill(dotX, by - 1, dotX + 2, by + bh + 1, 0xFFFFFFFF);
                if (now < flashEndTime)          ctx.fill(bx, by, bx+bw, by+bh, 0xBB00FF00);
                else if (now < failFlashEndTime) ctx.fill(bx, by, bx+bw, by+bh, 0xBBFF2222);
            } else if (qteType == 2) {
                float dotPos  = getBouncePos(now);
                float halfFrac = bouncePeriodMs > 0 ? (float) greenZoneStart / bouncePeriodMs : 0.1f;
                int gzX1 = bx + (int)((greenCenterFrac - halfFrac) * bw);
                int gzX2 = bx + (int)((greenCenterFrac + halfFrac) * bw);
                ctx.fill(bx, by, bx + bw, by + bh, 0xFF333333);
                ctx.fill(Math.max(bx, gzX1), by, Math.min(bx + bw, gzX2), by + bh, 0xFF00BB00);
                int dotX = Math.max(bx, Math.min(bx + bw - 3, bx + (int)(dotPos * bw) - 1));
                ctx.fill(dotX, by - 1, dotX + 3, by + bh + 1, 0xFFFFFFFF);
                if (now < flashEndTime)          ctx.fill(bx, by, bx+bw, by+bh, 0xBB00FF00);
                else if (now < failFlashEndTime) ctx.fill(bx, by, bx+bw, by+bh, 0xBBFF2222);
            } else {
                int fw; int barColor;
                if (now < flashEndTime)          { fw = bw; barColor = 0xFFFFFFFF; }
                else if (now < failFlashEndTime) { fw = bw; barColor = 0xFFFF2222; }
                else if (now < windowStart)      { fw = bw; barColor = 0xFFFF4444; }
                else if (now <= windowEnd) {
                    float rem = clamp(1f - (float)(now - windowStart) / (windowEnd - windowStart));
                    fw = (int)(bw * rem);
                    barColor = pressedThisWindow ? 0xFF00FF00 : 0xFF00DD00;
                } else { fw = 0; barColor = 0xFF444444; }
                if (fw > 0) ctx.fill(bx, by, bx+fw, by+bh, barColor);
            }
            if (expectedButton != null && !pressedThisWindow) {
                boolean inZone;
                if (qteType == 1) {
                    inZone = now >= greenZoneStart && now <= greenZoneEnd;
                } else if (qteType == 2) {
                    float dp = getBouncePos(now);
                    float hf = bouncePeriodMs > 0 ? (float) greenZoneStart / bouncePeriodMs : 0.1f;
                    inZone = Math.abs(dp - greenCenterFrac) <= hf;
                } else {
                    inZone = now >= windowStart && now <= windowEnd;
                }
                float alpha = inZone ? (float)(Math.sin(now / 60.0) * 0.4 + 0.6)
                        : (float)(Math.sin(now / 120.0) * 0.2 + 0.8);
                int a = (int)(alpha * 255);
                String keyText = QTEClientHandler.resolveKeyName(expectedButton);
                int kw = client.textRenderer.getWidth(keyText);
                ctx.drawText(client.textRenderer, keyText, (sw-kw)/2, by - 9, (a<<24)|0xFFFFFF, true);
            }
            if (maxStages > 1) {
                int ds = 3, dg = 2;
                int totalW = maxStages * ds + (maxStages - 1) * dg;
                int dx = (sw - totalW) / 2, dy = by + bh + 3;
                for (int i = 1; i <= maxStages; i++) {
                    int color = i < currentStage ? 0xFF00FF00
                            : i == currentStage ? 0xFFFFFF00 : 0xFF333333;
                    ctx.fill(dx, dy, dx+ds, dy+ds, color);
                    dx += ds + dg;
                }
            }
        }
        mat.pop();
    }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static void scheduleReset(long delayMs) {
        new Thread(() -> {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            resetState();
        }).start();
    }
    private static void resetState() {
        currentPhase = -1; phaseLabel = "";
        gWindowActive = false; qteActive = false;
        expectedButton = null; pressedThisWindow = false;
        blackScreenActive = false;
    }
}