package com.miseru.avemujica.career;

/**
 * Timing policy for the Regeneration I granted by a full-level masquerade concert.
 *
 * Vanilla Regeneration I heals every 50 ticks when its remaining duration is
 * divisible by 50. Do NOT reset the effect every second: doing so with a
 * 35-tick effect would prevent all natural healing ticks.
 */
public final class MasqueradeRegenerationPolicy {
    /** Up to 3.5 seconds of lingering recovery after the concert ends. */
    public static final int DURATION_TICKS = 70;
    /** Checked every 20 ticks by the concert manager. */
    public static final int RENEW_AT_OR_BELOW_TICKS = 15;

    private MasqueradeRegenerationPolicy() { }

    /** Never replace externally supplied Regeneration II+ or unexpired I. */
    public static boolean shouldRenew(int remainingTicks, int amplifier) {
        return amplifier == 0 && remainingTicks <= RENEW_AT_OR_BELOW_TICKS;
    }
}
