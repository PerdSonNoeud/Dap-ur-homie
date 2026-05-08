package com.cooptest.mixin.client;
import com.cooptest.ArmPoseTracker;
import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Unique private static final float READY_UP = 1.2f;
    @Unique private static final float READY_FORWARD = 0.8f;
    @Unique private static final float READY_PITCH = -85f;
    @Unique private static final float HOLD_UP = 1.5f;
    @Unique private static final float HOLD_FORWARD = 0.3f;
    @Unique private static final float HOLD_PITCH = -95f;
    @Unique private static final float CHARGE_UP = 0.8f;
    @Unique private static final float CHARGE_FORWARD = -0.5f;
    @Unique private static final float CHARGE_PITCH = -60f;
    @Unique private static final float CHARGE_SHAKE = 0.08f;
    @Unique private static final float THROW_DURATION = 300f;
    @Unique private static final float PUSH_IDLE_UP = 0.4f;
    @Unique private static final float PUSH_IDLE_FORWARD = 0.5f;
    @Unique private static final float PUSH_IDLE_PITCH = -50f;
    @Unique private static final float PUSH_ACTION_UP = 0.6f;
    @Unique private static final float PUSH_ACTION_FORWARD = 1.0f;
    @Unique private static final float PUSH_ACTION_PITCH = -80f;
    @Unique private static final float LERP_SPEED = 0.25f;
    @Unique private static final float FAST_LERP = 0.4f;
    @Unique private static float currUp = 0f;
    @Unique private static float currForward = 0f;
    @Unique private static float currPitch = 0f;
    @Unique private static long throwStartTime = 0;
    @Unique private static boolean wasHolding = false;
    @Inject(method = "renderArm", at = @At("HEAD"))
    private void onRenderArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                             int light, Arm arm, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        boolean handsEmpty = client.player.getMainHandStack().isEmpty() &&
                client.player.getOffHandStack().isEmpty();
        UUID playerId = client.player.getUuid();
        PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
        boolean isHolding = pose == PoseState.GRAB_HOLDING;
        if (wasHolding && !isHolding && pose == PoseState.GRAB_READY) {
            throwStartTime = System.currentTimeMillis();
        }
        wasHolding = isHolding;
        Long trackerThrowStart = ArmPoseTracker.throwAnimationStart.get(playerId);
        if (trackerThrowStart != null) {
            throwStartTime = trackerThrowStart;
        }
        float targetUp = 0f;
        float targetForward = 0f;
        float targetPitch = 0f;
        float lerpSpeed = LERP_SPEED;
        float shakeAmount = 0f;
        boolean inThrowAnim = false;
        float throwProgress = 0f;
        if (throwStartTime > 0) {
            long elapsed = System.currentTimeMillis() - throwStartTime;
            if (elapsed < THROW_DURATION) {
                inThrowAnim = true;
                throwProgress = elapsed / THROW_DURATION;
            } else {
                throwStartTime = 0;
            }
        }
        if (inThrowAnim) {
            lerpSpeed = FAST_LERP;
            if (throwProgress < 0.3f) {
                float p = throwProgress / 0.3f;
                targetUp = lerp(HOLD_UP, 0.3f, p);
                targetForward = lerp(HOLD_FORWARD, 1.2f, p);
                targetPitch = lerp(HOLD_PITCH, -100f, p);
            } else {
                float p = (throwProgress - 0.3f) / 0.7f;
                targetUp = lerp(0.3f, 0f, p);
                targetForward = lerp(1.2f, 0f, p);
                targetPitch = lerp(-100f, 0f, p);
            }
        } else if (handsEmpty && pose != PoseState.NONE && pose != PoseState.GRABBED) {
            float charge = GrabInputHandler.getThrowChargeProgress();
            boolean isCharging = charge >= 0f;
            switch (pose) {
                case GRAB_READY -> {
                    targetUp = READY_UP;
                    targetForward = READY_FORWARD;
                    targetPitch = READY_PITCH;
                }
                case GRAB_HOLDING -> {
                    if (isCharging) {
                        targetUp = lerp(HOLD_UP, CHARGE_UP, charge);
                        targetForward = lerp(HOLD_FORWARD, CHARGE_FORWARD, charge);
                        targetPitch = lerp(HOLD_PITCH, CHARGE_PITCH, charge);
                        if (charge > 0.5f) {
                            shakeAmount = CHARGE_SHAKE * (charge - 0.5f) * 2f;
                        }
                    } else {
                        targetUp = HOLD_UP;
                        targetForward = HOLD_FORWARD;
                        targetPitch = HOLD_PITCH;
                    }
                }
                case PUSH_IDLE, PUSH_RETURN -> {
                    targetUp = PUSH_IDLE_UP;
                    targetForward = PUSH_IDLE_FORWARD;
                    targetPitch = PUSH_IDLE_PITCH;
                }
                case PUSH_ACTION -> {
                    targetUp = PUSH_ACTION_UP;
                    targetForward = PUSH_ACTION_FORWARD;
                    targetPitch = PUSH_ACTION_PITCH;
                    lerpSpeed = FAST_LERP;
                }
            }
        }
        currUp = lerp(currUp, targetUp, lerpSpeed);
        currForward = lerp(currForward, targetForward, lerpSpeed);
        currPitch = lerp(currPitch, targetPitch, lerpSpeed);
        float shakeOffset = 0f;
        if (shakeAmount > 0f) {
            shakeOffset = (float)(Math.random() - 0.5) * shakeAmount;
        }
        if (Math.abs(currPitch) < 1f && Math.abs(currUp) < 0.01f && Math.abs(currForward) < 0.01f) {
            return;
        }
        matrices.translate(0.0, currUp + shakeOffset, -currForward + shakeOffset * 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(currPitch + shakeOffset * 20f));
    }
    @Unique
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}