package com.miseru.avemujica.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Transparent text button with purple outline used by the octopus UI family. */
public class OctopusSkinButton extends Button {
    public OctopusSkinButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!OctopusSkin.useCustomButtons()) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            return;
        }
        int fill = OctopusSkin.buttonFill(isHoveredOrFocused(), active);
        int edge = OctopusSkin.buttonBorder(isHoveredOrFocused(), active);
        int text = OctopusSkin.buttonText(active);
        g.fill(getX(), getY(), getX() + width, getY() + height, fill);
        g.renderOutline(getX(), getY(), width, height, edge);
        if (isHoveredOrFocused() && active && width > 4 && height > 4)
            g.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, 0x66FFFFFF);
        renderString(g, Minecraft.getInstance().font, text);
    }
}
