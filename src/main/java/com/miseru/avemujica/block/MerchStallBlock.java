package com.miseru.avemujica.block;

import com.miseru.avemujica.career.Merchandising;
import com.miseru.avemujica.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Right-click with a merch item to stock; empty-hand reads counts; sneak-empty-hand retrieves stock. */
public final class MerchStallBlock extends BaseEntityBlock {
    private static final MapCodec<MerchStallBlock> CODEC = simpleCodec(MerchStallBlock::new);
    public MerchStallBlock(BlockBehaviour.Properties props) { super(props); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MerchStallBlockEntity(pos, state);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                         BlockPos pos, Player player, InteractionHand hand,
                                                         BlockHitResult hit) {
        int type = Merchandising.typeOf(stack);
        if (type < 0 || player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MerchStallBlockEntity stall) {
            int added = stall.insert(type, stack.getCount(), player.getUUID());
            // Creative players contribute tangible stock too: every unit is actually stored.
            if (added > 0 && !player.getAbilities().instabuild) stack.shrink(added);
            player.displayClientMessage(Component.literal(added > 0
                    ? "[Mujica] 已入库 " + Merchandising.name(type) + " ×" + added + "（请取回后通过蓝色小章鱼上架寄售）"
                    : "[Mujica] 摊位已满，或由另一位玩家管理。"), true);
        }
        return ItemInteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MerchStallBlockEntity stall) {
            if (player.isShiftKeyDown()) {
                if (stall.owner() != null && stall.owner().equals(player.getUUID())) {
                    int returned = 0;
                    for (int i=0; i<Merchandising.TYPES; i++) {
                        int take = stall.remove(i, 64, player.getUUID());
                        if (take <= 0) continue;
                        ItemStack stack = new ItemStack(Merchandising.item(i), take);
                        if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
                        else if (!stack.isEmpty()) player.drop(stack, false);
                        returned += take;
                    }
                    player.displayClientMessage(Component.literal("[Mujica] 已取回周边 " + returned + " 件。"), true);
                } else player.displayClientMessage(Component.literal("[Mujica] 只能取回自己摊位的库存。"), true);
            } else {
                player.displayClientMessage(Component.literal("[Mujica] 周边摊库存：" + Merchandising.stockSummary(stall) + "。旧摊位不参与自动销售；空手潜行右键取回，再到蓝色小章鱼上架。"), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof MerchStallBlockEntity stall) {
            for (int i=0; i<Merchandising.TYPES; i++) {
                int count = stall.count(i);
                while (count > 0) {
                    int amount = Math.min(count, 64);
                    popResource(level, pos, new ItemStack(Merchandising.item(i), amount));
                    count -= amount;
                }
            }
            stall.clear();
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
