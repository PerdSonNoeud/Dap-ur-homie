package com.cooptest.client;

public class CoopImpactHandler {

    public static volatile boolean playing    = false;
    public static volatile boolean whiteFrame = true;
    public static volatile boolean renderingPlayer = false;

    private static long startMs       = 0;
    private static int  frameCount    = 6;
    private static long frameDurationMs = 33L;

    public static void start(int frames, long durationEach) {
        frameCount      = frames;
        frameDurationMs = durationEach;
        startMs         = System.currentTimeMillis();
        whiteFrame      = true;
        playing         = true;
    }

    public static void tick() {
        if (!playing) return;
        long elapsed   = System.currentTimeMillis() - startMs;
        int  frameIdx  = (int)(elapsed / frameDurationMs);
        if (frameIdx >= frameCount) {
            playing    = false;
            whiteFrame = true;
            return;
        }
        whiteFrame = (frameIdx % 2) == 0;
    }
}