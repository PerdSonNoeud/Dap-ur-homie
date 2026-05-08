package com.cooptest.mixin.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.UUID;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Unique
    private static final HashMap<UUID, Boolean> matrixPushed = new HashMap<>();

    @Unique
    private static final HashMap<UUID, Float> lockedYaw = new HashMap<>();

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void rotateGrabbedPlayer(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                     MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                     int light, CallbackInfo ci) {
        PoseState pose = PoseNetworking.poseStates.getOrDefault(player.getUuid(), PoseState.NONE);

        if (pose == PoseState.GRABBED) {

            com.cooptest.client.CoopAnimationHandler.AnimState animState =
                    com.cooptest.client.CoopAnimationHandler.getAnimState(player.getUuid());
            if (animState == com.cooptest.client.CoopAnimationHandler.AnimState.SPIN
                    || animState == com.cooptest.client.CoopAnimationHandler.AnimState.GROUND_POUND_DIVE) {
                matrixPushed.put(player.getUuid(), false);
                return;
            }

            matrices.push();

            float facingYaw;

            Entity vehicle = player.getVehicle();
            if (vehicle instanceof PlayerEntity holder) {

                facingYaw = holder.getYaw();
                lockedYaw.put(player.getUuid(), facingYaw);
            } else {

                if (lockedYaw.containsKey(player.getUuid())) {
                    facingYaw = lockedYaw.get(player.getUuid());
                } else {

                    facingYaw = player.getYaw();
                    lockedYaw.put(player.getUuid(), facingYaw);
                }
            }

            float counterRotation = -yaw + facingYaw;

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(counterRotation));

            matrices.translate(0, 0.9, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrices.translate(0, -0.9, 0);

            matrixPushed.put(player.getUuid(), true);
        } else {
            lockedYaw.remove(player.getUuid());
            matrixPushed.put(player.getUuid(), false);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"))
    private void restoreMatrix(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                               MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                               int light, CallbackInfo ci) {
        Boolean pushed = matrixPushed.get(player.getUuid());
        if (pushed != null && pushed) {
            matrices.pop();
            matrixPushed.put(player.getUuid(), false);
        }
    }
}