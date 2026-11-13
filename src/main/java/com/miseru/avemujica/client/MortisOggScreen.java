package com.miseru.avemujica.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** In-game selector for Mortis's user-owned Vorbis files (client only). */
public final class MortisOggScreen extends ScaledMujicaScreen {
    private static final String CUSTOM_PACK = "file/AveMujica_Custom_Assets";
    private final Screen previous;
    private final String roleId;
    private final Path gameDir = FabricLoader.getInstance().getGameDir();
    private EditBox pathField;
    private List<Path> samples = List.of();
    private String status = "采样基准音 A2 / 110Hz；推荐 0.3–2 秒的单音 OGG Vorbis";
    private int left, top;

    public MortisOggScreen(Screen previous) {
        this(previous, "mortis");
    }

    public MortisOggScreen(Screen previous, String roleId) {
        super(Component.literal(roleId.equals("doloris") ? "三角初华 · OGG 演唱人声"
                : roleId.equals("doloris_guitar") ? "三角初华 · OGG 电吉他音色"
                : "若叶睦 · OGG 电吉他音色"));
        this.previous = previous;
        this.roleId = roleId.equals("doloris") || roleId.equals("doloris_guitar") ? roleId : "mortis";
        if (isVocal()) status = "人声基准音 A3 / 220Hz；推荐 0.3–2 秒的单元音 OGG Vorbis";
    }

    private boolean isVocal() { return roleId.equals("doloris"); }

    @Override protected void init() {
        layout(388, 253);
        left = uiLeft();
        top = uiTop();
        pathField = new EditBox(font, left + 12, top + 49, 363, 20,
                Component.literal("OGG 文件完整路径"));
        pathField.setMaxLength(1024);
        pathField.setHint(Component.literal("粘贴本机 .ogg 完整路径，或使用下方文件列表"));
        addRenderableWidget(pathField);
        addRenderableWidget(Button.builder(Component.literal("导入文件并使用"), b -> {
            String value = pathField.getValue().trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
                value = value.substring(1, value.length() - 1);
            if (value.isEmpty()) { status = "请先填写文件路径，或点击下方文件"; return; }
            try { importSample(Path.of(value)); }
            catch (Exception e) { status = "导入失败：" + e.getMessage(); }
        }).bounds(left + 12, top + 76, 126, 22).build());
        addRenderableWidget(Button.builder(Component.literal("打开音色文件夹"), b -> {
            try {
                Path folder = isVocal() ? CustomAssetImporter.vocalSamplesDir(gameDir)
                        : CustomAssetImporter.guitarSamplesDir(gameDir);
                Files.createDirectories(folder);
                Util.getPlatform().openFile(folder.toFile());
                status = "将 OGG 文件复制到目录，点击刷新列表后选用";
            } catch (Exception e) { status = "无法打开目录：" + e.getMessage(); }
        }).bounds(left + 146, top + 76, 113, 22).build());
        addRenderableWidget(Button.builder(Component.literal("刷新文件列表"), b -> {
            minecraft.setScreen(new MortisOggScreen(previous, roleId));
        }).bounds(left + 268, top + 76, 107, 22).build());
        refreshSamples();
        for (int i = 0; i < 4; i++) {
            int index = i;
            Path file = i < samples.size() ? samples.get(i) : null;
            if (file == null) break;
            String filename = file.getFileName().toString();
            String label = filename.length() > 38 ? filename.substring(0, 35) + "..." : filename;
            addRenderableWidget(Button.builder(Component.literal("导入：" + label), b -> {
                try { importSample(file); }
                catch (Exception e) { status = "导入失败：" + e.getMessage(); }
            }).bounds(left + 12, top + 105 + index * 23, 363, 21).build());
        }
        addRenderableWidget(Button.builder(Component.literal(isVocal() ? "默认竖琴" : "原版电吉他"),
                b -> setPreset(isVocal() ? "harp" : "electric"))
                .bounds(left + 12, top + 203, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal(isVocal() ? "自选 OGG 人声" : "自选 OGG"), b -> setPreset("ogg_custom"))
                .bounds(left + 148, top + 203, 136, 20).build());
        addRenderableWidget(Button.builder(Component.literal("试听"), b -> send("avemujica midi instrument preview " + roleId))
                .bounds(left + 290, top + 203, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> minecraft.setScreen(previous))
                .bounds(left + 335, top + 203, 40, 20).build());
    }

    private void refreshSamples() {
        try {
            CustomAssetImporter.prepare(gameDir);
            Path dir = isVocal() ? CustomAssetImporter.vocalSamplesDir(gameDir)
                    : CustomAssetImporter.guitarSamplesDir(gameDir);
            try (Stream<Path> stream = Files.list(dir)) {
                samples = stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                        .sorted().limit(4).toList();
            }
        } catch (Exception e) { status = "扫描音色目录失败：" + e.getMessage(); }
    }

    private void importSample(Path file) throws Exception {
        if (isVocal()) CustomAssetImporter.selectVocalOgg(gameDir, file);
        else CustomAssetImporter.selectGuitarOgg(gameDir, file, roleId);
        CustomAssetImporter.Result result = CustomAssetImporter.install(gameDir);
        Path packaged = result.pack().resolve("assets/avemujica/sounds/instruments/" +
                (isVocal() ? "uika_vocal_custom.ogg"
                        : roleId.equals("doloris_guitar") ? "uika_guitar_custom.ogg"
                        : "mortis_guitar_custom.ogg"));
        if (!Files.isRegularFile(packaged)) throw new IllegalStateException("自定义 OGG 未写入资源包");
        // Re-scan Minecraft's pack repository; enable this generated resource pack
        // if the player has never manually selected it. Do not change other packs.
        PackRepository repository = minecraft.getResourcePackRepository();
        repository.reload();
        if (!repository.isAvailable(CUSTOM_PACK)) {
            status = "导入成功，但未找到资源包；请在设置→资源包中启用 AveMujica_Custom_Assets";
            return;
        }
        repository.addPack(CUSTOM_PACK);
        minecraft.options.updateResourcePacks(repository);
        minecraft.reloadResourcePacks();
        setPreset("ogg_custom");
        status = "已导入 " + file.getFileName() + "；正在重载 OGG 资源，请稍后试听";
    }

    private void setPreset(String name) {
        send("avemujica midi instrument set " + roleId + " " + name);
        if (!name.equals("ogg_custom")) status = "已切换为 " + name + "；原有音色与效果器设置不变";
    }

    private void send(String command) {
        if (minecraft != null && minecraft.player != null)
            minecraft.player.connection.sendCommand(command);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        beginUi(graphics);
        OctopusSkin.drawPanelFrame(graphics, left, top, 388, 253, 38);
        graphics.drawCenteredString(font, title, left + 194, top + 12, OctopusTheme.current().text);
        graphics.drawString(font, isVocal() ? "本地 OGG (≤20 MB) | A3 单音 | 仅影响初华的演唱轨"
                : "本地 OGG (≤20 MB) | A2 单音 | 仅影响当前吉他", left + 12, top + 39, OctopusTheme.current().muted, false);
        String info = font.plainSubstrByWidth(status, 360);
        graphics.drawString(font, info, left + 12, top + 231, OctopusTheme.current().secondary, false);
        super.render(graphics, logicalMouse(mouseX), logicalMouse(mouseY), partialTick);
        OctopusSkin.drawCenteredLogo(graphics, left, top, 388, 253);
        endUi(graphics);
    }

    @Override protected void renderUiBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        OctopusSkin.drawBackdrop(graphics, width, height);
    }

    @Override public boolean isPauseScreen() { return false; }
}
