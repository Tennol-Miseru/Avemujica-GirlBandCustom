package com.miseru.avemujica.block;

import com.miseru.avemujica.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Optional player-placed light fixture. Never included in generated stages. */
public final class ConcertLightOrbBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    // Required by BaseEntityBlock in Minecraft 1.21.1.
    // Decode using the existing Properties constructor, preserving LIT's default state.
    private static final MapCodec<ConcertLightOrbBlock> CODEC = simpleCodec(ConcertLightOrbBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    public ConcertLightOrbBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(LIT);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConcertLightOrbBlockEntity(pos, state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            level.setBlock(pos, state.cycle(LIT), 3);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                             BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModRegistry.CONCERT_LIGHT_ORB_ENTITY, ConcertLightOrbBlockEntity::clientTick)
                : null;
    }
}
