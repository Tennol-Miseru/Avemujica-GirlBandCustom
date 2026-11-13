package com.miseru.avemujica.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A reusable player camera; screenshots are captured only on the owning client. */
public final class MujicaCameraItem extends Item {
    public MujicaCameraItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (player.isShiftKeyDown())
                com.miseru.avemujica.client.CameraCapture.openDrafts();
            else com.miseru.avemujica.client.CameraCapture.start();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
