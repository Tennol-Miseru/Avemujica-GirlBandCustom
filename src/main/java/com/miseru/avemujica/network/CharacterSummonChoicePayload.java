package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative per-owner summon eligibility toggle. */
public record CharacterSummonChoicePayload(int roleIndex, boolean enabled) implements CustomPacketPayload {
    public static final Type<CharacterSummonChoicePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "character_summon_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSummonChoicePayload> CODEC = new StreamCodec<>() {
        @Override public CharacterSummonChoicePayload decode(RegistryFriendlyByteBuf buf) {
            return new CharacterSummonChoicePayload(buf.readVarInt(), buf.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, CharacterSummonChoicePayload value) {
            buf.writeVarInt(value.roleIndex());
            buf.writeBoolean(value.enabled());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
