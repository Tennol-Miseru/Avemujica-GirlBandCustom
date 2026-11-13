package com.miseru.avemujica.career;

import com.miseru.avemujica.network.TourArchivePayload;
import com.miseru.avemujica.network.TourPinPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fan-account facade shared by the octopus button and /avemujica account command. */
public final class TourAccount {
    private TourAccount() { }
    public static TourPost publish(ServerLevel level, UUID owner, BandCareerData.Career career,
                                   BlockPos stage, String venueKey, int score, int audience, int cheering,
                                   int emeralds, int liveFans) {
        return publish(level,owner,career,stage,venueKey,score,audience,cheering,emeralds,liveFans,
                new Merchandising.Sale(0,0,0,0,0,0,0,0,0,0));
    }
    public static TourPost publish(ServerLevel level, UUID owner, BandCareerData.Career career,
                                   BlockPos stage, String venueKey, int score, int audience, int cheering,
                                   int emeralds, int liveFans, Merchandising.Sale sale) {
        String biome = level.getBiome(stage).unwrapKey().map(k -> k.location().toString())
                .orElse("minecraft:plains");
        int stageTier = level.getBlockState(stage).is(com.miseru.avemujica.registry.ModRegistry.TOUR_STAGE_LEVEL2)
                ? 2 : level.getBlockState(stage).is(com.miseru.avemujica.registry.ModRegistry.TOUR_STAGE) ? 1 : 0;
        int fansBeforeSocial = career.fans;
        long day = level.getDayTime() / 24000L;
        long seed = level.getSeed() ^ owner.getMostSignificantBits() ^ (long)stage.getX() * 341873128712L
                ^ (long)stage.getZ() * 132897987541L ^ (long)career.villageShows * 0x9E3779B97F4A7C15L;
        int likes = TourEngagement.likes(fansBeforeSocial, score, audience, stageTier, seed);
        int onlineFans = TourEngagement.onlineFans(likes, score);
        TourPost post = new TourPost(career.villageShows, day, biome, venueKey,
                stage.getX(), stage.getY(), stage.getZ(), score, audience, cheering,
                emeralds + sale.emeralds(), liveFans, onlineFans, likes, stageTier,
                (int)(seed ^ (seed >>> 32)), false, "", "",
                sale.posters(), sale.lights(), sale.records(), sale.dolls(), sale.emeralds());
        career.publish(post);
        career.fans += onlineFans;
        return post;
    }
    public static void pin(ServerPlayer player, TourPinPayload request) {
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.career(player.getUUID());
        String key = request.postKey();
        // Only allow clearing or selecting an existing post belonging to this player.
        if (key == null || key.length() > 64) return;
        if (!key.isEmpty() && career.tourPosts.stream().noneMatch(p -> p.feedKey().equals(key))) return;
        if (!career.pinnedPostKey.equals(key)) {
            career.pinnedPostKey = key;
            data.setDirty();
        }
        open(player);
    }
    public static void open(ServerPlayer player) {
        BandCareerData.Career career = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        List<TourPost> newestFirst = new ArrayList<>(career.tourPosts);
        java.util.Collections.reverse(newestFirst);
        ServerPlayNetworking.send(player, new TourArchivePayload(career.fans,
                career.accountTotalLikes, career.villageShows, List.copyOf(newestFirst), career.pinnedPostKey));
    }
}
