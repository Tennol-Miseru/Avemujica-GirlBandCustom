package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client intent contains only the catalogue SKU; the price and items are never client-controlled. */
public record OctopusShopBuyPayload(int sku) implements CustomPacketPayload {
    public static final Type<OctopusShopBuyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "octopus_shop_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OctopusShopBuyPayload> CODEC = new StreamCodec<>() {
        @Override public OctopusShopBuyPayload decode(RegistryFriendlyByteBuf buf) {
            return new OctopusShopBuyPayload(buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, OctopusShopBuyPayload payload) {
            buf.writeVarInt(payload.sku());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
