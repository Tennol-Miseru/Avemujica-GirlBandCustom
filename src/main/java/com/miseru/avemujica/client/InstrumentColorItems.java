package com.miseru.avemujica.client;

import com.miseru.avemujica.career.InstrumentPalette;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/** Cached render-only variants. No extra registered items and no inventory mutation. */
public final class InstrumentColorItems {
    private InstrumentColorItems() { }
    public static ItemStack[] variants(Item item) {
        ItemStack[] result = new ItemStack[InstrumentPalette.COUNT];
        for (int i = 0; i < result.length; i++) {
            result[i] = new ItemStack(item);
            if (i > 0) result[i].set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(i));
        }
        return result;
    }
    public static ItemStack at(ItemStack[] variants, int color) {
        return variants[InstrumentPalette.safe(color)];
    }
}
