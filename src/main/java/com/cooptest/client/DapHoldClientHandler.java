package com.cooptest.client;

import com.cooptest.DapHoldHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DapHoldClientHandler {


    private static int myRole       = -1;
    private static UUID myPartnerId = null;
    private static boolean windowOpen = false;
    private static boolean looping    = false;


    private static boolean isGroupJoiner  = false;
    private static UUID    groupHfId      = null;
    private static int     groupMemberCount = 0;


    private static boolean animationLocked = false;
    private static final Set<UUID> lockedPlayers = new HashSet<>();


    private static final Set<String> startAnimPlayed = new HashSet<>();
    private static final Set<UUID> loopAnimPlayed  = new HashSet<>();


    private static final Map<UUID, Boolean> freezeMap = new HashMap<>();


    private static boolean jWasHeld = false;

    private static boolean gWasHeld = false;

    public static void register() {


        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldStartPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    MinecraftClient client = ctx.client();
                    if (client.player == null || client.world == null) return;

                    UUID localId = client.player.getUuid();
                    UUID payloadId = payload.playerId();
                    UUID partnerId = payload.partnerId();

                    System.out.println("[DapHold Client] Received payload for " + payloadId + " partner=" + partnerId + " role=" + payload.role());


                    lockedPlayers.add(payloadId);
                    lockedPlayers.add(partnerId);


                    if (payloadId.equals(localId)) {

                        if (myRole == -1) {
                            startAnimPlayed.clear();
                            loopAnimPlayed.clear();
                            System.out.println("[DapHold Client] Cleared animation tracking for new interaction");
                        }

                        myRole      = payload.role();
                        myPartnerId = partnerId;
                        windowOpen  = false;
                        looping     = false;
                        animationLocked = true;

                        System.out.println("[DapHold Client] Starting! My role=" + myRole);
                    }



                    String animKey = payloadId.toString() + "-" + payload.role();
                    if (!startAnimPlayed.contains(animKey)) {
                        startAnimPlayed.add(animKey);

                        PlayerEntity targetPlayer = client.world.getPlayerByUuid(payloadId);
                        if (targetPlayer != null) {
                            CoopAnimationHandler.playDapHoldStart(targetPlayer, payload.role());
                            System.out.println("[DapHold Client] ✓ Playing anim for " + payloadId + " role=" + payload.role());
                        }
                    } else {
                        System.out.println("[DapHold Client] Skipping - already played " + animKey);
                    }
                }));


        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldWindowPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (myRole == -1) return;
                    windowOpen = payload.open();
                }));


        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldLoopPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (myRole == -1) return;
                    MinecraftClient client = ctx.client();
                    if (client.player == null || client.world == null) return;

                    looping = payload.looping();

                    if (looping) {
                        System.out.println("[DapHold Client] 🔥 DAPPING LOOP! 🔥");


                        CoopAnimationHandler.playDapHoldDapping(client.player);

                        if (myPartnerId != null) {
                            PlayerEntity partner = client.world.getPlayerByUuid(myPartnerId);
                            if (partner != null) {
                                CoopAnimationHandler.playDapHoldDapping(partner);
                            }
                        }
                    }
                }));


        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldEndPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (myRole == -1) return;
                    MinecraftClient client = ctx.client();
                    if (client.player == null || client.world == null) return;

                    boolean wasLooping = payload.wasLooping();

                    if (wasLooping) {

                        CoopAnimationHandler.playDapHoldEnd(client.player);

                        if (myPartnerId != null) {
                            PlayerEntity partner = client.world.getPlayerByUuid(myPartnerId);
                            if (partner != null) {
                                CoopAnimationHandler.playDapHoldEnd(partner);
                            }
                        }
                    }


                    new Thread(() -> {
                        try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
                        client.execute(() -> {
                            FirstPersonAnimationTest.stop();


                            UUID localId = client.player != null ? client.player.getUuid() : null;
                            if (localId != null) {
                                lockedPlayers.remove(localId);
                                if (myPartnerId != null) {
                                    lockedPlayers.remove(myPartnerId);
                                }
                            }

                            animationLocked = false;
                            myRole       = -1;
                            myPartnerId  = null;
                            looping      = false;
                            windowOpen   = false;
                            jWasHeld     = false;

                            isGroupJoiner  = false;
                            groupHfId      = null;
                            groupMemberCount = 0;

                            startAnimPlayed.clear();
                            loopAnimPlayed.clear();
                        });
                    }).start();
                }));


        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldFreezePayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    freezeMap.put(payload.playerId(), payload.frozen());
                }));


        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.GroupJoinedPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    MinecraftClient client = ctx.client();
                    if (client.player == null || client.world == null) return;
                    UUID localId = client.player.getUuid();
                    groupMemberCount = payload.memberCount();


                    if (payload.joinerId().equals(localId)) {
                        isGroupJoiner = true;
                        groupHfId     = payload.hfId();
                        animationLocked = true;
                        lockedPlayers.add(localId);


                        CoopAnimationHandler.playDapHoldStart(client.player, 0);
                    }


                    PlayerEntity joinerEntity = client.world.getPlayerByUuid(payload.joinerId());
                    if (joinerEntity != null && !payload.joinerId().equals(localId)) {
                        CoopAnimationHandler.playDapHoldStart(joinerEntity, 0);
                    }
                }));


        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.GroupResultPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    MinecraftClient client = ctx.client();
                    if (client.player == null) return;

                    if (isGroupJoiner) {

                        new Thread(() -> {
                            try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
                            client.execute(() -> {
                                FirstPersonAnimationTest.stop();
                                if (client.player != null) {
                                    lockedPlayers.remove(client.player.getUuid());
                                }
                                animationLocked = false;
                                isGroupJoiner   = false;
                                groupHfId       = null;
                                groupMemberCount = 0;
                                jWasHeld        = false;
                            });
                        }).start();
                    }
                }));


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean jHeld = ChargedDapClientHandler.isFireDapJKeyHeld();

            boolean gHeld = ChargedDapClientHandler.getChargeKey() != null
                    && ChargedDapClientHandler.getChargeKey().isPressed();


            if (myRole == -1 && !isGroupJoiner) {

                if (!gHeld && gWasHeld) {
                    ClientPlayNetworking.send(new DapHoldHandler.GroupJoinPayload());
                }
                gWasHeld = gHeld;

                jWasHeld = false;
                return;
            }


            if (isGroupJoiner) {
                if (jHeld) {
                    ClientPlayNetworking.send(new DapHoldHandler.DapHoldJHoldPayload());
                    jWasHeld = true;
                } else if (jWasHeld) {
                    ClientPlayNetworking.send(new DapHoldHandler.DapHoldJReleasePayload());
                    jWasHeld = false;
                }
                gWasHeld = gHeld;
                return;
            }


            if (!windowOpen && !looping) {
                jWasHeld = false;
                gWasHeld = gHeld;
                return;
            }

            if (jHeld) {
                ClientPlayNetworking.send(new DapHoldHandler.DapHoldJHoldPayload());
                jWasHeld = true;
            } else if (jWasHeld) {
                ClientPlayNetworking.send(new DapHoldHandler.DapHoldJReleasePayload());
                jWasHeld = false;
            }
            gWasHeld = gHeld;
        });

        System.out.println("[DapHold Client] Registered!");
    }



    public static boolean isLocalPlayerFrozen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return freezeMap.getOrDefault(client.player.getUuid(), false);
    }

    public static boolean isPlayerFrozen(UUID playerId) {
        return freezeMap.getOrDefault(playerId, false);
    }

    public static boolean isAnimationLocked(UUID playerId) {
        return lockedPlayers.contains(playerId);
    }
}