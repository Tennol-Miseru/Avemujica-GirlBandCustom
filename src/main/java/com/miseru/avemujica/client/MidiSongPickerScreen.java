package com.miseru.avemujica.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.Util;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Song names are supplied by the server; clicking a song calls its validated MIDI command. */
public final class MidiSongPickerScreen extends ScaledMujicaScreen {
    private static final int ROWS = 8;
    private final int memberId;
    private final List<String> serverSongs;
    private final List<String> filtered = new ArrayList<>();
    private final Button[] songButtons = new Button[ROWS];
    private int page = 0;
    private int left, top;
    private EditBox search;
    private Button previous, next;

    public MidiSongPickerScreen(int memberId, List<String> songs) {
        super(Component.literal(memberId < 0 ? "Ave Mujica · 全队统一 MIDI 选曲" : "Ave Mujica · 成员独奏选曲"));
        this.memberId = memberId;
        this.serverSongs = List.copyOf(songs);
    }

    @Override protected void init() {
        layout(354, 314);
        left = uiLeft();
        top = uiTop();
        search = new EditBox(font, left + 14, top + 40, 326, 20, Component.literal("查找 MIDI 文件名"));
        search.setMaxLength(100);
        search.setHint(Component.literal("按文件名搜索（.mid / .midi）"));
        search.setResponder(query -> { page = 0; updateRows(); });
        addRenderableWidget(search);
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            songButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                int index = page * ROWS + row;
                if (index < filtered.size()) play(filtered.get(index));
            }).bounds(left + 14, top + 66 + i * 21, 326, 20).build());
        }
        previous = addRenderableWidget(Button.builder(Component.literal("上一页"), b -> {
            if (page > 0) { page--; updateRows(); }
        }).bounds(left + 14, top + 251, 75, 21).build());
        next = addRenderableWidget(Button.builder(Component.literal("下一页"), b -> {
            if ((page + 1) * ROWS < filtered.size()) { page++; updateRows(); }
        }).bounds(left + 94, top + 251, 75, 21).build());
        addRenderableWidget(Button.builder(Component.literal("打开 MIDI 目录"), b -> openLocalMidiFolder())
                .bounds(left + 14, top + 276, 155, 18)
                .tooltip(Tooltip.create(Component.literal("打开当前客户端的 config/avemujica/midi。单机可直接放文件；远程服务器须上传到服务器同名目录。"))).build());
        addRenderableWidget(Button.builder(Component.literal("成员乐器设置"), b -> sendCommand("avemujica midi instrument ui"))
                .bounds(left + 177, top + 276, 78, 18)
                .tooltip(Tooltip.create(Component.literal("设置五名成员使用的音符盒乐器。"))).build());
        addRenderableWidget(Button.builder(Component.literal("吉他效果器"), b -> sendCommand("avemujica midi guitar ui"))
                .bounds(left + 261, top + 276, 79, 18)
                .tooltip(Tooltip.create(Component.literal("分别为初华和睦选择效果器预设，并可试听。"))).build());
        addRenderableWidget(Button.builder(Component.literal("停止播放"), b -> sendCommand("avemujica midi stop"))
                .bounds(left + 187, top + 251, 73, 21).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(left + 266, top + 251, 74, 21).build());
        updateRows();
        setInitialFocus(search);
    }

    private void updateRows() {
        filtered.clear();
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).strip();
        for (String name : serverSongs) {
            String shown = name.equals("default") ? "KillKiss（模组内置）" : name;
            if (shown.toLowerCase(Locale.ROOT).contains(query)) filtered.add(name);
        }
        if (page > 0 && page * ROWS >= filtered.size()) page = Math.max(0, (filtered.size() - 1) / ROWS);
        for (int i = 0; i < ROWS; i++) {
            Button button = songButtons[i];
            if (button == null) continue;
            int index = page * ROWS + i;
            button.visible = index < filtered.size();
            if (!button.visible) continue;
            String song = filtered.get(index);
            String label = song.equals("default") ? "♪ KillKiss（模组内置）" : "♪ " + song;
            if (label.length() > 42) label = label.substring(0, 39) + "...";
            button.setMessage(Component.literal(label));
            button.setTooltip(Tooltip.create(Component.literal(song.equals("default") ? "KillKiss（模组内置）" : song)));
        }
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = (page + 1) * ROWS < filtered.size();
    }

    private void openLocalMidiFolder() {
        if (minecraft == null) return;
        // Safe, fixed, client-local path. No server-supplied filename is opened.
        Path folder = minecraft.gameDirectory.toPath().resolve("config")
                .resolve("avemujica").resolve("midi");
        try {
            Files.createDirectories(folder);
            Util.getPlatform().openFile(folder.toFile());
        } catch (IOException | SecurityException ex) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(Component.literal("MIDI 文件夹无法打开：" + folder), false);
        }
    }

    private void sendCommand(String command) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null)
            minecraft.player.connection.sendCommand(command);
    }

    private void play(String filename) {
        // Validate even server-supplied entries. Never let a UI callback throw
        // an unchecked exception back into the render/input event handler.
        if (filename == null || filename.isBlank() || filename.length() > 100) {
            if (minecraft != null && minecraft.player != null)
                minecraft.player.displayClientMessage(Component.literal("无效的 MIDI 曲目名称。"), false);
            return;
        }
        try {
            String args = StringArgumentType.escapeIfRequired(filename);
            if (memberId == -1) sendCommand("avemujica midi play " + args);
            else if (memberId < -1) sendCommand("avemujica midi play_team " + (-memberId - 2) + " " + args);
            else sendCommand("avemujica midi play_member " + memberId + " " + args);
            onClose();
        } catch (RuntimeException ex) {
            System.err.println("[AveMujica/MIDI] Song picker failed to select " + filename);
            ex.printStackTrace(System.err);
            if (minecraft != null && minecraft.player != null)
                minecraft.player.displayClientMessage(Component.literal("MIDI 选曲出错，请查看 latest.log。"), false);
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        beginUi(g); // Calls the no-blur override below.
        OctopusSkin.drawPanelFrame(g, left, top, 354, 314, 34);
        g.drawCenteredString(font, title, left + 177, top + 13, OctopusTheme.current().text);
        g.drawString(font, "服务器曲库：" + serverSongs.size() + " 首 | " + (page + 1) + "/"
                + Math.max(1, (filtered.size() + ROWS - 1) / ROWS), left + 14, top + 237, OctopusTheme.current().muted, false);
        
        super.render(g, logicalMouse(mouseX), logicalMouse(mouseY), partialTick);
        OctopusSkin.drawCenteredLogo(g, left, top, 354, 314);
        endUi(g);
    }

    /** Bypass Minecraft's screen background blur shader; keep the GUI and live world sharp. */
    @Override protected void renderUiBackdrop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // A flat translucent veil has no Gaussian/postprocess blur, including under Iris.
        OctopusSkin.drawBackdrop(g, width, height);
    }

    @Override public boolean isPauseScreen() { return false; }
}
