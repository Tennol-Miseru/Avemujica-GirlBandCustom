package com.miseru.avemujica.block;

import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.miseru.avemujica.stage.StageStructureLevel2;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only the level-2 stage controller owns this client-visible logo anchor.
 *  No player content or other save data is stored in this entity. */
public final class StageLogoBlockEntity extends BlockEntity {
    private boolean decorationUpgradeChecked;
    public StageLogoBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.STAGE_LEVEL2_LOGO_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StageLogoBlockEntity tile) {
        if (level.isClientSide || tile.decorationUpgradeChecked) return;
        if (!state.is(ModRegistry.TOUR_STAGE_LEVEL2)) return;
        // Delay until associated chunks have loaded, otherwise an unloaded
        // decorative block would be skipped and never reconciled.
        if (!level.hasChunkAt(pos.offset(16,0,8)) || !level.hasChunkAt(pos.offset(-16,0,-8))
                || !level.hasChunkAt(pos.offset(-16,0,8)) || !level.hasChunkAt(pos.offset(16,0,-8))) return;
        StageStructureLevel2.reconcileDecorations(level, pos,
                state.getValue(TourStageBlock.FACING), true);
        tile.decorationUpgradeChecked = true;
    }
}
