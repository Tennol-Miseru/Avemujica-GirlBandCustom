package com.miseru.avemujica.client;

import com.miseru.avemujica.menu.BandDebugMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** No additional packets: normal vanilla inventory button clicks are validated on server. */
public final class BandDebugScreen extends AbstractContainerScreen<BandDebugMenu> {
    private float uiScale = 1F;
    private static final int W = 475, H = 263;
    private static final String[] NAMES = {"三角初华", "若叶睦", "八幡海铃", "祐天寺若麦", "丰川祥子"};
    private final Button[] buttons = new Button[28];

    public BandDebugScreen(BandDebugMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelY = titleLabelY = 10000;
    }

    private void addAction(int id, String label, int x, int y, int w) {
        buttons[id] = addRenderableWidget(Button.builder(Component.literal(label), b -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }).bounds(leftPos + x, topPos + y, w, 20).build());
    }

    @Override protected void init() {
        UiViewport viewport = UiViewport.fit(width, height, 475, 263);
        uiScale = viewport.scale();
        super.init();
        leftPos = viewport.left();
        topPos = viewport.top();
        String[] actions = {"Lv.5", "Lv.15", "解锁攻击", "切换形态", "重置"};
        int[] positions = {150, 206, 267, 344, 416};
        int[] widths = {52, 56, 73, 68, 48};
        for (int r = 0; r < 5; r++) {
            for (int a = 0; a < 5; a++) addAction(r * 5 + a, actions[a], positions[a], 58 + r * 28, widths[a]);
        }
        addAction(25, "全员 Lv.5", 20, 215, 130);
        addAction(26, "全员满级 + 解锁", 160, 215, 158);
        addAction(27, "全部重置", 330, 215, 124);
    }

    @Override protected void containerTick() {
        super.containerTick();
        for (int r = 0; r < 5; r++) {
            if (buttons[r * 5 + 3] != null)
                buttons[r * 5 + 3].active = menu.unlocked(r);
            if (buttons[r * 5 + 2] != null)
                buttons[r * 5 + 2].active = r != 2 && r != 4;
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int x, int y) {
        g.fill(leftPos, topPos, leftPos + W, topPos + H, 0xF0241D2B);
        g.fill(leftPos + 3, topPos + 3, leftPos + W - 3, topPos + 40, 0xFF493249);
        g.fill(leftPos + 12, topPos + 204, leftPos + W - 12, topPos + 205, 0xFF7B617C);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "乐队角色调试器 · 仅创造/管理员", 14, 11, 0xFFF2D3A0, false);
        g.drawString(font, "直接修改玩家绑定的成长档案；没有角色会尝试召唤；不消耗绿宝石。", 14, 28, 0xFFD4C6D3, false);
        for (int r = 0; r < 5; r++) {
            String progress = menu.rank(r) < 0 ? "未加载" : "Lv." + menu.rank(r);
            String mode = menu.unlocked(r) ? (menu.active(r) ? "Ⅱ" : "Ⅰ") : "锁";
            g.drawString(font, NAMES[r] + "  " + progress + "  " + mode,
                    14, 64 + r * 28, 0xFFF7EAE6, false);
        }
        g.drawString(font, "Ⅱ=第二战斗形态  Ⅰ=默认攻击  锁=未解锁；设置后会持久保存。", 14, 247, 0xFFBEAFC8, false);
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
