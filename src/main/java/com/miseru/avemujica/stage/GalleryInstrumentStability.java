package com.miseru.avemujica.stage;

/**
 * Pure geometry + hysteresis policy for level-two gallery instruments.
 * No client state and no Minecraft types: the same rules are enforced on the
 * dedicated server and can be verified without running a Minecraft client.
 */
public final class GalleryInstrumentStability {
    private GalleryInstrumentStability() { }

    public static final int SETTLE_TICKS = 10;

    /** A valid mark must be on the proper elevated gallery, not on its stairs. */
    public static boolean onGallery(boolean physicalTierTwo, boolean rearMusician,
                                    boolean grounded, boolean onStageFloor,
                                    double dx, double dy, double dz) {
        return physicalTierTwo && rearMusician && grounded && onStageFloor
                && dx * dx + dz * dz <= 2.3D * 2.3D
                && Math.abs(dy) <= .55D;
    }

    /** Require 10 quiet ticks; jitter / reissued paths decay, rather than reset, readiness. */
    public static int update(int previousTicks, boolean onGallery, double horizontalMotionSq) {
        if (!onGallery) return 0;
        if (horizontalMotionSq < .0064D) return Math.min(60, previousTicks + 1);
        return Math.max(0, previousTicks - 2);
    }

    /** Exact arrival works immediately; slight but settled offsets also count. */
    public static boolean ready(boolean exactMark, boolean onGallery, int stableTicks) {
        return onGallery && (exactMark || stableTicks >= SETTLE_TICKS);
    }
}
