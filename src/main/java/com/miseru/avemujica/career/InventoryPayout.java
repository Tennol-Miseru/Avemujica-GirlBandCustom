package com.miseru.avemujica.career;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Safely transfers physical reward items into the owner's inventory.
 *
 * A transfer is counted only after Minecraft has actually consumed that many
 * units from its source stack. We deliberately DO NOT drop overflowing rewards
 * as entities: any remainder must stay in the saved escrow until the player
 * makes room and requests it again.
 */
public final class InventoryPayout {
    private InventoryPayout() { }

    /** @return number of units actually inserted (0..requested). */
    public static int insert(ServerPlayer player, Item item, int requested) {
        if (requested <= 0) return 0;
        int maxStack = new ItemStack(item).getMaxStackSize();
        if (maxStack <= 0) return 0;
        int delivered = 0;
        while (delivered < requested) {
            int batch = Math.min(requested - delivered, maxStack);
            ItemStack stack = new ItemStack(item, batch);
            player.getInventory().add(stack);
            int added = batch - stack.getCount();
            if (added <= 0) break; // Full inventory. Keep the remainder in escrow.
            delivered += added;
        }
        return delivered;
    }

    /** Tell the player immediately about the new slot counts after payout. */
    public static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }
}
