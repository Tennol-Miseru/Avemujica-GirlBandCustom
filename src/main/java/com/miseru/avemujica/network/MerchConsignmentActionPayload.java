package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Small C2S intent, never trusts the client for stock, prices or earnings. */
public record MerchConsignmentActionPayload(int kind, int operation) implements CustomPacketPayload {
    public static final Type<MerchConsignmentActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "consignment_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchConsignmentActionPayload> CODEC = new StreamCodec<>() {
        @Override public MerchConsignmentActionPayload decode(RegistryFriendlyByteBuf buf) {
            return new MerchConsignmentActionPayload(buf.readVarInt(), buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MerchConsignmentActionPayload value) {
            buf.writeVarInt(value.kind());
            buf.writeVarInt(value.operation());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
