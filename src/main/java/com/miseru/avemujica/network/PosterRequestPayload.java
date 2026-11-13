package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Request one visible stand's picture (server checks world position + distance). */
public record PosterRequestPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<PosterRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "poster_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PosterRequestPayload> CODEC = new StreamCodec<>() {
        @Override public PosterRequestPayload decode(RegistryFriendlyByteBuf b) {
            return new PosterRequestPayload(b.readBlockPos());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, PosterRequestPayload value) {
            b.writeBlockPos(value.pos());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
