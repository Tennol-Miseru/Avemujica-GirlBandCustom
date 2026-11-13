package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One owner-only invitation to save the latest real rendered frame for a completed show. */
public record TourPhotoPayload(int number, int photoSeed, int x, int y, int z) implements CustomPacketPayload {
    public static final Type<TourPhotoPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "tour_photo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TourPhotoPayload> CODEC = new StreamCodec<>() {
        @Override public TourPhotoPayload decode(RegistryFriendlyByteBuf buf) {
            return new TourPhotoPayload(buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, TourPhotoPayload value) {
            buf.writeVarInt(value.number());
            buf.writeInt(value.photoSeed());
            buf.writeInt(value.x()); buf.writeInt(value.y()); buf.writeInt(value.z());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
