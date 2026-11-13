package com.miseru.avemujica.client;

import com.miseru.avemujica.career.OctopusShopCatalog;
import com.miseru.avemujica.network.OctopusShopBuyPayload;
import com.miseru.avemujica.network.OctopusShopStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Compact, single-page shop accessible directly from the blue octopus. */
public final class OctopusShopScreen extends ScaledMujicaScreen {
    private static final int W = 548, H = 385;
    private int left, top, emeralds;
    private boolean creative;
    private final List<Button> buyButtons = new ArrayList<>();

    public OctopusShopScreen(OctopusShopStatePayload state) {
        super(Component.literal("Ave Mujica · 小章鱼商店"));
        emeralds = state.emeralds();
        creative = state.creative();
    }

    public void update(OctopusShopStatePayload state) {
        emeralds = state.emeralds();
        creative = state.creative();
        refresh();
    }

    @Override protected void init() {
        layout(W, H);
        left = uiLeft();
        top = uiTop();
        buyButtons.clear();
        for (int sku = 0; sku < OctopusShopCatalog.COUNT; sku++) {
            final int selection = sku;
            int x = (sku % 2) * 266;
            int y = sku < 2 ? 72 : 156 + ((sku - 2) / 2) * 42;
            Button button = addRenderableWidget(Button.builder(Component.literal("购买"), ignored -> {
                if (ClientPlayNetworking.canSend(OctopusShopBuyPayload.TYPE))
                    ClientPlayNetworking.send(new OctopusShopBuyPayload(selection));
            }).bounds(left + 194 + x, top + y + (sku < 2 ? 15 : 9), 59, 20).build());
            button.setTooltip(Tooltip.create(Component.literal(creative ? "创造模式：免费领取"
                    : "花费 " + OctopusShopCatalog.offer(sku).emeralds()
                    + " 绿宝石；服务端扣款并发放实体商品")));
            buyButtons.add(button);
        }
        refresh();
    }

    private void refresh() {
        for (int sku = 0; sku < buyButtons.size(); sku++) {
            Button button = buyButtons.get(sku);
            int price = OctopusShopCatalog.offer(sku).emeralds();
            button.active = creative || emeralds >= price;
            button.setTooltip(Tooltip.create(Component.literal(creative ? "创造模式：免费领取"
                    : "花费 " + price + " 绿宝石；服务端扣款并发放实体商品")));
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        beginUi(g);
        OctopusSkin.drawPanelFrame(g, left, top, W, H, 40);
        g.drawString(font, "蓝色小章鱼 · 官方物资商店", left + 14, top + 10, OctopusSkin.text(), false);
        // Clip account values before the title; do not draw text across the header.
        String balance = creative ? "创造模式 · 免费购买" : "背包绿宝石 " + emeralds;
        g.drawString(font, font.plainSubstrByWidth(balance, 186), left + 345, top + 10, OctopusSkin.gold(), false);
        g.drawString(font, "舞台：一级可合成，二级售价 128 绿宝石", left + 15, top + 47, OctopusTheme.current().muted, false);
        for (int sku = 0; sku < Math.min(2, OctopusShopCatalog.COUNT); sku++)
            drawOffer(g, sku);
        g.fill(left + 12, top + 134, left + W - 12, top + 135, OctopusSkin.borderSoft());
        g.drawString(font, "正式周边 · 成本价购买成品，购买后在「周边寄售」上架", left + 15, top + 141, OctopusTheme.current().text, false);
        for (int sku = 2; sku < OctopusShopCatalog.COUNT; sku++)
            drawOffer(g, sku);
        g.drawString(font, "售出的周边只扣寄售库存，不会直接扣背包物品。", left + 15, top + 347, OctopusTheme.current().muted, false);
        g.drawString(font, creative ? "创造模式：免费领取 · 背包满时商品掉落在身边。"
                : "支付实体绿宝石 · 背包已满时商品掉落在玩家身边。", left + 15, top + 365, OctopusTheme.current().muted, false);
        // Native buttons are always rendered last and share the exact same virtual canvas.
        super.render(g, logicalMouse(mouseX), logicalMouse(mouseY), partialTick);
        OctopusSkin.drawCenteredLogo(g, left, top, W, H);
        endUi(g);
    }
    private void drawOffer(GuiGraphics g, int sku) {
        OctopusShopCatalog.Offer offer = OctopusShopCatalog.offer(sku);
        int x = left + 11 + (sku % 2) * 266;
        int y = top + (sku < 2 ? 72 : 156 + ((sku - 2) / 2) * 42);
        int height = sku < 2 ? 49 : 39;
        g.fill(x, y, x + 259, y + height, (sku % 2 == 0) ? OctopusSkin.rowA() : OctopusSkin.rowB());
        g.renderItem(offer.goods()[0], x + 8, y + (sku < 2 ? 15 : 11));
        String name = font.plainSubstrByWidth(offer.title(), 146);
        g.drawString(font, name, x + 30, y + (sku < 2 ? 9 : 6), OctopusTheme.current().text, false);
        g.drawString(font, offer.emeralds() + " 绿宝石", x + 30, y + (sku < 2 ? 28 : 24), OctopusTheme.current().secondary, false);
    }
    @Override protected void renderUiBackdrop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        OctopusSkin.drawBackdrop(g, width, height);
    }
    @Override public boolean isPauseScreen() { return false; }
}
