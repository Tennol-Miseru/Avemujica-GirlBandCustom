package com.miseru.avemujica.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Small paged local-file picker; only lists regular files in the opt-in candidates folder. */
public final class CharacterCandidateScreen extends ScaledMujicaScreen {
    private static final int W=416,H=290;
    private final CharacterCustomizationScreen previous;
    private final String suffix;
    private final Path folder=CustomAssetImporter.candidateDir(FabricLoader.getInstance().getGameDir());
    private List<Path> files=List.of();
    private int page=0,left,top;
    private String status="将素材复制到候选文件夹，刷新后点击对应文件";

    public CharacterCandidateScreen(CharacterCustomizationScreen previous,String suffix) {
        super(Component.literal("选择自定义"+(suffix.equals(".png")?"皮肤":"语音")+"文件"));
        this.previous=previous;
        this.suffix=suffix.equals(".png")?".png":".ogg";
    }
    private void scan() {
        try {
            Files.createDirectories(folder);
            try(Stream<Path> stream=Files.list(folder)) {
                files=stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                        .filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                        .sorted().limit(512).toList();
            }
            page=Math.min(page,Math.max(0,(files.size()-1)/6));
        } catch(Exception e) {files=List.of();status="读取文件失败："+e.getMessage();}
    }
    @Override protected void init() {
        layout(W,H);left=uiLeft();top=uiTop();scan();
        for(int i=0;i<6;i++) {
            final int idx=page*6+i;
            if(idx>=files.size()) break;
            Path file=files.get(idx);
            String name=file.getFileName().toString();
            String shown=font.plainSubstrByWidth(name,345);
            addRenderableWidget(Button.builder(Component.literal(shown),b->{
                minecraft.setScreen(previous);
                previous.chooseCandidate(suffix,file);
            }).bounds(left+12,top+49+i*27,W-24,23).build());
        }
        addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page=Math.max(0,page-1);rebuildWidgets();})
                .bounds(left+12,top+222,75,22).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page=Math.min(Math.max(0,(files.size()-1)/6),page+1);rebuildWidgets();})
                .bounds(left+94,top+222,75,22).build());
        addRenderableWidget(Button.builder(Component.literal("打开素材目录"),b->{
            try {Files.createDirectories(folder);Util.getPlatform().openFile(folder.toFile());}
            catch(Exception e){status="无法打开目录："+e.getMessage();}
        }).bounds(left+176,top+222,112,22).build());
        addRenderableWidget(Button.builder(Component.literal("刷新"),b->rebuildWidgets())
                .bounds(left+295,top+222,47,22).build());
        addRenderableWidget(Button.builder(Component.literal("返回"),b->minecraft.setScreen(previous))
                .bounds(left+349,top+222,55,22).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g,mx,my,partial);beginUi(g);
        OctopusSkin.drawPanelFrame(g,left,top,W,H,36);
        g.drawString(font,"本地文件选择 · "+suffix.toUpperCase(Locale.ROOT)+" (第 "+(page+1)+" 页)",left+12,top+12,OctopusTheme.current().text,false);
        if(files.isEmpty()) g.drawString(font,"没有候选文件，请先将素材放入候选文件夹",left+14,top+86,OctopusTheme.current().secondary,false);
        g.drawString(font,font.plainSubstrByWidth(status,390),left+13,top+258,OctopusTheme.current().muted,false);
        super.render(g,logicalMouse(mx),logicalMouse(my),partial);
        OctopusSkin.drawCenteredLogo(g, left, top, W, H);
        endUi(g);
    }
    @Override protected void renderUiBackdrop(GuiGraphics g,int mx,int my,float tick) {
        OctopusSkin.drawBackdrop(g, width, height);
    }
    @Override public boolean isPauseScreen(){return false;}
}
