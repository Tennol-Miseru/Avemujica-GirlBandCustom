package com.miseru.avemujica.item;

import com.miseru.avemujica.menu.SakikoConsoleMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reusable blue octopus: opens Sakiko's mobile band command console. */
public final class BlueOctopusItem extends Item {
    public BlueOctopusItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer owner) {
            owner.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new SakikoConsoleMenu(id, inventory), Component.literal("丰川祥子 · 蓝色小章鱼")));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
