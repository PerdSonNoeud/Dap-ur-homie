package com.cooptest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
public class TestCoop implements ModInitializer {
    @Override
    public void onInitialize() {
        try {
            CoopMovesConfig.load();
            CoopMovesConfig cfg = CoopMovesConfig.get();
            ModSounds.register();
            ModEffects.register();
            MahitoItems.register();
            PoseNetworking.registerPayloads();
            NormalFacingDapHandler.registerPayloads();
            SitHandler.registerPayloads();
            PoseNetworking.registerServerReceiver();
            if (cfg.enableGrab) {
                GrabNetworking.registerPayloads();
                GrabNetworking.registerServerReceivers();
                GrabMechanic.ShieldModePayload.register();
                GrabInteractionHandler.register();
                GrabMechanic.registerShieldDamageEvent();
                if (cfg.enableSpin) {
                    SpinHandler.register();
                }
                if (cfg.enableGroundPound) {
                    GroundPoundHandler.register();
                }
            }
            if (cfg.enableHighFive) {
                HighFiveHandler.registerPayloads();
                HighFiveHandler.register();
            }
            if (cfg.enableHighFiveHug) {
                HighFiveHugHandler.registerPayloads();
                HighFiveHugHandler.register();
                HighFiveQTEHugHandler.registerPayloads();
                HighFiveQTEHugHandler.register();
            }
            if (cfg.enableHighFive) {
                HuddleHandler.registerPayloads();
                HuddleHandler.register();
            }
            if (cfg.enableDap) {
                ChargedDapHandler.registerPayloads();
                ChargedDapHandler.register();
                DapSessionManager.register();
                DapFusionHandler.registerPayloads();
                DapFusionHandler.register();
                MeteorStrikeHandler.registerPayloads();
                MeteorStrikeHandler.register();
                PerfectDapComboHandler.register();
                FacingDapHandler.register();
                NormalFacingDapHandler.register();
                SitHandler.register();
            }
            if (cfg.enableDapHold) {
                DapHoldHandler.register();
            }
            if (cfg.enablePush) {
                PushInteractionHandler.registerPayloads();
                PushInteractionHandler.register();
            }
            if (cfg.enableCatch) {
                FallCatchHandler.registerPayloads();
                FallCatchHandler.register();
            }
            if (cfg.enableMarioJump) {
                MarioJumpHandler.registerPayloads();
                MarioJumpHandler.register();
            }
            if (cfg.enableHeavenDap) {
                HeavenDapPayloads.registerPayloads();
            }
            if (cfg.enableFallDap) {
                FallDapHandler.register();
            }
            if (cfg.enableClap) {
                ClapHandler.registerPayloads();
                ClapHandler.register();
            }
            MahitoTrollHandler.register();
            AnimationTickHandler.register();
            LaunchedPlayerTracker.register();
            CarryingSlowdown.register();
            PlayerCleanupHandler.register();
            if (cfg.enableKick) {
                KickHandler.register();
            }
            if (cfg.enableSlap) {
                SlapHandler.register();
            }
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                DebugQTECommand.register(dispatcher);
                dispatcher.register(net.minecraft.server.command.CommandManager
                        .literal("sit").executes(SitHandler::executeSit));
           //     HeavenDapCommand.register(dispatcher);
            });
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if (cfg.enableGrab) {
                    if (cfg.enableGroundPound) GroundPoundHandler.tick(server);
                    GrabMechanic.tick(server);
                    if (cfg.enableSpin) SpinHandler.tick(server);
                }
                if (cfg.enableDap) {
                    ChargedDapHandler.checkTickSpeedRestore(server);
                    QTEManager.tick(server);
                    if (cfg.enableDapCombo) DapComboChain.tick(server);
                }
                if (cfg.enablePush && server.getTicks() % 20 == 0) {
                    PushInteractionHandler.cleanupExpiredImmunity();
                }
            });
        } catch (Exception e) {
            System.err.println("[TestCoop] CRASH during initialization!");
            e.printStackTrace();
            throw new RuntimeException("TestCoop initialization failed", e);
        }
    }
}