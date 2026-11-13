package com.miseru.avemujica.block;

import com.miseru.avemujica.career.BandManagement;
import com.miseru.avemujica.item.TourStageItem;
import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.stage.StageStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Center controller; its horizontal FRONT is persisted so old stages remain SOUTH by default. */
public class TourStageBlock extends Block {
    private final boolean levelTwo;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public TourStageBlock(BlockBehaviour.Properties settings) { this(settings, false); }
    public TourStageBlock(BlockBehaviour.Properties settings, boolean levelTwo) {
        super(settings);
        this.levelTwo = levelTwo;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction front = TourStageItem.facing(context.getItemInHand(),
                context.getPlayer() == null ? Direction.SOUTH : context.getPlayer().getDirection().getOpposite());
        return defaultBlockState().setValue(FACING, front);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        return packUp(level, pos, player);
    }

    /** Shared by the center and each of the four vertical corner columns.
     * Only the actual controller triggers teardown/refund (once) on the server.
     */
    public static InteractionResult packUp(Level level, BlockPos center, Player player) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.hasChunkAt(center)) return InteractionResult.FAIL;
        BlockState controller = level.getBlockState(center);
        if (!ModRegistry.isTourStage(controller)) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!level.mayInteract(player, center)) return InteractionResult.FAIL;
        if (level instanceof ServerLevel server && BandManagement.isAnyShowAt(server, center)) {
            player.displayClientMessage(Component.literal("演出正在进行，结束后才能收起舞台。"), true);
            return InteractionResult.FAIL;
        }
        Direction front = controller.getValue(FACING);
        // Re-read and remove the one controller; onRemove disassembles the attached parts.
        if (!level.removeBlock(center, false)) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) {
            ItemStack folded = new ItemStack(controller.is(ModRegistry.TOUR_STAGE_LEVEL2)
                    ? ModRegistry.TOUR_STAGE_LEVEL2_ITEM : ModRegistry.TOUR_STAGE_ITEM);
            TourStageItem.setFacing(folded, front);
            if (!player.getInventory().add(folded)) player.drop(folded, false);
        }
        player.displayClientMessage(Component.literal("舞台已收起，方向设置已保留。"), true);
        return InteractionResult.SUCCESS;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            if (levelTwo) com.miseru.avemujica.stage.StageStructureLevel2.disassemble(level, pos, state.getValue(FACING));
            else StageStructure.disassemble(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
