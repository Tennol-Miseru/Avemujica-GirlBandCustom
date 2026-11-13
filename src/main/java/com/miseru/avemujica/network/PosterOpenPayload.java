package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Server-authoritative list of the player's published camera photos. */
public record PosterOpenPayload(BlockPos pos, List<String> publishedIds,
        int width, int height, float zoom, float centerX, float centerY,
        String photoId, boolean photoPublished) implements CustomPacketPayload {
    public static final Type<PosterOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "poster_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PosterOpenPayload> CODEC = new StreamCodec<>() {
        @Override public PosterOpenPayload decode(RegistryFriendlyByteBuf b) {
            BlockPos pos = b.readBlockPos();
            int size = b.readVarInt();
            if (size < 0 || size > 128) throw new IllegalArgumentException("Invalid photo list size");
            List<String> ids = new ArrayList<>(size);
            for (int i=0; i<size; i++) ids.add(b.readUtf(40));
            return new PosterOpenPayload(pos, ids, b.readVarInt(), b.readVarInt(),
                    b.readFloat(),b.readFloat(),b.readFloat(),b.readUtf(40),b.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, PosterOpenPayload value) {
            b.writeBlockPos(value.pos());
            if (value.publishedIds().size() > 128) throw new IllegalArgumentException("Photo list too long");
            b.writeVarInt(value.publishedIds().size());
            for (String id : value.publishedIds()) b.writeUtf(id, 40);
            b.writeVarInt(value.width()); b.writeVarInt(value.height());
            b.writeFloat(value.zoom()); b.writeFloat(value.centerX()); b.writeFloat(value.centerY());
            b.writeUtf(value.photoId(),40); b.writeBoolean(value.photoPublished());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
