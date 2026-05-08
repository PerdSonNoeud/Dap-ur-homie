package com.cooptest.client;

import com.cooptest.ChargedDapHandler;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChargedDapClientHandler {

    private static KeyBinding chargedDapKey;
    private static boolean wasKeyPressed = false;
    private static boolean isCharging = false;
    private static boolean wasFireCharging = false;
    private static boolean fireChargeComplete = false;
    private static float lastFireLevel = 0f;
    private static long chargeStartTime = 0;


    private static long whiffCooldownEnd = 0;

    public static long postQTEBlockEndMs = 0;


    private static final Map<UUID, Float> otherPlayerCharges = new HashMap<>();
    private static final Map<UUID, Float> otherPlayerFire = new HashMap<>();
    private static final Map<UUID, Boolean> otherPlayerCharging = new HashMap<>();


    private static float localFireLevel = 0f;


    private static boolean isHeavenReady = false;
    private static long heavenReadyStartTime = 0;


    private static long flashStartTime = 0;
    private static int resultTier = 0;
    private static boolean resultPerfect = false;


    private static long perfectImpactStartTime = 0;


    private static boolean isPerfectDapFrozen = false;
    private static boolean perfectImpactActive = false;


    private static int perfectDapImpactFrame = 0;
    private static long perfectDapImpactFrameStartTime = 0;


    private static boolean facingDapImpactActive = false;
    private static long    facingDapImpactStartMs = 0;
    private static net.minecraft.util.Identifier IMPAC7_TEXTURE;
    private static net.minecraft.util.Identifier IMPAC8_TEXTURE;
    private static net.minecraft.util.Identifier IMPAC9_TEXTURE;




    public static boolean dropKickImpactActive = false;
    private static long   dropKickImpactStartMs = 0;

    private static final long DK_FADE_IN   = 50L;
    private static final long DK_FRAME1    = 150L;
    private static final long DK_FRAME2    = 230L;
    private static final long DK_FRAME3    = 310L;
    private static final long DK_FADE_OUT  = 430L;


    private static long dapBadBlockEnd = 0;
    public static boolean isDapBadBlocking() { return System.currentTimeMillis() < dapBadBlockEnd; }
    public static void triggerDapBadBlock() { dapBadBlockEnd = System.currentTimeMillis() + 1667L; }


    private static boolean inFaceDapSession = false;
    public static void setInFaceDapSession(boolean v) { inFaceDapSession = v; }

    public static void triggerDropKickImpact() {
        dropKickImpactActive = true;
        dropKickImpactStartMs = System.currentTimeMillis();
    }



    private static final long WHITE_FADE_DURATION = 30;
    private static final long IMPACT1_END = 80;
    private static final long IMPACT2_END = 130;
    private static final long IMPACT3_END = 180;


    private static net.minecraft.util.Identifier IMPACT1_TEXTURE;
    private static net.minecraft.util.Identifier IMPACT2_TEXTURE;
    private static net.minecraft.util.Identifier IMPACT3_TEXTURE;


    private static net.minecraft.util.Identifier PERFECT_FRAME0_TEXTURE;
    private static net.minecraft.util.Identifier PERFECT_FRAME1_TEXTURE;
    private static net.minecraft.util.Identifier PERFECT_FRAME2_TEXTURE;
    private static net.minecraft.util.Identifier PERFECT_FRAME3_TEXTURE;


    private static KeyBinding fireDapComboKey;

    public static boolean isFireDapJKeyHeld() {
        return fireDapComboKey != null && fireDapComboKey.isPressed();
    }
    private static boolean fireDapWasKeyPressed = false;


    private static long fireDapComboWindowStart = 0;
    private static boolean inFireDapComboWindow = false;
    private static final long FIRE_DAP_COMBO_WINDOW_MS = 2200;


    private static final Map<UUID, Boolean> fireDapFrozenPlayers = new HashMap<>();
    private static final Set<UUID> fireDapFirstPersonPlayers = new HashSet<>();




    private static final long CHARGE_TIME_MS = 250;
    private static final long FLASH_DURATION = 500;

    public static void forceStopCharging() {
        isCharging = false;
        localFireLevel = 0f;
        wasFireCharging = false;
        fireChargeComplete = false;
        lastFireLevel = 0f;
    }

    public static boolean isLocalPlayerCharging() {
        return isCharging;
    }

    public static void register() {
        chargedDapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.coopmoves.dap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.coopmoves"
        ));

        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ChargeSyncPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();
                        MinecraftClient client = MinecraftClient.getInstance();

                        if (payload.isCharging()) {
                            otherPlayerCharges.put(playerId, payload.chargePercent());
                            otherPlayerFire.put(playerId, payload.firePercent());
                            otherPlayerCharging.put(playerId, true);


                            if (client.player != null && client.player.getUuid().equals(playerId)) {

                                float newFireLevel = com.cooptest.CoopMovesConfig.get().enableFireDap
                                        ? payload.firePercent() : 0f;

                                if (newFireLevel < localFireLevel - 0.1f && wasFireCharging) {
                                    wasFireCharging = false;
                                    fireChargeComplete = false;
                                    CoopAnimationHandler.playDapChargeIdle(client.player);
                                }
                                localFireLevel = newFireLevel;
                            }
                        } else {
                            otherPlayerCharges.remove(playerId);
                            otherPlayerFire.remove(playerId);
                            otherPlayerCharging.remove(playerId);


                            if (client.player != null && client.player.getUuid().equals(playerId)) {
                                localFireLevel = 0f;
                            }
                        }
                    });
                }
        );


        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.WhiffCooldownPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {

                        long duration = payload.cooldownDurationMs();
                        whiffCooldownEnd = System.currentTimeMillis() + duration;


                        isCharging = false;
                        localFireLevel = 0f;
                        wasFireCharging = false;
                        fireChargeComplete = false;
                        lastFireLevel = 0f;


                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player != null) {
                            CoopAnimationHandler.stopDapCharge(client.player);
                        }
                    });
                }
        );


        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ImpactFramePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        if (payload.grayscale()) {

                            perfectImpactStartTime = System.currentTimeMillis();
                            perfectImpactActive = true;


                            if (IMPACT1_TEXTURE == null) {
                                IMPACT1_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact1.png");
                                IMPACT2_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact2.png");
                                IMPACT3_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact3.png");
                            }
                        }
                    });
                }
        );
//                  I HATE THIS
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.DapResultPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        onDapResult(payload.x(), payload.y(), payload.z(),
                                payload.player1(), payload.player2(),
                                payload.tier(), payload.perfectHit());
                    });
                }
        );


        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.PerfectDapFreezePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        isPerfectDapFrozen = payload.frozen();
                    });
                }
        );



        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.PerfectDapImpactFramePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        if (!isPerfectDapFrozen) return;
                        perfectDapImpactFrame = 1;
                        perfectDapImpactFrameStartTime = System.currentTimeMillis();
                    });
                }
        );


        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FacingDapImpactPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {

                        if (IMPAC7_TEXTURE == null) {
                            IMPAC7_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impac7.png");
                            IMPAC8_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impac8.png");
                            IMPAC9_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impac9.png");
                        }
                        facingDapImpactActive = true;
                        facingDapImpactStartMs = System.currentTimeMillis();
                    });
                }
        );


        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.HeavenReadyPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player == null) return;


                        if (client.player.getUuid().equals(payload.playerId())) {
                            if (payload.ready()) {

                                isHeavenReady = true;
                                heavenReadyStartTime = System.currentTimeMillis();
                            } else {

                                isHeavenReady = false;
                            }
                        }
                    });
                }
        );



        fireDapComboKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.coopmoves.fire_dap_combo",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.coopmoves"
        ));


        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapWindowPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        fireDapComboWindowStart = System.currentTimeMillis();
                        inFireDapComboWindow = true;
                    });
                }
        );


        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapFreezePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        fireDapFrozenPlayers.put(payload.playerId(), payload.frozen());
                    });
                }
        );


        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapFirstPersonPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();
                        boolean show = payload.showBothHands();

                        if (show) {
                            fireDapFirstPersonPlayers.add(playerId);


                            var client = context.client();
                            if (client.player != null && client.player.getUuid().equals(playerId)) {
                            }
                        } else {
                            fireDapFirstPersonPlayers.remove(playerId);


                            var client = context.client();
                            if (client.player != null && client.player.getUuid().equals(playerId)) {
                            }
                        }
                    });
                }
        );




        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean isKeyPressed = chargedDapKey.isPressed();




            boolean onCooldown = System.currentTimeMillis() < whiffCooldownEnd;


            boolean inHighFive = HighFiveClientHandler.isLocalPlayerInHighFive();


            boolean inBlocking = CoopAnimationHandler.isInBlockingState(client.player.getUuid());


            if (isKeyPressed && !wasKeyPressed) {

                if (isPerfectDapFrozen
                        && !QTEClientHandler.isActive()
                        && !FusionClientHandler.isQTEOpen()
                        && !FusionClientHandler.isGWindowOpen()) {
                    wasKeyPressed = true;
                    return;
                }
            }



            if (isKeyPressed && !wasKeyPressed) {

                if (FusionClientHandler.isQTEOpen()) {
                    FusionClientHandler.handleQTEGPress();
                    wasKeyPressed = true;
                    return;
                }
                if (FusionClientHandler.isGWindowOpen()) {
                    FusionClientHandler.handleGKeyPress();
                    wasKeyPressed = true;
                    return;
                }

                if (QTEClientHandler.isActive()) {
                    QTEClientHandler.handleKeyPress("G");
                    wasKeyPressed = true;
                    return;
                }
            }


            if (isKeyPressed && !wasKeyPressed && !inBlocking) {





                if (inHighFive || HighFiveClientHandler.isInComboWindow()) {
                    wasKeyPressed = true;
                    return;
                } else if (inFaceDapSession) {

                    wasKeyPressed = true;
                    return;
                } else if (isDapBadBlocking()) {

                    wasKeyPressed = true;
                    return;
                } else if (onCooldown) {
                    long remaining = (whiffCooldownEnd - System.currentTimeMillis()) / 100;
                    client.player.sendMessage(Text.literal("§cDap on cooldown! " + (remaining / 10.0) + "s"), true);
                } else if (!client.player.getMainHandStack().isEmpty()) {
                    client.player.sendMessage(Text.literal("§cMain hand must be empty for charged dap!"), true);
                } else {
                    isCharging = true;
                    chargeStartTime = System.currentTimeMillis();
                    localFireLevel = 0f;
                    wasFireCharging = false;
                    fireChargeComplete = false;
                    lastFireLevel = 0f;
                    ClientPlayNetworking.send(new ChargedDapHandler.ChargeStartPayload());


                    CoopAnimationHandler.startDapCharge(client.player);
                }
            }


            if (isCharging && inBlocking) {
                isCharging = false;
                localFireLevel = 0f;
                wasFireCharging = false;
                fireChargeComplete = false;
                lastFireLevel = 0f;

                CoopAnimationHandler.stopDapCharge(client.player);
            }


            if (isCharging && !onCooldown && !inBlocking) {

                if (localFireLevel > 0.05f && !wasFireCharging) {
                    wasFireCharging = true;
                    fireChargeComplete = false;
                    CoopAnimationHandler.startFireDapCharge(client.player);
                }


                if (wasFireCharging && localFireLevel >= 0.99f && !fireChargeComplete) {
                    fireChargeComplete = true;
                    CoopAnimationHandler.playFireDapChargeIdle(client.player);
                }


                if (wasFireCharging && localFireLevel < 0.05f) {
                    wasFireCharging = false;
                    fireChargeComplete = false;
                    CoopAnimationHandler.playDapChargeIdle(client.player);
                }

                lastFireLevel = localFireLevel;
            }


            if (isCharging && onCooldown) {
                isCharging = false;
                localFireLevel = 0f;
                wasFireCharging = false;
                fireChargeComplete = false;
                lastFireLevel = 0f;
                CoopAnimationHandler.stopDapCharge(client.player);
            }

            if (!isKeyPressed && wasKeyPressed && isCharging) {
                isCharging = false;
                localFireLevel = 0f;
                wasFireCharging = false;
                fireChargeComplete = false;
                lastFireLevel = 0f;
                ClientPlayNetworking.send(new ChargedDapHandler.ChargeReleasePayload());




            }




            boolean fireDapJKeyPressed = fireDapComboKey.isPressed();


            if (inFireDapComboWindow && fireDapJKeyPressed && !fireDapWasKeyPressed) {
                ClientPlayNetworking.send(new ChargedDapHandler.FireDapJPressPayload());
                inFireDapComboWindow = false;
            }


            if (inFireDapComboWindow && System.currentTimeMillis() - fireDapComboWindowStart > FIRE_DAP_COMBO_WINDOW_MS) {
                inFireDapComboWindow = false;
            }


            if (isKeyPressed && inFaceDapSession) {
                ClientPlayNetworking.send(new com.cooptest.NormalFacingDapHandler.DapLoopHoldPayload());
            }

            fireDapWasKeyPressed = fireDapJKeyPressed;


            wasKeyPressed = isKeyPressed;
        });

        HudRenderCallback.EVENT.register(ChargedDapClientHandler::renderHUD);
    }

    private static void onDapResult(double x, double y, double z, UUID player1, UUID player2, int tier, boolean perfectHit) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        UUID myId = client.player.getUuid();
        boolean iAmInDap = myId.equals(player1) || myId.equals(player2);


        isCharging = false;
        localFireLevel = 0f;
        wasFireCharging = false;
        fireChargeComplete = false;
        lastFireLevel = 0f;
        otherPlayerCharges.clear();
        otherPlayerFire.clear();
        otherPlayerCharging.clear();

        if (iAmInDap) {



            CoopAnimationHandler.stopDapChargeLocalOnly(client.player);






            flashStartTime = System.currentTimeMillis();
            resultTier = tier;
            resultPerfect = perfectHit;
        }


        spawnTierParticles(client, x, y, z, tier, perfectHit);
    }

    private static void spawnTierParticles(MinecraftClient client, double x, double y, double z, int tier, boolean perfect) {
        if (client.world == null) return;

        net.minecraft.particle.ParticleEffect particle;
        int particleCount;

        switch (tier) {
            case 0 -> { particle = net.minecraft.particle.ParticleTypes.SMOKE; particleCount = 5; }
            case 1 -> { particle = net.minecraft.particle.ParticleTypes.CRIT; particleCount = 10; }
            case 2 -> { particle = net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER; particleCount = 15; }
            case 3 -> { particle = net.minecraft.particle.ParticleTypes.ENCHANT; particleCount = 20; }
            case 4 -> { particle = net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING; particleCount = 25; }
            case 5 -> { particle = net.minecraft.particle.ParticleTypes.FLAME; particleCount = 30; }
            default -> { particle = net.minecraft.particle.ParticleTypes.CRIT; particleCount = 5; }
        }


        for (int i = 0; i < particleCount; i++) {
            double offsetX = (Math.random() - 0.5) * 0.5;
            double offsetY = (Math.random() - 0.5) * 0.5;
            double offsetZ = (Math.random() - 0.5) * 0.5;
            double velX = (Math.random() - 0.5) * 0.3;
            double velY = Math.random() * 0.2;
            double velZ = (Math.random() - 0.5) * 0.3;

            client.world.addParticle(particle, x + offsetX, y + offsetY, z + offsetZ, velX, velY, velZ);
        }


        if (perfect) {
            for (int i = 0; i < 8; i++) {
                double angle = (i / 8.0) * Math.PI * 2;
                double offsetX = Math.cos(angle) * 0.3;
                double offsetZ = Math.sin(angle) * 0.3;
                client.world.addParticle(net.minecraft.particle.ParticleTypes.ENCHANT,
                        x + offsetX, y + 0.5, z + offsetZ, 0, 0.1, 0);
            }
        }
    }

    private static void renderHUD(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();


        long timeSinceFlash = System.currentTimeMillis() - flashStartTime;
        if (timeSinceFlash < FLASH_DURATION) {
            float progress = (float) timeSinceFlash / FLASH_DURATION;


            int baseAlpha = switch (resultTier) {
                case 0 -> 30;
                case 1 -> 40;
                case 2 -> 50;
                case 3 -> 60;
                case 4 -> 80;
                case 5 -> 100;
                default -> 30;
            };

            if (resultPerfect) baseAlpha = Math.min(baseAlpha + 40, 140);

            int alpha = (int) ((1.0f - progress) * baseAlpha);

            int flashColor = switch (resultTier) {
                case 0 -> (alpha << 24) | 0x888888;
                case 1 -> (alpha << 24) | 0xFFFF00;
                case 2 -> (alpha << 24) | 0x00FF00;
                case 3 -> (alpha << 24) | 0xFFAA00;
                case 4 -> (alpha << 24) | 0xFF00FF;
                case 5 -> (alpha << 24) | 0xFF4400;
                default -> (alpha << 24) | 0xFFFFFF;
            };

            context.fill(0, 0, screenWidth, screenHeight, flashColor);
        }


        if (perfectImpactActive) {
            long elapsed = System.currentTimeMillis() - perfectImpactStartTime;

            if (elapsed < WHITE_FADE_DURATION) {

                float fadeProgress = (float) elapsed / WHITE_FADE_DURATION;
                int alpha = (int) (255 * fadeProgress);
                context.fill(0, 0, screenWidth, screenHeight, (alpha << 24) | 0xFFFFFF);
            } else if (elapsed < IMPACT1_END) {

                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                context.drawTexture(IMPACT1_TEXTURE, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
            } else if (elapsed < IMPACT2_END) {

                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                context.drawTexture(IMPACT2_TEXTURE, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
            } else if (elapsed < IMPACT3_END) {

                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                context.drawTexture(IMPACT3_TEXTURE, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
            } else {

                perfectImpactActive = false;
            }
        }


        if (perfectDapImpactFrame > 0) {
            long elapsed = System.currentTimeMillis() - perfectDapImpactFrameStartTime;


            if (IMPACT3_TEXTURE == null) {
                IMPACT1_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact1.png");
                IMPACT2_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact2.png");
                IMPACT3_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact3.png");
            }


            if (PERFECT_FRAME0_TEXTURE == null) {
                PERFECT_FRAME0_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/frame0.png");
                PERFECT_FRAME1_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/frame1.png");
                PERFECT_FRAME2_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/frame2.png");
                PERFECT_FRAME3_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/frame3.png");
            }




            net.minecraft.util.Identifier frameTexture;
            if (elapsed < 33) {
                frameTexture = PERFECT_FRAME0_TEXTURE;
            } else if (elapsed < 66) {
                frameTexture = PERFECT_FRAME1_TEXTURE;
            } else if (elapsed < 100) {
                frameTexture = PERFECT_FRAME2_TEXTURE;
            } else if (elapsed < 133) {
                frameTexture = PERFECT_FRAME0_TEXTURE;
            } else if (elapsed < 166) {
                frameTexture = PERFECT_FRAME3_TEXTURE;
            } else if (elapsed < 200) {
                frameTexture = PERFECT_FRAME0_TEXTURE;
            } else {

                perfectDapImpactFrame = 0;
                return;
            }


            if (frameTexture == null) {
                perfectDapImpactFrame = 0;
                return;
            }


            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.drawTexture(frameTexture, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
        }







        if (facingDapImpactActive) {
            long elapsed = System.currentTimeMillis() - facingDapImpactStartMs;
            net.minecraft.util.Identifier facingFrame;

            if (elapsed < 50) {
                facingFrame = IMPAC7_TEXTURE;
            } else if (elapsed < 100) {
                facingFrame = IMPAC8_TEXTURE;
            } else if (elapsed < 150) {
                facingFrame = IMPAC9_TEXTURE;
            } else if (elapsed < 200) {

                facingFrame = PERFECT_FRAME0_TEXTURE;
            } else {
                facingDapImpactActive = false;
                facingFrame = null;
            }

            if (facingFrame != null) {
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                context.drawTexture(facingFrame, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
            }
        }






        if (dropKickImpactActive) {
            long dke = System.currentTimeMillis() - dropKickImpactStartMs;
            if (dke >= DK_FADE_OUT) {
                dropKickImpactActive = false;
            } else {

                if (IMPACT1_TEXTURE == null) {
                    IMPACT1_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact1.png");
                    IMPACT2_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact2.png");
                    IMPACT3_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact3.png");
                }
                net.minecraft.util.Identifier dkTex;
                float dkAlpha;
                if (dke < DK_FADE_IN) {

                    dkTex   = IMPACT1_TEXTURE;
                    dkAlpha = (float) dke / DK_FADE_IN;
                } else if (dke < DK_FRAME1) {
                    dkTex   = IMPACT1_TEXTURE;
                    dkAlpha = 1.0f;
                } else if (dke < DK_FRAME2) {
                    dkTex   = IMPACT2_TEXTURE;
                    dkAlpha = 1.0f;
                } else if (dke < DK_FRAME3) {
                    dkTex   = IMPACT3_TEXTURE;
                    dkAlpha = 1.0f;
                } else {

                    dkTex   = IMPACT3_TEXTURE;
                    dkAlpha = 1.0f - (float)(dke - DK_FRAME3) / (DK_FADE_OUT - DK_FRAME3);
                }
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, dkAlpha);
                context.drawTexture(dkTex, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            }
        }


        boolean onCooldown = System.currentTimeMillis() < whiffCooldownEnd;
        if (onCooldown && !isCharging) {
            long remaining = whiffCooldownEnd - System.currentTimeMillis();
            float cooldownProgress = remaining / 800f;

            int barWidth = 40;
            int barHeight = 3;
            int barX = (screenWidth - barWidth) / 2;
            int barY = screenHeight / 2 + 20;


            context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x44000000);
            int fillWidth = (int) (barWidth * cooldownProgress);
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xBBFF0000);
        }


        if (inFireDapComboWindow) {
            long elapsed = System.currentTimeMillis() - fireDapComboWindowStart;
            long remaining = FIRE_DAP_COMBO_WINDOW_MS - elapsed;

            if (remaining > 0) {
                String text = "§c§l🔥 PRESS J! 🔥";
                int textWidth = client.textRenderer.getWidth(text);
                int textX = (screenWidth - textWidth) / 2;
                int textY = screenHeight / 2 + 10;


                float pulse = (float) (Math.sin(System.currentTimeMillis() / 80.0) * 0.4 + 0.6);
                int alpha = (int) (pulse * 255);


                float timeProgress = (float) elapsed / FIRE_DAP_COMBO_WINDOW_MS;
                int color = timeProgress < 0.5f ? (alpha << 24) | 0xFF8800 : (alpha << 24) | 0xFF0000;

                context.drawText(client.textRenderer, text, textX, textY, color, true);


                int barWidth = 100;
                int barHeight = 3;
                int barX = (screenWidth - barWidth) / 2;
                int barY = textY + 12;

                context.fill(barX, barY, barX + barWidth, barY + barHeight, 0x80000000);

                int fillWidth = (int) (barWidth * (1.0f - timeProgress));
                int barColor = timeProgress < 0.5f ? 0xFFFF8800 : 0xFFFF0000;
                context.fill(barX, barY, barX + fillWidth, barY + barHeight, barColor);
            }
        }






        if (isCharging && !onCooldown) {
            long elapsed = System.currentTimeMillis() - chargeStartTime;
            float chargePercent = Math.min(1.0f, (float) elapsed / CHARGE_TIME_MS);


            float myFire = com.cooptest.CoopMovesConfig.get().enableFireDap ? localFireLevel : 0f;


            int barWidth = 40;
            int barHeight = 3;
            int barX = (screenWidth - barWidth) / 2;
            int barY = screenHeight / 2 + 20;


            context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x44000000);


            if (myFire > 0.05f) {

                int greenWidth = (int) (barWidth * chargePercent);
                context.fill(barX, barY, barX + greenWidth, barY + barHeight, 0xBB00FF00);


                int redWidth = (int) (barWidth * myFire);


                int shakeX = 0;
                int shakeY = 0;

                if (isHeavenReady) {

                    long timeSinceReady = System.currentTimeMillis() - heavenReadyStartTime;


                    shakeX = (int) ((Math.random() - 0.5) * 8);
                    shakeY = (int) ((Math.random() - 0.5) * 6);


                    if (timeSinceReady % 500 < 250) {

                        context.fill(barX + shakeX, barY + shakeY,
                                barX + redWidth + shakeX, barY + barHeight + shakeY,
                                0xFFFF00FF);
                    } else {

                        context.fill(barX + shakeX, barY + shakeY,
                                barX + redWidth + shakeX, barY + barHeight + shakeY,
                                0xDDFF2200);
                    }



                    context.fill(barX - 5 + shakeX, barY + 1 + shakeY,
                            barX + barWidth + 5 + shakeX, barY + 2 + shakeY,
                            0xFFFFFFFF);


                    for (int i = 0; i < barWidth; i++) {
                        int crackY = barY + (i % 2) + shakeY;
                        context.fill(barX + i + shakeX, crackY,
                                barX + i + 1 + shakeX, crackY + 1,
                                0x88FFFFFF);
                    }


                    if (timeSinceReady % 100 < 50) {

                        context.fill(barX - 2 + shakeX, barY - 3 + shakeY,
                                barX - 1 + shakeX, barY - 2 + shakeY,
                                0xFFFFDD00);
                        context.fill(barX + barWidth + 1 + shakeX, barY - 3 + shakeY,
                                barX + barWidth + 2 + shakeX, barY - 2 + shakeY,
                                0xFFFFDD00);
                    }

                } else if (myFire >= 0.99f) {

                    shakeX = (int) ((Math.random() - 0.5) * 4);
                    shakeY = (int) ((Math.random() - 0.5) * 2);


                    context.fill(barX + shakeX, barY + shakeY,
                            barX + redWidth + shakeX, barY + barHeight + shakeY,
                            0xDDFF2200);
                } else {

                    context.fill(barX, barY, barX + redWidth, barY + barHeight, 0xDDFF2200);
                }
            } else {

                int fillWidth = (int) (barWidth * chargePercent);
                int fillColor;
                if (chargePercent >= 0.99f) {
                    fillColor = 0xBB00FF00;
                } else {
                    fillColor = 0xBBFFAA00;
                }
                context.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
            }


            int partnerY = barY + 8;
            for (Map.Entry<UUID, Float> entry : otherPlayerCharges.entrySet()) {
                if (!otherPlayerCharging.getOrDefault(entry.getKey(), false)) continue;
                if (entry.getKey().equals(client.player.getUuid())) continue;


                boolean inRange = false;
                if (client.world != null) {
                    for (var player : client.world.getPlayers()) {
                        if (player.getUuid().equals(entry.getKey())) {
                            if (client.player.distanceTo(player) <= 20.0) {
                                inRange = true;
                            }
                            break;
                        }
                    }
                }
                if (!inRange) continue;

                float partnerCharge = entry.getValue();
                float partnerFire = otherPlayerFire.getOrDefault(entry.getKey(), 0f);

                int pBarWidth = 30;
                int pBarHeight = 2;
                int pBarX = (screenWidth - pBarWidth) / 2;

                context.fill(pBarX - 1, partnerY - 1, pBarX + pBarWidth + 1, partnerY + pBarHeight + 1, 0x33000000);

                int pFillWidth = (int) (pBarWidth * partnerCharge);
                int pFillColor = partnerFire > 0.1f ? 0xAAFF4400 : (partnerCharge >= 0.99f ? 0xAA00FF00 : 0xAAFFAA00);
                context.fill(pBarX, partnerY, pBarX + pFillWidth, partnerY + pBarHeight, pFillColor);

                partnerY += 6;
            }
        }


        QTEClientHandler.renderHUD(context, screenWidth, screenHeight);

    }

    public static boolean isCurrentlyCharging() {
        return isCharging;
    }

    public static net.minecraft.client.option.KeyBinding getChargeKey() { return chargedDapKey; }
    public static net.minecraft.client.option.KeyBinding getComboKey()  { return fireDapComboKey; }

    public static float getChargePercent() {
        if (!isCharging) return 0f;
        long elapsed = System.currentTimeMillis() - chargeStartTime;
        return Math.min(1.0f, (float) elapsed / CHARGE_TIME_MS);
    }

    public static float getFireLevel() {
        return localFireLevel;
    }

    public static boolean isPlayerCharging(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(playerId)) {
            return isCharging;
        }
        return otherPlayerCharging.getOrDefault(playerId, false);
    }

    public static float getPlayerChargePercent(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(playerId)) {
            return getChargePercent();
        }
        return otherPlayerCharges.getOrDefault(playerId, 0f);
    }

    public static float getPlayerFireLevel(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(playerId)) {
            return localFireLevel;
        }
        return otherPlayerFire.getOrDefault(playerId, 0f);
    }

    public static boolean isOnWhiffCooldown() {
        return System.currentTimeMillis() < whiffCooldownEnd;
    }

    public static boolean isImpactFrameActive() {
        return perfectImpactActive;
    }

    public static boolean isLocalPlayerPerfectDapFrozen() {
        return isPerfectDapFrozen;
    }



    public static boolean isLocalPlayerFireDapFrozen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return fireDapFrozenPlayers.getOrDefault(client.player.getUuid(), false);
    }

    public static boolean shouldShowFireDapFirstPerson() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return fireDapFirstPersonPlayers.contains(client.player.getUuid());
    }




    public static boolean isInQTEWindow() {
        return QTEClientHandler.isActive();
    }

    public static boolean isPlayerFrozen() {
        return isPerfectDapFrozen;
    }

    public static String getQTEExpectedButton() {
        return QTEClientHandler.getExpectedButton();
    }

    public static long getQTEWindowStart() {
        return QTEClientHandler.getWindowStart();
    }

    public static long getQTEWindowEnd() {
        return QTEClientHandler.getWindowEnd();
    }

    public static int getQTEStage() {
        return QTEClientHandler.getStage();
    }


    public static void cleanup(UUID playerId) {
        otherPlayerCharges.remove(playerId);
        otherPlayerFire.remove(playerId);
        otherPlayerCharging.remove(playerId);

        fireDapFrozenPlayers.remove(playerId);
        fireDapFirstPersonPlayers.remove(playerId);
    }
}