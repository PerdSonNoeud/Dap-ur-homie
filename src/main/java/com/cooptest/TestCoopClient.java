package com.cooptest;
import com.cooptest.client.*;
import net.fabricmc.api.ClientModInitializer;
public class TestCoopClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PoseNetworking.registerClientReceiver();
        GrabClientNetworking.register();
        GrabInputHandler.register();
        ClientCrouchPoseHandler.register();
        GrabClientEffects.register();
        ThrowPowerHUD.register();
        TrajectoryRenderer.register();
        HighFiveClientHandler.register();
        HighFiveHugHandler.registerClientPayloads();
        HighFiveQTEHugHandler.registerClientPayloads();
        ChargedDapClientHandler.register();
        PushClientHandler.register();
        CatchClientHandler.register();
        MahitoClientHandler.register();
        FallDapClientHandler.register();
        DapHoldClientHandler.register();
        MarioJumpClientHandler.register();
        HugClientHandler.register();
        HeavenDapClientHandler.register();
        QTEClientHandler.registerReceivers();
        ClapClientHandler.register();
        DapFusionHandler.registerClientPayloads();
        FusionClientHandler.register();
        MeteorStrikeHandler.registerClientPayloads();
        MeteorStrikeClientHandler.register();
        KickClientHandler.register();
        SpinClientHandler.register();
        GroundPoundClientHandler.register();
        com.cooptest.client.SlapClientHandler.register();
        com.cooptest.client.HuddleClientHandler.register();
        com.cooptest.client.CoopAnimationHandler.register();
        com.cooptest.client.SitClientHandler.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.cooptest.NormalFacingDapHandler.FaceDapSessionPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        com.cooptest.client.ChargedDapClientHandler.setInFaceDapSession(payload.active())));
    }
}