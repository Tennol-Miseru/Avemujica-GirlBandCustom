package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client uploads one bounded PNG and caption after confirming publication. */
public record CameraPublishPayload(String photoId, String caption, byte[] png) implements CustomPacketPayload {
    public static final int MAX_PNG_BYTES = 262144;
    public static final Type<CameraPublishPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "camera_publish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraPublishPayload> CODEC = new StreamCodec<>() {
        @Override public CameraPublishPayload decode(RegistryFriendlyByteBuf buf) {
            return new CameraPublishPayload(buf.readUtf(40), buf.readUtf(120), buf.readByteArray(MAX_PNG_BYTES));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, CameraPublishPayload value) {
            buf.writeUtf(value.photoId(), 40);
            buf.writeUtf(value.caption(), 120);
            if (value.png().length > MAX_PNG_BYTES) throw new IllegalArgumentException("Oversize camera photo");
            buf.writeByteArray(value.png());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
