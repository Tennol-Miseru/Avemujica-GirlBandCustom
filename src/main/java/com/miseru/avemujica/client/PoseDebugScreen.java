package com.miseru.avemujica.client;

import com.miseru.avemujica.menu.PoseDebugActions;
import com.miseru.avemujica.menu.PoseDebugMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact NPC pose picker; actions are authoritative on the server. */
public final class PoseDebugScreen extends AbstractContainerScreen<PoseDebugMenu> {
    private float uiScale = 1F;
    private static final int W = 310, H = 222;
    private final Button[] actions = new Button[PoseDebugActions.SLOT_COUNT];
    public PoseDebugScreen(PoseDebugMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelY = titleLabelY = 10000;
    }
    @Override protected void init() {
        UiViewport viewport = UiViewport.fit(width, height, 310, 222);
        uiScale = viewport.scale();
        super.init();
        leftPos = viewport.left();
        topPos = viewport.top();
        for (int i = 0; i < actions.length; i++) {
            final int index = i;
            actions[i] = addRenderableWidget(Button.builder(Component.literal("动作 " + (i + 1)),
                    b -> send(index)).bounds(leftPos + 24, topPos + 49 + 25 * i, W - 48, 21).build());
        }
        addRenderableWidget(Button.builder(Component.literal("恢复待机"), b -> send(5))
                .bounds(leftPos + 24, topPos + 182, 125, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(leftPos + 161, topPos + 182, 125, 20).build());
        refreshLabels();
    }
    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    private void refreshLabels() {
        for (int i = 0; i < actions.length; i++) {
            String label = PoseDebugActions.label(menu.selectedRole(), i);
            actions[i].active = !label.isEmpty();
            actions[i].setMessage(Component.literal(label.isEmpty() ? "—" : label));
        }
    }
    @Override protected void containerTick() {
        super.containerTick();
        refreshLabels();
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int x, int y) {
        g.fill(leftPos, topPos, leftPos + W, topPos + H, 0xF024202D);
        g.fill(leftPos + 3, topPos + 3, leftPos + W - 3, topPos + 40, 0xFF493449);
        g.fill(leftPos + 12, topPos + H - 52, leftPos + W - 12, topPos + H - 51, 0xFF8B7089);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "Ave Mujica · 动作调试器", 14, 12, 0xFFF2D3A0, false);
        g.drawString(font, "对象：" + menu.selectedRole().display + "  当前动作 #" + menu.activeAction(),
                14, 28, 0xFFD7CBD7, false);
        g.drawString(font, "可覆盖普通日常动作 · 演奏及战斗仍锁定", 20, 210, 0xFFBCAABF, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1F);
        int localMouseX = Math.round(mouseX / uiScale), localMouseY = Math.round(mouseY / uiScale);
        super.render(g, localMouseX, localMouseY, partialTick);
        renderTooltip(g, localMouseX, localMouseY);
        g.pose().popPose();
    }
    // Widget hit tests must use the same inverse transform as the canvas.
    @Override public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(x / uiScale, y / uiScale, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / uiScale, y / uiScale, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / uiScale, y / uiScale, button, dx / uiScale, dy / uiScale);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return super.mouseScrolled(x / uiScale, y / uiScale, dx, dy);
    }
}
