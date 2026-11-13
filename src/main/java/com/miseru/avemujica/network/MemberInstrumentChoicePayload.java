package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Nonnegative values select an instrument. Negative constants toggle only Doloris. */
public record MemberInstrumentChoicePayload(int roleIndex, int choice) implements CustomPacketPayload {
    public static final int DISABLE_UIKA = -1;
    public static final int ENABLE_UIKA = -2;
    public static final Type<MemberInstrumentChoicePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "member_instrument_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MemberInstrumentChoicePayload> CODEC = new StreamCodec<>() {
        @Override public MemberInstrumentChoicePayload decode(RegistryFriendlyByteBuf buf) {
            return new MemberInstrumentChoicePayload(buf.readVarInt(), buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MemberInstrumentChoicePayload value) {
            buf.writeVarInt(value.roleIndex());
            buf.writeVarInt(value.choice());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
