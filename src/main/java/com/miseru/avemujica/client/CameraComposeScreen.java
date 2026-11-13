package com.miseru.avemujica.client;

import com.miseru.avemujica.network.CameraPublishPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Preview and caption editor. Nothing is posted until the player chooses Publish. */
public final class CameraComposeScreen extends ScaledMujicaScreen {
    private final String id;
    private final Path draft;
    private TourPhotoCapture.Preview preview;
    private EditBox caption;
    private Button publishButton;
    public CameraComposeScreen(String id, Path draft) {
        super(Component.literal("Mujica · 发布照片"));
        this.id = id; this.draft = draft;
    }
    private int left() { return uiLeft(); }
    private int top() { return uiTop(); }
    @Override protected void init() {
        layout(400, 294);
        String savedCaption = caption == null ? "" : caption.getValue();
        int x=left(), y=top();
        caption = addRenderableWidget(new EditBox(font, x+20,y+230,360,22,
                Component.literal("附文")));
        caption.setMaxLength(120);
        caption.setValue(savedCaption);
        caption.setHint(Component.literal("写下这张照片的故事（最多120字）"));
        setInitialFocus(caption);
        publishButton = addRenderableWidget(Button.builder(Component.literal("发布到 Mujica 官方账号"), b -> publish())
                .bounds(x+20,y+260,225,22).build());
        addRenderableWidget(Button.builder(Component.literal("关闭，保留草稿"), b -> onClose())
                .bounds(x+251,y+260,129,22).build());
        if (preview != null) preview.close();
        preview = TourPhotoCapture.Preview.open(draft);
    }
    private void publish() {
        if (!ClientPlayNetworking.canSend(CameraPublishPayload.TYPE)) {
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(
                    Component.literal("[Mujica] 当前服务器不支持相机动态。"), false);
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(draft);
            if (bytes.length > CameraPublishPayload.MAX_PNG_BYTES)
                throw new IOException("文件超过 256 KiB");
            // Retain a local readable copy; the server independently validates and stores the upload.
            Path local = TourPhotoCapture.manualFile(id);
            Files.createDirectories(local.getParent());
            Files.copy(draft, local, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            publishButton.active = false;
            ClientPlayNetworking.send(new CameraPublishPayload(id, caption.getValue(), bytes));
            if (minecraft != null) minecraft.setScreen(null);
        } catch (IOException ex) {
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(
                    Component.literal("[Mujica] 发布失败："+ex.getMessage()), false);
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float tick) {
        renderBackground(g,mx,my,tick);
        beginUi(g);
        int x=left(),y=top();
        OctopusSkin.drawPanelFrame(g, x, y, 400, 294, 34);
        g.fill(x+12,y+12,x+388,y+216,OctopusSkin.panelSoft());
        g.drawString(font,"Mujica / 照片预览",x+20,y+16,OctopusTheme.current().text,false);
        g.drawString(font,"附文：",x+20,y+219,OctopusTheme.current().text,false);
        // Draw the photo on a separate opaque surface before widgets. Neither the
        // background veil nor the edit controls are allowed to cover its pixels.
        if (preview != null) preview.render(g,x+20,y+43,360,159);
        else g.drawCenteredString(font,"预览不可用，保留照片草稿",x+200,y+120,OctopusTheme.current().accent);
        super.render(g,logicalMouse(mx),logicalMouse(my),tick);
        OctopusSkin.drawCenteredLogo(g, x, y, 400, 294);
        endUi(g);
    }
    @Override protected void renderUiBackdrop(GuiGraphics g,int mx,int my,float tick) {
        // One lightweight neutral scrim behind the editor, not a purple screen overlay.
        OctopusSkin.drawBackdrop(g, width, height);
    }
    @Override public void removed() {
        if (preview != null) { preview.close(); preview=null; }
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
