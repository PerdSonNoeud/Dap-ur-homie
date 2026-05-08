package com.cooptest.client;

import com.cooptest.BonkHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;




//IGNORE THIS

public class BonkClientHandler {

    private static KeyBinding keyBonk;
    private static boolean movementLocked = false;

    private static float pendingYawDelta   = 0f;
    private static float pendingPitchDelta = 0f;
    private static boolean jerkPending     = false;

    public static void register() {
        // Register L keybind
        keyBonk = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cooptest.bonk",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "category.cooptest"
        ));

        ClientPlayNetworking.registerGlobalReceiver(BonkHandler.BonkMoveLockPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    movementLocked = payload.locked();
                }));

        ClientPlayNetworking.registerGlobalReceiver(BonkHandler.BonkCameraPayload.ID,
                (payload, context) -> context.client().execute(() -> {

                    pendingYawDelta   = payload.deltaYaw();
                    pendingPitchDelta = payload.deltaPitch();
                    jerkPending       = true;
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (keyBonk.wasPressed()) {
                ClientPlayNetworking.send(new BonkHandler.BonkLKeyPayload());
            }

            if (movementLocked) {
                client.player.setVelocity(0, client.player.getVelocity().y, 0);
            }


            if (jerkPending && client.player != null) {
                jerkPending = false;
                client.player.setYaw(client.player.getYaw() + pendingYawDelta);
                client.player.setPitch(
                        Math.max(-90f, Math.min(90f, client.player.getPitch() + pendingPitchDelta)));
            }
        });
    }

    public static boolean isMovementLocked() { return movementLocked; }

    public static void reset() {
        movementLocked = false;
        jerkPending    = false;
    }

}