package com.miseru.avemujica.block;

import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.career.CameraPhotoStorage;
import com.miseru.avemujica.career.TourPost;
import com.miseru.avemujica.network.PosterOpenPayload;
import com.miseru.avemujica.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import java.util.List;

/** Existing merch_poster ID and model, with an optional photo attachment. */
public final class PhotoPosterBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty MULTI = BooleanProperty.create("multi");
    private static final MapCodec<PhotoPosterBlock> CODEC = simpleCodec(PhotoPosterBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 6, 14, 16, 10);

    public PhotoPosterBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MULTI,false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING,MULTI);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return state.getValue(MULTI) ? RenderShape.INVISIBLE : RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotoPosterBlockEntity(pos, state);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos,
                                            CollisionContext ctx) { return SHAPE; }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) { return open(level,pos,player); }
    static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PhotoPosterBlock)) return InteractionResult.FAIL;
            // Earlier worlds had a plain decor block with no block entity.
            if (!(level.getBlockEntity(pos) instanceof PhotoPosterBlockEntity)) {
                level.setBlockEntity(new PhotoPosterBlockEntity(pos, state));
                level.sendBlockUpdated(pos, state, state, 3);
            }
            if (!(level.getBlockEntity(pos) instanceof PhotoPosterBlockEntity poster))
                return InteractionResult.SUCCESS;
            if (poster.owner() != null && !poster.owner().equals(player.getUUID())) {
                serverPlayer.sendSystemMessage(Component.literal("[Mujica] 只有设置这张海报的玩家可以修改照片。"));
            } else {
                List<String> published = BandCareerData.get(serverPlayer.serverLevel())
                        .career(serverPlayer.getUUID()).tourPosts.stream()
                        .filter(TourPost::manual).map(TourPost::photoId)
                        .filter(CameraPhotoStorage::validId).distinct().limit(128).toList();
                ServerPlayNetworking.send(serverPlayer, new PosterOpenPayload(pos, published,
                        poster.widthBlocks(),poster.heightBlocks(),poster.cropZoom(),
                        poster.cropCenterX(),poster.cropCenterY(),poster.photoId(),poster.published()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && !state.is(next.getBlock())) {
            if (level.getBlockEntity(pos) instanceof PhotoPosterBlockEntity poster)
                PhotoPosterLayout.removeParts(level,pos,state,poster.widthBlocks(),poster.heightBlocks());
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
