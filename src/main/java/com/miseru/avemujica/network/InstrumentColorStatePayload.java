package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import com.miseru.avemujica.entity.MemberRole;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Five color IDs in MemberRole.values() order. Never carries texture bytes. */
public record InstrumentColorStatePayload(boolean open, int[] colors) implements CustomPacketPayload {
    public static final Type<InstrumentColorStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "instrument_color_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InstrumentColorStatePayload> CODEC = new StreamCodec<>() {
        @Override public InstrumentColorStatePayload decode(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            int[] colors = new int[MemberRole.values().length];
            for (int i = 0; i < colors.length; i++) colors[i] = buf.readVarInt();
            return new InstrumentColorStatePayload(open, colors);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, InstrumentColorStatePayload value) {
            buf.writeBoolean(value.open());
            for (int i = 0; i < MemberRole.values().length; i++)
                buf.writeVarInt(value.colors()[i]);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
