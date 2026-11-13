package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import com.miseru.avemujica.entity.MemberRole;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Per-owner daily-action and summon switches; skin/audio stay private client-side files. */
public record CharacterCustomizationStatePayload(boolean open, boolean[] enabled, boolean[] summonEnabled) implements CustomPacketPayload {
    public static final Type<CharacterCustomizationStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "character_customization_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterCustomizationStatePayload> CODEC = new StreamCodec<>() {
        @Override public CharacterCustomizationStatePayload decode(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            boolean[] enabled = new boolean[MemberRole.values().length];
            for (int i=0; i<enabled.length; i++) enabled[i] = buf.readBoolean();
            boolean[] summonEnabled = new boolean[MemberRole.values().length];
            for (int i=0; i<summonEnabled.length; i++) summonEnabled[i] = buf.readBoolean();
            return new CharacterCustomizationStatePayload(open, enabled, summonEnabled);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, CharacterCustomizationStatePayload value) {
            buf.writeBoolean(value.open());
            for (int i=0; i<MemberRole.values().length; i++) buf.writeBoolean(value.enabled()[i]);
            for (int i=0; i<MemberRole.values().length; i++) buf.writeBoolean(value.summonEnabled()[i]);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
