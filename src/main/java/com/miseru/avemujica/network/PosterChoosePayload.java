package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Drafts carry a bounded PNG; published images are read by ID from the server. */
public record PosterChoosePayload(BlockPos pos, String photoId, boolean published, byte[] png,
                                  int width, int height, float zoom, float centerX, float centerY)
        implements CustomPacketPayload {
    public static final Type<PosterChoosePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "poster_choose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PosterChoosePayload> CODEC = new StreamCodec<>() {
        @Override public PosterChoosePayload decode(RegistryFriendlyByteBuf b) {
            return new PosterChoosePayload(b.readBlockPos(), b.readUtf(40), b.readBoolean(),
                    b.readByteArray(CameraPublishPayload.MAX_PNG_BYTES), b.readVarInt(), b.readVarInt(),
                    b.readFloat(), b.readFloat(), b.readFloat());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, PosterChoosePayload value) {
            b.writeBlockPos(value.pos());
            b.writeUtf(value.photoId(), 40);
            b.writeBoolean(value.published());
            if (value.png().length > CameraPublishPayload.MAX_PNG_BYTES)
                throw new IllegalArgumentException("Oversize poster picture");
            b.writeByteArray(value.png());
            b.writeVarInt(value.width()); b.writeVarInt(value.height());
            b.writeFloat(value.zoom()); b.writeFloat(value.centerX()); b.writeFloat(value.centerY());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
