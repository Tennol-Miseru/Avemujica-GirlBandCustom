package com.miseru.avemujica.network;

import com.miseru.avemujica.AveMujicaMod;
import com.miseru.avemujica.career.TourPost;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.List;

/** Server-sent owner-only archive; complete and bounded so paging needs no RPC. */
public record TourArchivePayload(int fans, int cumulativeLikes, int totalShows, List<TourPost> posts, String pinnedPostKey)
        implements CustomPacketPayload {
    public TourArchivePayload(int fans, int cumulativeLikes, int totalShows, List<TourPost> posts) {
        this(fans, cumulativeLikes, totalShows, posts, "");
    }
    public static final Type<TourArchivePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "tour_archive"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TourArchivePayload> CODEC = new StreamCodec<>() {
        @Override public TourArchivePayload decode(RegistryFriendlyByteBuf buf) {
            int fans = buf.readVarInt(), likes = buf.readVarInt(), shows = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > 512) throw new IllegalArgumentException("Tour archive overflow");
            List<TourPost> posts = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                CompoundTag tag = buf.readNbt();
                if (tag == null) throw new IllegalArgumentException("Null tour post");
                posts.add(TourPost.load(tag));
            }
            String pinned = buf.readUtf(64);
            return new TourArchivePayload(fans, likes, shows, List.copyOf(posts), pinned);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, TourArchivePayload payload) {
            buf.writeVarInt(payload.fans()); buf.writeVarInt(payload.cumulativeLikes());
            buf.writeVarInt(payload.totalShows());
            buf.writeVarInt(payload.posts().size());
            for (TourPost post : payload.posts()) buf.writeNbt(post.save());
            buf.writeUtf(payload.pinnedPostKey(), 64);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
