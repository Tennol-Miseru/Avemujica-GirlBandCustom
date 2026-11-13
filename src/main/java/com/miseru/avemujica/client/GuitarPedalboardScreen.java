package com.miseru.avemujica.client;

import com.miseru.avemujica.music.GuitarPedalboard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Per-player GUI for Uika / Mutsumi. Settings are saved by the server. */
public final class GuitarPedalboardScreen extends ScaledMujicaScreen {
    private final List<String> roles;
    private final List<String> selected;
    private final List<Integer> mixes;
    private final List<Button> presetButtons = new ArrayList<>();
    private final List<Button> mixButtons = new ArrayList<>();
    private int left, top;
    private static final int[] MIX_VALUES = {0, 25, 50, 75, 100};

    public GuitarPedalboardScreen(List<String> roles, List<String> presets, List<Integer> mixes) {
        super(Component.literal("Ave Mujica · 双吉他效果器"));
        this.roles = List.copyOf(roles);
        this.selected = new ArrayList<>(presets);
        this.mixes = new ArrayList<>(mixes);
    }

    @Override protected void init() {
        layout(392, 248);
        left = uiLeft();
        top = uiTop();
        presetButtons.clear(); mixButtons.clear();
        for (int i = 0; i < roles.size(); i++) {
            final int row = i;
            int y = top + 67 + i * 76;
            presetButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> cycle(row))
                    .bounds(left + 116, y, 251, 22).build()));
            mixButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> nextMix(row))
                    .bounds(left + 116, y + 25, 123, 21).build()));
            addRenderableWidget(Button.builder(Component.literal("试听"), b -> send("avemujica midi guitar preview " + roles.get(row)))
                    .bounds(left + 245, y + 25, 122, 21).build());
        }
        addRenderableWidget(Button.builder(Component.literal("恢复两人原版音色"), b -> send("avemujica midi guitar reset"))
                .bounds(left + 20, top + 208, 153, 23)
                .tooltip(Tooltip.create(Component.literal("清空效果器设置，回到当前 v1.1.7 两人已有音色。"))).build());
        addRenderableWidget(Button.builder(Component.literal("返回乐器设置"), b -> send("avemujica midi instrument ui"))
                .bounds(left + 204, top + 208, 166, 23).build());
        refresh();
    }
    private void cycle(int row) {
        int old = GuitarPedalboard.PRESETS.indexOf(selected.get(row));
        String preset = GuitarPedalboard.PRESETS.get((old + 1) % GuitarPedalboard.PRESETS.size());
        selected.set(row, preset);
        send("avemujica midi guitar set " + roles.get(row) + " " + preset);
        refresh();
    }
    private void nextMix(int row) {
        int current = mixes.get(row);
        int next = MIX_VALUES[0];
        for (int value : MIX_VALUES) if (value > current) { next = value; break; }
        mixes.set(row, next);
        send("avemujica midi guitar mix " + roles.get(row) + " " + next);
        refresh();
    }
    private void refresh() {
        for (int i = 0; i < selected.size(); i++) {
            int index = GuitarPedalboard.PRESETS.indexOf(selected.get(i));
            if (index < 0) index = 0;
            presetButtons.get(i).setMessage(Component.literal(GuitarPedalboard.LABELS.get(index) + "  >"));
            mixButtons.get(i).setMessage(Component.literal("效果混合 " + mixes.get(i) + "%"));
            mixButtons.get(i).active = index != 0;
        }
    }
    private void send(String command) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null)
            minecraft.player.connection.sendCommand(command);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        beginUi(g); // Calls the no-blur override below.
        OctopusSkin.drawPanelFrame(g, left, top, 392, 248, 39);
        g.drawCenteredString(font, title, left + 196, top + 13, OctopusTheme.current().secondary);
        g.drawString(font, "默认旁通不改变现有吉他音色；预设只影响声音，不增加演奏动作", left + 15, top + 48, OctopusTheme.current().muted, false);
        for (int i = 0; i < roles.size(); i++) {
            int y = top + 67 + i * 76;
            g.fill(left + 12, y - 5, left + 380, y + 54, (i % 2 == 0) ? OctopusSkin.rowA() : OctopusSkin.rowB());
            g.drawString(font, roles.get(i).equals("doloris_guitar") ? "初华·电吉他" : "睦·电吉他", left + 27, y + 6, OctopusTheme.current().text, false);
            int index = GuitarPedalboard.PRESETS.indexOf(selected.get(i));
            if (index < 0) index = 0;
            g.drawString(font, GuitarPedalboard.DESCRIPTIONS.get(index), left + 26, y + 49, OctopusTheme.current().muted, false);
        }
        super.render(g, logicalMouse(mouseX), logicalMouse(mouseY), partialTick);
        OctopusSkin.drawCenteredLogo(g, left, top, 392, 248);
        endUi(g);
    }
    /** Bypass Minecraft's screen background blur shader; keep the GUI and live world sharp. */
    @Override protected void renderUiBackdrop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // A flat translucent veil has no Gaussian/postprocess blur, including under Iris.
        OctopusSkin.drawBackdrop(g, width, height);
    }

    @Override public boolean isPauseScreen() { return false; }
}
