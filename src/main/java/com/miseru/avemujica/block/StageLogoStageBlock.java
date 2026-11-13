package com.miseru.avemujica.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Only level-2 stages create the transparent-logo block entity. Level-1 is unchanged. */
public final class StageLogoStageBlock extends TourStageBlock implements EntityBlock {
    public StageLogoStageBlock(BlockBehaviour.Properties properties) {
        super(properties, true);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StageLogoBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModRegistry.STAGE_LEVEL2_LOGO_ENTITY || level.isClientSide) return null;
        return (world, pos, block, tile) -> {
            if (tile instanceof StageLogoBlockEntity logo)
                StageLogoBlockEntity.tick(world, pos, block, logo);
        };
    }
}
