package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** On-demand image data, never sent for every stand during chunk sync. */
public record PosterPhotoPayload(UUID owner, String photoId, byte[] png) implements CustomPacketPayload {
    public static final Type<PosterPhotoPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "poster_photo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PosterPhotoPayload> CODEC = new StreamCodec<>() {
        @Override public PosterPhotoPayload decode(RegistryFriendlyByteBuf b) {
            return new PosterPhotoPayload(b.readUUID(), b.readUtf(40),
                    b.readByteArray(CameraPublishPayload.MAX_PNG_BYTES));
        }
        @Override public void encode(RegistryFriendlyByteBuf b, PosterPhotoPayload value) {
            b.writeUUID(value.owner());
            b.writeUtf(value.photoId(), 40);
            if (value.png().length > CameraPublishPayload.MAX_PNG_BYTES)
                throw new IllegalArgumentException("Oversize poster picture");
            b.writeByteArray(value.png());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
