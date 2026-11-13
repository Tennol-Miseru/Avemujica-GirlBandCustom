package com.miseru.avemujica.client;

import com.miseru.avemujica.menu.SakikoConsoleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Single-page blue-octopus command console. Every action is visible at once.
 * All dimensions are measured in logical GUI pixels and are scaled uniformly
 * against the current screen; mouse hit boxes use the same inverse transform.
 */
public final class SakikoConsoleScreen extends AbstractContainerScreen<SakikoConsoleMenu> implements OctopusSkinnable {
    private static final int PANEL_WIDTH = 500;
    private static final int PANEL_HEIGHT = 269;
    private static final int LEFT_X = 12;
    private static final int RIGHT_X = 270;
    private static final int LEFT_COL = 118;
    private static final int RIGHT_COL = 106;
    private static final String[] MODES = {
            "全队跟随", "团队排练", "正式演奏", "全员休息", "原地待命", "自由活动"
    };

    private final Button[] modeButtons = new Button[MODES.length];
    private float uiScale = 1F;
    private Button summon, nameTags, redMoon, themeButton;

    public SakikoConsoleScreen(SakikoConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        // This is a command console; it has no inventory slots to display.
        inventoryLabelY = titleLabelY = 10000;
    }

    private Button command(String text, int id, int x, int y, int w) {
        return addRenderableWidget(Button.builder(Component.literal(text), ignored -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            }
        }).bounds(leftPos + x, topPos + y, w, 23).build());
    }

    @Override
    protected void init() {
        // At GUI scale 4, a 1280x720 window has only 320x180 logical pixels.
        // Do NOT use the fixed, unscaled dimensions for widget placement.
        UiViewport viewport = UiViewport.fit(width, height, PANEL_WIDTH, PANEL_HEIGHT);
        uiScale = viewport.scale();
        super.init();
        leftPos = viewport.left();
        topPos = viewport.top();

        themeButton = addRenderableWidget(Button.builder(Component.literal("肤"), b -> {
            OctopusTheme.toggle();
            b.setTooltip(Tooltip.create(Component.literal("当前主题：" + OctopusTheme.label() + "（点击切换三种皮肤）")));
        }).bounds(leftPos + PANEL_WIDTH - 40, topPos + 8, 28, 20).build());
        themeButton.setTooltip(Tooltip.create(Component.literal("当前主题：" + OctopusTheme.label() + "（点击切换三种皮肤）")));

        // Left: the entire band command area, no hidden page / tab.
        summon = command("召集 / 补齐已选成员", 0, LEFT_X, 61, 244);
        for (int i = 0; i < MODES.length; i++) {
            modeButtons[i] = command(MODES[i], i + 1,
                    LEFT_X + (i % 2) * 126, 91 + (i / 2) * 29, LEFT_COL);
        }
        nameTags = command("头顶名称：显示", 11, LEFT_X, 181, LEFT_COL);
        redMoon = command("红月：未解锁", 13, LEFT_X + 126, 181, LEFT_COL);

        // Right: management, MIDI, social and camera controls remain on this page.
        command("经营 / 强化排练", 7, RIGHT_X, 61, RIGHT_COL);
        command("全队 MIDI 选曲", 8, RIGHT_X + 112, 61, RIGHT_COL);
        command("停止 MIDI", 9, RIGHT_X, 90, RIGHT_COL);
        command("祥子个人互动", 10, RIGHT_X + 112, 90, RIGHT_COL);
        command("官方账号 · 巡演相册", 12, RIGHT_X, 119, 218);
        addRenderableWidget(Button.builder(Component.literal("拍摄并发布照片"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(null);
                CameraCapture.start();
            }
        }).bounds(leftPos + RIGHT_X, topPos + 148, RIGHT_COL, 23).build());
        addRenderableWidget(Button.builder(Component.literal("打开照片草稿"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(null);
                CameraCapture.openDrafts();
            }
        }).bounds(leftPos + RIGHT_X + 112, topPos + 148, RIGHT_COL, 23).build());
        command("周边寄售", 14, RIGHT_X, 181, RIGHT_COL);
        command("小章鱼商店", 15, RIGHT_X + 112, 181, RIGHT_COL);
        addRenderableWidget(Button.builder(Component.literal("乐器换色"), b -> {
            if (minecraft != null && minecraft.player != null)
                minecraft.player.connection.sendCommand("avemujica instrument_colors");
        }).bounds(leftPos+RIGHT_X, topPos+209, RIGHT_COL, 22).build());
        addRenderableWidget(Button.builder(Component.literal("角色自定义"), b -> {
            if (minecraft != null && minecraft.player != null)
                minecraft.player.connection.sendCommand("avemujica customize");
        }).bounds(leftPos+RIGHT_X+112, topPos+209, RIGHT_COL, 22).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (summon != null) {
            summon.active = !menu.summoning();
            summon.setMessage(Component.literal(menu.summoning()
                    ? "正在依次召集成员…" : "召集 / 补齐已选成员"));
        }
        for (int i = 0; i < modeButtons.length; i++) {
            modeButtons[i].active = menu.members() > 0 && !menu.summoning();
            modeButtons[i].setMessage(Component.literal(
                    (menu.teamModeId() == i + 1 ? "▶ " : "") + MODES[i]));
        }
        nameTags.setMessage(Component.literal(menu.nameTagsVisible()
                ? "头顶名称：显示" : "头顶名称：隐藏"));
        redMoon.active = menu.redMoonAvailable();
        redMoon.setMessage(Component.literal(!menu.redMoonAvailable()
                ? "红月：未解锁" : (menu.redMoonEnabled() ? "红月：开启" : "红月：关闭")));
        redMoon.setTooltip(Tooltip.create(Component.literal(menu.redMoonAvailable()
                ? "红月只影响视觉效果，不影响合奏增益"
                : "五人全部训练至 Lv.15 后解锁")));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        var theme = OctopusTheme.current();
        OctopusSkin.drawPanelFrame(g, leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT, 36);
        g.fill(leftPos + 9, topPos + 41, leftPos + PANEL_WIDTH - 9, topPos + 42, theme.edge);
        g.fill(leftPos + 262, topPos + 47, leftPos + 263, topPos + 224, theme.edge);
        g.fill(leftPos + 9, topPos + 241, leftPos + PANEL_WIDTH - 9, topPos + 242, theme.edge);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        var theme = OctopusTheme.current();
        g.drawString(font, "丰川祥子 · 蓝色小章鱼", 12, 8, theme.accent, false);
        g.drawString(font, "便携乐队终端  ·  附近成员 " + menu.members() + "/5", 12, 22, theme.text, false);
        g.drawString(font, "乐队指挥", LEFT_X, 48, theme.secondary, false);
        g.drawString(font, "经营 · 音乐 · 社交", RIGHT_X, 48, theme.secondary, false);
        g.drawString(font, "全部功能均在当前页面，选择后立即生效", LEFT_X, 211, theme.muted, false);
        g.drawString(font, "Esc 关闭   ·   右上角「肤」循环切换三种主题", LEFT_X, 250, theme.muted, false);
    }

    private boolean renderingWidgets;

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!renderingWidgets) OctopusSkin.drawBackdrop(graphics, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        OctopusSkin.drawBackdrop(graphics, width, height);
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1F);
        int virtualMouseX = (int)(mouseX / uiScale);
        int virtualMouseY = (int)(mouseY / uiScale);
        renderingWidgets = true;
        try {
            super.render(graphics, virtualMouseX, virtualMouseY, partialTick);
            OctopusSkin.drawCenteredLogo(graphics, leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT);
            renderTooltip(graphics, virtualMouseX, virtualMouseY);
        } finally {
            renderingWidgets = false;
            graphics.pose().popPose();
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(x / uiScale, y / uiScale, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / uiScale, y / uiScale, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / uiScale, y / uiScale, button, dx / uiScale, dy / uiScale);
    }
    @Override public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        return super.mouseScrolled(x / uiScale, y / uiScale, deltaX, deltaY);
    }
}
