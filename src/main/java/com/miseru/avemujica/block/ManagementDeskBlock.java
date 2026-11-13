package com.miseru.avemujica.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Hidden compatibility block only: no longer used for management. */
public class ManagementDeskBlock extends Block {
    public ManagementDeskBlock(BlockBehaviour.Properties settings) { super(settings); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
