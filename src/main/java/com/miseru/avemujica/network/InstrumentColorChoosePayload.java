package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client submits only the role and palette IDs; ownership is enforced by the server. */
public record InstrumentColorChoosePayload(int roleIndex, int colorIndex) implements CustomPacketPayload {
    public static final Type<InstrumentColorChoosePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "instrument_color_choose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InstrumentColorChoosePayload> CODEC = new StreamCodec<>() {
        @Override public InstrumentColorChoosePayload decode(RegistryFriendlyByteBuf buf) {
            return new InstrumentColorChoosePayload(buf.readVarInt(), buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, InstrumentColorChoosePayload value) {
            buf.writeVarInt(value.roleIndex());
            buf.writeVarInt(value.colorIndex());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
