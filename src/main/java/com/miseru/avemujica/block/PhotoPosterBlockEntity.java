package com.miseru.avemujica.block;

import com.miseru.avemujica.career.CameraPhotoStorage;
import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/** Only stores a tiny photo reference. Pixel data is kept in bounded server-side PNG files. */
public final class PhotoPosterBlockEntity extends BlockEntity {
    private UUID owner;
    private String photoId = "";
    private boolean published;
    private int widthBlocks = 1, heightBlocks = 1;
    private float cropZoom = 1F, cropCenterX = .5F, cropCenterY = .5F;
    public PhotoPosterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PHOTO_POSTER_ENTITY, pos, state);
    }
    public UUID owner() { return owner; }
    public String photoId() { return photoId; }
    public boolean published() { return published; }
    public int widthBlocks() { return widthBlocks; }
    public int heightBlocks() { return heightBlocks; }
    public float cropZoom() { return cropZoom; }
    public float cropCenterX() { return cropCenterX; }
    public float cropCenterY() { return cropCenterY; }
    public void configure(int width, int height, float zoom, float cx, float cy) {
        widthBlocks = Math.max(1, Math.min(4, width));
        heightBlocks = Math.max(1, Math.min(4, height));
        cropZoom = (Float.isFinite(zoom) ? Math.max(1F, Math.min(4F, zoom)) : 1F);
        cropCenterX = (Float.isFinite(cx) ? Math.max(0F, Math.min(1F, cx)) : .5F);
        cropCenterY = (Float.isFinite(cy) ? Math.max(0F, Math.min(1F, cy)) : .5F);
        markAndSync();
    }
    public boolean hasPhoto() { return owner != null && CameraPhotoStorage.validId(photoId); }
    public boolean canEdit(UUID player) { return owner == null || owner.equals(player); }
    public void assign(UUID player, String id, boolean isPublished) {
        if (!canEdit(player) || !CameraPhotoStorage.validId(id)) return;
        owner = player;
        photoId = id;
        published = isPublished;
        markAndSync();
    }
    public void clear(UUID player) {
        if (!canEdit(player)) return;
        owner = null;
        photoId = "";
        published = false;
        markAndSync();
    }
    private void markAndSync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("PhotoOwner", owner);
        if (hasPhoto()) tag.putString("PhotoId", photoId);
        tag.putBoolean("PhotoPublished", published);
        tag.putInt("PosterWidth", widthBlocks);
        tag.putInt("PosterHeight", heightBlocks);
        tag.putFloat("CropZoom", cropZoom);
        tag.putFloat("CropCenterX", cropCenterX);
        tag.putFloat("CropCenterY", cropCenterY);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UUID idOwner = tag.hasUUID("PhotoOwner") ? tag.getUUID("PhotoOwner") : null;
        String id = tag.getString("PhotoId");
        owner = idOwner != null && CameraPhotoStorage.validId(id) ? idOwner : null;
        photoId = owner == null ? "" : id;
        published = owner != null && tag.getBoolean("PhotoPublished");
        widthBlocks = tag.contains("PosterWidth") ? Math.max(1, Math.min(4, tag.getInt("PosterWidth"))) : 1;
        heightBlocks = tag.contains("PosterHeight") ? Math.max(1, Math.min(4, tag.getInt("PosterHeight"))) : 1;
        cropZoom = tag.contains("CropZoom") ? valid(tag.getFloat("CropZoom"),1F,4F,1F) : 1F;
        cropCenterX = tag.contains("CropCenterX") ? valid(tag.getFloat("CropCenterX"),0F,1F,.5F) : .5F;
        cropCenterY = tag.contains("CropCenterY") ? valid(tag.getFloat("CropCenterY"),0F,1F,.5F) : .5F;
    }
    private static float valid(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
