package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CameraPhotoRequestPayload(String photoId) implements CustomPacketPayload {
    public static final Type<CameraPhotoRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "camera_photo_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraPhotoRequestPayload> CODEC = new StreamCodec<>() {
        @Override public CameraPhotoRequestPayload decode(RegistryFriendlyByteBuf buf) {
            return new CameraPhotoRequestPayload(buf.readUtf(40));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, CameraPhotoRequestPayload value) {
            buf.writeUtf(value.photoId(), 40);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
