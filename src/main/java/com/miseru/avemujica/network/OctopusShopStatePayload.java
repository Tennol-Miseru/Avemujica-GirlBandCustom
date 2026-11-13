package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative wallet snapshot for the portable shop UI. */
public record OctopusShopStatePayload(boolean open, int emeralds, boolean creative) implements CustomPacketPayload {
    public static final Type<OctopusShopStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "octopus_shop_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OctopusShopStatePayload> CODEC = new StreamCodec<>() {
        @Override public OctopusShopStatePayload decode(RegistryFriendlyByteBuf buf) {
            return new OctopusShopStatePayload(buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, OctopusShopStatePayload payload) {
            buf.writeBoolean(payload.open());
            buf.writeVarInt(payload.emeralds());
            buf.writeBoolean(payload.creative());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
