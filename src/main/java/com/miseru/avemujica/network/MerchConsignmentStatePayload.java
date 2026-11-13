package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import com.miseru.avemujica.career.Merchandising;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C owner-only consignment snapshot. No arbitrary inventory or item NBT crosses the network. */
public record MerchConsignmentStatePayload(boolean open, int[] stock, int unclaimed,
                                           int fans, int dailySold, int tourSold)
        implements CustomPacketPayload {
    public static final Type<MerchConsignmentStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "consignment_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchConsignmentStatePayload> CODEC = new StreamCodec<>() {
        @Override public MerchConsignmentStatePayload decode(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            int length = buf.readVarInt();
            if (length != Merchandising.TYPES) throw new IllegalArgumentException("Invalid merch stock length");
            int[] stock = new int[length];
            for (int i = 0; i < length; i++) stock[i] = Math.max(0, buf.readVarInt());
            return new MerchConsignmentStatePayload(open, stock, buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MerchConsignmentStatePayload p) {
            buf.writeBoolean(p.open());
            buf.writeVarInt(Merchandising.TYPES);
            for (int i = 0; i < Merchandising.TYPES; i++) buf.writeVarInt(p.stock()[i]);
            buf.writeVarInt(p.unclaimed());
            buf.writeVarInt(p.fans());
            buf.writeVarInt(p.dailySold());
            buf.writeVarInt(p.tourSold());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
