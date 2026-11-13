package com.miseru.avemujica.client;

import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.entity.TeamMode;
import com.miseru.avemujica.menu.MemberInteractionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Click an NPC to interact, assign actual jobs, and access MIDI / business management. */
public final class MemberInteractionScreen extends AbstractContainerScreen<MemberInteractionMenu> implements OctopusSkinnable {
    private float uiScale = 1F;
    private static final int W = 336, H = 355;
    private final Button[] jobs = new Button[4];
    private Button food, solo, unlockStyle, toggleStyle, teamPicker, soloPicker, defaultPicker, octopusReplacement, giftToggle;
    private final Button[] teamButtons = new Button[6];
    private static final String[] TEAM_LABELS = {"全队跟随", "团队排练", "正式演奏", "全员休息", "原地待命", "自由活动"};
    private int shownMode = -1;

    public MemberInteractionScreen(MemberInteractionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelY = 10000;
        titleLabelY = 10000;
    }

    private Button action(String label, int x, int y, int w, int h, int id) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            }
        }).bounds(leftPos+x,topPos+y,w,h).build());
    }

    @Override protected void init() {
        UiViewport viewport = UiViewport.fit(width, height, 336, 355);
        uiScale = viewport.scale();
        super.init();
        leftPos = viewport.left();
        topPos = viewport.top();
        food = action("喂食 / 补充体力", 14, 62, 149, 22, 0);
        solo = action("开始独奏 · 12秒", 174, 62, 149, 22, 1);
        jobs[0] = action("原地待命", 14, 109, 149, 22, 2);
        jobs[1] = action("附近巡逻", 174, 109, 149, 22, 3);
        jobs[2] = action("守卫 · 攻击怪物", 14, 135, 149, 22, 4);
        jobs[3] = action("休息 · 缓慢回血", 174, 135, 149, 22, 5);
        action("打开模拟经营面板", 14, 173, 149, 24, 6);
        // Role data arrives through vanilla ContainerData AFTER Screen.init() on some
        // clients. Create both controls and toggle visibility in containerTick(),
        // rather than reading an unsynchronized role once during init.
        teamPicker = addRenderableWidget(Button.builder(Component.literal("全队选曲"), b -> {
            if (minecraft != null && minecraft.player != null && menu.role() == MemberRole.OBLIVIONIS.ordinal())
                minecraft.player.connection.sendCommand("avemujica midi browse_team " + menu.entityId());
        }).bounds(leftPos+174,topPos+173,71,24).build());
        soloPicker = addRenderableWidget(Button.builder(Component.literal("独奏选曲"), b -> {
            if (minecraft != null && minecraft.player != null)
                minecraft.player.connection.sendCommand("avemujica midi browse " + menu.entityId());
        }).bounds(leftPos+249,topPos+173,74,24).build());
        defaultPicker = addRenderableWidget(Button.builder(Component.literal("默认曲目"), b -> {
            if (minecraft != null && minecraft.player != null) {
                String mode = menu.role() == MemberRole.OBLIVIONIS.ordinal() ? "play_team " : "play_member ";
                minecraft.player.connection.sendCommand("avemujica midi " + mode + menu.entityId() + " default");
            }
        }).bounds(leftPos+174,topPos+200,68,19).build());
        // The former long line at y=205 overlapped these buttons. Keep the
        // details on hover, so nothing is drawn beneath interactive widgets.
        defaultPicker.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("默认曲目：KillKiss（模组内置）")));
        soloPicker.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("自选 MIDI 文件夹：config/avemujica/midi")));
        addRenderableWidget(Button.builder(Component.literal("停止 MIDI"), b -> {
            if (minecraft != null && minecraft.player != null)
                minecraft.player.connection.sendCommand("avemujica midi stop");
        }).bounds(leftPos+245,topPos+200,78,19).build());
        octopusReplacement = action("补领蓝色小章鱼", 14, 200, 149, 19, 22);
        unlockStyle = action("解锁战斗模式 · 8绿宝石", 14, 228, 149, 23, 20);
        toggleStyle = action("切换战斗模式", 174, 228, 149, 23, 21);
        giftToggle = action("日常赠礼：开启", 14, 285, 309, 24, 23);
        for (int i = 0; i < TEAM_LABELS.length; i++) {
            final int slot = i;
            teamButtons[i] = action(TEAM_LABELS[slot], 14 + (slot % 2) * 160,
                    264 + (slot / 2) * 25, 149, 22, 10 + slot);
            teamButtons[i].visible = false;
        }
    }

    @Override protected void containerTick() {
        super.containerTick();
        boolean owner = menu.owned();
        if (food != null) {
            food.active = owner && !menu.performing();
            // Deliberately never show food preferences in the member UI.
            food.setMessage(Component.literal("喂食 / 补充体力"));
        }
        if (solo != null) solo.active = owner && !menu.performing();
        if (giftToggle != null) {
            giftToggle.visible = menu.role() != MemberRole.OBLIVIONIS.ordinal();
            giftToggle.active = owner;
            giftToggle.setMessage(Component.literal("日常赠礼：" + (menu.giftsEnabled() ? "开启" : "关闭") + "（低频）"));
        }
        boolean sakiko = menu.role() == MemberRole.OBLIVIONIS.ordinal();
        if (octopusReplacement != null) {
            octopusReplacement.visible = sakiko;
            octopusReplacement.active = owner;
        }
        if (teamPicker != null) {
            teamPicker.visible = sakiko;
            teamPicker.active = owner; // song may be switched during a show
        }
        if (soloPicker != null) {
            soloPicker.setMessage(Component.literal(sakiko ? "独奏选曲" : "个人选曲独奏"));
            soloPicker.setX(leftPos + (sakiko ? 249 : 174));
            soloPicker.setWidth(sakiko ? 74 : 149);
            // MIDI song selection is permitted during/after playback: the server
            // validates ownership and switches songs atomically. Never leave it
            // disabled because a stale solo/stage flag has not synced yet.
            soloPicker.active = owner;
        }
        if (defaultPicker != null) {
            defaultPicker.setMessage(Component.literal(sakiko ? "默认全队" : "默认独奏"));
            defaultPicker.active = owner;
        }
        if (unlockStyle != null) {
            unlockStyle.visible = menu.supportsAlternate();
            unlockStyle.active = owner && !menu.alternateUnlocked() && menu.trainingRank() >= 5;
        }
        if (toggleStyle != null) {
            toggleStyle.visible = menu.supportsAlternate();
            toggleStyle.active = owner && menu.alternateUnlocked();
            toggleStyle.setMessage(Component.literal(menu.alternateActive()
                    ? "使用音符模式" : "启用第二模式"));
        }
        for (Button button : jobs) if (button != null)
            button.active = owner && !(sakiko && menu.teamMode() != TeamMode.NONE);
        for (Button button : teamButtons) if (button != null) {
            button.visible = sakiko;
            button.active = sakiko && owner;
        }
        int now = menu.teamMode().ordinal();
        if (shownMode != now) {
            shownMode = now;
            for (int i = 0; i < teamButtons.length; i++) if (teamButtons[i] != null)
                teamButtons[i].setMessage(Component.literal((now == i + 1 ? "▶ " : "") + TEAM_LABELS[i]));
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        OctopusSkin.drawPanelFrame(graphics, leftPos, topPos, W, H, 47);
        graphics.fill(leftPos+9, topPos+94, leftPos+W-9, topPos+95, OctopusSkin.borderSoft());
        graphics.fill(leftPos+9, topPos+162, leftPos+W-9, topPos+163, OctopusSkin.borderSoft());
        if (menu.role() == MemberRole.OBLIVIONIS.ordinal()) {
            graphics.fill(leftPos+9, topPos+254, leftPos+W-9, topPos+255, OctopusSkin.borderSoft());
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MemberRole[] roles = MemberRole.values();
        int roleIndex = Math.max(0, Math.min(roles.length - 1, menu.role()));
        graphics.drawString(font, Component.literal(roles[roleIndex].display), 14, 12, OctopusTheme.current().secondary, false);
        graphics.drawString(font, Component.literal("生命值: " + menu.health() + " / " + menu.maxHealth()
                + " · 训练Lv." + menu.trainingRank() + " · " + (menu.performing() ? "演奏中" : "空闲") ), 14, 29, OctopusTheme.current().text, false);
        String[] tasks = {"待命", "巡逻", "守卫", "休息"};
        graphics.drawString(font, Component.literal(menu.teamMode() == TeamMode.NONE
                ? "个人任务: " + tasks[Math.max(0, Math.min(3, menu.task()))]
                : "团队模式: " + menu.teamMode().label), 14, 49, OctopusTheme.current().secondary, false);
        String[] moods = {"无特殊动作", "抱琴坐下", "吃蛋白棒", "举手机拍摄", "向祥子示好", "思考排练",
                "翻阅乐谱", "带领成员打拍子", "蹲下观察花朵", "小声调弦", "伸展肩膀", "补充水分",
                "整理刘海", "邀请成员合影", "回应合影", "轻声练唱", "送花给祥子", "回应初华",
                "抬手挡雨", "留意受伤成员", "品尝咖啡", "拿着黄瓜", "送黄瓜给队友", "品尝咖啡", "接过睦送的黄瓜"};
        int mood = Math.max(0, Math.min(moods.length - 1, menu.personality()));
        graphics.drawString(font, font.plainSubstrByWidth("日常状态: " + moods[mood], W - 26),
                14, 87, OctopusTheme.current().muted, false);
        graphics.drawString(font, Component.literal("任务指派"), 14, 98, OctopusTheme.current().secondary, false);
        if (menu.supportsAlternate()) {
            String weapon = switch (menu.role()) {
                case 4 -> "铁剑 / 音符";
                case 0 -> "电锯近战";
                case 2 -> "步枪远程";
                default -> "特殊战斗";
            };
            String style = menu.alternateUnlocked()
                    ? (menu.alternateActive() ? weapon + " · 已启用" : weapon + " · 已解锁")
                    : weapon + " · 需训练Lv.5";
            graphics.drawString(font, Component.literal(style), 14, 219, OctopusTheme.current().accent, false);
        }
        if (menu.role() == MemberRole.OBLIVIONIS.ordinal()) {
            graphics.drawString(font, Component.literal("祥子 · 乐队指挥  [一次向附近全队下令]"), 14, 255, OctopusTheme.current().secondary, false);
        } else {
            graphics.drawString(font, Component.literal("团队命令请右键祥子 · 本页可单独安排成员"), 14, 260, OctopusTheme.current().muted, false);
        }
    }

    private boolean renderingWidgets;

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!renderingWidgets) OctopusSkin.drawBackdrop(graphics, width, height);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        OctopusSkin.drawBackdrop(graphics, width, height);
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1F);
        int localMouseX = Math.round(mouseX / uiScale), localMouseY = Math.round(mouseY / uiScale);
        renderingWidgets = true;
        try {
            super.render(graphics, localMouseX, localMouseY, partialTick);
            OctopusSkin.drawCenteredLogo(graphics, leftPos, topPos, W, H);
            renderTooltip(graphics, localMouseX, localMouseY);
        } finally {
            renderingWidgets = false;
            graphics.pose().popPose();
        }
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
