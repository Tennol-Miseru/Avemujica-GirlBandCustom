package com.miseru.avemujica.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared hit-testing and rendering coordinates for Minecraft GUI-scale-aware screens. */
public abstract class ScaledMujicaScreen extends Screen implements OctopusSkinnable {
    private UiViewport viewport;

    protected ScaledMujicaScreen(Component title) { super(title); }

    protected final void layout(int logicalWidth, int logicalHeight) {
        viewport = UiViewport.fit(width, height, logicalWidth, logicalHeight);
    }
    protected final int uiLeft() { return viewport.left(); }
    protected final int uiTop() { return viewport.top(); }
    protected final float uiScale() { return viewport == null ? 1F : viewport.scale(); }
    protected final int logicalMouse(int value) { return viewport == null ? value : viewport.logical(value); }
    protected final double logicalMouse(double value) { return viewport == null ? value : viewport.logical(value); }
    protected final void beginUi(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale(), uiScale(), 1F);
    }
    protected final void endUi(GuiGraphics graphics) { graphics.pose().popPose(); }

    /**
     * Minecraft 1.21.1 Screen.render() renders the screen background BEFORE
     * rendering its widgets. Our screens already paint that background once
     * in unscaled screen coordinates, then call super.render() after beginUi().
     * Without this guard the second background is drawn over the panel in the
     * scaled coordinate system, producing the large misplaced dark rectangle.
     */
    private boolean renderingWidgets;

    @Override public final void renderBackground(GuiGraphics graphics, int mouseX,
                                                  int mouseY, float partialTick) {
        if (!renderingWidgets) renderUiBackdrop(graphics, mouseX, mouseY, partialTick);
    }

    /** Draw the background exactly once, in physical GUI coordinates. */
    protected void renderUiBackdrop(GuiGraphics graphics, int mouseX,
                                    int mouseY, float partialTick) {
        OctopusSkin.drawBackdrop(graphics, width, height);
    }

    /**
     * Called by each child as super.render() AFTER its scaled panel was drawn.
     * Screen.render() may still try to call renderBackground(), but the guard
     * makes that call a no-op while preserving all vanilla button rendering.
     */
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        renderingWidgets = true;
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            renderingWidgets = false;
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(logicalMouse(x), logicalMouse(y), button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(logicalMouse(x), logicalMouse(y), button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(logicalMouse(x), logicalMouse(y), button,
                logicalMouse(dx), logicalMouse(dy));
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return super.mouseScrolled(logicalMouse(x), logicalMouse(y), dx, dy);
    }
}
