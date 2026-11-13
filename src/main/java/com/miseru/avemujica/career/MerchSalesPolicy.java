package com.miseru.avemujica.career;

/** Deterministic, bounded demand policy. Stock limits actual transactions. */
public final class MerchSalesPolicy {
    private MerchSalesPolicy() { }
    // v1.1.106: emerald-backed manufacturing; premium goods earn more per sale.
    // Retired entries retain their unused legacy slots.
    private static final int[] PRICES = {3, 2, 9, 6, 15, 15, 15, 15, 15};
    public static int price(int type) {
        if (type < 0 || type >= PRICES.length) throw new IllegalArgumentException("type");
        return PRICES[type];
    }
    /** Routine online sales: a small wave every 1-3 real-time minutes. */
    public static int routineDemand(int fans, int randomRoll) {
        int base = 1 + (int)(Math.sqrt(Math.max(0, fans)) / 25D);
        return Math.min(14, base + Math.max(0, Math.min(2, randomRoll)));
    }
    /** An on-site village tour converts its audience and accumulated fandom into a larger burst. */
    public static int tourDemand(int fans, int audience, int score) {
        return Math.min(72, 10 + (int)(Math.sqrt(Math.max(0, fans)) / 4D)
                + Math.max(0, audience) / 30 + Math.max(0, score) / 25);
    }
    /** Kept as a compatibility helper for any older callers or tests. */
    public static int demand(int audience, int score) {
        return tourDemand(0, audience, score);
    }
}
