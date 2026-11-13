package com.miseru.avemujica.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared rendering helpers for all three octopus UI themes. */
public final class OctopusSkin {
    private OctopusSkin() { }

    public static int panelSoft() { return OctopusTheme.current().alternate; }
    public static int border() { return OctopusTheme.current().edge; }
    public static int borderSoft() { return withAlpha(OctopusTheme.current().edge, 0xAA); }
    public static int text() { return OctopusTheme.current().text; }
    public static int muted() { return OctopusTheme.current().muted; }
    public static int gold() { return OctopusTheme.current().secondary; }
    public static int rowA() { return OctopusTheme.current().panel; }
    public static int rowB() { return OctopusTheme.current().alternate; }
    public static int positive() { return OctopusTheme.current().positive; }

    /**
     * Every theme draws the same thing behind the window: the world, dimmed.
     * The coloured surface itself is the panel drawn by {@link #drawPanelFrame};
     * painting it across the whole screen is what removed the windowed look.
     */
    public static void drawBackdrop(GuiGraphics g, int screenWidth, int screenHeight) {
        int veil = switch (OctopusTheme.current()) {
            case LIGHT -> 0xB3141C26;
            case CRIMSON -> 0xC7060409;
            case CRIMSON_LOGO -> 0x99000000;
        };
        g.fill(0, 0, screenWidth, screenHeight, veil);
    }

    /** Solid window: shadow, body, coloured header band, double outline, header rule. */
    public static void drawPanelFrame(GuiGraphics g, int x, int y, int w, int h, int headerHeight) {
        OctopusTheme.Palette theme = OctopusTheme.current();
        int header = Math.max(22, headerHeight);
        g.fill(x + 6, y + 8, x + w + 6, y + h + 8, 0x66000000);
        g.fill(x, y, x + w, y + h, theme.panel);
        g.fill(x, y, x + w, y + header, theme.header);
        g.renderOutline(x, y, w, h, theme.edge);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, withAlpha(theme.edge, 0x66));
        g.fill(x + 2, y + header, x + w - 2, y + header + 1, theme.edge);
    }

    /**
     * The wordmark layer was removed: at header size it was an unreadable smudge and
     * at panel size it covered the content. Kept as a no-op so every screen keeps
     * its call site.
     */
    public static void drawCenteredLogo(GuiGraphics g, int x, int y, int w, int h) {
        // intentionally empty
    }

    public static int buttonFill(boolean hovered, boolean enabled) {
        if (!enabled) return 0x33200F20;
        return hovered ? 0x3A5C2480 : 0x2221123A;
    }

    public static int buttonBorder(boolean hovered, boolean enabled) {
        if (!enabled) return withAlpha(OctopusTheme.current().edge, 0x88);
        return hovered ? 0xFFE3A7FF : 0xFFAE67DA;
    }

    public static int buttonText(boolean enabled) {
        return enabled ? OctopusTheme.current().text : OctopusTheme.current().muted;
    }

    /** Only the LOGO skin replaces vanilla buttons; the two legacy skins keep them. */
    public static boolean useCustomButtons() {
        return OctopusTheme.current() == OctopusTheme.Palette.CRIMSON_LOGO;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
