package com.miseru.avemujica.client;

import com.miseru.avemujica.career.Merchandising;
import com.miseru.avemujica.network.MerchConsignmentActionPayload;
import com.miseru.avemujica.network.MerchConsignmentStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** One-page octopus sales screen: deposit only explicitly selected goods, withdraw, claim. */
public final class MerchConsignmentScreen extends ScaledMujicaScreen {
    private static final int W = 572, H = 370;
    private int left, top;
    private int[] stock = new int[Merchandising.TYPES];
    private int unclaimed, fans, routineSold, tourSold;
    private final List<Button> buttons = new ArrayList<>();

    public MerchConsignmentScreen(MerchConsignmentStatePayload snapshot) {
        super(Component.literal("Ave Mujica · 蓝色小章鱼周边贩售"));
        apply(snapshot);
    }
    private void apply(MerchConsignmentStatePayload snapshot) {
        stock = snapshot.stock().clone();
        unclaimed = snapshot.unclaimed();
        fans = snapshot.fans();
        routineSold = snapshot.dailySold();
        tourSold = snapshot.tourSold();
    }
    public void update(MerchConsignmentStatePayload snapshot) { apply(snapshot); updateButtons(); }

    private void action(int type, int operation) {
        if (ClientPlayNetworking.canSend(MerchConsignmentActionPayload.TYPE))
            ClientPlayNetworking.send(new MerchConsignmentActionPayload(type, operation));
    }
    private Button control(String name, int x, int y, int w, int type, int operation) {
        Button b = addRenderableWidget(Button.builder(Component.literal(name), ignored -> action(type, operation))
                .bounds(left + x, top + y, w, 20).build());
        buttons.add(b);
        return b;
    }
    @Override protected void init() {
        layout(W, H);
        left = uiLeft();
        top = uiTop();
        buttons.clear();
        for (int row = 0; row < Merchandising.SALE_TYPES.size(); row++) {
            int kind = Merchandising.SALE_TYPES.get(row);
            int y = 81 + row * 26;
            control("上架1", 321, y, 53, kind, 0);
            control("上架64", 378, y, 57, kind, 1);
            control("取回1", 439, y, 53, kind, 2);
            control("取回64", 496, y, 62, kind, 3);
        }
        control("领取周边收入", 322, 303, 137, -1, 4)
                .setTooltip(Tooltip.create(Component.literal("每次最多领取9组绿宝石；巡演固定奖励仍在经营面板领取")));
        control("刷新", 466, 303, 92, -1, 5);
        control("取回退役库存", 391, 269, 167, -1, 6)
                .setTooltip(Tooltip.create(Component.literal("仅用于取回旧存档中已上架的退役周边；不会出售。每次最多取回8组")));
        updateButtons();
    }
    private void updateButtons() {
        int active = Merchandising.SALE_TYPES.size();
        if (buttons.size() < active * 4 + 3) return;
        for (int row = 0; row < active; row++) {
            int kind = Merchandising.SALE_TYPES.get(row);
            int owned = inventoryCount(kind);
            buttons.get(4*row).active = owned > 0 && stock[kind] < Merchandising.MAX_STOCK;
            buttons.get(4*row+1).active = owned > 0 && stock[kind] < Merchandising.MAX_STOCK;
            buttons.get(4*row+2).active = stock[kind] > 0;
            buttons.get(4*row+3).active = stock[kind] > 0;
        }
        buttons.get(active * 4).active = unclaimed > 0;
        // Don't show even a recovery control unless an older save actually has stock.
        Button retired = buttons.get(active * 4 + 2);
        retired.visible = stock[Merchandising.LIGHTSTICK] > 0
                || stock[Merchandising.LEGACY_DOLL_SET] > 0;
        retired.active = retired.visible;
    }
    private int inventoryCount(int type) {
        if (minecraft == null || minecraft.player == null) return 0;
        int result = 0;
        for (ItemStack s : minecraft.player.getInventory().items)
            if (s.is(Merchandising.item(type))) result += s.getCount();
        return result;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        beginUi(g);
        OctopusSkin.drawPanelFrame(g, left, top, W, H, 37);
        g.drawString(font, "蓝色小章鱼 · 周边寄售", left + 14, top + 11, OctopusSkin.text(), false);
        String balance = "粉丝 " + fans + " · 待领取 " + unclaimed + " 绿宝石";
        g.drawString(font, font.plainSubstrByWidth(balance, 270), left + 281, top + 11, OctopusSkin.gold(), false);
        g.drawString(font, "只有主动上架的周边才参与销售；日常成交、巡演集中售卖", left + 15, top + 45, OctopusSkin.muted(), false);
        g.drawString(font, "商品", left + 37, top + 67, OctopusTheme.current().muted, false);
        g.drawString(font, "寄售库存", left + 175, top + 67, OctopusTheme.current().muted, false);
        g.drawString(font, "背包", left + 271, top + 67, OctopusTheme.current().muted, false);
        for (int row = 0; row < Merchandising.SALE_TYPES.size(); row++) {
            int kind = Merchandising.SALE_TYPES.get(row);
            int y = top + 81 + 26*row;
            g.fill(left + 10, y - 2, left + W - 10, y + 22, (row%2==0)?OctopusSkin.rowA():OctopusSkin.rowB());
            g.renderItem(new ItemStack(Merchandising.item(kind)), left + 14, y + 2);
            g.drawString(font, font.plainSubstrByWidth(Merchandising.name(kind), 128),
                    left + 37, y + 6, OctopusTheme.current().text, false);
            g.drawString(font, Integer.toString(stock[kind]), left + 189, y + 6, OctopusTheme.current().secondary, false);
            g.drawString(font, Integer.toString(inventoryCount(kind)), left + 273, y + 6, OctopusTheme.current().positive, false);
        }
        int retiredStock = stock[Merchandising.LIGHTSTICK] + stock[Merchandising.LEGACY_DOLL_SET];
        if (retiredStock > 0)
            g.drawString(font, "旧存档退役库存 " + retiredStock + " 件", left + 16, top + 276, OctopusTheme.current().muted, false);
        g.drawString(font, "日常累计 " + routineSold + " 件 / 巡演累计 " + tourSold + " 件", left + 16, top + 310, OctopusTheme.current().muted, false);
        g.drawString(font, "寄售价：海报3 · 唱片9 · 娃娃15 绿宝石", left + 16, top + 337, OctopusSkin.muted(), false);
        g.drawString(font, "只有寄售中的周边才会售出；点击「取回」可拿回未售商品。", left + 16, top + 355, OctopusTheme.current().muted, false);
        super.render(g, logicalMouse(mouseX), logicalMouse(mouseY), partialTick);
        OctopusSkin.drawCenteredLogo(g, left, top, W, H);
        endUi(g);
    }
    @Override protected void renderUiBackdrop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        OctopusSkin.drawBackdrop(g, width, height);
    }
    @Override public boolean isPauseScreen() { return false; }
}
