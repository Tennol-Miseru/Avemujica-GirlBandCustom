package com.miseru.avemujica.career;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Server-authored feed record: automatic tour results or a validated, manually published camera photo.
 * Existing tour posts keep their original NBT fields; manual entries add caption and photoId. */
public record TourPost(int number, long day, String biome, String venue,
                       int x, int y, int z, int score, int audience, int cheering,
                       int emeralds, int liveFans, int onlineFans, int likes, int stageTier,
                       int photoSeed, boolean manual, String caption, String photoId,
                       int postersSold, int lightsSold, int recordsSold, int dollsSold, int merchRevenue) {
    public TourPost {
        biome = safe(biome, 96);
        caption = safe(caption, 120);
        photoId = photoId != null && photoId.matches("[0-9a-f]{32}") ? photoId : "";
        manual = manual && !photoId.isEmpty();
        venue = safe(venue, 96);
        number = Mth.clamp(number, 0, 10000000);
        score = Mth.clamp(score, 0, 100);
        audience = Mth.clamp(audience, 0, 20000);
        cheering = Mth.clamp(cheering, 0, 2000);
        emeralds = Mth.clamp(emeralds, 0, 10000);
        liveFans = Mth.clamp(liveFans, 0, 100000);
        onlineFans = Mth.clamp(onlineFans, 0, 100000);
        likes = Mth.clamp(likes, 0, 10000000);
        stageTier = Mth.clamp(stageTier, 0, 8);
        postersSold = Mth.clamp(postersSold, 0, 1000);
        lightsSold = Mth.clamp(lightsSold, 0, 1000);
        recordsSold = Mth.clamp(recordsSold, 0, 1000);
        dollsSold = Mth.clamp(dollsSold, 0, 1000);
        merchRevenue = Mth.clamp(merchRevenue, 0, 10000);
    }
    /** Legacy constructor: manual posts have no merchandise or extra revenue. */
    public TourPost(int number, long day, String biome, String venue,
                    int x, int y, int z, int score, int audience, int cheering,
                    int emeralds, int liveFans, int onlineFans, int likes, int stageTier,
                    int photoSeed, boolean manual, String caption, String photoId) {
        this(number, day, biome, venue, x, y, z, score, audience, cheering,
                emeralds, liveFans, onlineFans, likes, stageTier, photoSeed,
                manual, caption, photoId, 0, 0, 0, 0, 0);
    }
    /** Keep all historical call sites and serialized tour posts compatible. */
    public TourPost(int number, long day, String biome, String venue,
                    int x, int y, int z, int score, int audience, int cheering,
                    int emeralds, int liveFans, int onlineFans, int likes, int stageTier,
                    int photoSeed) {
        this(number, day, biome, venue, x, y, z, score, audience, cheering,
                emeralds, liveFans, onlineFans, likes, stageTier, photoSeed, false, "", "");
    }
    private static String safe(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
    /** Stable per-owner feed identity, including when posts share their display number. */
    public String feedKey() {
        return manual ? "m:" + photoId : "t:" + number + ":" + Integer.toHexString(photoSeed);
    }
    public int totalFans() { return liveFans + onlineFans; }
    public String grade() { return score >= 90 ? "S" : score >= 75 ? "A" : score >= 60 ? "B" : "C"; }
    public String biomeLabel() {
        String id = biome.substring(biome.lastIndexOf(':') + 1).toLowerCase(java.util.Locale.ROOT);
        if (id.contains("desert")) return "沙漠";
        if (id.contains("snow") || id.contains("ice") || id.contains("frozen")) return "雪原";
        if (id.contains("taiga")) return "针叶林";
        if (id.contains("savanna")) return "热带草原";
        if (id.contains("jungle")) return "丛林";
        if (id.contains("swamp")) return "沼泽";
        if (id.contains("cherry")) return "樱花林";
        if (id.contains("forest")) return "森林";
        if (id.contains("plains")) return "平原";
        return "远方";
    }
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("No", number); t.putLong("Day", day); t.putString("Biome", biome);
        t.putString("Venue", venue); t.putInt("X", x); t.putInt("Y", y); t.putInt("Z", z);
        t.putInt("Score", score); t.putInt("Audience", audience); t.putInt("Cheering", cheering);
        t.putInt("Emeralds", emeralds); t.putInt("LiveFans", liveFans);
        t.putInt("OnlineFans", onlineFans); t.putInt("Likes", likes);
        t.putInt("StageTier", stageTier); t.putInt("PhotoSeed", photoSeed);
        if (manual) { t.putBoolean("Manual", true); t.putString("Caption", caption); t.putString("PhotoId", photoId); }
        if (merchRevenue > 0) {
            t.putInt("MerchPoster",postersSold); t.putInt("MerchLight",lightsSold);
            t.putInt("MerchRecord",recordsSold); t.putInt("MerchDoll",dollsSold); t.putInt("MerchEmeralds",merchRevenue);
        }
        return t;
    }
    public static TourPost load(CompoundTag t) {
        return new TourPost(t.getInt("No"), t.getLong("Day"), t.getString("Biome"),
                t.getString("Venue"), t.getInt("X"), t.getInt("Y"), t.getInt("Z"),
                t.getInt("Score"), t.getInt("Audience"), t.getInt("Cheering"),
                t.getInt("Emeralds"), t.getInt("LiveFans"), t.getInt("OnlineFans"),
                t.getInt("Likes"), t.getInt("StageTier"), t.getInt("PhotoSeed"),
                t.getBoolean("Manual"), t.getString("Caption"), t.getString("PhotoId"),
                t.getInt("MerchPoster"), t.getInt("MerchLight"), t.getInt("MerchRecord"),
                t.getInt("MerchDoll"), t.getInt("MerchEmeralds"));
    }
}
