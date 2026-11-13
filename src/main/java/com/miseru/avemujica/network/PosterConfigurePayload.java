package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Poster dimensions and camera crop are sent independently of photo PNG data. */
public record PosterConfigurePayload(BlockPos pos, int width, int height,
                                     float zoom, float centerX, float centerY) implements CustomPacketPayload {
    public static final Type<PosterConfigurePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID,"poster_configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PosterConfigurePayload> CODEC = new StreamCodec<>() {
        @Override public PosterConfigurePayload decode(RegistryFriendlyByteBuf b) {
            return new PosterConfigurePayload(b.readBlockPos(),b.readVarInt(),b.readVarInt(),
                    b.readFloat(),b.readFloat(),b.readFloat());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, PosterConfigurePayload p) {
            b.writeBlockPos(p.pos()); b.writeVarInt(p.width()); b.writeVarInt(p.height());
            b.writeFloat(p.zoom()); b.writeFloat(p.centerX()); b.writeFloat(p.centerY());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
