package com.miseru.avemujica.career;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;

/** The village-tour progression loop: verified settlement, cooldown, themed loot tiers. */
public final class VillageTour {
    private VillageTour() { }
    public static final long REPEAT_COOLDOWN = 48000L; // two full in-game days
    public record Venue(String key, int population, boolean hasBell) { }

    /** Snapshot of a single candidate location, suitable for both GUI diagnostics and booking. */
    public record Survey(BlockPos center, Venue venue, int villagers, int beds, boolean hasBell) {
        public boolean qualified() { return venue != null; }
    }

    /** A tour stage is preferred only if that stage actually has a valid village.
     * Otherwise use the owner's position. This avoids a nearby but distant stage
     * causing a false negative when the bell/beds are close to the player. */
    public static Survey bestSurvey(ServerLevel level, BlockPos stage, BlockPos playerOrigin) {
        Survey byStage = survey(level, stage);
        if (stage.equals(playerOrigin) || byStage.qualified()) return byStage;
        Survey byPlayer = survey(level, playerOrigin);
        if (byPlayer.qualified()) return byPlayer;
        // Return the more informative failed candidate for UI diagnostics.
        return byPlayer.villagers() > byStage.villagers() ? byPlayer : byStage;
    }

    /** Cheap spectator-presence test for an already-running show, not a village-qualified check. */
    public static boolean nearbyAudience(ServerLevel level, BlockPos stage) {
        return !level.getEntitiesOfClass(Villager.class, new AABB(stage).inflate(32D, 9D, 32D),
                Villager::isAlive).isEmpty();
    }

    /** Requires living villagers plus the bell or beds that distinguish a settlement from random NPCs. */
    public static Venue findVenue(ServerLevel level, BlockPos stage) {
        return survey(level, stage).venue();
    }

    /** Use precisely the same village criteria for the GUI and actual booking.
     * For performance, do not scan blocks when no villagers are present. */
    public static Survey survey(ServerLevel level, BlockPos stage) {
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                new AABB(stage).inflate(32D, 9D, 32D), Villager::isAlive);
        if (villagers.isEmpty()) return new Survey(stage.immutable(), null, 0, 0, false);
        BlockPos bell = null;
        int beds = 0;
        // This block scan happens only at booking (not in the per-tick UI snapshot).
        for (BlockPos pos : BlockPos.betweenClosed(stage.offset(-29, -5, -29), stage.offset(29, 5, 29))) {
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            var block = state.getBlock();
            if (block == Blocks.BELL) {
                if (bell == null || pos.distSqr(stage) < bell.distSqr(stage)) bell = pos.immutable();
            } else if (block instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                beds++;
            }
        }
        if (bell == null && (villagers.size() < 2 || beds < 2))
            return new Survey(stage.immutable(), null, villagers.size(), beds, false);
        // Bell anchors are stable across return visits. Bed villages use villager-centroid regions.
        String key;
        if (bell != null) {
            key = "bell:" + bell.getX() + ":" + bell.getZ();
        } else {
            double centerX = 0, centerZ = 0;
            for (Villager villager : villagers) { centerX += villager.getX(); centerZ += villager.getZ(); }
            int x = Math.floorDiv((int)Math.floor(centerX / villagers.size()), 96);
            int z = Math.floorDiv((int)Math.floor(centerZ / villagers.size()), 96);
            key = "region:" + x + ":" + z;
        }
        return new Survey(stage.immutable(), new Venue(key, villagers.size(), bell != null),
                villagers.size(), beds, bell != null);
    }

    private static void add(Map<String, Integer> loot, Item item, int count) {
        if (count <= 0) return;
        loot.merge(BuiltInRegistries.ITEM.getKey(item).toString(), count, Integer::sum);
    }

    /** Rarity depends primarily on band level, with extra draws for quality and new towns. */
    public static Map<String, Integer> generateRewards(ServerLevel world, int bandLevel,
                                                        int score, boolean firstVisit) {
        RandomSource random = world.getRandom();
        int tier = Math.max(1, Math.min(10, bandLevel));
        Map<String, Integer> loot = new LinkedHashMap<>();
        add(loot, Items.BREAD, 2 + random.nextInt(4));
        add(loot, Items.IRON_NUGGET, 3 + random.nextInt(5));
        if (tier >= 2) add(loot, Items.EMERALD, 1 + random.nextInt(1 + tier / 3));
        int draws = 1 + tier / 3 + (score >= 75 ? 1 : 0) + (firstVisit ? 1 : 0);
        for (int i = 0; i < draws; i++) {
            int roll = random.nextInt(1000);
            if (tier >= 10 && roll < 11) add(loot, Items.TOTEM_OF_UNDYING, 1);
            else if (tier >= 9 && roll < 47) add(loot, Items.NETHERITE_SCRAP, 1);
            else if (tier >= 8 && roll < 70) add(loot, Items.ENCHANTED_GOLDEN_APPLE, 1);
            else if (tier >= 7 && roll < 170) add(loot, Items.DIAMOND, 1 + random.nextInt(2));
            else if (tier >= 6 && roll < 250) add(loot, Items.BLAZE_ROD, 1 + random.nextInt(2));
            else if (tier >= 5 && roll < 320) add(loot, Items.ENDER_PEARL, 1 + random.nextInt(3));
            else if (tier >= 4 && roll < 425) add(loot, Items.GOLD_INGOT, 1 + random.nextInt(3));
            else if (tier >= 3 && roll < 570) add(loot, Items.LAPIS_LAZULI, 3 + random.nextInt(6));
            else if (tier >= 2 && roll < 690) add(loot, Items.IRON_INGOT, 1 + random.nextInt(4));
            else if (roll < 820) add(loot, Items.COPPER_INGOT, 2 + random.nextInt(4));
            else add(loot, Items.COAL, 2 + random.nextInt(5));
        }
        if (firstVisit) add(loot, Items.EMERALD, Math.max(1, tier / 2));
        return loot;
    }
}
