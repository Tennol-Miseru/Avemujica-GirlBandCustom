package com.miseru.avemujica.career;

import com.miseru.avemujica.network.OctopusShopBuyPayload;
import com.miseru.avemujica.network.OctopusShopStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Purchases are checked and committed entirely on the server, using physical emeralds. */
public final class OctopusShop {
    private OctopusShop() { }

    public static void open(ServerPlayer player) {
        if (!MerchConsignment.hasOctopus(player)) return;
        send(player, true);
    }

    public static void buy(ServerPlayer player, OctopusShopBuyPayload payload) {
        if (!MerchConsignment.hasOctopus(player)) {
            player.displayClientMessage(Component.literal("[Mujica] 购买未执行：请将蓝色小章鱼握在主手或副手。"), false);
            return;
        }
        int sku = payload.sku();
        if (sku < 0 || sku >= OctopusShopCatalog.COUNT) return;
        OctopusShopCatalog.Offer offer = OctopusShopCatalog.offer(sku);
        // Validate the entire order BEFORE charging the player. Never announce a
        // successful purchase if the catalogue has no valid goods to deliver.
        ItemStack[] goods = offer.goods();
        if (goods.length == 0) {
            player.displayClientMessage(Component.literal("[Mujica] 购买失败：商品配置为空，未扣除绿宝石。"), false);
            send(player, false);
            return;
        }
        for (ItemStack stack : goods) {
            if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
                player.displayClientMessage(Component.literal("[Mujica] 购买失败：商品配置异常，未扣除绿宝石。"), false);
                send(player, false);
                return;
            }
        }
        if (!BandManagement.payEmeralds(player, offer.emeralds())) {
            player.displayClientMessage(Component.literal("[Mujica] 绿宝石不足：" + offer.title()
                    + " 需要 " + offer.emeralds() + " 颗。寄售收益请先在周边界面领取。"), false);
            send(player, false);
            return;
        }
        // Inventory.add() does not provide a guaranteed client-side receipt, and
        // the old code inferred delivery from the mutated remainder of the stack.
        // Vanilla's placeItemBackInInventory inserts and syncs the actual item,
        // automatically dropping a remainder if the player's inventory is full.
        int total = 0;
        for (ItemStack offered : goods) {
            ItemStack stack = offered.copy();
            total += stack.getCount();
            player.getInventory().placeItemBackInInventory(stack);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.literal("[Mujica] 已发放 " + offer.title()
                + "（共 " + total + " 件），"
                + (player.getAbilities().instabuild ? "创造模式免费领取" : "支付 " + offer.emeralds() + " 绿宝石")
                + "。背包满时溢出物品会掉落在身边。"), false);
        send(player, false);
    }

    private static void send(ServerPlayer player, boolean open) {
        ServerPlayNetworking.send(player, new OctopusShopStatePayload(
                open, BandManagement.emeralds(player), player.getAbilities().instabuild));
    }
}
