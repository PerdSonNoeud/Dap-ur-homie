package com.cooptest.client;
import com.cooptest.SitHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
public class SitClientHandler {
    private static boolean wasFHeld = false;
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.currentScreen != null) return;
            boolean fHeld = InputUtil.isKeyPressed(
                    net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle(),
                    GLFW.GLFW_KEY_F);
            if (fHeld != wasFHeld) {
                wasFHeld = fHeld;
                ClientPlayNetworking.send(new SitHandler.SitFHoldPayload(fHeld));
            }
        });
    }
}