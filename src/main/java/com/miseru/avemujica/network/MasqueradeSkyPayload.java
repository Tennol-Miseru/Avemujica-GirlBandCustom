package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-only sky toggle. No MobEffectInstance, status icon or HUD text. */
public record MasqueradeSkyPayload(boolean active) implements CustomPacketPayload {
    public static final Type<MasqueradeSkyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "masquerade_sky"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MasqueradeSkyPayload> CODEC = new StreamCodec<>() {
        @Override public MasqueradeSkyPayload decode(RegistryFriendlyByteBuf buf) {
            return new MasqueradeSkyPayload(buf.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MasqueradeSkyPayload value) {
            buf.writeBoolean(value.active());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
