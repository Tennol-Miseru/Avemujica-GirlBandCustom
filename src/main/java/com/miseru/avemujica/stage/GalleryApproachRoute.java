package com.miseru.avemujica.stage;

/**
 * Navigation policy for the two elevated level-two galleries.
 *
 * <p>The actual v1.1.92+ stage has TWO Minecraft stair blocks (at local
 * z=2/y=1 and z=1/y=2), each with two half-height treads. A* must navigate
 * across their collision surfaces. Do NOT aim A* at an intermediate point
 * inside those stairs: the previous four-waypoint route used z=2.25 at y+.5,
 * where the upper half of the first stair is solid, and could never finish.
 *
 * <p>Only use full, standable floor nodes as goals: on the main deck before
 * the stairs, on the upper gallery landing, and finally the musician mark.
 * The normal pathfinder chooses the half-treads between the two endpoints.
 */
public final class GalleryApproachRoute {
    private GalleryApproachRoute() { }

    public static final int APPROACH = 1;
    public static final int LANDING = 2;
    public static final int PERFORMANCE_MARK = 3;

    public record Step(double localZ, double elevation) { }

    /** Local, air-filled goals with a solid floor at exactly the given height. */
    public static Step step(int phase) {
        return switch (phase) {
            case APPROACH -> new Step(3.0D, 0.0D);  // main-deck block, before stair z=2
            case LANDING -> new Step(0.0D, 2.0D); // upper gallery floor, behind stair z=1
            default -> throw new IllegalArgumentException("Gallery phase " + phase);
        };
    }

    /** Never send a member back down the stairs once it has reached their top. */
    public static int adjustForHeight(int phase, double relativeY) {
        if (relativeY >= 1.75D) return PERFORMANCE_MARK;
        if (phase == PERFORMANCE_MARK) return relativeY >= .30D ? LANDING : APPROACH;
        if (relativeY >= .30D) return LANDING;
        return phase;
    }

    public static boolean reached(double dx, double dy, double dz) {
        return dx * dx + dz * dz <= .75D * .75D && Math.abs(dy) <= .35D;
    }
}
