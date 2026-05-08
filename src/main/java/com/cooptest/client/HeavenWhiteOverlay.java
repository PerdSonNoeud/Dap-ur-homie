package com.cooptest.client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundCategory;
public class HeavenWhiteOverlay {
    private static boolean active = false;
    private static float opacity = 0.0f;
    private static long phaseStartTime = 0;
    private static HeavenPhase currentPhase = HeavenPhase.NONE;
    private static float originalMasterVolume = 1.0f;
    private static float originalMusicVolume = 1.0f;
    private static boolean soundsMuted = false;
    public enum HeavenPhase {
        NONE,
        FULL_WHITE,
        FADE_TO_THIRTY,
        HEAVEN,
        FADE_OUT,
        FADE_TO_NORMAL,
        DONE
    }
    public static void start() {
        active = true;
        opacity = 1.0f;
        currentPhase = HeavenPhase.FULL_WHITE;
        phaseStartTime = System.currentTimeMillis();
        muteSounds();
    }
    public static void stop() {
        active = false;
        opacity = 0.0f;
        currentPhase = HeavenPhase.NONE;
        unmuteSounds();
    }
    public static void render(DrawContext context, float tickDelta) {
        if (!active || opacity <= 0.0f) return;
        int alpha = (int)(opacity * 255);
        int color = (alpha << 24) | 0xFFFFFF;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        context.fill(0, 0, screenWidth, screenHeight, color);
    }
    public static void tick() {
        if (!active) return;
        long elapsed = System.currentTimeMillis() - phaseStartTime;
        switch (currentPhase) {
            case FULL_WHITE -> {
                opacity = 1.0f;
                if (elapsed >= 3000) {
                    currentPhase = HeavenPhase.FADE_TO_THIRTY;
                    phaseStartTime = System.currentTimeMillis();
                    System.out.println("[Heaven Overlay] Fading to 30%");
                }
            }
            case FADE_TO_THIRTY -> {
                float progress = Math.min(elapsed / 500.0f, 1.0f);
                opacity = 1.0f - (progress * 0.7f);
                if (progress >= 1.0f) {
                    opacity = 0.3f;
                    currentPhase = HeavenPhase.HEAVEN;
                    phaseStartTime = System.currentTimeMillis();
                }
            }
            case HEAVEN -> {
                opacity = 0.3f;
                if (elapsed >= 6000) {
                    currentPhase = HeavenPhase.FADE_OUT;
                    phaseStartTime = System.currentTimeMillis();
                }
            }
            case FADE_OUT -> {
                float progress = Math.min(elapsed / 2000.0f, 1.0f);
                opacity = 0.3f + (progress * 0.7f);
                if (progress >= 1.0f) {
                    opacity = 1.0f;
                    currentPhase = HeavenPhase.FADE_TO_NORMAL;
                    phaseStartTime = System.currentTimeMillis();
                }
            }
            case FADE_TO_NORMAL -> {
                float progress = Math.min(elapsed / 5000.0f, 1.0f);
                opacity = 1.0f - progress;
                if (progress >= 1.0f) {
                    opacity = 0.0f;
                    currentPhase = HeavenPhase.DONE;
                }
            }
            case DONE -> {
                opacity = 0.0f;
            }
        }
    }
    private static void muteSounds() {
        if (soundsMuted) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null && client.getSoundManager() != null) {
            originalMasterVolume = client.options.getSoundVolumeOption(SoundCategory.MASTER).getValue().floatValue();
            originalMusicVolume = client.options.getSoundVolumeOption(SoundCategory.MUSIC).getValue().floatValue();
            client.getSoundManager().stopAll();
            client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
            soundsMuted = true;
        }
    }
    private static void unmuteSounds() {
        if (!soundsMuted) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue((double)originalMasterVolume);
            client.options.getSoundVolumeOption(SoundCategory.MUSIC).setValue((double)originalMusicVolume);
            soundsMuted = false;
        }
    }
    public static boolean isActive() {
        return active;
    }
    public static HeavenPhase getCurrentPhase() {
        return currentPhase;
    }
    public static float getOpacity() {
        return opacity;
    }
}