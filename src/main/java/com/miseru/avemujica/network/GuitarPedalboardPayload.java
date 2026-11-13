package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Guitar effect selections sent by the server; names are checked server-side before saving. */
public record GuitarPedalboardPayload(List<String> roles, List<String> presets, List<Integer> mixes)
        implements CustomPacketPayload {
    public static final Type<GuitarPedalboardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "guitar_pedalboard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuitarPedalboardPayload> CODEC = new StreamCodec<>() {
        @Override public GuitarPedalboardPayload decode(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid pedalboard role count");
            List<String> roles = new ArrayList<>(count), presets = new ArrayList<>(count);
            List<Integer> mixes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                roles.add(buf.readUtf(40)); presets.add(buf.readUtf(40));
                mixes.add(buf.readVarInt());
            }
            return new GuitarPedalboardPayload(List.copyOf(roles), List.copyOf(presets), List.copyOf(mixes));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, GuitarPedalboardPayload payload) {
            buf.writeVarInt(payload.roles().size());
            for (int i = 0; i < payload.roles().size(); i++) {
                buf.writeUtf(payload.roles().get(i), 40);
                buf.writeUtf(payload.presets().get(i), 40);
                buf.writeVarInt(payload.mixes().get(i));
            }
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
