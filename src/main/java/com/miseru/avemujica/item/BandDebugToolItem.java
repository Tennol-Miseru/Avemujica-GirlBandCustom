package com.miseru.avemujica.item;

import com.miseru.avemujica.menu.BandDebugMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Creative / operator-only testing tool. Not obtainable through recipes. */
public final class BandDebugToolItem extends Item {
    public BandDebugToolItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            if (!BandDebugMenu.authorized(server)) {
                server.displayClientMessage(Component.literal("调试工具仅允许创造模式或管理员使用。"), false);
            } else {
                server.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                        new BandDebugMenu(id, inventory), Component.literal("Ave Mujica · 角色调试器")));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
