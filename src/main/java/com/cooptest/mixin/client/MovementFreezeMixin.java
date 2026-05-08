package com.cooptest.mixin.client;

import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.DapHoldClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(KeyboardInput.class)
public class MovementFreezeMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(boolean slowDown, float f, CallbackInfo ci) {
        boolean shouldFreeze =
                HighFiveClientHandler.isLocalPlayerFrozen()
                        || com.cooptest.client.DivineFlamComboClient.isLocalPlayerInCombo()
                        || ChargedDapClientHandler.isLocalPlayerFireDapFrozen()
                        || ChargedDapClientHandler.isLocalPlayerPerfectDapFrozen()
                        || DapHoldClientHandler.isLocalPlayerFrozen()
                        || com.cooptest.client.HugClientHandler.isLocalPlayerInHug()
                        || ChargedDapClientHandler.isDapBadBlocking()
                        || isInHuddle();

        if (shouldFreeze) {
            Input input = (Input) (Object) this;
            input.movementForward  = 0;
            input.movementSideways = 0;
            input.jumping  = false;
            input.sneaking = false;
        }
    }

    private static boolean isInHuddle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return com.cooptest.client.CoopAnimationHandler.isInHuddleAnim(client.player.getUuid());
    }
}