package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import com.miseru.avemujica.entity.MemberRole;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Current instruments in MemberRole order, plus the separate Doloris toggle. */
public record MemberInstrumentStatePayload(boolean uikaEnabled, int[] instruments) implements CustomPacketPayload {
    public static final Type<MemberInstrumentStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "member_instrument_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MemberInstrumentStatePayload> CODEC = new StreamCodec<>() {
        @Override public MemberInstrumentStatePayload decode(RegistryFriendlyByteBuf buf) {
            boolean uikaEnabled = buf.readBoolean();
            int[] instruments = new int[MemberRole.values().length];
            for (int i = 0; i < instruments.length; i++) instruments[i] = buf.readVarInt();
            return new MemberInstrumentStatePayload(uikaEnabled, instruments);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MemberInstrumentStatePayload value) {
            buf.writeBoolean(value.uikaEnabled());
            for (int i = 0; i < MemberRole.values().length; i++)
                buf.writeVarInt(value.instruments()[i]);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
