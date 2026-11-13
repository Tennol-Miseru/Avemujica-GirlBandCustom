package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owner asks to pin/unpin a post; the server resolves the post from the owner's archive. */
public record TourPinPayload(String postKey) implements CustomPacketPayload {
    public static final Type<TourPinPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "tour_pin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TourPinPayload> CODEC = new StreamCodec<>() {
        @Override public TourPinPayload decode(RegistryFriendlyByteBuf buf) {
            return new TourPinPayload(buf.readUtf(64));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, TourPinPayload value) {
            buf.writeUtf(value.postKey(), 64);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
