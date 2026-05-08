package com.cooptest.client;
import com.cooptest.HuddleHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
public class HuddleClientHandler {
    private static boolean fWasHeld = false;
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleEndPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            CoopAnimationHandler.AnimState animSt = CoopAnimationHandler.getAnimState(client.player.getUuid());
            boolean inHugAnim = animSt == CoopAnimationHandler.AnimState.HUG_START
                    || animSt == CoopAnimationHandler.AnimState.HUGGING
                    || animSt == CoopAnimationHandler.AnimState.HUGGING2
                    || animSt == CoopAnimationHandler.AnimState.HUG_END
                    || animSt == CoopAnimationHandler.AnimState.HIGHFIVE_HUG
                    || animSt == CoopAnimationHandler.AnimState.HIGHFIVE_HUG2;
            boolean inHighFiveWindow = animSt == CoopAnimationHandler.AnimState.HIGHFIVE_HIT
                    || HighFiveClientHandler.isInHugOpportunityWindow();
            PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUuid(), PoseState.NONE);
            boolean blocked = pose == PoseState.GRAB_READY || pose == PoseState.GRAB_HOLDING
                    || inHugAnim || inHighFiveWindow;
            if (blocked) {
                if (fWasHeld) {
                    ClientPlayNetworking.send(new HuddleHandler.HuddleFHoldPayload(false));
                    fWasHeld = false;
                }
                return;
            }
            long win  = client.getWindow().getHandle();
            boolean fHeld = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_F)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (fHeld && !fWasHeld) {
                ClientPlayNetworking.send(new HuddleHandler.HuddleFHoldPayload(true));
                fWasHeld = true;
            } else if (!fHeld && fWasHeld) {
                ClientPlayNetworking.send(new HuddleHandler.HuddleFHoldPayload(false));
                fWasHeld = false;
            }
        });
    }
}