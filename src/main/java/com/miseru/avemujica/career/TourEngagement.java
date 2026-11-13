package com.miseru.avemujica.career;

/** Bounded, reproducible social-engagement model. No network or external AI calls. */
public final class TourEngagement {
    private TourEngagement() { }
    public static int likes(int currentFans, int score, int audience, int stageTier, long seed) {
        long mixed = seed ^ (seed >>> 17) ^ (seed << 11);
        int variation = (int) Math.floorMod(mixed, 31L) - 15; // -15..+15 percent
        double quality = .40D + Math.max(0, Math.min(100, score)) / 125D;
        double reach = Math.sqrt(Math.max(0, currentFans)) * 3D
                + Math.max(0, audience) * .55D + 5D + Math.max(0, stageTier) * 9D;
        return (int) Math.max(1D, Math.min(2000000D, Math.round(reach * quality * (1D + variation / 100D))));
    }
    public static int onlineFans(int likes, int score) {
        return Math.min(50, Math.max(1, likes / (score >= 75 ? 12 : 20)));
    }
}
