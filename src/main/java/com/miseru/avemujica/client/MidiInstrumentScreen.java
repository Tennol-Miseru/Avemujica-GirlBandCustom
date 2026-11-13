package com.miseru.avemujica.client;

import com.miseru.avemujica.entity.MemberRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Instrument preferences for the player's band. Server stores and applies all changes. */
public final class MidiInstrumentScreen extends ScaledMujicaScreen {
    private static final List<String> PRESETS = List.of("auto", "guitar", "electric", "electric_soft", "ogg_custom", "pling",
            "harp", "bass", "bit", "flute", "chime", "bell", "xylophone", "iron_xylophone", "banjo", "didgeridoo", "drums");
    private static final List<String> NAMES = List.of("依 MIDI 自动", "木吉他", "电吉他（明显）", "电吉他（柔和）", "自选 OGG 电吉他", "Pling",
            "竖琴/弦乐", "贝斯", "Bit 电子", "长笛", "风铃", "铃铛", "木琴", "铁木琴", "班卓琴", "迪吉里杜", "鼓组");
    private final List<String> roles;
    private final List<String> settings;
    private final List<Integer> volumes;
    private final List<Button> lowerVolumes = new ArrayList<>();
    private final List<Button> upperVolumes = new ArrayList<>();
    private final List<Button> choices = new ArrayList<>();
    private final List<Button> backwards = new ArrayList<>();
    private int left, top, page;
    private static final int PAGE_SIZE = 6;
    private Button previous, next;

    public MidiInstrumentScreen(List<String> roles, List<String> settings, List<Integer> volumes) {
        super(Component.literal("Ave Mujica · 成员乐器设置"));
        this.roles = List.copyOf(roles);
        this.settings = new ArrayList<>(settings);
        this.volumes = new ArrayList<>(volumes);
    }

    @Override protected void init() {
        layout(348, 285);
        left = uiLeft(); top = uiTop();
        choices.clear(); backwards.clear(); lowerVolumes.clear(); upperVolumes.clear();
        for (int i = 0; i < PAGE_SIZE; i++) {
            final int row = i;
            int rowY = top + 64 + i * 24;
            backwards.add(addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(page * PAGE_SIZE + row, -1))
                    .bounds(left + 102, rowY, 21, 22).build()));
            choices.add(addRenderableWidget(Button.builder(Component.empty(), b -> cycle(page * PAGE_SIZE + row, 1))
                    .bounds(left + 126, rowY, 128, 22).build()));
            lowerVolumes.add(addRenderableWidget(Button.builder(Component.literal("−"), b -> volumeCycle(page * PAGE_SIZE + row, -10))
                    .bounds(left + 259, rowY, 21, 22).build()));
            upperVolumes.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> volumeCycle(page * PAGE_SIZE + row, 10))
                    .bounds(left + 312, rowY, 21, 22).build()));
        }
        addRenderableWidget(Button.builder(Component.literal("初华人声 OGG"), b ->
                minecraft.setScreen(new MortisOggScreen(this, "doloris")))
                .bounds(left + 77, top + 37, 79, 13).build());
        addRenderableWidget(Button.builder(Component.literal("初华吉他 OGG"), b ->
                minecraft.setScreen(new MortisOggScreen(this, "doloris_guitar")))
                .bounds(left + 158, top + 37, 87, 13).build());
        addRenderableWidget(Button.builder(Component.literal("睦吉他 OGG"), b ->
                minecraft.setScreen(new MortisOggScreen(this, "mortis")))
                .bounds(left + 247, top + 37, 82, 13).build());
        previous = addRenderableWidget(Button.builder(Component.literal("◀"), b -> {page--; updateButtons();})
                .bounds(left + 12, top + 37, 28, 13).build());
        next = addRenderableWidget(Button.builder(Component.literal("▶"), b -> {page++; updateButtons();})
                .bounds(left + 43, top + 37, 28, 13).build());
        addRenderableWidget(Button.builder(Component.literal("试听初华人声"), b -> send("avemujica midi instrument preview doloris"))
                .bounds(left + 15, top + 229, 147, 13).build());
        addRenderableWidget(Button.builder(Component.literal("试听初华吉他"), b -> send("avemujica midi instrument preview doloris_guitar"))
                .bounds(left + 171, top + 229, 157, 13).build());
        addRenderableWidget(Button.builder(Component.literal("重置乐器"), b -> send("avemujica midi instrument reset"))
                .bounds(left + 12, top + 247, 99, 22).build());
        addRenderableWidget(Button.builder(Component.literal("双吉他效果器"), b -> send("avemujica midi guitar ui"))
                .bounds(left + 115, top + 247, 125, 22).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(left + 246, top + 247, 83, 22).build());
        updateButtons();
    }

    private void cycle(int i, int delta) {
        if (i < 0 || i >= settings.size()) return;
        // Initial singer's MIDI voice has a two-state choice: vanilla HARP / custom vowel OGG.
        // Do not expose guitar/effects presets to the singing part.
        if (roles.get(i).equals("doloris")) {
            String selected = "ogg_custom".equals(settings.get(i)) ? "harp" : "ogg_custom";
            settings.set(i, selected);
            send("avemujica midi instrument set doloris " + selected);
            updateButtons();
            return;
        }
        String current = settings.get(i);
        int pos = PRESETS.indexOf(current);
        int next = (pos < 0 ? 0 : pos);
        String selected;
        do {
            next = Math.floorMod(next + delta, PRESETS.size());
            selected = PRESETS.get(next);
        } while ((roles.get(i).equals("doloris_guitar")
                    && !List.of("guitar", "electric", "electric_soft", "ogg_custom").contains(selected))
                || (!roles.get(i).equals("mortis") && !roles.get(i).equals("doloris_guitar")
                    && selected.startsWith("ogg_")));
        settings.set(i, selected);
        send("avemujica midi instrument set " + roles.get(i) + " " + selected);
        updateButtons();
    }

    private void volumeCycle(int index, int delta) {
        if (index < 0 || index >= volumes.size()) return;
        int next = Math.max(0, Math.min(200, volumes.get(index) + delta));
        if (next == volumes.get(index)) return;
        volumes.set(index, next);
        send("avemujica midi instrument volume " + roles.get(index) + " " + next);
        updateButtons();
    }

    private void updateButtons() {
        for (int i = 0; i < choices.size(); i++) {
            int roleIndex = page * PAGE_SIZE + i;
            boolean active = roleIndex < roles.size();
            choices.get(i).visible = active;
            backwards.get(i).visible = active;
            lowerVolumes.get(i).visible = active;
            upperVolumes.get(i).visible = active;
            if (!active) continue;
            boolean fixedVocal = roles.get(roleIndex).equals("doloris");
            lowerVolumes.get(i).active = volumes.get(roleIndex) > 0;
            upperVolumes.get(i).active = volumes.get(roleIndex) < 200;
            backwards.get(i).active = true;
            choices.get(i).active = true;
            if (fixedVocal) {
                choices.get(i).setMessage(Component.literal(
                        "ogg_custom".equals(settings.get(roleIndex)) ? "自选 OGG 人声  >" : "默认竖琴  >"));
                continue;
            }
            int index = PRESETS.indexOf(settings.get(roleIndex));
            choices.get(i).setMessage(Component.literal((index < 0 ? settings.get(roleIndex) : NAMES.get(index)) + "  >"));
        }
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = (page + 1) * PAGE_SIZE < roles.size();
    }

    private void send(String command) {
        if (minecraft != null && minecraft.player != null)
            minecraft.player.connection.sendCommand(command);
    }

    private static String label(String roleId) {
        if (roleId.equals("doloris_guitar")) return "初华 · 电吉他";
        if (roleId.equals("doloris")) return "初华 · 人声";
        for (MemberRole role : MemberRole.values()) if (role.id.equals(roleId)) return role.display.split(" · ")[0];
        return roleId;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        beginUi(g); // Calls the no-blur override below.
        OctopusSkin.drawPanelFrame(g, left, top, 348, 285, 37);
        g.drawCenteredString(font, title, left + 174, top + 13, OctopusTheme.current().text);
        for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < roles.size(); i++) {
            int idx = page * PAGE_SIZE + i;
            g.drawString(font, label(roles.get(idx)), left + 12, top + 70 + i * 24, OctopusTheme.current().text, false);
            g.drawCenteredString(font, volumes.get(idx) + "%", left + 296, top + 71 + i * 24, OctopusTheme.current().text);
        }
        g.drawString(font, (page + 1) + "/" + Math.max(1, (roles.size() + PAGE_SIZE - 1) / PAGE_SIZE),
                left + 82, top + 39, OctopusTheme.current().secondary, false);
        g.drawString(font, "独立音量 0～200% · 人声可选 OGG", left + 15, top + 212, OctopusTheme.current().muted, false);
        super.render(g, logicalMouse(mouseX), logicalMouse(mouseY), partialTick);
        OctopusSkin.drawCenteredLogo(g, left, top, 348, 285);
        endUi(g);
    }
    /** Bypass Minecraft's screen background blur shader; keep the GUI and live world sharp. */
    @Override protected void renderUiBackdrop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // A flat translucent veil has no Gaussian/postprocess blur, including under Iris.
        OctopusSkin.drawBackdrop(g, width, height);
    }

    @Override public boolean isPauseScreen() { return false; }
}
