package com.cooptest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CoopMovesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("coopmoves.json").toFile();

    private static CoopMovesConfig INSTANCE;

    public boolean noGriefMode = false;
    public boolean easyFusionTest = false;
    public boolean debugMode = false;

    public boolean enableGrab = true;
    public boolean enableSpin = true;
    public boolean enableGroundPound = true;
    public boolean enableThrow = true;
    public boolean enableYeet = true;
    public boolean enableGrabThrow = true;
    public boolean allowForcedPickup = true;
    public boolean grabBreaksBlocks = false;
    public float throwMinPower = 0.5f;
    public float throwMaxPower = 1.5f;
    public int grabCooldownTicks = 20;

    public boolean enableDap = true;
    public boolean enableDapCombo = true;
    public boolean enableDapHold = true;
    public boolean enableDapFirstPerson = true;
    public boolean enableFallDap = true;
    public boolean enableFireDap = true;
    public boolean enableHeavenDap = true;
    public boolean dapCausesExplosion = false;
    public boolean enablePerfectLegendary = true;
    public long dapChargeWindowMs = 250;
    public long dapReleaseWindowMs = 500;
    public long dapPerfectWindowMs = 85;
    public long dapCooldownMs = 1500;
    public long dapWhiffCooldownMs = 800;
    public long dapFireDelayMs = 2000;
    public long dapFireBuildTimeMs = 2000;
    public double tier1Knockback = 0.3;
    public double tier2Knockback = 0.6;
    public double tier3Knockback = 1.0;
    public double tier4Knockback = 2.0;
    public int fireChargeDelayMs = 2000;
    public int fireBuildTimeMs = 2000;
    public float fireExplosionPower = 3.0f;
    public int fireExplosionRadius = 50;
    public float fireKnockbackMultiplier = 15.0f;
    public boolean fireBreaksBlocks = false;
    public double perfectLegendaryMinSpeed = 10.0;
    public int perfectLegendaryLevitationSec = 10;
    public int perfectLegendarySlowFallSec = 30;
    public boolean perfectLegendaryKillsOnFail = true;
    public boolean perfectLegendaryGivesEffects = true;

    public boolean enableHighFive = true;
    public boolean enableHighFiveHug = true;
    public boolean enableHighFiveCombo = true;
    public boolean enableHighFiveFirstPerson = true;
    public int highFiveTimeoutMs = 2500;
    public int highFiveLeftHangingCooldownMs = 1500;
    public int highFiveComboWindowMs = 400;
    public boolean highFiveComboAura = true;
    public boolean highFiveComboBeam = true;
    public boolean highFiveCausesLightning = false;

    public boolean enableHug = true;
    public boolean enableHugFirstPerson = true;
    public boolean hugHealsPlayers = true;
    public float hugHealAmount = 2.0f;
    public int hugDurationSec = 10;

    public boolean enablePush = true;
    public boolean enablePushFirstPerson = true;
    public boolean pushCausesParticles = true;
    public boolean pushIntoOrbit = false;
    public float pushDistance = 2.5f;
    public int pushCooldownMs = 500;

    public boolean enableCatch = true;
    public boolean catchNegatesFallDamage = true;
    public int catchWindowMs = 500;
    public int catchCooldownMs = 1000;

    public boolean enableMarioJump = true;
    public boolean marioJumpSound = true;
    public boolean marioJumpParticles = true;
    public float marioJumpPower = 2.0f;

    public boolean enableKick = true;
    public boolean enableDropKick = true;

    public boolean enableSlap = true;

    public boolean enableClap = true;

    public boolean enableMahito = true;
    public boolean mahitoTransformsPlayer = true;
    public int mahitoCurseDurationSec = 60;

    public boolean enableShieldMode = true;
    public boolean shieldBlocksProjectiles = true;
    public float shieldDamageReduction = 0.8f;
    public int shieldSwapCooldownMs = 3000;

    public boolean enableSquash = true;
    public boolean squashDropsItems = true;
    public boolean squashMakesFlat = true;
    public float squashDamage = 10.0f;
    public int squashDurationSec = 25;
    public int squashNauseaSec = 15;

    public boolean enableFirstPersonAnimations = true;
    public boolean firstPersonSmoothEndings = true;
    public float firstPersonArmForwardOffset = 3.0f;
    public float firstPersonArmHeightOffset = 2.0f;

    public boolean showDapChargeBar = true;
    public boolean showFireChargeBar = true;
    public boolean announcePerectLegendaryInChat = true;
    public boolean announceMahitoInChat = true;
    public boolean announceComboInChat = true;

    public boolean enableParticles = true;
    public float particleDensity = 1.0f;
    public float dapSoundVolume = 1.0f;
    public float explosionSoundVolume = 1.5f;
    public float epicDapSoundVolume = 2.0f;
    public float highFiveSoundVolume = 1.0f;
    public float pushSoundVolume = 1.0f;
    public boolean muteAllSounds = false;

    public static CoopMovesConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, CoopMovesConfig.class);
                if (INSTANCE == null) INSTANCE = new CoopMovesConfig();
            } catch (IOException e) {
                System.err.println("[CoopMoves] Failed to load config: " + e.getMessage());
                INSTANCE = new CoopMovesConfig();
            }
        } else {
            INSTANCE = new CoopMovesConfig();
            save();
        }
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            System.err.println("[CoopMoves] Failed to save config: " + e.getMessage());
        }
    }

    public static void reload() {
        INSTANCE = null;
        load();
    }
}