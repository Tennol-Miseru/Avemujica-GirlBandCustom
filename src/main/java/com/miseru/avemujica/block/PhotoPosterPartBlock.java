package com.miseru.avemujica.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible structural cells belonging to a larger photo billboard. */
public final class PhotoPosterPartBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty OFFSET_X = IntegerProperty.create("x", 0, 3);
    public static final IntegerProperty OFFSET_Y = IntegerProperty.create("y", 0, 3);
    private static final MapCodec<PhotoPosterPartBlock> CODEC = simpleCodec(PhotoPosterPartBlock::new);
    private static final VoxelShape NS = Block.box(0,0,6,16,16,10);
    private static final VoxelShape EW = Block.box(6,0,0,10,16,16);
    public PhotoPosterPartBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)
                .setValue(OFFSET_X,0).setValue(OFFSET_Y,0));
    }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING,OFFSET_X,OFFSET_Y);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis()==Direction.Axis.Z ? NS : EW;
    }
    private BlockPos anchor(BlockPos pos, BlockState state) {
        return PhotoPosterLayout.cell(pos,state.getValue(FACING),-state.getValue(OFFSET_X),-state.getValue(OFFSET_Y));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return PhotoPosterBlock.open(level,anchor(pos,state),player);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !PhotoPosterLayout.updating() && !state.is(newState.getBlock())) {
            BlockPos anchor=anchor(pos,state);
            if (level.hasChunkAt(anchor) && level.getBlockState(anchor).getBlock() instanceof PhotoPosterBlock)
                level.destroyBlock(anchor,true);
        }
        super.onRemove(state,level,pos,newState,moving);
    }
}
