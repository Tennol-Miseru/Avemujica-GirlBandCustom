package com.miseru.avemujica.client;

import java.util.EnumSet;
import net.minecraft.client.Minecraft;

/**
 * Temporarily hides the entire HUD for in-world photos (F1 effect).
 * Owns only the visibility change and restores the user's previous preference
 * even on cancellation, disconnect or capture failure. The two capture systems
 * may briefly overlap without restoring the HUD halfway through the other shot.
 */
public final class PhotoHudVisibility {
    private PhotoHudVisibility() { }
    public enum Capture { CAMERA, TOUR }
    private static final EnumSet<Capture> active = EnumSet.noneOf(Capture.class);
    private static boolean previousHideGui;

    public static void begin(Capture capture) {
        Minecraft mc = Minecraft.getInstance();
        if (active.isEmpty()) previousHideGui = mc.options.hideGui;
        active.add(capture);
        mc.options.hideGui = true;
    }

    public static boolean active(Capture capture) { return active.contains(capture); }

    public static void end(Capture capture) {
        if (!active.remove(capture)) return;
        if (active.isEmpty()) Minecraft.getInstance().options.hideGui = previousHideGui;
    }
}
