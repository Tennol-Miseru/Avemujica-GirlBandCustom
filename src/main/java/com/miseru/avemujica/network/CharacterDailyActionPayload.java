package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only the member index and desired state travel to the server, never user files. */
public record CharacterDailyActionPayload(int roleIndex, boolean enabled) implements CustomPacketPayload {
    public static final Type<CharacterDailyActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "character_daily_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterDailyActionPayload> CODEC = new StreamCodec<>() {
        @Override public CharacterDailyActionPayload decode(RegistryFriendlyByteBuf buf) {
            return new CharacterDailyActionPayload(buf.readVarInt(), buf.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, CharacterDailyActionPayload value) {
            buf.writeVarInt(value.roleIndex());
            buf.writeBoolean(value.enabled());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
