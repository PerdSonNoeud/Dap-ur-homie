package com.cooptest.client;
import com.cooptest.PoseState;
import com.cooptest.client.ChargedDapClientHandler;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
@Environment(EnvType.CLIENT)
public class CoopAnimationHandler {
    private static final String MOD_ID = "testcoop";
    public static final Identifier ANIMATION_LAYER_ID = Identifier.of(MOD_ID, "coop_animations");
    public static final Identifier GRAB_HOLDING_ANIM = Identifier.of(MOD_ID, "grab_holding");
    public static final Identifier GRAB_HOLDING_CHARGE_ANIM = Identifier.of(MOD_ID, "grab_holding_charge");
    public static final Identifier GRAB_HOLDING_CHARGE_IDLE_ANIM = Identifier.of(MOD_ID, "grab_holding_charge_idle");
    public static final Identifier GRAB_THROW_ANIM = Identifier.of(MOD_ID, "grab_throw");
    public static final Identifier GRAB_READY_ANIM = Identifier.of(MOD_ID, "grab_ready");
    public static final Identifier GRAB_READY_IDLE_ANIM = Identifier.of(MOD_ID, "grab_ready_idle");
    public static final Identifier DAP_CHARGE_ANIM = Identifier.of(MOD_ID, "dap_charge");
    public static final Identifier DAP_CHARGE_IDLE_ANIM = Identifier.of(MOD_ID, "dap_charge_idle");
    public static final Identifier DAP_HIT_ANIM = Identifier.of(MOD_ID, "dap_hit");
    public static final Identifier FIRE_DAP_CHARGE_ANIM = Identifier.of(MOD_ID, "fire_dap_charge");
    public static final Identifier FIRE_DAP_CHARGE_IDLE_ANIM = Identifier.of(MOD_ID, "fire_dap_charge_idle");
    public static final Identifier FIRE_DAP_HIT_ANIM = Identifier.of(MOD_ID, "fire_dap_hit");
    public static final Identifier PUSH_START_ANIM = Identifier.of(MOD_ID, "push_start");
    public static final Identifier PUSH_IDLE_ANIM = Identifier.of(MOD_ID, "push_idle");
    public static final Identifier PUSH_ANIM = Identifier.of(MOD_ID, "push");
    public static final Identifier CATCH_ANIM = Identifier.of(MOD_ID, "catch");
    public static final Identifier MAHITO_ANIM = Identifier.of(MOD_ID, "mahito");
    public static final Identifier HIGHFIVE_START_ANIM = Identifier.of(MOD_ID, "highfive_start");
    public static final Identifier HIGHFIVE_END_ANIM = Identifier.of(MOD_ID, "highfive_end");
    public static final Identifier HIGHFIVE_HIT_ANIM = Identifier.of(MOD_ID, "highfive_hit");
    public static final Identifier HIGHFIVE_HIT_COMBO_ANIM = Identifier.of(MOD_ID, "highfive_hitcombo");
    public static final Identifier DAP_CHARGE_FALL_START_ANIM = Identifier.of(MOD_ID, "dap_charge_fall_start");
    public static final Identifier DAP_CHARGE_FALLING_ANIM = Identifier.of(MOD_ID, "dap_charge_falling");
    public static final Identifier DAP_CHARGE_FALL_HIT_ANIM = Identifier.of(MOD_ID, "dap_charge_fall_hit");
    public static final Identifier SQUASHED_ANIM = Identifier.of(MOD_ID, "squashed");
    public static final Identifier PERFECT_DAP_HIT_ANIM = Identifier.of(MOD_ID, "perfect_dap_hit");
    public static final Identifier DAP_DOWN_ANIM = Identifier.of(MOD_ID, "dap_down");
    public static final Identifier DAP_HIT_WEAK_ANIM = Identifier.of(MOD_ID, "dap_hit_weak");
    public static final Identifier PERFECT_DAP_EXTEND1_P1_ANIM = Identifier.of(MOD_ID, "perfect_dap_extandp1");
    public static final Identifier PERFECT_DAP_EXTEND1_P2_ANIM = Identifier.of(MOD_ID, "perfect_dap_extandp2");
    public static final Identifier PERFECT_DAP_MYBOY_P1_ANIM = Identifier.of(MOD_ID, "perfect_dap_extande_myboyp1");
    public static final Identifier PERFECT_DAP_MYBOY_P2_ANIM = Identifier.of(MOD_ID, "perfect_dap_extande_myboyp2");
    public static final Identifier PERFECT_DAP_EXTEND_BOTH_ANIM = Identifier.of(MOD_ID, "perfect_dap_extand_both");
    public static final Identifier HEAVE_DAP_ANIM = Identifier.of(MOD_ID, "heave_dap");
    public static final Identifier HOLD_SHIELD_ANIM = Identifier.of(MOD_ID, "hold_shield");
    public static final Identifier SHIELD_ANIM = Identifier.of(MOD_ID, "shield");
    public static final Identifier MARIO_JUMP_ANIM = Identifier.of(MOD_ID, "jumpmario");
    public static final Identifier POP_ANIM = Identifier.of(MOD_ID, "pop");
    public static final Identifier HUG_START_ANIM = Identifier.of(MOD_ID, "hug_start");
    public static final Identifier HUGGING_ANIM = Identifier.of(MOD_ID, "hugging");
    public static final Identifier HUGGING2_ANIM = Identifier.of(MOD_ID, "hugging2");
    public static final Identifier HUG_END_ANIM = Identifier.of(MOD_ID, "hugend");
    public static final Identifier FIRE_DAP_HIT_PERFECT_ANIM = Identifier.of(MOD_ID, "fire_dap_hit_perfect");
    public static final Identifier FIRE_DAP_COMBO_P1_ANIM = Identifier.of(MOD_ID, "fire_dap_hitp1");
    public static final Identifier FIRE_DAP_COMBO_P2_ANIM = Identifier.of(MOD_ID, "fire_dap_hitp2");
    public static final Identifier DAPHOLD_HIGHFIVE_ANIM = Identifier.of(MOD_ID, "highfive_dap");
    public static final Identifier DAPHOLD_DAP_ANIM = Identifier.of(MOD_ID, "dap_high");
    public static final Identifier DAPHOLD_DAPPING_ANIM = Identifier.of(MOD_ID, "dapping");
    public static final Identifier DAPHOLD_DAPPING_END_ANIM = Identifier.of(MOD_ID, "dapping_end");
    public static final Identifier CLAP_ANIM        = Identifier.of(MOD_ID, "clap");
    public static final Identifier CLAP_SPAM_ANIM   = Identifier.of(MOD_ID, "clapspam");
    public static final Identifier CLAP_STRONG_ANIM = Identifier.of(MOD_ID, "clap_strong");
    public static final Identifier FUSION_START_P1_ANIM = Identifier.of(MOD_ID, "fusion_startp1");
    public static final Identifier FUSION_START_P2_ANIM = Identifier.of(MOD_ID, "fusion_startp2");
    public static final Identifier FUSION_HIT_P1_ANIM   = Identifier.of(MOD_ID, "fusion_hitp1");
    public static final Identifier FUSION_HIT_P2_ANIM   = Identifier.of(MOD_ID, "fusion_hitp2");
    public static final Identifier FUSION_IDLE_P1_ANIM  = Identifier.of(MOD_ID, "fusion_idlep1");
    public static final Identifier FUSION_IDLE_P2_ANIM  = Identifier.of(MOD_ID, "fusion_idlep2");
    public static final Identifier AURA_WALK_ANIM       = Identifier.of(MOD_ID, "walk_aura");
    public static final Identifier HIGHFIVE_HUG_ANIM    = Identifier.of(MOD_ID, "highfive_hug");
    public static final Identifier HIGHFIVE_HUG2_ANIM   = Identifier.of(MOD_ID, "highfive_hug2");
    public static final Identifier KICK_ANIM               = Identifier.of(MOD_ID, "kick");
    public static final Identifier DROP_KICK_ANIM          = Identifier.of(MOD_ID, "drop_kick");
    public static final Identifier HIGHFIVE_SIKE_ANIM      = Identifier.of(MOD_ID, "highfive_sike");
    public static final Identifier SPIN_ANIM               = Identifier.of(MOD_ID, "spin");
    public static final Identifier GROUND_POUND_DIVE_ANIM  = Identifier.of(MOD_ID, "ground_pound_dive");
    public static final Identifier GROUND_POUND_LAND_ANIM  = Identifier.of(MOD_ID, "ground_pound_land");
    public static final Identifier SLAP_ANIM               = Identifier.of(MOD_ID, "slap");
    public static final Identifier END_GROUP_ANIM          = Identifier.of(MOD_ID, "end_group");
    public static final Identifier PERFECT_DAP_HIT_COMBO_ANIM = Identifier.of(MOD_ID, "perfect_dap_hitcombo");
    public static final Identifier PERFECT_DAP_HIT_COMBO_END_ANIM = Identifier.of(MOD_ID, "perfect_dap_hitcombo_end");
    public static final Identifier FACING_DAP_P1_ANIM  = Identifier.of(MOD_ID, "perfect_dap_hitp1");
    public static final Identifier FACING_DAP_P2_ANIM  = Identifier.of(MOD_ID, "perfect_dap_hitp2");
    public static final Identifier HUDDLE_START_ANIM       = Identifier.of(MOD_ID, "huddle_start");
    public static final Identifier HUDDLE_IDLE_ANIM        = Identifier.of(MOD_ID, "huddle_idle");
    public static final Identifier HUDDLE_QTE1_ANIM        = Identifier.of(MOD_ID, "huddle_qte1");
    public static final Identifier HUDDLE_QTE2_ANIM        = Identifier.of(MOD_ID, "huddle_qte2");
    public static final Identifier HUDDLE_QTE3_ANIM        = Identifier.of(MOD_ID, "huddle_qte3");
    public static final Identifier LAY_DOWN_ANIM            = Identifier.of(MOD_ID, "lay_down");
    public static final Identifier BONK_ANIM               = Identifier.of(MOD_ID, "bonk");
    public static final Identifier DAP_HIT_FACE_ANIM       = Identifier.of(MOD_ID, "dap_hit_face");
    public static final Identifier SLAP_FRONT_ANIM         = Identifier.of(MOD_ID, "slap_front");
    public static final Identifier DAP_HIT_BAD_ANIM        = Identifier.of(MOD_ID, "dap_hit_bad");
    public static final Identifier DAP_LOOP_ANIM           = Identifier.of(MOD_ID, "dap_loop");
    public static final Identifier DAP_LOOP_END_ANIM       = Identifier.of(MOD_ID, "dap_loop_end");
    public static final Identifier HEAVEN_DAP_ANIM         = Identifier.of(MOD_ID, "heaven_dap");
    public static final Identifier SITTING_ANIM            = Identifier.of(MOD_ID, "sitting");
    public static final Identifier REACH_DOWN_ANIM         = Identifier.of(MOD_ID, "reach_down");
    public static final Identifier REACH_PICKUP_ANIM       = Identifier.of(MOD_ID, "reach_pickup");
    public static final Identifier STAND_UP_ANIM           = Identifier.of(MOD_ID, "stand_up");
    public static final Identifier HUDDLE_END_ANIM         = Identifier.of(MOD_ID, "huddle_end");
    private static final Map<UUID, PoseState> currentPoses = new HashMap<>();
    private static final Map<UUID, AnimState> animStates = new HashMap<>();
    public enum AnimState {
        NONE,
        GRAB_READY,
        GRAB_READY_IDLE,
        GRAB_HOLDING,
        GRAB_CHARGING,
        GRAB_CHARGE_IDLE,
        GRAB_THROWING,
        DAP_CHARGING,
        DAP_CHARGE_IDLE,
        DAP_HIT,
        FIRE_DAP_CHARGING,
        FIRE_DAP_CHARGE_IDLE,
        FIRE_DAP_HIT,
        PUSH_START,
        PUSH_IDLE,
        PUSHING,
        CATCHING,
        MAHITO,
        HIGHFIVE_START,
        HIGHFIVE_END,
        HIGHFIVE_HIT,
        HIGHFIVE_HIT_COMBO,
        DAP_CHARGE_FALL_START,
        DAP_CHARGE_FALLING,
        DAP_CHARGE_FALL_HIT,
        SQUASHED,
        PERFECT_DAP_HIT,
        DAP_DOWN,
        HOLD_SHIELD,
        SHIELD,
        MARIO_JUMP,
        POP,
        HUG_START,
        HUGGING,
        HUGGING2,
        HUG_END,
        FIRE_DAP_COMBO_P1,
        FIRE_DAP_COMBO_P2,
        DAPHOLD_HIGHFIVE,
        DAPHOLD_DAP,
        DAPHOLD_DAPPING,
        DAPHOLD_DAPPING_END,
        DAP_HIT_WEAK,
        PERFECT_DAP_EXTEND1_P1,
        PERFECT_DAP_EXTEND1_P2,
        PERFECT_DAP_MYBOY_P1,
        PERFECT_DAP_MYBOY_P2,
        PERFECT_DAP_EXTEND_BOTH,
        HEAVE_DAP,
        CLAP,
        CLAP_SPAM,
        CLAP_STRONG,
        FUSION_START_P1,
        FUSION_START_P2,
        FUSION_HIT_P1,
        FUSION_HIT_P2,
        FUSION_IDLE_P1,
        FUSION_IDLE_P2,
        AURA_WALK,
        HIGHFIVE_HUG,
        HIGHFIVE_HUG2,
        KICK,
        DROP_KICK,
        HIGHFIVE_SIKE,
        SPIN,
        GROUND_POUND_DIVE,
        GROUND_POUND_LAND,
        SLAP,
        END_GROUP,
        PERFECT_DAP_HIT_COMBO,
        HUDDLE_START,
        HUDDLE_IDLE,
        HUDDLE_QTE1,
        HUDDLE_END,
        PERFECT_DAP_HIT_COMBO_END,
        FACING_DAP_P1,
        FACING_DAP_P2,
        HUDDLE_QTE2,
        HUDDLE_QTE3,
        LAY_DOWN,
        BONK,
        DAP_HIT_FACE,
        SLAP_FRONT,
        DAP_HIT_BAD,
        DAP_LOOP,
        DAP_LOOP_END,
        SITTING,
        REACH_DOWN,
        REACH_PICKUP,
        STAND_UP,
        HEAVEN_DAP
    }
    public static void syncAnimState(UUID playerId, AnimState state) {
        animStates.put(playerId, state);
        com.cooptest.PoseNetworking.sendAnimState(playerId, state.ordinal());
    }
    private static final Map<UUID, Long> chargeStartTime = new HashMap<>();
    private static final int DAP_CHARGE_DURATION_TICKS = 5;
    private static final int GRAB_CHARGE_DURATION_TICKS = 32;
    private static final int GRAB_READY_DURATION_TICKS = 5;
    private static final int PUSH_START_DURATION_TICKS = 9;
    private static final int THROW_ANIM_DURATION_TICKS = 6;
    private static final int HIGHFIVE_START_DURATION_TICKS = 7;
    private static final int HIGHFIVE_HIT_DURATION_TICKS = 29;
    private static final int HIGHFIVE_END_DURATION_TICKS = 30;
    private static final int DAP_HIT_EFFECT_DELAY_TICKS = 5;
    private static final int FALL_CHARGE_DURATION_TICKS = 15;
    private static final int PERFECT_DAP_HIT_DURATION_TICKS = 33;
    private static final int DAP_DOWN_DURATION_TICKS = 7;
    private static final int FIRE_DAP_HIT_DURATION_TICKS = 46;
    private static final int DAP_HIT_DURATION_TICKS = 34;
    private static final int MARIO_JUMP_DURATION_TICKS = 10;
    private static final int CLAP_DURATION_TICKS        = 8;
    private static final int CLAP_SPAM_DURATION_TICKS   = 5;
    private static final int CLAP_STRONG_DURATION_TICKS = 3;
    private static final int FUSION_HIT_DURATION_TICKS  = 12;
    private static final int HIGHFIVE_HUG_DURATION_TICKS  = 88;
    private static final int HIGHFIVE_HUG2_DURATION_TICKS = 51;
    private static final int POP_DURATION_TICKS = 8;
    private static final int KICK_DURATION_TICKS              = 20;
    private static final int DROP_KICK_DURATION_TICKS         = 35;
    private static final int HIGHFIVE_SIKE_DURATION_TICKS     = 29;
    private static final int GROUND_POUND_LAND_DURATION_TICKS = 10;
    private static final int SLAP_DURATION_TICKS              = 19;
    private static final int END_GROUP_DURATION_TICKS         = 78;
    private static final int PERFECT_DAP_HIT_COMBO_TICKS      = 23;
    private static final int PERFECT_DAP_HIT_COMBO_END_TICKS  = 10;
    private static final int FACING_DAP_P1_TICKS               = 80;
    private static final int FACING_DAP_P2_TICKS               = 82;
    private static final int HUDDLE_START_DURATION_TICKS      = 11;
    private static final int HUDDLE_QTE1_DURATION_TICKS       = 20;
    private static final int HUDDLE_QTE2_DURATION_TICKS       = 20;
    private static final int HUDDLE_QTE3_DURATION_TICKS       = 20;
    private static final int HUDDLE_END_DURATION_TICKS        = 28;
    private static boolean initialized = false;
    public static void register() {
        if (!isPALAvailable()) {
            return;
        }
        try {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    ANIMATION_LAYER_ID,
                    1500,
                    player -> new PlayerAnimationController(player,
                            (controller, state, animSetter) -> PlayState.STOP
                    )
            );
            initialized = true;
            FirstPersonAnimationTest.init();
            ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        } catch (Exception e) {
            System.err.println("[CoopMoves] Failed to register PAL animations: " + e.getMessage());
        }
    }
    private static boolean isPALAvailable() {
        try {
            Class.forName("com.zigythebird.playeranimcore.animation.layered.IAnimation");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    private static PlayerAnimationController getController(AbstractClientPlayerEntity player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(
                player, ANIMATION_LAYER_ID
        );
    }
    public static void updatePlayerAnimation(PlayerEntity player, PoseState newPose) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        PoseState currentPose = currentPoses.get(playerId);
        if (currentPose == newPose) return;
        AnimState currentAnimState = animStates.get(playerId);
        System.out.println("[DAPANIM] updatePlayerAnimation: " + player.getName().getString()
                + " pose " + currentPose + " -> " + newPose
                + " | animState=" + currentAnimState);
        currentPoses.put(playerId, newPose);
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isLocalPlayer = client.player != null && client.player.getUuid().equals(playerId);
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller == null) return;
            switch (newPose) {
                case GRABBED -> {
                    animStates.put(playerId, AnimState.NONE);
                }
                case GRAB_READY -> {
                    controller.triggerAnimation(GRAB_READY_ANIM);
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.showBothHands();
                        syncAnimState(playerId, AnimState.GRAB_READY);
                    } else {
                        animStates.put(playerId, AnimState.GRAB_READY);
                    }
                    chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                }
                case GRAB_HOLDING -> {
                    controller.triggerAnimation(GRAB_HOLDING_ANIM);
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.showBothHands();
                        syncAnimState(playerId, AnimState.GRAB_HOLDING);
                    } else {
                        animStates.put(playerId, AnimState.GRAB_HOLDING);
                    }
                }
                case PUSH_IDLE -> {
                    controller.triggerAnimation(PUSH_START_ANIM);
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.showBothHands();
                        syncAnimState(playerId, AnimState.PUSH_START);
                    } else {
                        animStates.put(playerId, AnimState.PUSH_START);
                    }
                    chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                }
                case NONE -> {
                    System.out.println("[DAPANIM] updatePlayerAnimation NONE is STOPPING CONTROLLER for "
                            + player.getName().getString() + " was animState=" + currentAnimState);
                    controller.stop();
                    if (isLocalPlayer) {
                        syncAnimState(playerId, AnimState.NONE);
                        FirstPersonAnimationTest.stop();
                    } else {
                        animStates.put(playerId, AnimState.NONE);
                    }
                    chargeStartTime.remove(playerId);
                }
                default -> {
                }
            }
        } catch (Exception e) {
            System.err.println("[CoopMoves] Animation error: " + e.getMessage());
        }
    }
    public static void startGrabCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        AnimState currentState = animStates.get(playerId);
        if (currentState != AnimState.GRAB_HOLDING && currentState != AnimState.GRAB_CHARGE_IDLE) {
            return;
        }
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(GRAB_HOLDING_CHARGE_ANIM);
                syncAnimState(playerId, AnimState.GRAB_CHARGING);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playThrowAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(GRAB_THROW_ANIM);
                syncAnimState(playerId, AnimState.GRAB_THROWING);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playThrow();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void startDapCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGING);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playDapCharge();
                }
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
            }
        } catch (Exception e) {
        }
    }
    public static void stopDapCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        AnimState state = animStates.get(playerId);
        System.out.println("[DAPANIM] stopDapCharge called for " + player.getName().getString() + " state=" + state + " <<WILL SYNC NONE>>");
        if (state == AnimState.DAP_CHARGING || state == AnimState.DAP_CHARGE_IDLE
                || state == AnimState.FIRE_DAP_CHARGING || state == AnimState.FIRE_DAP_CHARGE_IDLE) {
            try {
                PlayerAnimationController controller = getController(clientPlayer);
                if (controller != null) {
                    controller.stop();
                    syncAnimState(playerId, AnimState.NONE);
                    chargeStartTime.remove(playerId);
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
            } catch (Exception e) {
            }
        }
    }
    public static void stopDapChargeLocalOnly(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        AnimState state = animStates.get(playerId);
        System.out.println("[DAPANIM] stopDapChargeLocalOnly called for " + player.getName().getString() + " state=" + state + " (no network sync)");
        if (state == AnimState.DAP_CHARGING || state == AnimState.DAP_CHARGE_IDLE
                || state == AnimState.FIRE_DAP_CHARGING || state == AnimState.FIRE_DAP_CHARGE_IDLE) {
            try {
                PlayerAnimationController controller = getController(clientPlayer);
                if (controller != null) {
                    controller.stop();
                    animStates.put(playerId, AnimState.NONE);
                    chargeStartTime.remove(playerId);
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
            } catch (Exception e) {
            }
        }
    }
    public static void cancelDapCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        AnimState state = animStates.get(playerId);
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                if (state == AnimState.DAP_CHARGING || state == AnimState.DAP_CHARGE_IDLE
                        || state == AnimState.FIRE_DAP_CHARGING || state == AnimState.FIRE_DAP_CHARGE_IDLE
                        || state == AnimState.DAP_HIT || state == AnimState.FIRE_DAP_HIT
                        || state == null) {
                    controller.triggerAnimation(DAP_DOWN_ANIM);
                    syncAnimState(playerId, AnimState.DAP_DOWN);
                }
                chargeStartTime.remove(playerId);
            }
        } catch (Exception e) {
            System.err.println("[CoopMoves] cancelDapCharge error: " + e.getMessage());
        }
    }
    public static void tick() {
        if (!initialized) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        long currentTime = client.world.getTime();
        UUID localPlayerId = client.player != null ? client.player.getUuid() : null;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) continue;
            UUID playerId = player.getUuid();
            AnimState state = animStates.get(playerId);
            if (state == null) continue;
            boolean isLocalPlayer = playerId.equals(localPlayerId);
            try {
                PlayerAnimationController controller = getController(clientPlayer);
                if (controller == null) continue;
                Long startTime = chargeStartTime.get(playerId);
                switch (state) {
                    case GRAB_CHARGING -> {
                        if (startTime != null && currentTime - startTime >= GRAB_CHARGE_DURATION_TICKS) {
                            controller.triggerAnimation(GRAB_HOLDING_CHARGE_IDLE_ANIM);
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.GRAB_CHARGE_IDLE);
                            } else {
                                animStates.put(playerId, AnimState.GRAB_CHARGE_IDLE);
                            }
                        }
                    }
                    case GRAB_THROWING -> {
                        if (startTime != null && currentTime - startTime >= THROW_ANIM_DURATION_TICKS) {
                            PoseState pose = currentPoses.get(playerId);
                            if (pose == PoseState.GRAB_HOLDING) {
                                controller.triggerAnimation(GRAB_HOLDING_ANIM);
                                if (isLocalPlayer) {
                                    syncAnimState(playerId, AnimState.GRAB_HOLDING);
                                } else {
                                    animStates.put(playerId, AnimState.GRAB_HOLDING);
                                }
                            } else {
                                controller.stop();
                                if (isLocalPlayer) {
                                    syncAnimState(playerId, AnimState.NONE);
                                } else {
                                    animStates.put(playerId, AnimState.NONE);
                                }
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case DAP_CHARGING -> {
                        if (startTime != null && currentTime - startTime >= DAP_CHARGE_DURATION_TICKS) {
                            controller.triggerAnimation(DAP_CHARGE_IDLE_ANIM);
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.DAP_CHARGE_IDLE);
                            } else {
                                animStates.put(playerId, AnimState.DAP_CHARGE_IDLE);
                            }
                        }
                    }
                    case GRAB_READY -> {
                        if (startTime != null && currentTime - startTime >= GRAB_READY_DURATION_TICKS) {
                            controller.triggerAnimation(GRAB_READY_IDLE_ANIM);
                            if (isLocalPlayer) {
                                FirstPersonAnimationTest.showBothHands();
                                syncAnimState(playerId, AnimState.GRAB_READY_IDLE);
                            } else {
                                animStates.put(playerId, AnimState.GRAB_READY_IDLE);
                            }
                        }
                    }
                    case PUSH_START -> {
                        if (startTime != null && currentTime - startTime >= PUSH_START_DURATION_TICKS) {
                            controller.triggerAnimation(PUSH_IDLE_ANIM);
                            if (isLocalPlayer) {
                                FirstPersonAnimationTest.showBothHands();
                                syncAnimState(playerId, AnimState.PUSH_IDLE);
                            } else {
                                animStates.put(playerId, AnimState.PUSH_IDLE);
                            }
                        }
                    }
                    case PERFECT_DAP_HIT -> {
                        if (startTime != null && currentTime - startTime >= PERFECT_DAP_HIT_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case DAP_DOWN -> {
                        if (startTime != null && currentTime - startTime >= DAP_DOWN_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case DAP_HIT -> {
                        if (startTime != null && currentTime - startTime >= DAP_HIT_DURATION_TICKS) {
                            System.out.println("[DAPANIM] tick: DAP_HIT expired for " + playerId + " elapsed=" + (currentTime - startTime) + "/" + DAP_HIT_DURATION_TICKS);
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case FIRE_DAP_HIT -> {
                        if (startTime != null && currentTime - startTime >= FIRE_DAP_HIT_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HIGHFIVE_START -> {
                        if (startTime != null && currentTime - startTime >= HIGHFIVE_START_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HIGHFIVE_END -> {
                        if (startTime != null && currentTime - startTime >= HIGHFIVE_END_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HIGHFIVE_HIT -> {
                        if (startTime != null && currentTime - startTime >= HIGHFIVE_HIT_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case MARIO_JUMP -> {
                        if (startTime != null && currentTime - startTime >= MARIO_JUMP_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case POP -> {
                        if (startTime != null && currentTime - startTime >= POP_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case DAPHOLD_HIGHFIVE, DAPHOLD_DAP, DAPHOLD_DAPPING, DAPHOLD_DAPPING_END -> {
                    }
                    case KICK -> {
                        if (startTime != null && currentTime - startTime >= KICK_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) syncAnimState(playerId, AnimState.NONE);
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case DROP_KICK -> {
                        if (startTime != null && currentTime - startTime >= DROP_KICK_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) syncAnimState(playerId, AnimState.NONE);
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HIGHFIVE_SIKE -> {
                        if (startTime != null && currentTime - startTime >= HIGHFIVE_SIKE_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case SPIN -> {
                    }
                    case GROUND_POUND_DIVE -> {
                    }
                    case GROUND_POUND_LAND -> {
                        if (startTime != null && currentTime - startTime >= GROUND_POUND_LAND_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) syncAnimState(playerId, AnimState.NONE);
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case SLAP -> {
                        if (startTime != null && currentTime - startTime >= SLAP_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case END_GROUP -> {
                        if (startTime != null && currentTime - startTime >= END_GROUP_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case PERFECT_DAP_HIT_COMBO -> {
                    }
                    case PERFECT_DAP_HIT_COMBO_END -> {
                        if (startTime != null && currentTime - startTime >= PERFECT_DAP_HIT_COMBO_END_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case FACING_DAP_P1 -> {
                        if (startTime != null && currentTime - startTime >= FACING_DAP_P1_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case FACING_DAP_P2 -> {
                        if (startTime != null && currentTime - startTime >= FACING_DAP_P2_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HUDDLE_START -> {
                        if (startTime != null && currentTime - startTime >= HUDDLE_START_DURATION_TICKS) {
                            controller.stop();
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HUDDLE_IDLE -> {
                    }
                    case HUDDLE_QTE1 -> {
                    }
                    case HUDDLE_QTE2 -> {
                    }
                    case HUDDLE_QTE3 -> {
                    }
                    case LAY_DOWN    -> {
                    }
                    case BONK        -> {
                    }
                    case DAP_HIT_FACE -> {
                    }
                    case SLAP_FRONT   -> {
                    }
                    case DAP_HIT_BAD  -> {
                    }
                    case DAP_LOOP     -> {
                    }
                    case DAP_LOOP_END  -> {
                    }
                    case SITTING -> {
                        if (client.player != null && client.player.getUuid().equals(playerId))
                            FirstPersonAnimationTest.showBothHands();
                    }
                    case REACH_DOWN    -> {
                    }
                    case REACH_PICKUP  -> {
                    }
                    case STAND_UP      -> {
                    }
                    case HEAVEN_DAP    -> {
                    }
                    case HUDDLE_END -> {
                        if (startTime != null && currentTime - startTime >= HUDDLE_END_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case CLAP -> {
                        if (startTime != null && currentTime - startTime >= CLAP_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case CLAP_SPAM -> {
                        if (startTime != null && currentTime - startTime >= CLAP_SPAM_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case CLAP_STRONG -> {
                        if (startTime != null && currentTime - startTime >= CLAP_STRONG_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case FUSION_START_P1, FUSION_START_P2, FUSION_IDLE_P1, FUSION_IDLE_P2, AURA_WALK -> {}
                    case FUSION_HIT_P1, FUSION_HIT_P2 -> {
                        if (startTime != null && currentTime - startTime >= FUSION_HIT_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) syncAnimState(playerId, AnimState.NONE);
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HIGHFIVE_HUG -> {
                        if (startTime != null && currentTime - startTime >= HIGHFIVE_HUG_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HIGHFIVE_HUG2 -> {
                        if (startTime != null && currentTime - startTime >= HIGHFIVE_HUG2_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) { FirstPersonAnimationTest.stop(); syncAnimState(playerId, AnimState.NONE); }
                            else animStates.put(playerId, AnimState.NONE);
                            chargeStartTime.remove(playerId);
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
    }
    public static void stopAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.stop();
            }
        } catch (Exception e) {
        }
        UUID playerId = player.getUuid();
        currentPoses.remove(playerId);
        animStates.remove(playerId);
        chargeStartTime.remove(playerId);
    }
    public static void cleanup(UUID playerId) {
        System.out.println("[DAPANIM] cleanup() called for " + playerId + " | prev animState=" + animStates.get(playerId));
        currentPoses.remove(playerId);
        animStates.remove(playerId);
        chargeStartTime.remove(playerId);
    }
    public static boolean isInChargeIdle(UUID playerId) {
        AnimState state = animStates.get(playerId);
        return state == AnimState.GRAB_CHARGE_IDLE || state == AnimState.DAP_CHARGE_IDLE
                || state == AnimState.FIRE_DAP_CHARGE_IDLE;
    }
    public static void playDapHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        if (DapHoldClientHandler.isAnimationLocked(playerId)) {
            return;
        }
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_HIT_ANIM);
                syncAnimState(playerId, AnimState.DAP_HIT);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playDapHit();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playFireDapHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_HIT_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_HIT);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
            }
        } catch (Exception e) {
        }
    }
    public static void startFireDapCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_CHARGE_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_CHARGING);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
            }
        } catch (Exception e) {
        }
    }
    public static void playFireDapChargeIdle(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_CHARGE_IDLE_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_CHARGE_IDLE);
            }
        } catch (Exception e) {
        }
    }
    public static void playDapChargeIdle(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_IDLE_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGE_IDLE);
            }
        } catch (Exception e) {
        }
    }
    public static void playPushAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(PUSH_ANIM);
                syncAnimState(playerId, AnimState.PUSHING);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playPush();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playCatchAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(CATCH_ANIM);
                syncAnimState(playerId, AnimState.CATCHING);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playMahitoAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(MAHITO_ANIM);
                syncAnimState(playerId, AnimState.MAHITO);
            }
        } catch (Exception e) {
        }
    }
    public static void playHighFiveStart(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(HIGHFIVE_START_ANIM);
                syncAnimState(playerId, AnimState.HIGHFIVE_START);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playHighFiveStart();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playHighFiveEnd(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(HIGHFIVE_END_ANIM);
                syncAnimState(playerId, AnimState.HIGHFIVE_END);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
            }
        } catch (Exception e) {
        }
    }
    public static void playHighFiveHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        if (DapHoldClientHandler.isAnimationLocked(playerId)) {
            return;
        }
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(HIGHFIVE_HIT_ANIM);
                syncAnimState(playerId, AnimState.HIGHFIVE_HIT);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playHighFiveHit();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playFallDapChargeStart(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_FALL_START_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGE_FALL_START);
            }
        } catch (Exception e) {
        }
    }
    public static void playFallDapFalling(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_FALLING_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGE_FALLING);
            }
        } catch (Exception e) {
        }
    }
    public static void playFallDapHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_FALL_HIT_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGE_FALL_HIT);
            }
        } catch (Exception e) {
        }
    }
    public static void playSquashed(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(SQUASHED_ANIM);
                syncAnimState(playerId, AnimState.SQUASHED);
            }
        } catch (Exception e) {
        }
    }
    public static void playPerfectDapHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(PERFECT_DAP_HIT_ANIM);
                syncAnimState(playerId, AnimState.PERFECT_DAP_HIT);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playPerfectDap();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playDapDown(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_DOWN_ANIM);
                syncAnimState(playerId, AnimState.DAP_DOWN);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
            }
        } catch (Exception e) {
        }
    }
    public static void playHoldShield(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(HOLD_SHIELD_ANIM);
                syncAnimState(playerId, AnimState.HOLD_SHIELD);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playShield(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(SHIELD_ANIM);
                syncAnimState(playerId, AnimState.SHIELD);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void stopShieldAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.stop();
                syncAnimState(playerId, AnimState.NONE);
            }
        } catch (Exception e) {
        }
    }
    public static boolean isInBlockingState(UUID playerId) {
        AnimState state = animStates.get(playerId);
        return state == AnimState.HIGHFIVE_END
                || state == AnimState.SQUASHED
                || state == AnimState.PERFECT_DAP_HIT
                || state == AnimState.DAP_DOWN;
    }
    public static boolean isInHuddleAnim(UUID playerId) {
        AnimState s = getAnimState(playerId);
        return s == AnimState.HUDDLE_START || s == AnimState.HUDDLE_IDLE
                || s == AnimState.HUDDLE_QTE1 || s == AnimState.HUDDLE_QTE2
                || s == AnimState.HUDDLE_QTE3 || s == AnimState.HUDDLE_END;
    }
    public static boolean isInHugAnim(UUID playerId) {
        AnimState s = getAnimState(playerId);
        return s == AnimState.HUG_START || s == AnimState.HUGGING
                || s == AnimState.HUGGING2 || s == AnimState.HUG_END
                || s == AnimState.HIGHFIVE_HUG || s == AnimState.HIGHFIVE_HUG2;
    }
    public static AnimState getAnimState(UUID playerId) {
        return animStates.getOrDefault(playerId, AnimState.NONE);
    }
    public static void setAnimStateFromNetwork(PlayerEntity player, int stateOrdinal) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (stateOrdinal == 0) {
            UUID playerId = player.getUuid();
            AnimState prevState = animStates.get(playerId);
            System.out.println("[DAPANIM] NONE received for " + player.getName().getString()
                    + " | prev=" + prevState
                    + " | caller=" + new Exception().getStackTrace()[1]);
            try {
                PlayerAnimationController controller = getController(clientPlayer);
                if (controller != null) {
                    controller.stop();
                }
            } catch (Exception e) {
            }
            animStates.remove(playerId);
            currentPoses.remove(playerId);
            return;
        }
        AnimState state = AnimState.values()[stateOrdinal];
        UUID playerId = player.getUuid();
        AnimState current = animStates.get(playerId);
        System.out.println("[DAPANIM] SET " + state + " for " + player.getName().getString()
                + " | prev=" + current);
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller == null) return;
            switch (state) {
                case GRAB_READY -> {
                    controller.triggerAnimation(GRAB_READY_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case GRAB_READY_IDLE -> {
                    controller.triggerAnimation(GRAB_READY_IDLE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case GRAB_HOLDING -> {
                    controller.triggerAnimation(GRAB_HOLDING_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case GRAB_CHARGING -> {
                    controller.triggerAnimation(GRAB_HOLDING_CHARGE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case GRAB_CHARGE_IDLE -> controller.triggerAnimation(GRAB_HOLDING_CHARGE_IDLE_ANIM);
                case GRAB_THROWING -> {
                    controller.triggerAnimation(GRAB_THROW_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playThrow();
                    }
                }
                case DAP_CHARGING -> {
                    controller.triggerAnimation(DAP_CHARGE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playDapCharge();
                    }
                }
                case DAP_CHARGE_IDLE -> {
                    controller.triggerAnimation(DAP_CHARGE_IDLE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playDapCharge();
                    }
                }
                case DAP_HIT -> {
                    controller.triggerAnimation(DAP_HIT_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playDapHit();
                    }
                }
                case FIRE_DAP_CHARGING -> {
                    controller.triggerAnimation(FIRE_DAP_CHARGE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case FIRE_DAP_CHARGE_IDLE -> {
                    controller.triggerAnimation(FIRE_DAP_CHARGE_IDLE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case FIRE_DAP_HIT -> {
                    controller.triggerAnimation(FIRE_DAP_HIT_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PUSH_START -> {
                    controller.triggerAnimation(PUSH_START_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PUSH_IDLE -> {
                    controller.triggerAnimation(PUSH_IDLE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PUSHING -> {
                    controller.triggerAnimation(PUSH_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playPush();
                    }
                }
                case CATCHING -> {
                    controller.triggerAnimation(CATCH_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case MAHITO -> controller.triggerAnimation(MAHITO_ANIM);
                case HIGHFIVE_START -> {
                    controller.triggerAnimation(HIGHFIVE_START_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHighFiveStart();
                    }
                }
                case HIGHFIVE_END -> controller.triggerAnimation(HIGHFIVE_END_ANIM);
                case HIGHFIVE_HIT -> {
                    controller.triggerAnimation(HIGHFIVE_HIT_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHighFiveHit();
                    }
                }
                case HIGHFIVE_HIT_COMBO -> {
                    controller.triggerAnimation(HIGHFIVE_HIT_COMBO_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHighFiveCombo();
                    }
                }
                case DAP_CHARGE_FALL_START -> controller.triggerAnimation(DAP_CHARGE_FALL_START_ANIM);
                case DAP_CHARGE_FALLING -> controller.triggerAnimation(DAP_CHARGE_FALLING_ANIM);
                case DAP_CHARGE_FALL_HIT -> controller.triggerAnimation(DAP_CHARGE_FALL_HIT_ANIM);
                case SQUASHED -> controller.triggerAnimation(SQUASHED_ANIM);
                case PERFECT_DAP_HIT -> {
                    controller.triggerAnimation(PERFECT_DAP_HIT_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playPerfectDap();
                    }
                }
                case PERFECT_DAP_EXTEND1_P1 -> {
                    controller.triggerAnimation(PERFECT_DAP_EXTEND1_P1_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PERFECT_DAP_EXTEND1_P2 -> {
                    controller.triggerAnimation(PERFECT_DAP_EXTEND1_P2_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PERFECT_DAP_MYBOY_P1 -> {
                    controller.triggerAnimation(PERFECT_DAP_MYBOY_P1_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PERFECT_DAP_MYBOY_P2 -> {
                    controller.triggerAnimation(PERFECT_DAP_MYBOY_P2_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PERFECT_DAP_EXTEND_BOTH -> {
                    controller.triggerAnimation(PERFECT_DAP_EXTEND_BOTH_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case HEAVE_DAP -> {
                    controller.triggerAnimation(HEAVE_DAP_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case DAP_DOWN -> {
                    controller.triggerAnimation(DAP_DOWN_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
                case HOLD_SHIELD -> {
                    controller.triggerAnimation(HOLD_SHIELD_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case SHIELD -> {
                    controller.triggerAnimation(SHIELD_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case MARIO_JUMP -> controller.triggerAnimation(MARIO_JUMP_ANIM);
                case POP -> controller.triggerAnimation(POP_ANIM);
                case HUG_START -> {
                    controller.triggerAnimation(HUG_START_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHug();
                    }
                }
                case HUGGING -> controller.triggerAnimation(HUGGING_ANIM);
                case HUGGING2 -> controller.triggerAnimation(HUGGING2_ANIM);
                case HUG_END -> {
                    controller.triggerAnimation(HUG_END_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
                case FIRE_DAP_COMBO_P1 -> {
                    controller.triggerAnimation(FIRE_DAP_COMBO_P1_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case FIRE_DAP_COMBO_P2 -> {
                    controller.triggerAnimation(FIRE_DAP_COMBO_P2_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case DAPHOLD_HIGHFIVE -> {
                    controller.triggerAnimation(DAPHOLD_HIGHFIVE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHighFiveStart();
                    }
                }
                case DAPHOLD_DAP -> {
                    controller.triggerAnimation(DAPHOLD_DAP_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playDapHit();
                    }
                }
                case DAPHOLD_DAPPING -> {
                    controller.triggerAnimation(DAPHOLD_DAPPING_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case DAPHOLD_DAPPING_END -> {
                    controller.triggerAnimation(DAPHOLD_DAPPING_END_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
                case NONE -> {
                    controller.stop();
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
                case CLAP -> {
                    controller.triggerAnimation(CLAP_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case CLAP_SPAM -> {
                    controller.triggerAnimation(CLAP_SPAM_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case CLAP_STRONG -> {
                    controller.triggerAnimation(CLAP_STRONG_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case FUSION_START_P1 -> controller.triggerAnimation(FUSION_START_P1_ANIM);
                case FUSION_START_P2 -> controller.triggerAnimation(FUSION_START_P2_ANIM);
                case FUSION_HIT_P1   -> controller.triggerAnimation(FUSION_HIT_P1_ANIM);
                case FUSION_HIT_P2   -> controller.triggerAnimation(FUSION_HIT_P2_ANIM);
                case FUSION_IDLE_P1  -> controller.triggerAnimation(FUSION_IDLE_P1_ANIM);
                case FUSION_IDLE_P2  -> controller.triggerAnimation(FUSION_IDLE_P2_ANIM);
                case AURA_WALK       -> controller.triggerAnimation(AURA_WALK_ANIM);
                case HIGHFIVE_HUG   -> {
                    controller.triggerAnimation(HIGHFIVE_HUG_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHug();
                    }
                }
                case HIGHFIVE_HUG2  -> {
                    controller.triggerAnimation(HIGHFIVE_HUG2_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHug();
                    }
                }
                case KICK -> {
                    controller.triggerAnimation(KICK_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playKick();
                    }
                }
                case DROP_KICK -> {
                    controller.triggerAnimation(DROP_KICK_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playDropKick();
                    }
                }
                case HIGHFIVE_SIKE -> {
                    controller.triggerAnimation(HIGHFIVE_SIKE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case SPIN -> {
                    controller.triggerAnimation(SPIN_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case GROUND_POUND_DIVE -> {
                    controller.triggerAnimation(GROUND_POUND_DIVE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
                case GROUND_POUND_LAND -> {
                    controller.triggerAnimation(GROUND_POUND_LAND_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
                case SLAP -> {
                    controller.triggerAnimation(SLAP_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playSlap();
                    }
                }
                case END_GROUP -> {
                    controller.triggerAnimation(END_GROUP_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PERFECT_DAP_HIT_COMBO -> {
                    controller.triggerAnimation(PERFECT_DAP_HIT_COMBO_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case PERFECT_DAP_HIT_COMBO_END -> {
                    controller.triggerAnimation(PERFECT_DAP_HIT_COMBO_END_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case FACING_DAP_P1 -> {
                    controller.triggerAnimation(FACING_DAP_P1_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case FACING_DAP_P2 -> {
                    controller.triggerAnimation(FACING_DAP_P2_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case HUDDLE_START -> {
                    controller.triggerAnimation(HUDDLE_START_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case HUDDLE_IDLE -> {
                    controller.triggerAnimation(HUDDLE_IDLE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case HUDDLE_QTE1 -> {
                    controller.triggerAnimation(HUDDLE_QTE1_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case HUDDLE_QTE2 -> {
                    controller.triggerAnimation(HUDDLE_QTE2_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case HUDDLE_QTE3 -> {
                    controller.triggerAnimation(HUDDLE_QTE3_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case HUDDLE_END -> {
                    controller.triggerAnimation(HUDDLE_END_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case LAY_DOWN -> {
                    controller.triggerAnimation(LAY_DOWN_ANIM);
                }
                case BONK -> {
                    controller.triggerAnimation(BONK_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case DAP_HIT_FACE -> {
                    controller.triggerAnimation(DAP_HIT_FACE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case SLAP_FRONT -> {
                    controller.triggerAnimation(SLAP_FRONT_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case DAP_LOOP -> {
                    controller.triggerAnimation(DAP_LOOP_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case DAP_LOOP_END -> {
                    controller.triggerAnimation(DAP_LOOP_END_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case SITTING -> {
                    controller.triggerAnimation(SITTING_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case REACH_DOWN -> {
                    controller.triggerAnimation(REACH_DOWN_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case REACH_PICKUP -> {
                    controller.triggerAnimation(REACH_PICKUP_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case STAND_UP -> { controller.triggerAnimation(STAND_UP_ANIM); }
                case HEAVEN_DAP -> {
                    controller.triggerAnimation(HEAVEN_DAP_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId))
                        FirstPersonAnimationTest.showBothHands();
                }
                case DAP_HIT_BAD -> {
                    controller.triggerAnimation(DAP_HIT_BAD_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        ChargedDapClientHandler.triggerDapBadBlock();
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
            }
            if (client.world != null) {
                long worldTime = client.world.getTime();
                switch (state) {
                    case DAP_HIT, FIRE_DAP_HIT, PERFECT_DAP_HIT,
                         HIGHFIVE_END, HIGHFIVE_HIT, HIGHFIVE_HUG, HIGHFIVE_HUG2,
                         MARIO_JUMP, POP, DAP_DOWN,
                         CLAP, CLAP_SPAM, CLAP_STRONG,
                         FUSION_HIT_P1, FUSION_HIT_P2,
                         KICK, DROP_KICK, HIGHFIVE_SIKE, GROUND_POUND_LAND,
                         SLAP, END_GROUP, PERFECT_DAP_HIT_COMBO, HUDDLE_START, HUDDLE_END, HUDDLE_QTE2, HUDDLE_QTE3,
                         PERFECT_DAP_HIT_COMBO_END, FACING_DAP_P1, FACING_DAP_P2 -> {
                        chargeStartTime.put(playerId, worldTime);
                        System.out.println("[DAPANIM] chargeStartTime SET for " + player.getName().getString() + " state=" + state + " t=" + worldTime);
                    }
                    default -> {}
                }
            }
            animStates.put(playerId, state);
            System.out.println("[DAPANIM] animStates confirmed=" + animStates.get(playerId) + " for " + player.getName().getString());
        } catch (Exception e) {
        }
    }
    public static void playFireDapHitPerfect(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_HIT_PERFECT_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_HIT);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playFireDapComboP1(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_COMBO_P1_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_HIT);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playFireDapComboP2(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_COMBO_P2_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_HIT);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playDapHoldStart(net.minecraft.entity.player.PlayerEntity player, int role) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                animStates.put(playerId, AnimState.NONE);
                MinecraftClient client = MinecraftClient.getInstance();
                boolean isLocalPlayer = client.player != null && client.player.getUuid().equals(playerId);
                if (role == 0) {
                    controller.triggerAnimation(DAPHOLD_HIGHFIVE_ANIM);
                    syncAnimState(playerId, AnimState.DAPHOLD_HIGHFIVE);
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.playHighFiveStart();
                    }
                } else {
                    controller.triggerAnimation(DAPHOLD_DAP_ANIM);
                    syncAnimState(playerId, AnimState.DAPHOLD_DAP);
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.playDapHit();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void playDapHoldDapping(net.minecraft.entity.player.PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAPHOLD_DAPPING_ANIM);
                syncAnimState(playerId, AnimState.DAPHOLD_DAPPING);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playDapHoldResume(net.minecraft.entity.player.PlayerEntity player, int role) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                if (role == 0) {
                    controller.triggerAnimation(DAPHOLD_HIGHFIVE_ANIM);
                    syncAnimState(playerId, AnimState.DAPHOLD_HIGHFIVE);
                } else {
                    controller.triggerAnimation(DAPHOLD_DAP_ANIM);
                    syncAnimState(playerId, AnimState.DAPHOLD_DAP);
                }
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.stop();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playDapHoldEnd(net.minecraft.entity.player.PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAPHOLD_DAPPING_END_ANIM);
                syncAnimState(playerId, AnimState.DAPHOLD_DAPPING_END);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playKick(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(KICK_ANIM);
                syncAnimState(playerId, AnimState.KICK);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playKick();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playDropKick(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DROP_KICK_ANIM);
                syncAnimState(playerId, AnimState.DROP_KICK);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playDropKick();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playHighFiveSike(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(HIGHFIVE_SIKE_ANIM);
                syncAnimState(playerId, AnimState.HIGHFIVE_SIKE);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
    public static void playSlap(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(SLAP_ANIM);
                animStates.put(playerId, AnimState.SLAP);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playSlap();
                }
                chargeStartTime.put(playerId, client.world != null ? client.world.getTime() : 0L);
            }
        } catch (Exception e) {
        }
    }
    public static void playEndGroup(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(END_GROUP_ANIM);
                animStates.put(playerId, AnimState.END_GROUP);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
                chargeStartTime.put(playerId, client.world != null ? client.world.getTime() : 0L);
            }
        } catch (Exception e) {
        }
    }
    public static void playSpinAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(SPIN_ANIM);
                animStates.put(playerId, AnimState.SPIN);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
}