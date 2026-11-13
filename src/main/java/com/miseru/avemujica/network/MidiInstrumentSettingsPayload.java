package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Server sends the owner's current instrument choices; client can only request changes via validated commands. */
public record MidiInstrumentSettingsPayload(List<String> roles, List<String> presets, List<Integer> volumes) implements CustomPacketPayload {
    public static final Type<MidiInstrumentSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "instrument_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MidiInstrumentSettingsPayload> CODEC = new StreamCodec<>() {
        @Override public MidiInstrumentSettingsPayload decode(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > 32) throw new IllegalArgumentException("Invalid band role count");
            List<String> roles = new ArrayList<>(count), presets = new ArrayList<>(count);
            List<Integer> volumes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                roles.add(buf.readUtf(40)); presets.add(buf.readUtf(40));
                volumes.add(buf.readVarInt());
            }
            return new MidiInstrumentSettingsPayload(List.copyOf(roles), List.copyOf(presets), List.copyOf(volumes));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MidiInstrumentSettingsPayload payload) {
            buf.writeVarInt(payload.roles().size());
            for (int i = 0; i < payload.roles().size(); i++) {
                buf.writeUtf(payload.roles().get(i), 40);
                buf.writeUtf(payload.presets().get(i), 40);
                buf.writeVarInt(payload.volumes().get(i));
            }
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
