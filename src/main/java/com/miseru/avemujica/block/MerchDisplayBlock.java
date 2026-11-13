package com.miseru.avemujica.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** All merch is ordinary, player-placeable decorative blocks; not NPC-only props. */
public final class MerchDisplayBlock extends HorizontalDirectionalBlock {
    private final VoxelShape shape;
    // Every merch variant has its own collision shape. Keep that shape when a
    // block is reconstructed from its codec (rather than using one shared shape).
    private final MapCodec<MerchDisplayBlock> variantCodec;

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return variantCodec;
    }
    public MerchDisplayBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
        this.variantCodec = simpleCodec(propertiesForCodec ->
                new MerchDisplayBlock(propertiesForCodec, shape));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos,
                                            CollisionContext context) {
        return shape;
    }
}
