package com.miseru.avemujica.client;

import com.miseru.avemujica.career.TourPost;
import com.miseru.avemujica.network.TourArchivePayload;
import com.miseru.avemujica.network.TourPinPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Actual owner-synced official account. The feed is a chronological timeline, with one optional pinned post. */
public final class TourArchiveScreen extends Screen {
    private static final int W = 676, H = 402;
    private static final int PER_PAGE = 4;
    private static final ResourceLocation AVATAR = ResourceLocation.fromNamespaceAndPath(
            "avemujica", "textures/item/blue_octopus.png");
    private int BG() { return OctopusTheme.current().background; }
    private int PANEL() { return OctopusTheme.current().panel; }
    private int PANEL_ALT() { return OctopusTheme.current().alternate; }
    private int EDGE() { return OctopusTheme.current().edge; }
    private int VIOLET() { return OctopusTheme.current().accent; }
    private int GOLD() { return OctopusTheme.current().secondary; }
    private int WHITE() { return OctopusTheme.current().text; }
    private int MUTED() { return OctopusTheme.current().muted; }
    private int GREEN() { return OctopusTheme.current().positive; }
    private static final int PHOTO_X = 263, PHOTO_Y = 154, PHOTO_W = 388, PHOTO_H = 155;

    private enum Filter { ALL, TOUR, CAMERA }
    private TourArchivePayload snapshot;
    private List<TourPost> timeline = List.of();
    private Filter filter = Filter.ALL;
    private String selectedKey = "";
    private int page = 0;
    private TourPhotoCapture.Preview preview;
    private String previewKey = "";
    private boolean enlarged, captionExpanded, showLegacyCard;

    public TourArchiveScreen(TourArchivePayload snapshot) {
        super(Component.literal("Mujica 官方账号"));
        this.snapshot = snapshot;
        rebuild();
    }
    /** Keep a consistent layout on smaller Minecraft GUI scales/windows. */
    private float uiScale() {
        return Math.max(.01F, Math.min(.90F, Math.min((width - 28F) / W, (height - 28F) / H)));
    }
    private int virtualWidth() { return (int)Math.ceil(width / uiScale()); }
    private int virtualHeight() { return (int)Math.ceil(height / uiScale()); }
    private int left() { return (virtualWidth() - W) / 2; }
    private int top() { return (virtualHeight() - H) / 2; }
    private String pinKey() { return snapshot.pinnedPostKey(); }
    private TourPost selectedPost() {
        for (TourPost post : timeline) if (post.feedKey().equals(selectedKey)) return post;
        return null;
    }

    /** Keep the current filter, selection and page after an owner-authorized pin change. */
    public void updateArchive(TourArchivePayload updated) {
        snapshot = updated;
        rebuild();
    }
    public void photoReceived(String id) {
        TourPost selected = selectedPost();
        if (selected != null && selected.photoId().equals(id)) resetPreview();
    }
    private void rebuild() {
        timeline = TourFeedOrder.order(snapshot.posts(), pinKey(), filter.name());
        if (timeline.stream().noneMatch(p -> p.feedKey().equals(selectedKey))) {
            selectedKey = timeline.isEmpty() ? "" : timeline.get(0).feedKey();
            page = 0;
        } else {
            int found = 0;
            for (int i = 0; i < timeline.size(); i++) {
                if (timeline.get(i).feedKey().equals(selectedKey)) { found = i; break; }
            }
            page = found / PER_PAGE;
        }
        showLegacyCard = false;
        resetPreview();
    }
    private void resetPreview() {
        if (preview != null) { preview.close(); preview = null; }
        previewKey = "";
    }
    private void select(int index) {
        if (index < 0 || index >= timeline.size()) return;
        selectedKey = timeline.get(index).feedKey();
        page = index / PER_PAGE;
        enlarged = false;
        captionExpanded = false;
        showLegacyCard = false;
        resetPreview();
    }
    private void chooseFilter(Filter next) {
        if (filter == next) return;
        filter = next;
        selectedKey = "";
        page = 0;
        rebuild();
    }
    private void step(int direction) {
        int max = Math.max(0, (timeline.size() - 1) / PER_PAGE);
        int dest = Math.max(0, Math.min(max, page + direction));
        if (dest != page) select(dest * PER_PAGE);
    }
    private void pinSelected() {
        TourPost post = selectedPost();
        if (post == null) return;
        if (!ClientPlayNetworking.canSend(TourPinPayload.TYPE)) return;
        String next = post.feedKey().equals(pinKey()) ? "" : post.feedKey();
        ClientPlayNetworking.send(new TourPinPayload(next));
        // The authoritative feed refresh and persistence happen on the server.
    }
    private void retakeSelected() {
        TourPost p = selectedPost();
        if (p == null || p.manual()) return;
        if (TourPhotoCapture.retake(p)) {
            if (minecraft != null) minecraft.setScreen(null);
        } else if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(
                    "请回到本次巡演主世界坐标附近 42 格内重拍。"), false);
        }
    }
    private void openPreview() {
        TourPost p = selectedPost();
        if (p == null || p.feedKey().equals(previewKey)) return;
        resetPreview();
        preview = TourPhotoCapture.Preview.open(p);
        previewKey = p.feedKey();
        if (preview == null && p.manual()) TourPhotoCapture.requestManual(p);
    }
    private void fill(GuiGraphics g, int x, int y, int w, int h, int color) { g.fill(x, y, x + w, y + h, color); }
    private void frame(GuiGraphics g, int x, int y, int w, int h, int outline, int inside) {
        fill(g, x, y, w, h, outline);
        if (w > 2 && h > 2) fill(g, x + 1, y + 1, w - 2, h - 2, inside);
    }
    private void text(GuiGraphics g, String value, int x, int y, int color) {
        g.drawString(font, value, x, y, color, false);
    }
    private void center(GuiGraphics g, String value, int x, int y, int color) {
        g.drawCenteredString(font, value, x, y, color);
    }
    private String shorten(String s, int width) {
        return font.plainSubstrByWidth(s == null ? "" : s, Math.max(1, width));
    }
    private void pill(GuiGraphics g, int x, int y, int w, int h, String title, boolean active, boolean enabled, boolean hover) {
        int bg = !enabled ? OctopusTheme.current().alternate
                : active ? OctopusTheme.current().selection
                : hover ? OctopusTheme.current().hover : PANEL();
        frame(g, x, y, w, h, active ? VIOLET() : EDGE(), bg);
        center(g, title, x + w / 2, y + (h - 8) / 2, enabled ? WHITE() : MUTED());
    }
    private void stat(GuiGraphics g, int x, int y, int w, String name, int number) {
        frame(g, x, y, w, 34, EDGE(), PANEL_ALT());
        center(g, name, x + w / 2, y + 5, MUTED());
        center(g, String.valueOf(number), x + w / 2, y + 19, WHITE());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g, mx, my, partial);
        float scale = uiScale();
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1F);
        mx = Math.round(mx / scale);
        my = Math.round(my / scale);
        final int x = left(), y = top();
        // Narrow account surface with a clear frame and surrounding game-world margins.
        frame(g, x-2, y-2, W+4, H+4, VIOLET(), BG());
        // White/light-blue web header or dark/crimson header, following octopus skin.
        fill(g, x + 2, y + 2, W - 4, 31, OctopusTheme.current().header);
        fill(g, x + 2, y + 30, W - 4, 53, PANEL());
        for (int i = 0; i < 7; i++) {
            fill(g, x + 50 + i * 82, y + 12, 53, 2, i % 2 == 0 ? EDGE() : OctopusTheme.current().hover);
        }
        frame(g, x + 20, y + 24, 52, 52, VIOLET(), PANEL_ALT());
        g.blit(AVATAR, x + 27, y + 31, 38, 38, 0F, 0F, 32, 32, 32, 32);
        text(g, "Mujica Official", x + 82, y + 39, WHITE());
        text(g, "@ave_mujica_official", x + 82, y + 55, MUTED());
        text(g, "乐队动态 · 巡演足迹 · 摄影", x + 82, y + 71, GOLD());
        stat(g, x + 402, y + 40, 78, "粉丝", snapshot.fans());
        stat(g, x + 489, y + 40, 78, "巡演", snapshot.totalShows());
        stat(g, x + 576, y + 40, 78, "获赞", snapshot.cumulativeLikes());

        // Filter tabs. The selected filter controls both feed ordering and selection.
        pill(g, x + 17, y + 93, 62, 23, "全部", filter == Filter.ALL, true,
                inside(mx,my,x+17,y+93,62,23));
        pill(g, x + 85, y + 93, 62, 23, "巡演", filter == Filter.TOUR, true,
                inside(mx,my,x+85,y+93,62,23));
        pill(g, x + 153, y + 93, 62, 23, "摄影", filter == Filter.CAMERA, true,
                inside(mx,my,x+153,y+93,62,23));
        text(g, "最新动态 · 时间线", x + 18, y + 128, GOLD());
        text(g, timeline.size() + " 条", x + 185, y + 128, MUTED());

        // Four cards per page, with a subtle chronological connector and pinned badge.
        int listX = x + 16, listY = y + 150, listW = 226;
        frame(g, x + 13, y + 143, 231, 225, EDGE(), PANEL());
        fill(g, listX + 10, listY + 12, 2, 184, EDGE());
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i, ry = listY + i * 54;
            if (index >= timeline.size()) continue;
            TourPost p = timeline.get(index);
            boolean selected = p.feedKey().equals(selectedKey);
            boolean pinned = p.feedKey().equals(pinKey());
            frame(g, listX + 20, ry, listW - 24, 49,
                    selected ? VIOLET() : EDGE(), selected ? OctopusTheme.current().selection : PANEL_ALT());
            fill(g, listX + 6, ry + 14, 9, 9, pinned ? GOLD() : VIOLET());
            String title = (p.manual() ? "摄影 · " : "巡演 · ") + p.biomeLabel() + (p.manual() ? "" : "站");
            text(g, shorten(title, pinned ? 131 : 162), listX + 30, ry + 7, WHITE());
            if (pinned) text(g, "置顶", listX + listW - 55, ry + 7, GOLD());
            String sub = p.manual() ? shorten(p.caption().isBlank() ? "摄影记录" : p.caption(), 160)
                    : p.grade() + "级 · " + p.audience() + "位观众 · +" + p.totalFans() + "粉";
            text(g, sub, listX + 30, ry + 21, MUTED());
            text(g, "Day " + p.day() + "  ·  " + p.likes() + " 赞", listX + 30, ry + 34, GREEN());
        }

        // The post detail is part of the timeline, not a second full-screen editor.
        int rx = x + 253;
        frame(g, rx, y + 119, 408, 249, EDGE(), PANEL());
        TourPost p = selectedPost();
        if (p == null) {
            center(g, "这里还没有动态", rx + 204, y + 236, WHITE());
            center(g, "完成巡演或使用照相机发布第一条动态", rx + 204, y + 255, MUTED());
        } else {
            openPreview();
            String headline = p.manual() ? "摄影动态 · " + p.biomeLabel() : "巡演记录 · " + p.biomeLabel() + "站";
            text(g, shorten(headline, 192), rx + 13, y + 130, WHITE());
            pill(g, x + W - 210, y + 124, 91, 21,
                    p.manual() ? "查看附文" : "重拍现场", true,
                    p.manual() ? !p.caption().isBlank() : true,
                    inside(mx,my,x+W-210,y+124,91,21));
            pill(g, x + W - 112, y + 124, 96, 21,
                    p.feedKey().equals(pinKey()) ? "取消置顶" : "置顶动态", false, true,
                    inside(mx,my,x+W-112,y+124,96,21));
            frame(g, x + PHOTO_X, y + PHOTO_Y, PHOTO_W, PHOTO_H, EDGE(), PANEL_ALT());
            if (preview != null) preview.render(g, x + PHOTO_X + 1, y + PHOTO_Y + 1,
                    PHOTO_W - 2, PHOTO_H - 2);
            else if (!p.manual() && showLegacyCard) drawLegacy(g, p, x+PHOTO_X+1, y+PHOTO_Y+1, PHOTO_W-2, PHOTO_H-2);
            else {
                center(g, p.manual() ? "正在获取摄影原图…" : "该场巡演尚无实拍照片", x+PHOTO_X+PHOTO_W/2, y+PHOTO_Y+61, WHITE());
                center(g, p.manual() ? "请检查网络及本机照片缓存" : "回到演出现场可选择重拍", x+PHOTO_X+PHOTO_W/2, y+PHOTO_Y+80, MUTED());
            }
            if (p.manual()) text(g, "手动发布（不计入巡演）· Day " + p.day() + " · 坐标 " + p.x() + ", " + p.z(), rx + 13, y + 318, MUTED());
            else text(g, "Day " + p.day() + " · " + p.biomeLabel() + " · " + p.x() + ", " + p.y() + ", " + p.z(), rx + 13, y + 318, MUTED());
            if (p.manual()) {
                text(g, shorten(p.caption().isBlank() ? "（没有附文）" : p.caption(), 376), rx + 13, y + 333, WHITE());
            } else {
                String showSummary = p.merchRevenue() > 0
                        ? "周边售出：海报 " + p.postersSold() + " / 应援灯 " + p.lightsSold()
                          + " / 唱片 " + p.recordsSold() + " / 娃娃 " + p.dollsSold() + " · 周边收入 +" + p.merchRevenue()
                        : "演出 " + p.grade() + "级 (" + p.score() + "分) · 观众 " + p.audience()
                          + " · 收入 +" + p.emeralds() + " 绿宝石 · 涨粉 +" + p.totalFans();
                text(g, shorten(showSummary, 379), rx + 13, y + 333, WHITE());
            }
            // Likes are persisted real account statistics. No fabricated comments or shares.
            fill(g, rx + 9, y + 347, 390, 1, EDGE());
            text(g, "❤ 赞 " + p.likes(), rx + 14, y + 354, GOLD());
            text(g, "评论 · 暂未开放", rx + 114, y + 354, MUTED());
            text(g, "转发 · 暂未开放", rx + 246, y + 354, MUTED());
        }

        // Footer controls.
        boolean canPrevious = page > 0;
        boolean canNext = (page + 1) * PER_PAGE < timeline.size();
        pill(g, x + 16, y + H - 27, 72, 20, "上一页", false, canPrevious,
                inside(mx,my,x+16,y+H-27,72,20));
        pill(g, x + 94, y + H - 27, 72, 20, "下一页", false, canNext,
                inside(mx,my,x+94,y+H-27,72,20));
        text(g, "第 " + (page + 1) + "/" + Math.max(1, (timeline.size() + PER_PAGE - 1)/PER_PAGE) + " 页",
                x + 184, y + H - 21, MUTED());
        if (p != null && !p.manual() && preview == null) {
            text(g, shorten(showLegacyCard ? "已显示旧版纪念卡" : "无实拍？点击这里查看旧纪念卡", 221),
                    x + 323, y + H - 21, MUTED());
        }
        pill(g, x + W - 113, y + H - 27, 96, 20, "返回游戏", false, true,
                inside(mx,my,x+W-113,y+H-27,96,20));

        // Modal layers are always drawn last, with fully opaque photo contents.
        if (enlarged && p != null) renderLightbox(g, p);
        if (captionExpanded && p != null) renderCaption(g, p);
        g.pose().popPose();
    }

    /** Legacy cards remain optional for old tour records, not mistaken for real screenshots. */
    private void drawLegacy(GuiGraphics g, TourPost p, int x, int y, int w, int h) {
        fill(g, x, y, w, h, 0xFF393D56);
        fill(g, x, y + h / 2, w, h / 2, 0xFF5E5B69);
        fill(g, x + 18, y + h / 2 - 3, w - 36, 12, 0xFF211C30);
        for (int i = 0; i < 5; i++) {
            int cx = x + w / 2 + (i - 2) * (w / 7);
            fill(g, cx - 5, y + h / 2 - 31, 10, 27, 0xFFB38AAE);
            fill(g, cx - 4, y + h / 2 - 41, 8, 9, 0xFFDEBDD0);
        }
        fill(g, x, y, w, 18, 0xFF1B1725);
        text(g, "示意纪念卡 · " + p.biomeLabel() + "站 (非真实照片)", x + 8, y + 5, 0xFFFFFFFF);
    }
    private void renderLightbox(GuiGraphics g, TourPost p) {
        int vw = virtualWidth(), vh = virtualHeight();
        fill(g, 0, 0, vw, vh, 0xF80B0A12);
        int bigW = Math.min(vw - 66, 1160);
        int bigH = bigW * 226 / 512;
        if (bigH > vh - 80) { bigH = vh - 80; bigW = bigH * 512 / 226; }
        int x = (vw - bigW) / 2, y = (vh - bigH) / 2;
        frame(g, x - 3, y - 3, bigW + 6, bigH + 6, VIOLET(), 0xFF11111D);
        if (preview != null) preview.render(g, x, y, bigW, bigH);
        else if (showLegacyCard && !p.manual()) drawLegacy(g, p, x, y, bigW, bigH);
        else center(g, "暂无可放大的照片", vw/2, vh/2, 0xFFFFFFFF);
        center(g, "单击关闭大图 · ESC 返回账号", vw / 2, y + bigH + 16, 0xFFFFFFFF);
    }
    private void renderCaption(GuiGraphics g, TourPost p) {
        int vw = virtualWidth(), vh = virtualHeight();
        fill(g, 0, 0, vw, vh, 0xF80B0A12);
        int w = Math.min(480, vw - 48), x = (vw - w) / 2;
        var lines = font.split(Component.literal(p.caption().isEmpty() ? "（没有附文）" : p.caption()), w - 38);
        int count = Math.min(lines.size(), Math.max(1, (vh - 125) / 14));
        int h = Math.max(108, 75 + count * 14), y = (vh - h) / 2;
        frame(g, x, y, w, h, VIOLET(), PANEL());
        center(g, "Mujica · 动态附文", vw/2, y + 15, GOLD());
        for (int i=0; i<count; i++) g.drawString(font, lines.get(i), x + 18, y + 40 + i*14, WHITE(), false);
        center(g, "单击关闭 · ESC 返回账号", vw/2, y + h - 18, MUTED());
    }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        mx /= uiScale();
        my /= uiScale();
        if (captionExpanded) { captionExpanded = false; return true; }
        if (enlarged) { enlarged = false; return true; }
        int x = left(), y = top();
        if (inside(mx,my,x+17,y+93,62,23)) { chooseFilter(Filter.ALL); return true; }
        if (inside(mx,my,x+85,y+93,62,23)) { chooseFilter(Filter.TOUR); return true; }
        if (inside(mx,my,x+153,y+93,62,23)) { chooseFilter(Filter.CAMERA); return true; }
        int lx=x+16, ly=y+150;
        for (int i=0; i<PER_PAGE; i++) {
            int index=page*PER_PAGE+i;
            if (index<timeline.size() && inside(mx,my,lx+20,ly+i*54,202,49)) {
                select(index); return true;
            }
        }
        TourPost p = selectedPost();
        if (p != null) {
            if (inside(mx,my,x+W-112,y+124,96,21)) { pinSelected(); return true; }
            if (inside(mx,my,x+W-210,y+124,91,21)) {
                if (p.manual()) { if (!p.caption().isBlank()) captionExpanded=true; }
                else retakeSelected();
                return true;
            }
            if (inside(mx,my,x+PHOTO_X,y+PHOTO_Y,PHOTO_W,PHOTO_H)) {
                if (preview != null || showLegacyCard) enlarged=true;
                return true;
            }
        }
        if (inside(mx,my,x+16,y+H-27,72,20)) { step(-1); return true; }
        if (inside(mx,my,x+94,y+H-27,72,20)) { step(1); return true; }
        if (inside(mx,my,x+W-113,y+H-27,96,20)) { onClose(); return true; }
        if (p != null && !p.manual() && preview == null
                && inside(mx,my,x+313,y+H-29,238,26)) {
            showLegacyCard = !showLegacyCard; return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        mx /= uiScale();
        my /= uiScale();
        if (!enlarged && !captionExpanded && inside(mx,my,left()+13,top()+143,231,225)) {
            step(dy > 0 ? -1 : 1); return true;
        }
        return super.mouseScrolled(mx,my,dx,dy);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && captionExpanded) { captionExpanded = false; return true; }
        if (key == 256 && enlarged) { enlarged = false; return true; }
        return super.keyPressed(key,scan,mods);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        // Deliberately NOT a full-screen web sheet; keep the game scene visible
        // around the centred account window. No vanilla Gaussian blur.
        fill(g, 0, 0, width, height, 0x85080B12);
    }
    @Override public void removed() {
        resetPreview();
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
