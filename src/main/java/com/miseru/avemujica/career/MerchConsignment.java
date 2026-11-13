package com.miseru.avemujica.career;

import com.miseru.avemujica.network.MerchConsignmentActionPayload;
import com.miseru.avemujica.network.MerchConsignmentStatePayload;
import com.miseru.avemujica.registry.ModRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Player-owned, server-authoritative consignment interface for the blue octopus. */
public final class MerchConsignment {
    private MerchConsignment() { }

    public static boolean hasOctopus(ServerPlayer player) {
        return player.getMainHandItem().is(ModRegistry.BLUE_OCTOPUS)
                || player.getOffhandItem().is(ModRegistry.BLUE_OCTOPUS);
    }

    public static void open(ServerPlayer player) {
        if (!hasOctopus(player)) return;
        send(player, true);
    }

    /** The server is the only writer of consignment stock and its earnings. */
    public static void action(ServerPlayer player, MerchConsignmentActionPayload request) {
        if (!hasOctopus(player)) return;
        int operation = request.operation(), type = request.kind();
        if (operation < 0 || operation > 6) return;
        if (operation <= 3 && (type < 0 || type >= Merchandising.TYPES)) return;
        // A modified client cannot re-list retired items, even with a valid old item ID.
        if (operation <= 1 && !Merchandising.isConsignable(type)) return;
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.career(player.getUUID());
        boolean changed = false;
        if (operation == 6) {
            // One-time legacy stock recovery for players who listed either retired item
            // before v1.1.105. Never exchange legacy stock for emeralds or delete it.
            // Limit each click to eight stacks, to avoid large entity bursts with full bags.
            for (int legacy : new int[]{Merchandising.LIGHTSTICK, Merchandising.LEGACY_DOLL_SET}) {
                int remaining = Math.min(4 * 64, career.consignmentStock[legacy]);
                while (remaining > 0) {
                    int take = Math.min(64, remaining);
                    ItemStack stack = new ItemStack(Merchandising.item(legacy), take);
                    player.getInventory().add(stack);
                    if (!stack.isEmpty()) player.drop(stack.copy(), false);
                    career.consignmentStock[legacy] -= take;
                    remaining -= take;
                    changed = true;
                }
            }
            if (changed) player.displayClientMessage(Component.literal(
                    "[Mujica] 已取回退役周边库存（背包满时掉落在身旁）。"), false);
        } else if (operation == 4) {
            // Never debit escrow for items that failed to enter the inventory.
            // In particular, 357 emeralds need six inventory stacks (5*64+37).
            // A full bag must not silently turn the five full stacks into drops.
            int requested = Math.min(9 * 64, career.consignmentEmeralds);
            if (requested > 0) {
                int delivered = InventoryPayout.insert(player, Items.EMERALD, requested);
                if (delivered > 0) {
                    career.consignmentEmeralds -= delivered;
                    InventoryPayout.sync(player);
                    changed = true;
                }
                String hint = delivered < requested
                        ? "；背包空间不足，未领取部分已保留，请腾出空位后再次领取。" : "。";
                player.displayClientMessage(Component.literal("[Mujica] 本次实际领取 " + delivered
                        + " 绿宝石；仍待领取 " + career.consignmentEmeralds + " 绿宝石" + hint), false);
            }
        } else if (operation <= 3) {
            int requested = (operation == 1 || operation == 3) ? 64 : 1;
            if (operation == 0 || operation == 1) {
                if (Merchandising.isConsignable(type)) {
                    int room = Merchandising.MAX_STOCK - career.consignmentStock[type];
                    int remaining = Math.min(requested, room);
                    // Only remove an item the player explicitly chose for consignment.
                    for (ItemStack stack : player.getInventory().items) {
                        if (remaining == 0) break;
                        if (!stack.is(Merchandising.item(type))) continue;
                        int take = Math.min(remaining, stack.getCount());
                        if (take <= 0) continue;
                        stack.shrink(take);
                        career.consignmentStock[type] += take;
                        remaining -= take;
                        changed = true;
                    }
                    if (changed) {
                        player.getInventory().setChanged();
                        player.inventoryMenu.broadcastChanges();
                        if (career.nextConsignmentSaleTick <= 0)
                            career.nextConsignmentSaleTick = player.serverLevel().getServer().overworld().getGameTime()
                                    + 1200L + player.serverLevel().random.nextInt(2401);
                    }
                }
            } else {
                int take = Math.min(requested, career.consignmentStock[type]);
                if (take > 0) {
                    career.consignmentStock[type] -= take;
                    ItemStack stack = new ItemStack(Merchandising.item(type), take);
                    player.getInventory().add(stack);
                    if (!stack.isEmpty()) player.drop(stack.copy(), false);
                    changed = true;
                }
            }
        }
        if (changed) data.setDirty();
        send(player, false);
    }

    /** open=false never pops up a screen during a routine sale or a concert. */
    public static void send(ServerPlayer player, boolean open) {
        BandCareerData.Career c = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        ServerPlayNetworking.send(player, new MerchConsignmentStatePayload(
                open, c.consignmentStock.clone(), c.consignmentEmeralds, c.fans,
                c.consignmentDailySold, c.consignmentTourSold));
    }
}
