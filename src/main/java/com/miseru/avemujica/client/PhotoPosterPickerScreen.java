package com.miseru.avemujica.client;

import com.miseru.avemujica.career.CameraPhotoStorage;
import com.miseru.avemujica.network.CameraPhotoRequestPayload;
import com.miseru.avemujica.network.PosterChoosePayload;
import com.miseru.avemujica.network.PosterConfigurePayload;
import com.miseru.avemujica.network.PosterOpenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Photo stand editor: choose image, reserve 1..4 by 1..4 blocks and crop by dragging. */
public final class PhotoPosterPickerScreen extends ScaledMujicaScreen {
    private static final int PANEL_WIDTH = 640, PANEL_HEIGHT = 420;
    private record Entry(String id,Path local,boolean published) { }
    private final BlockPos pos;
    private final List<String> publishedIds;
    private final String currentId;
    private final boolean currentPublished;
    private List<Entry> entries=List.of();
    private Entry selected;
    private TourPhotoCapture.Preview preview;
    private boolean publishedTab;
    private int page,rows;
    private int x,y,panelWidth,panelHeight,listWidth;
    private int previewX,previewY,previewW,previewH;
    private int standWidth,standHeight;
    private float zoom,centerX,centerY;
    private boolean initialSelection;

    public PhotoPosterPickerScreen(PosterOpenPayload packet) {
        super(Component.literal("Mujica · 照片立牌编辑器"));
        pos=packet.pos(); publishedIds=List.copyOf(packet.publishedIds());
        currentId=packet.photoId(); currentPublished=packet.photoPublished();
        standWidth=Math.max(1,Math.min(4,packet.width()));
        standHeight=Math.max(1,Math.min(4,packet.height()));
        zoom=PosterCrop.clamp(packet.zoom(),1,4);
        centerX=PosterCrop.clamp(packet.centerX(),0,1);
        centerY=PosterCrop.clamp(packet.centerY(),0,1);
        publishedTab=currentPublished;
    }
    @Override protected void init() {
        // Stable one-page virtual canvas. Never shrink only the panel while leaving
        // buttons at their original sizes: that caused controls to cover the preview.
        layout(PANEL_WIDTH, PANEL_HEIGHT);
        panelWidth = PANEL_WIDTH; panelHeight = PANEL_HEIGHT;
        x = uiLeft(); y = uiTop();
        listWidth = 212;
        rows = 7;
        reloadList();
        if (!initialSelection) {
            initialSelection = true;
            for (Entry entry:entries) if (entry.id().equals(currentId) && entry.published()==currentPublished) {
                select(entry,false); break;
            }
        }
        button("本地草稿", x+12, y+38, 102, 23, ()->switchTab(false));
        button("已发布照片", x+120, y+38, 104, 23, ()->switchTab(true));
        for (int k=0;k<rows;k++) {
            int index=page*rows+k;
            if (index>=entries.size()) break;
            Entry entry=entries.get(index);
            button((selected!=null && selected.id().equals(entry.id())?"● ":"")+"照片 "+(index+1)
                    +" / "+entry.id().substring(0,6),x+12,y+73+k*27,listWidth,22,
                    ()->select(entry,true));
        }
        button("上页",x+12,y+271,101,22,()->{page=Math.max(0,page-1);rebuildWidgets();});
        button("下页",x+123,y+271,101,22,
                ()->{page=Math.min(Math.max(0,(entries.size()-1)/rows),page+1);rebuildWidgets();});
        int row1=y+316,row2=y+347,actions=y+381;
        int cw=123;
        button("宽 -",x+9,row1,cw,23,()->resizeWidth(-1));
        button("宽 +",x+13+cw,row1,cw,23,()->resizeWidth(1));
        button("高 -",x+17+cw*2,row1,cw,23,()->resizeHeight(-1));
        button("高 +",x+21+cw*3,row1,cw,23,()->resizeHeight(1));
        button("重置 1×1",x+25+cw*4,row1,112,23,this::reset);
        button("缩小 -",x+9,row2,140,23,()->adjustZoom(-.2F));
        button("放大 +",x+157,row2,140,23,()->adjustZoom(.2F));
        button("居中裁切",x+305,row2,155,23,()->{centerX=.5F;centerY=.5F;zoom=1F;});
        button("应用尺寸/裁切",x+9,actions,149,23,this::configureOnly);
        button("设为照片",x+166,actions,149,23,this::commit);
        button("恢复默认",x+323,actions,149,23,this::clearPhoto);
        button("关闭",x+480,actions,149,23,this::onClose);
        updatePreviewBounds();
    }
    private void button(String title,int bx,int by,int bw,int bh,Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(title),b->action.run())
                .bounds(bx,by,bw,bh).build());
    }
    private void updatePreviewBounds() {
        float aspect=PosterCrop.apertureAspect(standWidth,standHeight);
        int boxLeft=x+246, boxTop=y+75;
        int boxW=375,boxH=183;
        int drawW=boxW,drawH=Math.max(1,Math.round(boxW/aspect));
        if (drawH>boxH) {drawH=boxH;drawW=Math.max(1,Math.round(boxH*aspect));}
        previewX=boxLeft+(boxW-drawW)/2;
        previewY=boxTop+(boxH-drawH)/2;
        previewW=drawW; previewH=drawH;
    }
    private void resizeWidth(int by) {standWidth=Math.max(1,Math.min(4,standWidth+by)); updatePreviewBounds();}
    private void resizeHeight(int by) {standHeight=Math.max(1,Math.min(4,standHeight+by)); updatePreviewBounds();}
    private void reset() {standWidth=1;standHeight=1;zoom=1F;centerX=.5F;centerY=.5F;updatePreviewBounds();}
    private void adjustZoom(float delta) { zoom=PosterCrop.clamp(zoom+delta,1F,4F); }
    private void reloadList() {
        if (publishedTab) {
            entries=publishedIds.stream().filter(CameraPhotoStorage::validId)
                    .map(id->new Entry(id,TourPhotoCapture.manualFile(id),true)).toList();
        } else {
            Path dir=FabricLoader.getInstance().getGameDir().resolve("screenshots")
                    .resolve("avemujica_tours").resolve("camera_drafts");
            try (Stream<Path> stream=Files.isDirectory(dir)?Files.list(dir):Stream.empty()) {
                entries=stream.filter(Files::isRegularFile)
                        .filter(f->f.getFileName().toString().matches("[0-9a-f]{32}\\.png"))
                        .sorted(Comparator.comparingLong(PhotoPosterPickerScreen::modified).reversed())
                        .limit(128).map(f->new Entry(f.getFileName().toString().substring(0,32),f,false))
                        .toList();
            } catch (IOException ignored) { entries=List.of(); }
        }
        page=Math.min(page,Math.max(0,(entries.size()-1)/Math.max(1,rows)));
    }
    private static long modified(Path f) {try{return Files.getLastModifiedTime(f).toMillis();}catch(IOException ex){return 0;}}
    private void switchTab(boolean published) {
        publishedTab=published;page=0; selected=null;closePreview();rebuildWidgets();
    }
    private void select(Entry entry,boolean resetCrop) {
        selected=entry;
        closePreview();
        if (resetCrop) {zoom=1F;centerX=.5F;centerY=.5F;}
        if (Files.isRegularFile(entry.local())) preview=TourPhotoCapture.Preview.open(entry.local());
        else if (entry.published() && ClientPlayNetworking.canSend(CameraPhotoRequestPayload.TYPE))
            ClientPlayNetworking.send(new CameraPhotoRequestPayload(entry.id()));
    }
    public void photoReceived(String id) {
        if(selected!=null && selected.published() && selected.id().equals(id)) {
            closePreview();preview=TourPhotoCapture.Preview.open(selected.local());
        }
    }
    private void configureOnly() {
        if (!ClientPlayNetworking.canSend(PosterConfigurePayload.TYPE)) return;
        ClientPlayNetworking.send(new PosterConfigurePayload(pos,standWidth,standHeight,zoom,centerX,centerY));
        onClose();
    }
    private void commit() {
        if (selected==null || !ClientPlayNetworking.canSend(PosterChoosePayload.TYPE)) {
            message("请先选择一张照片，或使用应用尺寸/裁切。 "); return;
        }
        try {
            byte[] png=selected.published()?new byte[0]:Files.readAllBytes(selected.local());
            if (!selected.published() && (png.length<48 || png.length>262144))
                throw new IOException("照片尺寸超过限制");
            ClientPlayNetworking.send(new PosterChoosePayload(pos,selected.id(),selected.published(),png,
                    standWidth,standHeight,zoom,centerX,centerY));
            onClose();
        } catch(IOException ex) { message("照片设置失败："+ex.getMessage()); }
    }
    private void clearPhoto() {
        if (!ClientPlayNetworking.canSend(PosterChoosePayload.TYPE)) return;
        ClientPlayNetworking.send(new PosterChoosePayload(pos,"",false,new byte[0],
                standWidth,standHeight,zoom,centerX,centerY));
        onClose();
    }
    private void message(String msg) {
        if (minecraft!=null && minecraft.player!=null)
            minecraft.player.displayClientMessage(Component.literal("[Mujica] "+msg),false);
    }
    private void closePreview() {if(preview!=null){preview.close();preview=null;}}
    @Override public void removed() {closePreview();super.removed();}
    @Override public boolean isPauseScreen() {return false;}
    private boolean overPreview(double mx,double my) {
        return mx>=previewX&&mx<=previewX+previewW&&my>=previewY&&my<=previewY+previewH;
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) {
        double ux = logicalMouse(mx), uy = logicalMouse(my);
        if (button==0 && overPreview(ux,uy) && preview!=null) {
            PosterCrop.Region r=PosterCrop.calculate(preview.imageWidth(),preview.imageHeight(),
                    PosterCrop.apertureAspect(standWidth,standHeight),zoom,centerX,centerY);
            centerX=PosterCrop.clamp(centerX-(float)logicalMouse(dx)/Math.max(1,previewW)*r.width(),0,1);
            centerY=PosterCrop.clamp(centerY-(float)logicalMouse(dy)/Math.max(1,previewH)*r.height(),0,1);
            return true;
        }
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy) {
        if (overPreview(logicalMouse(mx),logicalMouse(my))) {adjustZoom((float)(dy*.15));return true;}
        return super.mouseScrolled(mx,my,dx,dy);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float t) {
        renderBackground(g,mx,my,t);
        beginUi(g);
        OctopusSkin.drawPanelFrame(g, x, y, panelWidth, panelHeight, 33);
        g.drawString(font,"Mujica / 自定义照片立牌",x+13,y+12,OctopusSkin.text(),false);
        g.drawString(font,standWidth+" × "+standHeight+" 格 · 缩放 "
                +String.format(java.util.Locale.ROOT,"%.1f",zoom)+"×",
                x+247,y+48,OctopusTheme.current().text,false);
        if (entries.isEmpty()) g.drawString(font,"暂无可用照片",x+14,y+81,OctopusTheme.current().muted,false);
        g.fill(previewX-3,previewY-3,previewX+previewW+3,previewY+previewH+3,OctopusSkin.border());
        g.fill(previewX,previewY,previewX+previewW,previewY+previewH,OctopusSkin.panelSoft());
        if (preview!=null) {
            PosterCrop.Region crop=PosterCrop.calculate(preview.imageWidth(),preview.imageHeight(),
                    PosterCrop.apertureAspect(standWidth,standHeight),zoom,centerX,centerY);
            preview.renderCrop(g,previewX,previewY,previewW,previewH,crop);
        } else g.drawCenteredString(font,selected==null?"请选择照片":"照片加载中…",
                previewX+previewW/2,previewY+previewH/2,OctopusTheme.current().muted);
        g.drawString(font,"拖拽移动照片 · 滚轮缩放",x+247,y+276,OctopusTheme.current().muted,false);
        g.fill(x+12,y+307,x+panelWidth-12,y+308,OctopusSkin.borderSoft());
        super.render(g,logicalMouse(mx),logicalMouse(my),t);
        OctopusSkin.drawCenteredLogo(g, x, y, panelWidth, panelHeight);
        endUi(g);
    }
    @Override protected void renderUiBackdrop(GuiGraphics g,int mx,int my,float t) {
        OctopusSkin.drawBackdrop(g, width, height);
    }
}
