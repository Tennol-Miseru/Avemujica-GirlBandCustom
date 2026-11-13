package com.miseru.avemujica.client;

import com.miseru.avemujica.menu.BandManagementMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Ave Mujica's burgundy/gold in-game management office screen. */
public final class BandManagementScreen extends AbstractContainerScreen<BandManagementMenu> {
    private float uiScale = 1F;
    private static final int WIDTH = 432, HEIGHT = 300;
    private static final int BACK = 0xF01A141F, HEADER = 0xFF3D1729;
    private static final int PANEL = 0xFF261D2A, PANEL_ALT = 0xFF302232;
    private static final int GOLD = 0xFFE3BD87, MUTED = 0xFFB8A7B5;
    private static final int WHITE = 0xFFF7EAE6, GREEN = 0xFF80C2A1;
    private final Button[] actions = new Button[8];
    private Button enhanceCheck;
    private boolean enhanced = false;

    public BandManagementScreen(BandManagementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        UiViewport viewport = UiViewport.fit(width, height, WIDTH, HEIGHT);
        uiScale = viewport.scale();
        super.init();
        leftPos = viewport.left();
        topPos = viewport.top();
        int x = leftPos + 238;
        // Slot 0 is the retired recruitment action. Keep menu action IDs
        // stable for protocol compatibility, but do not create its UI button.
        for (int i = 1; i < actions.length; i++) {
            final int actionId = i;
            Button button = Button.builder(Component.literal(""), pressed -> {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    // Selected training mode uses a distinct server-validated button action.
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                            actionId == 1 && enhanced ? 8 : actionId);
                }
            }).bounds(x, topPos + actionY(i), 180, 21).build();
            button.setTooltip(Tooltip.create(Component.translatable("gui.avemujica.tip." + i)));
            actions[i] = this.addRenderableWidget(button);
        }
        enhanceCheck = addRenderableWidget(Button.builder(Component.literal("[ ] 强化排练（额外 2 绿宝石）"), button -> {
            enhanced = !enhanced;
            updateButtons();
        }).bounds(leftPos + 238, topPos + 92, 180, 18).build());
        // MIDI controls use existing permission-checked server commands.  The play
        // button opens chat with command completion for .mid filenames on the server.
        this.addRenderableWidget(Button.builder(Component.translatable("gui.avemujica.midi.list"), button -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("avemujica midi browse");
            }
        }).bounds(leftPos + 237, topPos + 15, 46, 19)
          .tooltip(Tooltip.create(Component.translatable("gui.avemujica.midi.list.tip"))).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.avemujica.midi.play"), button -> {
            if (this.minecraft != null) {
                if (this.minecraft.player != null)
                    this.minecraft.player.connection.sendCommand("avemujica midi browse");
            }
        }).bounds(leftPos + 287, topPos + 15, 86, 19)
          .tooltip(Tooltip.create(Component.translatable("gui.avemujica.midi.play.tip"))).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.avemujica.midi.stop"), button -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("avemujica midi stop");
            }
        }).bounds(leftPos + 377, topPos + 15, 44, 19)
          .tooltip(Tooltip.create(Component.translatable("gui.avemujica.midi.stop.tip"))).build());
        updateButtons();
    }

    private static int actionY(int i) {
        // Reflow the remaining buttons upward after removing Recruitment.
        // Rehearsal at 69, show/tour at 117/140, and collection at 232.
        return i == 1 ? 69 : 117 + (i - 2) * 23;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void updateButtons() {
        if (actions[1] == null) return;
        actions[1].setMessage(Component.literal(enhanced ? "开始强化排练" : "开始普通排练 · 免费"));
        if (enhanceCheck != null) {
            enhanceCheck.setMessage(Component.literal((enhanced ? "[x]" : "[ ]") + " 强化排练 · 2绿宝石"));
            enhanceCheck.active = menu.stageReady() && !menu.performing();
            enhanceCheck.setTooltip(Tooltip.create(Component.literal("可选：强化排练提升队员生命值和攻击力；普通排练不扣材料。")));
        }
        actions[2].setMessage(menu.performing()
                ? Component.translatable("gui.avemujica.show.running", menu.showRemaining())
                : Component.translatable("gui.avemujica.show"));
        actions[3].setMessage(menu.performing()
                ? Component.translatable("gui.avemujica.show.running", menu.showRemaining())
                : Component.literal(menu.villageHint() ? "村庄巡演45秒（4绿宝石）" : "村庄巡演 · 点击诊断"));
        actions[3].setTooltip(Tooltip.create(Component.literal(menu.villageHint()
                ? "村庄已识别。点击后服务器仍会检查五位成员、默契、士气、费用和冷却。"
                : "点击查看未满足的条件。需钟+1村民，或2床+2村民。创造模式免支付。")));
        actions[4].setMessage(menu.promoteWait() > 0
                ? Component.translatable("gui.avemujica.wait.promote", menu.promoteWait())
                : Component.translatable("gui.avemujica.promote"));
        actions[5].setMessage(menu.soundLevel() >= 5
                ? Component.translatable("gui.avemujica.sound.max")
                : Component.translatable("gui.avemujica.sound", menu.soundLevel(), menu.soundCost()));
        actions[6].setMessage(menu.lightLevel() >= 5
                ? Component.translatable("gui.avemujica.lights.max")
                : Component.translatable("gui.avemujica.lights", menu.lightLevel(), menu.lightsCost()));
        actions[7].setMessage(Component.translatable("gui.avemujica.claim", menu.pendingLoot()));
        for (int i = 1; i < actions.length; i++)
            actions[i].active = (i == 1 ? menu.canRun(enhanced ? 8 : 1) : menu.canRun(i));
    }

    private static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        rect(g, x, y, w, 1, color);
        rect(g, x, y + h - 1, w, 1, color);
        rect(g, x, y, 1, h, color);
        rect(g, x + w - 1, y, 1, h, color);
    }

    private void line(GuiGraphics g, Component value, int x, int y, int color) {
        // Left-side text must never bleed into the operation buttons' panel.
        int maxWidth = x < 230 ? Math.max(1, 225 - x) : WIDTH - x - 12;
        g.drawString(font, font.plainSubstrByWidth(value.getString(), maxWidth), x, y, color, false);
    }

    private void statBar(GuiGraphics g, int x, int y, String key, int amount, int color) {
        line(g, Component.translatable(key, amount), x, y, WHITE);
        int filled = Math.max(0, Math.min(100, amount));
        rect(g, x, y + 12, 207, 6, 0xFF4D3D4A);
        rect(g, x, y + 12, (207 * filled) / 100, 6, color);
        frame(g, x, y + 12, 207, 6, 0xFF6B5265);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        rect(g, x, y, WIDTH, HEIGHT, BACK);
        frame(g, x, y, WIDTH, HEIGHT, GOLD);
        rect(g, x + 2, y + 2, WIDTH - 4, 40, HEADER);
        rect(g, x + 12, y + 41, WIDTH - 24, 1, 0xFF765068);
        rect(g, x + 11, y + 51, 217, 232, PANEL);
        rect(g, x + 233, y + 51, 190, 232, PANEL_ALT);
        frame(g, x + 11, y + 51, 217, 232, 0xFF554052);
        frame(g, x + 233, y + 51, 190, 232, 0xFF685163);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Relative screen coordinates: used instead of the vanilla inventory labels.
        line(g, Component.translatable("gui.avemujica.title"), 15, 11, GOLD);
        line(g, Component.translatable("gui.avemujica.subtitle"), 15, 25, MUTED);
        line(g, Component.translatable("gui.avemujica.overview"), 19, 57, GOLD);
        line(g, Component.translatable("gui.avemujica.emeralds", menu.emeralds()), 19, 75, WHITE);
        line(g, Component.translatable("gui.avemujica.fans", menu.fans()), 19, 92, WHITE);
        line(g, Component.translatable("gui.avemujica.fame", menu.fame()), 19, 109, WHITE);
        line(g, Component.translatable("gui.avemujica.shows", menu.shows()), 125, 109, WHITE);
        line(g, Component.translatable("gui.avemujica.level", menu.bandLevel(), menu.bandXp(),
                menu.nextXp() == 0 ? "MAX" : String.valueOf(menu.nextXp())), 19, 121, GOLD);
        statBar(g, 19, 145, "gui.avemujica.skill", menu.skill(), 0xFFAB81DA);
        statBar(g, 19, 172, "gui.avemujica.bond", menu.bond(), 0xFF80C2A1);
        statBar(g, 19, 199, "gui.avemujica.morale", menu.morale(), 0xFFE2B173);
        line(g, menu.noteBlocks() >= 99
                ? Component.literal("成员 " + menu.members() + "/5  · 大型舞台")
                : Component.literal("成员 " + menu.members() + "/5  · 可在任意地点排练"),
                19, 227, MUTED);
        line(g, Component.translatable("gui.avemujica.touring_stats", menu.visited(), menu.tourShows(),
                menu.averageTraining()), 19, 242, GOLD);
        Component readiness;
        int color = GREEN;
        if (!menu.inOverworld()) {
            readiness = Component.translatable("gui.avemujica.overworld_required"); color = 0xFFFFA7A1;
        } else if (!menu.stageReady()) {
            readiness = Component.literal("需要五位队员在玩家附近"); color = 0xFFE2B173;
        } else if (menu.performing()) {
            readiness = Component.translatable("gui.avemujica.show.running", menu.showRemaining());
        } else {
            readiness = Component.literal("五位队员到齐，可开始演奏");
        }
        line(g, readiness, 19, 258, color);
        line(g, Component.literal(menu.villageHint()
                ? "村庄已识别：" + menu.villagePopulation() + "村民 · 可巡演"
                : "村庄：" + menu.villagePopulation() + "村民 / " + menu.villageBeds()
                        + "床 / 钟" + (menu.villageBell() ? "有" : "无")),
                19, 271, menu.villageHint() ? GREEN : MUTED);
        line(g, Component.translatable("gui.avemujica.operations"), 242, 57, GOLD);
        line(g, Component.translatable("gui.avemujica.footer"), 15, 286, MUTED);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1F);
        int localMouseX = Math.round(mouseX / uiScale), localMouseY = Math.round(mouseY / uiScale);
        super.render(graphics, localMouseX, localMouseY, partialTick);
        this.renderTooltip(graphics, localMouseX, localMouseY);
        graphics.pose().popPose();
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
