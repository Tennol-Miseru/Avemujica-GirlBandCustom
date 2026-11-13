package com.miseru.avemujica.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Camera photo drafts can be posted later; the player chooses when to publish. */
public final class CameraDraftScreen extends ScaledMujicaScreen {
    private static final int PAGE_SIZE = 8;
    private List<Path> drafts = List.of();
    private int page;
    private int left, top;
    public CameraDraftScreen() { super(Component.literal("Mujica · 照相机草稿")); }
    @Override protected void init() {
        layout(324, 292);
        left = uiLeft(); top = uiTop();
        Path folder = FabricLoader.getInstance().getGameDir().resolve("screenshots")
                .resolve("avemujica_tours").resolve("camera_drafts");
        try (Stream<Path> entries = Files.isDirectory(folder) ? Files.list(folder) : Stream.empty()) {
            drafts = entries.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("[0-9a-f]{32}\\.png"))
                    .sorted(Comparator.comparingLong(CameraDraftScreen::modified).reversed())
                    .limit(128).toList();
        } catch (IOException ex) { drafts = List.of(); }
        page = Math.min(page, Math.max(0, (drafts.size()-1) / PAGE_SIZE));
        for (int i=0;i<PAGE_SIZE;i++) {
            int index=page*PAGE_SIZE+i;
            if (index>=drafts.size()) break;
            final Path path = drafts.get(index);
            addRenderableWidget(Button.builder(Component.literal("照片 #"+(index+1)+" · "+path.getFileName().toString().substring(0,8)), b -> {
                if (minecraft != null) minecraft.setScreen(new CameraComposeScreen(
                        path.getFileName().toString().substring(0,32), path));
            }).bounds(left+16,top+34+i*27,292,24).build());
        }
        addRenderableWidget(Button.builder(Component.literal("上一页"), b -> {
            page=Math.max(0,page-1); rebuildWidgets();
        }).bounds(left+16,top+259,90,22).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("下一页"), b -> {
            page=Math.min((drafts.size()-1)/PAGE_SIZE,page+1); rebuildWidgets();
        }).bounds(left+116,top+259,90,22).build()).active=(page+1)*PAGE_SIZE<drafts.size();
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(left+216,top+259,92,22).build());
    }
    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ex) { return 0; }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float t) {
        renderBackground(g,mx,my,t);
        beginUi(g);
        OctopusSkin.drawPanelFrame(g, left, top, 324, 292, 34);
        g.drawCenteredString(font,"Mujica · 我的摄影草稿",left+162,top+11,OctopusTheme.current().text);
        if (drafts.isEmpty()) g.drawCenteredString(font,"还没有草稿。右键照相机拍摄一张吧。",left+162,top+128,OctopusTheme.current().muted);
        super.render(g,logicalMouse(mx),logicalMouse(my),t);
        OctopusSkin.drawCenteredLogo(g, left, top, 324, 292);
        endUi(g);
    }
    @Override protected void renderUiBackdrop(GuiGraphics g,int mx,int my,float t) {
        OctopusSkin.drawBackdrop(g, width, height);
    }
    @Override public boolean isPauseScreen() { return false; }
}
