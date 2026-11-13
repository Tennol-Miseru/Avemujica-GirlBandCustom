package com.miseru.avemujica.block;

import com.miseru.avemujica.stage.StageStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Non-dropping sections automatically assembled by the tour-stage item. */
public final class StagePartBlock extends Block {
    public static final EnumProperty<StagePart> PART = EnumProperty.create("part", StagePart.class);
    private static final VoxelShape STEP_SHAPE = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape POST_SHAPE = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape BEAM_X_SHAPE = Block.box(0, 6, 6, 16, 12, 10);
    private static final VoxelShape BEAM_Z_SHAPE = Block.box(6, 6, 0, 10, 12, 16);
    private static final VoxelShape MONITOR_SHAPE = Block.box(1, 0, 2, 15, 7, 14);
    // Two real stair treads, oriented with the performance stage.
    private static final VoxelShape STAIR_SOUTH_SHAPE = Shapes.or(STEP_SHAPE, Block.box(0,8,0,16,16,8));
    private static final VoxelShape STAIR_NORTH_SHAPE = Shapes.or(STEP_SHAPE, Block.box(0,8,8,16,16,16));
    private static final VoxelShape STAIR_WEST_SHAPE = Shapes.or(STEP_SHAPE, Block.box(8,8,0,16,16,16));
    private static final VoxelShape STAIR_EAST_SHAPE = Shapes.or(STEP_SHAPE, Block.box(0,8,0,8,16,16));
    private static VoxelShape railShape(String arms) {
        VoxelShape result = POST_SHAPE;
        for (int layer : new int[]{6,12}) {
            if (arms.indexOf('N')>=0) result = Shapes.or(result,Block.box(6,layer,0,10,layer+3,8));
            if (arms.indexOf('S')>=0) result = Shapes.or(result,Block.box(6,layer,8,10,layer+3,16));
            if (arms.indexOf('E')>=0) result = Shapes.or(result,Block.box(8,layer,6,16,layer+3,10));
            if (arms.indexOf('W')>=0) result = Shapes.or(result,Block.box(0,layer,6,8,layer+3,10));
        }
        return result;
    }

    private static final VoxelShape RAIL_X_SHAPE = railShape("EW");
    private static final VoxelShape RAIL_Z_SHAPE = railShape("NS");
    private static final VoxelShape RAIL_NE_SHAPE = railShape("NE");
    private static final VoxelShape RAIL_NW_SHAPE = railShape("NW");
    private static final VoxelShape RAIL_SE_SHAPE = railShape("SE");
    private static final VoxelShape RAIL_SW_SHAPE = railShape("SW");
    private static final VoxelShape RAIL_N_SHAPE = railShape("N");
    private static final VoxelShape RAIL_E_SHAPE = railShape("E");
    private static final VoxelShape RAIL_S_SHAPE = railShape("S");
    private static final VoxelShape RAIL_W_SHAPE = railShape("W");

    public StagePartBlock(BlockBehaviour.Properties settings) {
        super(settings);
        registerDefaultState(stateDefinition.any().setValue(PART, StagePart.DECK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos center = StageStructure.controllerAtCorner(level, pos);
        if (center == null) center = com.miseru.avemujica.stage.StageStructureLevel2.controllerAtCorner(level, pos);
        if (center == null) return InteractionResult.PASS;
        if (!level.isClientSide && !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        return TourStageBlock.packUp(level, center, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case DECK, EDGE, SPEAKER, LV2_DECK, LV2_DECK_LIT, LV2_BORDER, LV2_BLACK, LV2_RED,
                    LV2_CARPET, LV2_CARPET_LIT, LV2_GOLD, LV2_WINDOW_GOLD,
                    LV2_WINDOW_VIOLET, LV2_WINDOW_BLUE, LV2_WINDOW_PINK, LV2_ROSE,
                    LV2_ROSE_DARK, LV2_ROSE_GOLD, LV2_ROSE_WHITE -> Shapes.block();
            case LV2_RAIL -> POST_SHAPE;
            case LV2_RAIL_X -> RAIL_X_SHAPE;
            case LV2_RAIL_Z -> RAIL_Z_SHAPE;
            case LV2_RAIL_CORNER_NE -> RAIL_NE_SHAPE;
            case LV2_RAIL_CORNER_NW -> RAIL_NW_SHAPE;
            case LV2_RAIL_CORNER_SE -> RAIL_SE_SHAPE;
            case LV2_RAIL_CORNER_SW -> RAIL_SW_SHAPE;
            case LV2_RAIL_END_N -> RAIL_N_SHAPE;
            case LV2_RAIL_END_E -> RAIL_E_SHAPE;
            case LV2_RAIL_END_S -> RAIL_S_SHAPE;
            case LV2_RAIL_END_W -> RAIL_W_SHAPE;
            case LV2_STAIR_HALF -> STEP_SHAPE;
            case LV2_STAIR_SOUTH -> STAIR_SOUTH_SHAPE;
            case LV2_STAIR_NORTH -> STAIR_NORTH_SHAPE;
            case LV2_STAIR_WEST -> STAIR_WEST_SHAPE;
            case LV2_STAIR_EAST -> STAIR_EAST_SHAPE;
            case LV2_TRUSS -> Shapes.block();
            case LV2_KEYBOARD, LV2_DRUMS, LV2_SPOTLIGHT -> Shapes.empty();
            case STEP -> STEP_SHAPE;
            case POST, SUPPORT -> POST_SHAPE;
            case BEAM_X -> BEAM_X_SHAPE;
            case BEAM_Z -> BEAM_Z_SHAPE;
            case MONITOR -> MONITOR_SHAPE;
            default -> Shapes.empty();
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        StagePart part = state.getValue(PART);
        return (part.name().startsWith("LAMP_") || part == StagePart.LV2_SPOTLIGHT)
                ? Shapes.empty() : getShape(state, level, pos, context);
    }
}
