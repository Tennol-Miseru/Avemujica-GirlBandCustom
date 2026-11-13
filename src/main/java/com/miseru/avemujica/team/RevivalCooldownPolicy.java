package com.miseru.avemujica.team;

/** Server-tick resurrection cooldown. Does not constrain ordinary member recall. */
public final class RevivalCooldownPolicy {
    private RevivalCooldownPolicy() { }
    public static final long DURATION_TICKS = 20L * 180L;

    public static long cooldownEnd(long resurrectionTick) {
        return resurrectionTick + DURATION_TICKS;
    }

    public static long remainingTicks(boolean awaitingRevival, long readyAt, long now) {
        return awaitingRevival ? Math.max(0L, readyAt - now) : 0L;
    }
}
