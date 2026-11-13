package com.miseru.avemujica.career;

import com.miseru.avemujica.block.MerchStallBlockEntity;
import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lightweight Minecraft-native merchandise sales and safe, deferred village decoration. */
public final class Merchandising {
    private Merchandising() { }

    public static final int POSTER = 0;
    public static final int LIGHTSTICK = 1;
    public static final int RECORD = 2;
    /** Retained only for old saves and old stocked kiosks. Hidden from the creative tab. */
    public static final int LEGACY_DOLL_SET = 3;
    public static final int OBLIVIONIS_DOLL = 4;
    public static final int DOLORIS_DOLL = 5;
    public static final int MORTIS_DOLL = 6;
    public static final int TIMORIS_DOLL = 7;
    public static final int AMORIS_DOLL = 8;
    public static final int TYPES = 9;
    // Keep the original nine persisted stock slots and network indices for old worlds.
    // The two retired goods are excluded from the official sales catalogue.
    public static final List<Integer> SALE_TYPES = List.of(
            POSTER, RECORD, OBLIVIONIS_DOLL, DOLORIS_DOLL,
            MORTIS_DOLL, TIMORIS_DOLL, AMORIS_DOLL);
    public static boolean isConsignable(int type) { return SALE_TYPES.contains(type); }

    private static final String[] NAMES = {
            "乐队海报立牌", "应援灯展示架", "限定唱片展示框", "旧版乐队娃娃套组",
            "Oblivionis娃娃", "Doloris娃娃", "Mortis娃娃", "Timoris娃娃", "Amoris娃娃"
    };
    private static final int MAX_DECOR_PER_VILLAGE = 5;

    public static Item item(int i) { return switch (i) {
        case POSTER -> ModRegistry.MERCH_POSTER_ITEM;
        case LIGHTSTICK -> ModRegistry.MERCH_LIGHTSTICK_ITEM;
        case RECORD -> ModRegistry.MERCH_RECORD_ITEM;
        case LEGACY_DOLL_SET -> ModRegistry.MERCH_DOLL_ITEM;
        case OBLIVIONIS_DOLL -> ModRegistry.MERCH_DOLL_OBLIVIONIS_ITEM;
        case DOLORIS_DOLL -> ModRegistry.MERCH_DOLL_DOLORIS_ITEM;
        case MORTIS_DOLL -> ModRegistry.MERCH_DOLL_MORTIS_ITEM;
        case TIMORIS_DOLL -> ModRegistry.MERCH_DOLL_TIMORIS_ITEM;
        case AMORIS_DOLL -> ModRegistry.MERCH_DOLL_AMORIS_ITEM;
        default -> throw new IllegalArgumentException("Invalid merch type: " + i);
    }; }
    public static Block block(int i) { return switch (i) {
        case POSTER -> ModRegistry.MERCH_POSTER;
        case LIGHTSTICK -> ModRegistry.MERCH_LIGHTSTICK;
        case RECORD -> ModRegistry.MERCH_RECORD;
        case LEGACY_DOLL_SET -> ModRegistry.MERCH_DOLL;
        case OBLIVIONIS_DOLL -> ModRegistry.MERCH_DOLL_OBLIVIONIS;
        case DOLORIS_DOLL -> ModRegistry.MERCH_DOLL_DOLORIS;
        case MORTIS_DOLL -> ModRegistry.MERCH_DOLL_MORTIS;
        case TIMORIS_DOLL -> ModRegistry.MERCH_DOLL_TIMORIS;
        case AMORIS_DOLL -> ModRegistry.MERCH_DOLL_AMORIS;
        default -> throw new IllegalArgumentException("Invalid merch type: " + i);
    }; }
    public static String name(int i) { return NAMES[i]; }
    public static int typeOf(ItemStack stack) {
        for (int i = 0; i < TYPES; i++) if (stack.is(item(i))) return i;
        return -1;
    }
    public static int dollsIn(MerchStallBlockEntity stall) {
        int total = 0;
        for (int i = LEGACY_DOLL_SET; i < TYPES; i++) total += stall.count(i);
        return total;
    }
    public static String stockSummary(MerchStallBlockEntity stall) {
        return "海报 " + stall.count(POSTER)
                + " / 应援灯 " + stall.count(LIGHTSTICK)
                + " / 唱片 " + stall.count(RECORD)
                + " / 娃娃总计 " + dollsIn(stall)
                + "（祥子 " + stall.count(OBLIVIONIS_DOLL)
                + " / 初华 " + stall.count(DOLORIS_DOLL)
                + " / 睦 " + stall.count(MORTIS_DOLL)
                + " / 海铃 " + stall.count(TIMORIS_DOLL)
                + " / 喵梦 " + stall.count(AMORIS_DOLL)
                + (stall.count(LEGACY_DOLL_SET) > 0 ? " / 旧套组 " + stall.count(LEGACY_DOLL_SET) : "") + "）";
    }
    public record Sale(int posters, int lights, int records, int legacySets,
                       int sakikoDolls, int uikaDolls, int mutsumiDolls, int umiriDolls, int nyamuDolls,
                       int emeralds) {
        public int sold(int type) { return switch (type) {
            case POSTER -> posters;
            case LIGHTSTICK -> lights;
            case RECORD -> records;
            case LEGACY_DOLL_SET -> legacySets;
            case OBLIVIONIS_DOLL -> sakikoDolls;
            case DOLORIS_DOLL -> uikaDolls;
            case MORTIS_DOLL -> mutsumiDolls;
            case TIMORIS_DOLL -> umiriDolls;
            case AMORIS_DOLL -> nyamuDolls;
            default -> 0;
        }; }
        public int dolls() {
            return legacySets + sakikoDolls + uikaDolls + mutsumiDolls + umiriDolls + nyamuDolls;
        }
        public int total() {
            return posters + lights + records + dolls();
        }
        public String summary() {
            return "海报 " + posters + " / 应援灯 " + lights + " / 唱片 " + records
                    + " / 娃娃总计 " + dolls()
                    + "（祥子 " + sakikoDolls + " / 初华 " + uikaDolls + " / 睦 " + mutsumiDolls
                    + " / 海铃 " + umiriDolls + " / 喵梦 " + nyamuDolls
                    + (legacySets > 0 ? " / 旧套组 " + legacySets : "") + "）"
                    + "，周边收入 +" + emeralds + " 绿宝石";
        }
    }
    /** A single one-off task saved with the owner; the world is modified only near online players. */
    public record DecorJob(String villageKey, int x, int y, int z, int type, long due, int attempts) { }

    /** Bounded consignment escrow; regular inventory is never scanned or consumed by sales. */
    public static final int MAX_STOCK = 16384;
    public static final int MAX_UNCLAIMED = 100000;

    /** A village tour can sell a substantial amount of pre-listed merchandise, even offline. */
    public static Sale settle(ServerLevel level, BlockPos stage, UUID owner, int audience,
                              int score, BandCareerData.Career career) {
        Sale sale = sellListed(career, MerchSalesPolicy.tourDemand(career.fans, audience, score), level.random);
        career.consignmentTourSold = cappedAdd(career.consignmentTourSold, sale.total());
        // Career is marked dirty by BandManagement when settling the full show.
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
        if (online != null && sale.total() > 0) MerchConsignment.send(online, false);
        return sale;
    }

    private static int cappedAdd(int a, int b) {
        return (int)Math.min(Integer.MAX_VALUE, (long)Math.max(0,a) + Math.max(0,b));
    }

    /** All sales spend only server-persisted consignment stock. Each successful unit credits escrow. */
    static Sale sellListed(BandCareerData.Career career, int demand, net.minecraft.util.RandomSource random) {
        int[] sold = new int[TYPES];
        int revenue = 0;
        int limit = Math.max(0, Math.min(72, demand));
        for (int n = 0; n < limit; n++) {
            int start = random.nextInt(TYPES);
            int chosen = -1;
            for (int j = 0; j < TYPES; j++) {
                int type = (start + j) % TYPES;
                // Old world slots remain readable but retired merchandise is never sold.
                if (type != LIGHTSTICK && type != LEGACY_DOLL_SET && career.consignmentStock[type] > 0
                        && career.consignmentEmeralds <= MAX_UNCLAIMED - MerchSalesPolicy.price(type)) {
                    chosen = type;
                    break;
                }
            }
            if (chosen < 0) break;
            career.consignmentStock[chosen]--;
            sold[chosen]++;
            int price = MerchSalesPolicy.price(chosen);
            career.consignmentEmeralds += price;
            revenue += price;
        }
        return new Sale(sold[POSTER], sold[LIGHTSTICK], sold[RECORD], sold[LEGACY_DOLL_SET],
                sold[OBLIVIONIS_DOLL], sold[DOLORIS_DOLL], sold[MORTIS_DOLL],
                sold[TIMORIS_DOLL], sold[AMORIS_DOLL], revenue);
    }

    /** Timed routine transactions take place only for online players with stocked goods. */
    private static boolean tickRoutine(MinecraftServer server, UUID owner, BandCareerData.Career career,
                                       long now) {
        boolean hasSellable = false;
        for (int i = 0; i < TYPES; i++) {
            if (i != LIGHTSTICK && i != LEGACY_DOLL_SET && career.consignmentStock[i] > 0) { hasSellable = true; break; }
        }
        if (!hasSellable) {
            if (career.nextConsignmentSaleTick != 0L) {
                career.nextConsignmentSaleTick = 0L;
                return true;
            }
            return false;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online == null) return false; // No offline acceleration or catch-up exploit.
        if (career.nextConsignmentSaleTick == 0L) {
            career.nextConsignmentSaleTick = now + 1200L + server.overworld().random.nextInt(2401);
            return true;
        }
        if (now < career.nextConsignmentSaleTick) return false;
        int demand = MerchSalesPolicy.routineDemand(career.fans, server.overworld().random.nextInt(3));
        Sale sold = sellListed(career, demand, server.overworld().random);
        career.nextConsignmentSaleTick = now + 1200L + server.overworld().random.nextInt(2401);
        if (sold.total() > 0) {
            career.consignmentDailySold = cappedAdd(career.consignmentDailySold, sold.total());
            online.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Mujica] 周边日常寄售：售出 " + sold.total() + " 件，获得 " + sold.emeralds()
                            + " 绿宝石。使用蓝色小章鱼领取。"), false);
            MerchConsignment.send(online, false);
        }
        return true;
    }

    /** Village souvenirs appear only after a successful stocked tour, over time, near residents. */
    public static void scheduleDecor(ServerLevel level, BandCareerData.Career career, String villageKey,
                                     BlockPos origin, Sale sale) {
        if (sale.total() == 0 || villageKey == null || villageKey.isBlank()) return;
        if (career.decorPlaced.getOrDefault(villageKey, 0) >= MAX_DECOR_PER_VILLAGE) return;
        if (career.decorJobs.stream().anyMatch(job -> job.villageKey().equals(villageKey))) return;
        if (career.decorJobs.size() >= 32) return;
        int chosen = 0;
        for (int i = 1; i < TYPES; i++) if (sale.sold(i) > sale.sold(chosen)) chosen = i;
        career.decorJobs.add(new DecorJob(villageKey, origin.getX(), origin.getY(), origin.getZ(),
                chosen, level.getGameTime() + 200 + level.random.nextInt(400), 0));
    }

    public static void tick(MinecraftServer server, BandCareerData data, long now) {
        if (now % 200L != 0) return;
        ServerLevel level = server.overworld();
        boolean changed = false;
        for (Map.Entry<UUID, BandCareerData.Career> entry : data.careers.entrySet())
            if (tickRoutine(server, entry.getKey(), entry.getValue(), now)) changed = true;
        for (Map.Entry<UUID, BandCareerData.Career> entry : data.careers.entrySet()) {
            var career = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.serverLevel() != level) continue;
            var postponed = new ArrayList<DecorJob>();
            var iter = career.decorJobs.iterator();
            while (iter.hasNext()) {
                DecorJob job = iter.next();
                if (job.type() == LIGHTSTICK || job.type() == LEGACY_DOLL_SET) {
                    iter.remove(); changed = true; continue;
                }
                if (now < job.due()) continue;
                BlockPos origin = new BlockPos(job.x(), job.y(), job.z());
                if (origin.distSqr(player.blockPosition()) > 48 * 48 || !level.hasChunkAt(origin)) continue;
                if (career.decorPlaced.getOrDefault(job.villageKey(), 0) >= MAX_DECOR_PER_VILLAGE) {
                    iter.remove(); changed = true; continue;
                }
                BlockPos placed = findVillageDisplaySpot(level, origin);
                if (placed != null && level.setBlock(placed, block(job.type()).defaultBlockState(), 3)) {
                    career.decorPlaced.merge(job.villageKey(), 1, Integer::sum);
                    while (career.decorPlaced.size() > 512)
                        career.decorPlaced.remove(career.decorPlaced.keySet().iterator().next());
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                            placed.getX() + .5D, placed.getY() + .8D, placed.getZ() + .5D,
                            7, .25D, .25D, .25D, .02D);
                    iter.remove(); changed = true;
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "[Mujica] " + job.villageKey() + " 的村民摆出了 " + name(job.type()) + "！"), false);
                } else if (job.attempts() >= 10) {
                    iter.remove(); changed = true;
                } else {
                    iter.remove();
                    postponed.add(new DecorJob(job.villageKey(), job.x(), job.y(), job.z(),
                            job.type(), now + 400 + level.random.nextInt(600), job.attempts() + 1));
                    changed = true;
                }
            }
            career.decorJobs.addAll(postponed);
        }
        if (changed) data.setDirty();
    }

    /** Never replace a block, touch roofs/farms/beds, or force-load a chunk. */
    private static BlockPos findVillageDisplaySpot(ServerLevel level, BlockPos origin) {
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                new AABB(origin).inflate(24D, 6D, 24D), Villager::isAlive);
        if (villagers.isEmpty()) return null;
        for (int tries = 0; tries < 36; tries++) {
            Villager resident = villagers.get(level.random.nextInt(villagers.size()));
            BlockPos near = resident.blockPosition().offset(level.random.nextInt(9) - 4,
                    level.random.nextInt(5) - 2, level.random.nextInt(9) - 4);
            if (near.distSqr(origin) < 7 * 7 || near.distSqr(origin) > 29 * 29 || !level.hasChunkAt(near)) continue;
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos spot = near.offset(0, dy, 0);
                if (!level.hasChunkAt(spot) || !level.hasChunkAt(spot.below())
                        || !level.hasChunkAt(spot.above()) || !level.isEmptyBlock(spot)
                        || !level.isEmptyBlock(spot.above())) continue;
                BlockState floor = level.getBlockState(spot.below());
                if (!(floor.is(Blocks.GRASS_BLOCK) || floor.is(Blocks.DIRT) || floor.is(Blocks.STONE)
                        || floor.is(Blocks.COBBLESTONE) || floor.is(Blocks.MOSS_BLOCK)
                        || floor.is(Blocks.OAK_PLANKS) || floor.is(Blocks.SPRUCE_PLANKS)
                        || floor.is(Blocks.DARK_OAK_PLANKS) || floor.is(Blocks.POLISHED_ANDESITE))) continue;
                if (!level.getEntitiesOfClass(Villager.class, new AABB(spot), Villager::isAlive).isEmpty()) continue;
                boolean crowded = false;
                for (BlockPos beside : BlockPos.betweenClosed(spot.offset(-1, 0, -1), spot.offset(1, 0, 1))) {
                    if (!level.hasChunkAt(beside)) { crowded = true; break; }
                    Block s = level.getBlockState(beside).getBlock();
                    if (s == ModRegistry.MERCH_POSTER || s == ModRegistry.MERCH_LIGHTSTICK || s == ModRegistry.MERCH_RECORD
                            || s == ModRegistry.MERCH_DOLL || s == ModRegistry.MERCH_DOLL_OBLIVIONIS
                            || s == ModRegistry.MERCH_DOLL_DOLORIS || s == ModRegistry.MERCH_DOLL_MORTIS
                            || s == ModRegistry.MERCH_DOLL_TIMORIS || s == ModRegistry.MERCH_DOLL_AMORIS
                            || s == Blocks.BELL || s == Blocks.FARMLAND || s == Blocks.CHEST) {
                        crowded = true; break;
                    }
                }
                if (!crowded) return spot;
            }
        }
        return null;
    }
}
