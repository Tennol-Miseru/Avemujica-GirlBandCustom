package com.miseru.avemujica.client.mixin;

import com.miseru.avemujica.client.OctopusSkin;
import com.miseru.avemujica.client.OctopusSkinnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class OctopusButtonMixin extends net.minecraft.client.gui.components.AbstractWidget {
    protected OctopusButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void ave$renderOctopusStyle(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof OctopusSkinnable)) return;
        if (!OctopusSkin.useCustomButtons()) return;
        if (!visible) return;
        int fill = OctopusSkin.buttonFill(isHoveredOrFocused(), active);
        int edge = OctopusSkin.buttonBorder(isHoveredOrFocused(), active);
        int text = OctopusSkin.buttonText(active);
        g.fill(getX(), getY(), getX() + width, getY() + height, fill);
        g.renderOutline(getX(), getY(), width, height, edge);
        if (isHoveredOrFocused() && active && width > 4 && height > 4)
            g.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, 0x66FFFFFF);
        ((AbstractButton)(Object)this).renderString(g, Minecraft.getInstance().font, text);
        ci.cancel();
    }
}
