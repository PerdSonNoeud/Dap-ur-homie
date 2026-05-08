package com.cooptest.client;
import com.cooptest.MeteorStrikeHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.lwjgl.glfw.GLFW;
public class MeteorStrikeClientHandler {
    private static boolean hasAbility  = false;
    private static long abilityLeftMs  = 0;
    private static long countdownMs    = -1;
    private static boolean wasGPressed = false;
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MeteorStrikeHandler.MeteorGrantPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    hasAbility    = true;
                    abilityLeftMs = payload.expiryMs() - System.currentTimeMillis();
                    countdownMs   = -1;
                }));
        ClientPlayNetworking.registerGlobalReceiver(MeteorStrikeHandler.MeteorStatusPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    abilityLeftMs = payload.remainingAbilityMs();
                    countdownMs   = payload.countdownMs();
                    hasAbility    = abilityLeftMs > 0;
                }));
        ClientPlayNetworking.registerGlobalReceiver(MeteorStrikeHandler.MeteorExpiredPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    hasAbility = false; abilityLeftMs = 0; countdownMs = -1;
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!hasAbility || client.player == null) return;
            long win = client.getWindow().getHandle();
            boolean g = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
            if (g && !wasGPressed && countdownMs < 0) {
                ClientPlayNetworking.send(new MeteorStrikeHandler.MeteorFirePayload());
            }
            wasGPressed = g;
        });
        HudRenderCallback.EVENT.register(MeteorStrikeClientHandler::renderHUD);
    }
    private static void renderHUD(DrawContext ctx, RenderTickCounter ticker) {
        if (!hasAbility) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        var mat = ctx.getMatrices();
        mat.push();
        mat.translate(0, 0, 500);
        if (countdownMs > 0) {
            String cdText = "☄ METEOR IN " + String.format("%.1f", countdownMs / 1000.0) + "s";
            int tw = client.textRenderer.getWidth(cdText);
            int alpha = (int)(180 + 75 * Math.abs(Math.sin(System.currentTimeMillis() / 300.0)));
            int col = (alpha << 24) | 0xFF2200;
            ctx.drawText(client.textRenderer, cdText, (sw - tw) / 2, sh / 2 - 60, col, true);
        } else {
            int bw = 100, bh = 20, bx = sw / 2 - bw / 2, by = sh - 75;
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
            long pulse = System.currentTimeMillis() / 500;
            int bcol = (pulse % 2 == 0) ? 0xFFCC2200 : 0xFFFF4400;
            ctx.fill(bx, by, bx + bw, by + bh, bcol);
            String label = "☄ G — METEOR";
            int lw = client.textRenderer.getWidth(label);
            ctx.drawText(client.textRenderer, label, bx + (bw - lw) / 2, by + 6, 0xFFFFFFFF, true);
            String timer = (abilityLeftMs / 1000) + "s";
            int tiw = client.textRenderer.getWidth(timer);
            ctx.drawText(client.textRenderer, timer, bx + (bw - tiw) / 2, by + bh + 3, 0xFFAAAAAA, false);
        }
        mat.pop();
    }
    public static net.minecraft.client.option.KeyBinding getBurstKey() { return null; }
}