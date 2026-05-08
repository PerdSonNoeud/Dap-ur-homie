package com.cooptest.mixin;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class PlayerEntityPassengerOffsetMixin {

    private static final double GRAB_OFFSET_FORWARD = 0.6;
    private static final double GRAB_OFFSET_UP      = 0.8;
    private static final double GRAB_OFFSET_RIGHT   = 3.0;

    @Inject(method = "getPassengerRidingPos", at = @At("RETURN"), cancellable = true)
    private void customPassengerPosition(Entity passenger, CallbackInfoReturnable<Vec3d> cir) {
        Entity vehicle = (Entity)(Object)this;
        if (!(vehicle instanceof PlayerEntity holder)) return;
        if (!(passenger instanceof PlayerEntity)) return;

        PoseState holderPose = PoseNetworking.poseStates.getOrDefault(holder.getUuid(), PoseState.NONE);
        if (holderPose == PoseState.GRAB_HOLDING) {
            Vec3d base = cir.getReturnValue();
            float yaw = holder.getYaw();
            double yawRad = Math.toRadians(-yaw);
            double rotX = GRAB_OFFSET_RIGHT * Math.cos(yawRad) + GRAB_OFFSET_FORWARD * Math.sin(yawRad);
            double rotZ = GRAB_OFFSET_RIGHT * Math.sin(yawRad) - GRAB_OFFSET_FORWARD * Math.cos(yawRad);
            cir.setReturnValue(new Vec3d(base.x + rotX, base.y + GRAB_OFFSET_UP, base.z + rotZ));
        }
    }
}