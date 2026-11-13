package com.miseru.avemujica.client;

/**
 * Uniform virtual-canvas layout for GUI screens.
 *
 * Every widget and decoration uses the SAME unscaled logical coordinates.
 * When Minecraft's GUI scale or window size changes, draw the entire canvas
 * and inverse-transform mouse events rather than independently squeezing the
 * panel width, which previously caused overlapping buttons and previews.
 */
public record UiViewport(float scale, int left, int top, int width, int height) {
    public static UiViewport fit(int screenWidth, int screenHeight, int contentWidth, int contentHeight) {
        float ratio = Math.min(1F, Math.min((screenWidth - 12F) / contentWidth,
                (screenHeight - 12F) / contentHeight));
        // A small positive minimum only protects division on a temporary 0px window.
        float scale = Math.max(.01F, ratio);
        int left = Math.round((screenWidth / scale - contentWidth) * .5F);
        int top = Math.round((screenHeight / scale - contentHeight) * .5F);
        return new UiViewport(scale, left, top, contentWidth, contentHeight);
    }
    public double logical(double actual) { return actual / scale; }
    public int logical(int actual) { return Math.round(actual / scale); }
}
