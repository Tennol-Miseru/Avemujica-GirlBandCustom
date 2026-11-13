package com.miseru.avemujica.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/** The coffee is a real food item, but should animate as a drink, not a bite. */
public final class CoffeeItem extends Item {
    public CoffeeItem(Properties properties) { super(properties); }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
}
