package com.miseru.avemujica.career;

import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative shop catalog. Finished merchandise costs precisely the
 * emerald component of its existing crafting recipe, with matching batch sizes.
 * Buying here is an alternative to crafting, not an alternative way to mint
 * sale proceeds: consignment still requires a separate deposit and buyer demand.
 */
public final class OctopusShopCatalog {
    private OctopusShopCatalog() { }

    public record Offer(String title, int emeralds, ItemStack[] goods) { }
    public static final int COUNT = 9;

    public static Offer offer(int sku) {
        return switch (sku) {
            case 0 -> new Offer("一级巡演舞台", 16,
                    new ItemStack[]{new ItemStack(ModRegistry.TOUR_STAGE_ITEM)});
            case 1 -> new Offer("二级巡演舞台", 128,
                    new ItemStack[]{new ItemStack(ModRegistry.TOUR_STAGE_LEVEL2_ITEM)});
            case 2 -> new Offer("乐队海报立牌 ×4", 4,
                    new ItemStack[]{new ItemStack(ModRegistry.MERCH_POSTER_ITEM, 4)});
            case 3 -> new Offer("限定唱片 ×2", 6,
                    new ItemStack[]{new ItemStack(ModRegistry.MERCH_RECORD_ITEM, 2)});
            case 4 -> new Offer("Oblivionis娃娃", 6,
                    new ItemStack[]{new ItemStack(ModRegistry.MERCH_DOLL_OBLIVIONIS_ITEM)});
            case 5 -> new Offer("Doloris娃娃", 6,
                    new ItemStack[]{new ItemStack(ModRegistry.MERCH_DOLL_DOLORIS_ITEM)});
            case 6 -> new Offer("Mortis娃娃", 6,
                    new ItemStack[]{new ItemStack(ModRegistry.MERCH_DOLL_MORTIS_ITEM)});
            case 7 -> new Offer("Timoris娃娃", 6,
                    new ItemStack[]{new ItemStack(ModRegistry.MERCH_DOLL_TIMORIS_ITEM)});
            case 8 -> new Offer("Amoris娃娃", 6,
                    new ItemStack[]{new ItemStack(ModRegistry.MERCH_DOLL_AMORIS_ITEM)});
            default -> throw new IllegalArgumentException("Invalid shop offer: " + sku);
        };
    }
}
