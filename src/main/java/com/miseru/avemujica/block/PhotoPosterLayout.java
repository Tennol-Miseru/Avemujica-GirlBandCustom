package com.miseru.avemujica.block;

import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Physically reserves a width x height rectangle of blocks for one anchor poster.
 * All operations run on the server thread; removal guard prevents recursive teardown.
 */
public final class PhotoPosterLayout {
    private PhotoPosterLayout() { }
    private static boolean updating;
    static boolean updating() { return updating; }
    public static Direction rightOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            default -> Direction.EAST;
        };
    }
    public static BlockPos cell(BlockPos anchor, Direction facing, int x, int y) {
        return anchor.relative(rightOf(facing), x).above(y);
    }
    private static boolean isOwnPart(Level level, BlockPos pos, BlockPos anchor, Direction facing, int x, int y) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModRegistry.MERCH_POSTER_PART)
                && state.getValue(PhotoPosterPartBlock.FACING) == facing
                && state.getValue(PhotoPosterPartBlock.OFFSET_X) == x
                && state.getValue(PhotoPosterPartBlock.OFFSET_Y) == y;
    }
    public static boolean canResize(Level level,BlockPos anchor,PhotoPosterBlockEntity poster,int width,int height) {
        if (level.isClientSide || updating || width<1 || height<1 || width>4 || height>4) return false;
        Direction facing=level.getBlockState(anchor).getValue(PhotoPosterBlock.FACING);
        int oldW=poster.widthBlocks(), oldH=poster.heightBlocks();
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
            if (x==0 && y==0) continue;
            BlockPos dest=cell(anchor,facing,x,y);
            if (!level.hasChunkAt(dest)) return false;
            boolean old=x<oldW && y<oldH && isOwnPart(level,dest,anchor,facing,x,y);
            if (!old && !level.getBlockState(dest).isAir()) return false;
        }
        return true;
    }
    public static boolean resize(Level level, BlockPos anchor, PhotoPosterBlockEntity poster, int width, int height) {
        if (!canResize(level,anchor,poster,width,height)) return false;
        Direction facing = level.getBlockState(anchor).getValue(PhotoPosterBlock.FACING);
        int oldW = poster.widthBlocks(), oldH = poster.heightBlocks();
        if (oldW == width && oldH == height) return true;
        updating = true;
        try {
            for (int y=0; y<oldH; y++) for (int x=0; x<oldW; x++) {
                if ((x==0 && y==0) || (x<width && y<height)) continue;
                BlockPos pos = cell(anchor,facing,x,y);
                if (level.hasChunkAt(pos) && isOwnPart(level,pos,anchor,facing,x,y))
                    level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            }
            for (int y=0; y<height; y++) for (int x=0; x<width; x++) {
                if ((x==0 && y==0) || (x<oldW && y<oldH)) continue;
                BlockPos pos=cell(anchor,facing,x,y);
                level.setBlockAndUpdate(pos,ModRegistry.MERCH_POSTER_PART.defaultBlockState()
                        .setValue(PhotoPosterPartBlock.FACING,facing)
                        .setValue(PhotoPosterPartBlock.OFFSET_X,x)
                        .setValue(PhotoPosterPartBlock.OFFSET_Y,y));
            }
            BlockState oldState=level.getBlockState(anchor);
            boolean multi = width>1 || height>1;
            if (oldState.getValue(PhotoPosterBlock.MULTI) != multi)
                level.setBlockAndUpdate(anchor,oldState.setValue(PhotoPosterBlock.MULTI,multi));
            poster.configure(width,height,poster.cropZoom(),poster.cropCenterX(),poster.cropCenterY());
            return true;
        } finally { updating=false; }
    }
    public static void removeParts(Level level, BlockPos anchor, BlockState state, int width, int height) {
        if (level.isClientSide || updating) return;
        updating=true;
        try {
            Direction facing=state.getValue(PhotoPosterBlock.FACING);
            for (int y=0; y<height; y++) for (int x=0; x<width; x++) {
                if (x==0 && y==0) continue;
                BlockPos pos=cell(anchor,facing,x,y);
                if (level.hasChunkAt(pos) && isOwnPart(level,pos,anchor,facing,x,y))
                    level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            }
        } finally { updating=false; }
    }
}
