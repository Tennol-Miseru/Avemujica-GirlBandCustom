package com.miseru.avemujica.client;

/** Active sky state is synced directly, never exposed as a potion or HUD overlay. */
public final class MasqueradeSkyState {
    private static volatile boolean active;
    private MasqueradeSkyState() { }
    public static boolean active() { return active; }
    public static void set(boolean state) { active = state; }
    public static void clear() { active = false; }
}
