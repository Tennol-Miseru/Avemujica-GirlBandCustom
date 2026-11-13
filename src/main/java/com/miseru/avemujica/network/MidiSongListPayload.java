package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Server-owned, bounded song library. -1: band management, <= -2: Sakiko team picker
 * (decode entity id as -memberId-2), positive: member solo. The command still
 * validates the target NPC; this packet never grants authority. */
public record MidiSongListPayload(int memberId, List<String> songs) implements CustomPacketPayload {
    public static final Type<MidiSongListPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "midi_library"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MidiSongListPayload> CODEC = new StreamCodec<>() {
        @Override public MidiSongListPayload decode(RegistryFriendlyByteBuf buf) {
            int memberId = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > 129) throw new IllegalArgumentException("MIDI song list out of bounds");
            List<String> songs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) songs.add(buf.readUtf(100));
            return new MidiSongListPayload(memberId, List.copyOf(songs));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MidiSongListPayload payload) {
            buf.writeVarInt(payload.memberId());
            buf.writeVarInt(payload.songs().size());
            for (String name : payload.songs()) buf.writeUtf(name, 100);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
