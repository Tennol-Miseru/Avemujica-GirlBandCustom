package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Transfer server-stored camera shots on demand; not included in the large archive packet. */
public record CameraPhotoDataPayload(String photoId, byte[] png) implements CustomPacketPayload {
    public static final Type<CameraPhotoDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "camera_photo_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraPhotoDataPayload> CODEC = new StreamCodec<>() {
        @Override public CameraPhotoDataPayload decode(RegistryFriendlyByteBuf buf) {
            return new CameraPhotoDataPayload(buf.readUtf(40), buf.readByteArray(CameraPublishPayload.MAX_PNG_BYTES));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, CameraPhotoDataPayload value) {
            buf.writeUtf(value.photoId(), 40);
            if (value.png().length > CameraPublishPayload.MAX_PNG_BYTES)
                throw new IllegalArgumentException("Oversize stored photo");
            buf.writeByteArray(value.png());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
