package com.miseru.avemujica.career;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.music.MidiBandPlayer;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.menu.BandManagementMenu;
import com.miseru.avemujica.item.SummonMemberItem;
import com.miseru.avemujica.team.PlayerBandRoster;
import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.voice.VoiceDirector;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.LinkedHashMap;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3f;

/** Server-authoritative career progression using physical Minecraft emeralds. */
public final class BandManagement {
    private BandManagement() {}
    private static final int SHOW_TIME = 20 * 30;
    private static final int TOUR_TIME = 20 * 45;
    // Physical emerald costs, intentionally affordable through village trades.
    public static final int FIRST_RECRUIT_COST = 8;
    public static final int REPLACEMENT_COST = 3;
    public static final int PRACTICE_COST = 2;
    public static final int SHOW_COST = 2;
    public static final int TOUR_COST = 4;
    public static final int PROMOTION_COST = 3;
    public static int upgradeCost(int level) { return 8 + 5 * level; }

    /** Count accessible real emerald items, never a phantom account balance. */
    public static int emeralds(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items)
            if (stack.is(Items.EMERALD)) total += stack.getCount();
        for (ItemStack stack : player.getInventory().offhand)
            if (stack.is(Items.EMERALD)) total += stack.getCount();
        return total;
    }

    /** Called only after the action's all other preconditions have passed. */
    public static boolean payEmeralds(ServerPlayer player, int cost) {
        if (player.getAbilities().instabuild) return cost >= 0;
        if (cost < 0 || emeralds(player) < cost) return false;
        int remaining = cost;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining == 0) break;
            if (!stack.is(Items.EMERALD)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (remaining == 0) break;
            if (!stack.is(Items.EMERALD)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return remaining == 0;
    }

    private static final int REHEARSAL_COOLDOWN = 20 * 60;
    private static final int PROMOTION_COOLDOWN = 20 * 45;
    private static final int REQUIRED_NOTEBLOCKS = 4;

    private static void say(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[Ave Mujica] " + message).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void action(ServerPlayer player, String label, String command) {
        player.sendSystemMessage(Component.literal("  » " + label)
                .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/avemujica manage " + command))));
    }

    /** The player's location is the mobile management origin (no desk required). */
    private static BlockPos managementOrigin(ServerPlayer player) {
        return player.blockPosition();
    }

    private static EnumMap<MemberRole, BandMemberEntity> team(ServerLevel level, BlockPos pos, UUID owner) {
        EnumMap<MemberRole, BandMemberEntity> out = new EnumMap<>(MemberRole.class);
        AABB box = new AABB(pos).inflate(20D, 8D, 20D);
        for (BandMemberEntity npc : level.getEntitiesOfClass(BandMemberEntity.class, box,
                npc -> npc.isAlive() && owner.equals(npc.manager()))) {
            out.putIfAbsent(npc.role(), npc);
        }
        return out;
    }

    private static int noteBlocks(ServerLevel level, BlockPos stage) {
        int found = 0;
        for (BlockPos p : BlockPos.betweenClosed(stage.offset(-6, -2, -6), stage.offset(6, 2, 6))) {
            if (level.getBlockState(p).is(Blocks.NOTE_BLOCK)) found++;
        }
        return found;
    }

    private static BlockPos nearestTourStage(ServerLevel level, BlockPos origin) {
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-18, -4, -18), origin.offset(18, 4, 18))) {
            if (ModRegistry.isTourStage(level.getBlockState(p))) {
                double d = p.distSqr(origin);
                if (d < best) { best = d; nearest = p.immutable(); }
            }
        }
        return nearest;
    }

    private static boolean hasLargeStage(ServerLevel level, BlockPos origin) {
        return nearestTourStage(level, origin) != null;
    }

    private static BlockPos performanceCenter(ServerLevel level, BlockPos origin) {
        BlockPos stage = nearestTourStage(level, origin);
        return stage != null ? stage : origin;
    }

    /** A distant stage must not cancel a just-started show because the band is still at the owner. */
    private static BlockPos usablePerformanceCenter(ServerLevel level, BlockPos origin, UUID owner) {
        BlockPos stage = nearestTourStage(level, origin);
        // The band may still be next to the owner: guide them to the real stage
        // instead of rejecting it merely because they have not walked there yet.
        if (stage != null) return stage;
        return origin;
    }

    /** Locate the raised stage without requiring the musicians to already stand on it. */
    public static Vec3 preferredFormationCenter(ServerLevel level, BlockPos origin, Vec3 fallback) {
        BlockPos stage = nearestTourStage(level, origin);
        return stage == null ? fallback : Vec3.atBottomCenterOf(stage).add(0D, 1D, 0D);
    }

    private static void stageThemeLights(ServerLevel level, BlockPos stage) {
        // Use the same role marks as the NPC AI, including elevated tiers and
        // the actual placement direction. Never maintain a second copy of the
        // five stage coordinates in the lighting code.
        com.miseru.avemujica.entity.MemberRole[] roles = {
                MemberRole.OBLIVIONIS, MemberRole.AMORIS, MemberRole.MORTIS,
                MemberRole.DOLORIS, MemberRole.TIMORIS
        };
        var state = level.getBlockState(stage);
        boolean hasStage = ModRegistry.isTourStage(state);
        boolean levelTwo = state.is(ModRegistry.TOUR_STAGE_LEVEL2);
        // Level two has client-side translucent coloured stage beams. Its
        // lighting must not also emit particle clouds around the performers.
        if (levelTwo) return;
        net.minecraft.core.Direction front = hasStage
                ? state.getValue(com.miseru.avemujica.block.TourStageBlock.FACING)
                : net.minecraft.core.Direction.SOUTH;
        for (MemberRole role : roles) {
            com.miseru.avemujica.stage.StageFormation.Mark mark =
                    com.miseru.avemujica.stage.StageFormation.rotated(role, levelTwo, front);
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(
                    role.colorR(), role.colorG(), role.colorB()), 1.7F);
            double x = stage.getX() + 0.5D + mark.x();
            double z = stage.getZ() + 0.5D + mark.z();
            double y = stage.getY() + (hasStage ? 4.6D : 3.1D) + mark.y();
            level.sendParticles(dust, x, y, z, 8, 0.22D, 0.95D, 0.22D, 0.0D);
            level.sendParticles(dust, x, y + 0.9D, z, 4, 0.10D, 0.15D, 0.10D, 0.0D);
        }
    }

    /** Any player with a nearby owned bandmate can use the team MIDI picker. */
    public static boolean canUseMidi(ServerPlayer player) {
        return !com.miseru.avemujica.entity.TeamOrders.nearbyOwned(player).isEmpty();
    }

    /** A stage cannot be folded during a paid show or an active MIDI performance. */
    public static boolean isAnyShowAt(ServerLevel level, BlockPos stagePos) {
        if (level == level.getServer().overworld()) {
            for (BandCareerData.Career career : BandCareerData.get(level).careers.values()) {
                if (career.performing() && new BlockPos(career.stageX, career.stageY, career.stageZ).distSqr(stagePos) < 4D)
                    return true;
            }
        }
        return !level.getEntitiesOfClass(BandMemberEntity.class,
                new net.minecraft.world.phys.AABB(stagePos).inflate(15D, 8D, 15D),
                npc -> npc.isAlive() && npc.isPerforming()).isEmpty();
    }

    public static boolean isPerforming(ServerLevel level, UUID owner, BlockPos entityPos) {
        if (owner == null || level != level.getServer().overworld()) return false;
        BandCareerData.Career c = BandCareerData.get(level).careers.get(owner);
        if (c == null || !c.performing()) return false;
        BlockPos stage = new BlockPos(c.stageX, c.stageY, c.stageZ);
        // Members can start 13 blocks from the player and the stage can be 18
        // blocks farther away. They still need to approach while the show runs.
        return stage.distSqr(entityPos) <= 48 * 48;
    }

    /** Reconstruct a paid-show formation after a member's chunk unloads/reloads. */
    public static Vec3 activeShowFormation(ServerLevel level, UUID owner, Vec3 memberPosition) {
        if (owner == null || level != level.getServer().overworld()) return null;
        BandCareerData.Career c = BandCareerData.get(level).careers.get(owner);
        if (c == null || !c.performing()) return null;
        BlockPos stage = new BlockPos(c.stageX, c.stageY, c.stageZ);
        if (Vec3.atCenterOf(stage).distanceToSqr(memberPosition) > 48D * 48D) return null;
        double elevation = ModRegistry.isTourStage(level.getBlockState(stage)) ? 1D : 0D;
        return Vec3.atBottomCenterOf(stage).add(0D, elevation, 0D);
    }

    /** Cancel a paid performance when Sakiko issues a conflicting order. No refund. */
    public static boolean cancelActiveShow(ServerPlayer player) {
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.careers.get(player.getUUID());
        if (career == null || !career.performing()) return false;
        career.endShowAt = 0L;
        VillageAudience.stop(player.getUUID());
        career.showType = 0;
        career.showVillageKey = "";
        career.firstVillageTour = false;
        career.showTicksLeft = -1; career.lastLoadedShowTick = 0L;
        data.setDirty();
        return true;
    }

    public static void dashboard(ServerPlayer player) {
        BandCareerData.Career c = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        say(player, "—— 乐队事务所 / 经营面板 ——");
        say(player, "背包绿宝石 " + emeralds(player) + " | 粉丝 " + c.fans + " | 名气 " + c.fame + " | 公演 " + c.shows);
        say(player, "乐队 Lv." + c.bandLevel() + " | 经验 " + c.bandXp + "/" + (c.nextLevelXp() == 0 ? "MAX" : c.nextLevelXp())
                + " | 已到访村庄 " + c.tourHistory.size() + " | 巡演 " + c.villageShows);
        say(player, "技巧 " + c.skill + "/100  默契 " + c.bond + "/100  士气 " + c.morale + "/100");
        say(player, "音响 Lv." + c.soundLevel + " | 灯光 Lv." + c.lightLevel + (c.performing() ? " | 正在演出" : ""));
        BlockPos stage = managementOrigin(player);
        if (player.serverLevel() == player.getServer().overworld()) {
            int members = team(player.serverLevel(), stage, player.getUUID()).size();
            say(player, "附近队员 " + members + "/5 · " + (hasLargeStage(player.serverLevel(), stage)
                    ? "可使用大型舞台" : "可就地演奏（大型舞台可选）"));
        }
        action(player, "签约 / 补招成员", "recruit");
        say(player, "待领取物资：" + c.pendingCount() + " 件");
        action(player, "普通排练（免费）", "rehearse");
        action(player, "强化排练（2绿宝石，成员成长）", "rehearse_enhanced");
        action(player, "举办30秒普通公演（2绿宝石）", "show");
        action(player, "村庄巡演45秒（4绿宝石）", "tour");
        action(player, "查看待领取物资明细", "rewards");
        action(player, "领取巡演物资", "claim");
        action(player, "宣传推广（3绿宝石）", "promote");
        action(player, "升级音响", "sound");
        action(player, "升级灯光", "lights");
    }

    /** Open the mobile management GUI; no furniture is required. */
    public static void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new BandManagementMenu(id, inventory), Component.translatable("gui.avemujica.title")));
        VoiceDirector.managementOpened(player);
    }

    /** Build the small server-owned state snapshot consumed by the menu's DataSlots. */
    public static int[] menuSnapshot(ServerPlayer player) {
        int[] result = new int[BandManagementMenu.DATA_COUNT];
        ServerLevel overworld = player.getServer().overworld();
        BandCareerData.Career c = BandCareerData.get(overworld).career(player.getUUID());
        result[BandManagementMenu.EMERALDS_LO] = emeralds(player) & 65535;
        result[BandManagementMenu.EMERALDS_HI] = (emeralds(player) >>> 16) & 65535;
        result[BandManagementMenu.FANS_LO] = c.fans & 65535;
        result[BandManagementMenu.FANS_HI] = (c.fans >>> 16) & 65535;
        result[BandManagementMenu.FAME] = c.fame;
        result[BandManagementMenu.SHOWS] = c.shows;
        result[BandManagementMenu.BAND_LEVEL] = c.bandLevel();
        result[BandManagementMenu.BAND_XP_LO] = c.bandXp & 65535;
        result[BandManagementMenu.BAND_XP_HI] = (c.bandXp >>> 16) & 65535;
        result[BandManagementMenu.NEXT_XP] = c.nextLevelXp();
        result[BandManagementMenu.VISITED] = c.tourHistory.size();
        result[BandManagementMenu.TOUR_SHOWS] = c.villageShows;
        result[BandManagementMenu.PENDING_LOOT] = Math.min(30000, c.pendingCount());
        result[BandManagementMenu.SKILL] = c.skill;
        result[BandManagementMenu.BOND] = c.bond;
        result[BandManagementMenu.MORALE] = c.morale;
        result[BandManagementMenu.SOUND] = c.soundLevel;
        result[BandManagementMenu.LIGHTS] = c.lightLevel;
        result[BandManagementMenu.RECRUITED] = c.recruited ? 1 : 0;
        result[BandManagementMenu.PERFORMING] = c.performing() ? 1 : 0;
        if (player.serverLevel() != overworld) return result;
        result[BandManagementMenu.IN_OVERWORLD] = 1;
        BlockPos origin = managementOrigin(player);
        result[BandManagementMenu.CONTEXT_READY] = 1; // Legacy DataSlot 16: available without a desk.
        EnumMap<MemberRole, BandMemberEntity> nearby = team(overworld, origin, player.getUUID());
        result[BandManagementMenu.MEMBERS] = nearby.size();
        result[BandManagementMenu.AVG_TRAINING] = nearby.isEmpty() ? 0
                : nearby.values().stream().mapToInt(BandMemberEntity::trainingRank).sum() / nearby.size();
        BlockPos stage = performanceCenter(overworld, origin);
        VillageTour.Survey survey = VillageTour.bestSurvey(overworld, stage, origin);
        result[BandManagementMenu.VILLAGE_HINT] = survey.qualified() ? 1 : 0;
        result[BandManagementMenu.VILLAGE_POPULATION] = survey.villagers();
        result[BandManagementMenu.VILLAGE_BEDS] = survey.beds();
        result[BandManagementMenu.VILLAGE_BELL] = survey.hasBell() ? 1 : 0;
        result[BandManagementMenu.NOTE_BLOCKS] = hasLargeStage(overworld, origin) ? 99 : noteBlocks(overworld, origin);
        long now = overworld.getGameTime();
        result[BandManagementMenu.PRACTICE_WAIT] = (int)Math.max(0L, (REHEARSAL_COOLDOWN - (now - c.lastPractice) + 19L) / 20L);
        result[BandManagementMenu.PROMOTE_WAIT] = (int)Math.max(0L, (PROMOTION_COOLDOWN - (now - c.lastPromote) + 19L) / 20L);
        result[BandManagementMenu.SHOW_REMAINING] = c.performing()
                ? (c.showTicksLeft >= 0 ? (c.showTicksLeft + 19) / 20
                        : (int)Math.max(0L, (c.endShowAt - now + 19L) / 20L)) : 0;
        return result;
    }

    /** Economy actions are tied to the owner, not to a placeable management block. */
    public static int command(ServerPlayer player, String command) {
        return command(player, command, true);
    }

    /** showDashboard=false suppresses the old chat-only dashboard after a GUI action. */
    public static int command(ServerPlayer player, String command, boolean showDashboard) {
        if (command.equals("status")) { dashboard(player); return 1; }
        if (command.equals("rewards")) { rewardList(player); return 1; }
        // Claiming is a personal inventory action: no dimension, stage or nearby origin is required.
        if (command.equals("claim")) return claim(player);
        if (player.serverLevel() != player.getServer().overworld()) {
            say(player, "第一版演出业务限定在主世界。"); return 0;
        }
        BlockPos origin = managementOrigin(player); // legacy variable name: current player location
        ServerLevel level = player.serverLevel();
        BandCareerData data = BandCareerData.get(level);
        BandCareerData.Career c = data.career(player.getUUID());
        long now = level.getGameTime();
        EnumMap<MemberRole, BandMemberEntity> members = team(level, origin, player.getUUID());

        switch (command) {
            case "recruit" -> {
                int missing = 5 - members.size();
                if (missing == 0) { say(player, "全员已经到齐。"); return 0; }
                int unsupportedLegacy = 0;
                for (MemberRole role : MemberRole.values())
                    if (c.summonEnabled(role.id) && !members.containsKey(role) && !PlayerBandRoster.hasRecoverableProfile(level, player.getUUID(), role))
                        unsupportedLegacy++;
                if (unsupportedLegacy > 0) {
                    say(player, "有 " + unsupportedLegacy + " 名旧版成员尚未备份；请先加载原区块，不能直接覆盖旧角色数据。");
                    return 0;
                }
                int coolingDown = 0;
                for (MemberRole role : MemberRole.values()) {
                    if (!c.summonEnabled(role.id) || members.containsKey(role)) continue;
                    int wait = PlayerBandRoster.revivalRemainingSeconds(level, player.getUUID(), role);
                    if (wait > 0) {
                        coolingDown++;
                        say(player, role.display + "的复活冷却还剩 " + wait + " 秒。");
                    }
                }
                int newRoles = 0;
                for (MemberRole role : MemberRole.values())
                    if (c.summonEnabled(role.id) && !members.containsKey(role) && !c.recruited && !PlayerBandRoster.hasIdentity(level, player.getUUID(), role))
                        newRoles++;
                int cost = newRoles == 0 ? 0 : (c.recruited ? newRoles * REPLACEMENT_COST : FIRST_RECRUIT_COST);
                if (!payEmeralds(player, cost)) { say(player, "招募需要 " + cost + " 颗绿宝石，背包数量不足。"); return 0; }
                c.recruited = true;
                int i = 0;
                for (MemberRole role : MemberRole.values()) {
                    if (!c.summonEnabled(role.id) || members.containsKey(role)
                            || PlayerBandRoster.revivalRemainingTicks(level, player.getUUID(), role) > 0L) continue;
                    Vec3 place = Vec3.atBottomCenterOf(origin).add((i++ % 3 - 1) * 2.0, 1.0, 2.0 + (i / 3) * 2);
                    BandMemberEntity npc = PlayerBandRoster.summonOrRecall(level, place, role, player.getUUID());
                    if (npc != null) {
                        VoiceDirector.feature(npc, VoiceDirector.Feature.SUMMON);
                    }
                }
                say(player, "已发起成员召集：新招募支付 " + cost + " 颗绿宝石。"
                        + (coolingDown > 0 ? " " + coolingDown + " 名队员仍在复活冷却中。" : ""));
            }
            case "rehearse", "rehearse_enhanced" -> {
                if (!ready(player, level, origin, members)) return 0;
                if (c.performing()) { say(player, "售票公演尚未结束，不能同时排练。"); return 0; }
                boolean enhanced = command.equals("rehearse_enhanced");
                if (enhanced && now - c.lastPractice < REHEARSAL_COOLDOWN) {
                    say(player, "强化排练冷却还剩 " + (REHEARSAL_COOLDOWN - (now - c.lastPractice)) / 20 + " 秒。"); return 0;
                }
                if (enhanced && !payEmeralds(player, PRACTICE_COST)) {
                    say(player, "强化排练需要 2 颗绿宝石，普通排练无需绿宝石。"); return 0;
                }
                BlockPos practiceLocation = usablePerformanceCenter(level, origin, player.getUUID());
                Vec3 practiceCenter = Vec3.atBottomCenterOf(practiceLocation);
                if (ModRegistry.isTourStage(level.getBlockState(practiceLocation)))
                    practiceCenter = practiceCenter.add(0D, 1D, 0D);
                BandMemberEntity leader = members.get(MemberRole.OBLIVIONIS);
                if (leader == null) { say(player, "需要祥子在场才能指挥排练。"); return 0; }
                // Always begin the rehearsal behavior. A paid enhancement augments it;
                // ordinary rehearsal is free and must never change permanent combat attributes.
                MidiBandPlayer.stopForOwner(player.getUUID());
                MidiBandPlayer.allowAutomaticForOwner(player.getUUID());
                for (BandMemberEntity musician : members.values())
                    musician.applyTeamOrder(com.miseru.avemujica.entity.TeamMode.REHEARSAL,
                            leader.getUUID(), practiceCenter);
                MidiBandPlayer.ensureDefaultForTeam(leader);
                VoiceDirector.economyAction(level, members, VoiceDirector.Feature.REHEARSAL);
                if (enhanced) {
                    int levelBefore = c.bandLevel();
                    c.skill += 6; c.bond += 14; c.morale += 14; c.lastPractice = now;
                    c.grantXp(14);
                    EnumMap<MemberRole, Integer> ranksBefore = new EnumMap<>(MemberRole.class);
                    for (BandMemberEntity musician : members.values()) {
                        ranksBefore.put(musician.role(), musician.trainingRank());
                        musician.trainFromRehearsal();
                    }
                    VoiceDirector.rehearsal(level, player.getUUID(), members, ranksBefore);
                    VoiceDirector.bandLevelUp(level, player.getUUID(), members, levelBefore, c.bandLevel());
                    say(player, "强化排练已启动（2绿宝石）：技巧+6、默契+14、士气+14、经验+14，五人训练等级+1（最高15级）。");
                } else {
                    say(player, "普通排练已启动：免费演奏已选全队曲目，不增加永久生命值或攻击力。勾选强化排练才花费绿宝石。");
                }
                c.clamp();
                BlockPos effectCenter = practiceLocation;
                level.sendParticles(ParticleTypes.NOTE, effectCenter.getX()+.5, effectCenter.getY()+1.5, effectCenter.getZ()+.5,
                        enhanced ? 45 : 14, 3, 1, 3, .02);
                level.playSound(null, effectCenter, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.RECORDS,
                        enhanced ? 1.5F : 0.6F, enhanced ? 1.0F : .85F);
            }
            case "show" -> {
                if (!ready(player, level, origin, members)) return 0;
                if (c.performing()) { say(player, "演出尚未结束。"); return 0; }
                if (c.bond < 20) { say(player, "需要默契≥20，请先排练。"); return 0; }
                if (c.morale < 15) { say(player, "士气过低，请先排练。"); return 0; }
                if (emeralds(player) < SHOW_COST) { say(player, "普通公演需要2颗绿宝石。"); return 0; }
                // Prefer a nearby built stage; otherwise the player's current position.
                BlockPos stageCenterPos = usablePerformanceCenter(level, origin, player.getUUID());
                MidiBandPlayer.allowAutomaticForOwner(player.getUUID());
                if (!payEmeralds(player, SHOW_COST)) return 0;
                c.endShowAt = now + SHOW_TIME;
                c.showTicksLeft = SHOW_TIME; c.lastLoadedShowTick = now;
                c.showType = 0; c.showVillageKey = ""; c.firstVillageTour = false;
                c.stageX = stageCenterPos.getX(); c.stageY = stageCenterPos.getY(); c.stageZ = stageCenterPos.getZ();
                c.deskX = origin.getX(); c.deskY = origin.getY(); c.deskZ = origin.getZ();
                // A raised stage adds one block; otherwise perform at player position.
                Vec3 center = Vec3.atBottomCenterOf(stageCenterPos)
                        .add(0.0D, ModRegistry.isTourStage(level.getBlockState(stageCenterPos)) ? 1.0D : 0.0D, 0.0D);
                for (BandMemberEntity musician : members.values()) musician.assignStagePosition(center);
                level.playSound(null, stageCenterPos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.RECORDS, 1.8F, .7F);
                VoiceDirector.economyAction(level, members, VoiceDirector.Feature.SHOW_START);
                say(player, hasLargeStage(level, origin) ? "大型舞台公演开始！30秒后结算票房。" : "公演开始！请保持舞台及五人完整，30秒后结算票房。");
            }
            case "tour" -> {
                if (!ready(player, level, origin, members)) return 0;
                if (c.performing()) { say(player, "已有一场演出正在进行。"); return 0; }
                if (c.bond < 20 || c.morale < 15) { say(player, "巡演需要默契≥20且士气≥15，请先排练。"); return 0; }
                if (!player.getAbilities().instabuild && emeralds(player) < TOUR_COST) {
                    say(player, "村庄巡演需要4颗绿宝石（创造模式免支付）。"); return 0;
                }
                BlockPos preferredStage = usablePerformanceCenter(level, origin, player.getUUID());
                VillageTour.Survey survey = VillageTour.bestSurvey(level, preferredStage, origin);
                VillageTour.Venue venue = survey.venue();
                if (venue == null) {
                    say(player, "村庄检测未通过：找到村民 " + survey.villagers() + " 位、床 "
                            + survey.beds() + " 张、钟 " + (survey.hasBell() ? "有" : "无")
                            + "。需要至少 1 位村民+1 个钟，或至少 2 位村民+2 张床。"
                            + "检查玩家及舞台周围 29~32 格范围，且必须在主世界。");
                    return 0;
                }
                // Use the location that was actually validated; otherwise a stage
                // several blocks away could fail the audience check mid-performance.
                BlockPos stageCenterPos = survey.center();
                Long previous = c.tourHistory.get(venue.key());
                if (previous != null && now - previous < VillageTour.REPEAT_COOLDOWN) {
                    long seconds = (VillageTour.REPEAT_COOLDOWN - (now - previous) + 19L) / 20L;
                    say(player, "这个村庄才演出过，需再等待 " + seconds + " 秒。换个村庄可立即继续巡演。");
                    return 0;
                }
                if (!payEmeralds(player, TOUR_COST)) return 0;
                MidiBandPlayer.allowAutomaticForOwner(player.getUUID());
                c.endShowAt = now + TOUR_TIME;
                c.showTicksLeft = TOUR_TIME; c.lastLoadedShowTick = now;
                c.showType = 1; c.showVillageKey = venue.key();
                c.firstVillageTour = previous == null;
                c.stageX = stageCenterPos.getX(); c.stageY = stageCenterPos.getY(); c.stageZ = stageCenterPos.getZ();
                c.deskX = origin.getX(); c.deskY = origin.getY(); c.deskZ = origin.getZ();
                // A raised stage adds one block; otherwise perform at player position.
                Vec3 center = Vec3.atBottomCenterOf(stageCenterPos)
                        .add(0.0D, ModRegistry.isTourStage(level.getBlockState(stageCenterPos)) ? 1.0D : 0.0D, 0.0D);
                for (BandMemberEntity musician : members.values()) musician.assignStagePosition(center);
                level.playSound(null, stageCenterPos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.RECORDS, 1.8F, 1.05F);
                VoiceDirector.economyAction(level, members, VoiceDirector.Feature.TOUR_START);
                VillageAudience.start(level, player.getUUID(), stageCenterPos);
                say(player, (ModRegistry.isTourStage(level.getBlockState(stageCenterPos))
                        ? "舞台村庄巡演开始！" : "村庄巡演开始！") + "观众 " + venue.population() + " 位村民；45秒后结算。"
                        + (c.firstVillageTour ? "首次到访：额外经验及战利品抽取。" : ""));
            }
            case "promote" -> {
                if (c.performing()) { say(player, "公演期间无法安排宣传。"); return 0; }
                if (now - c.lastPromote < PROMOTION_COOLDOWN) {
                    say(player, "宣传冷却还剩 " + (PROMOTION_COOLDOWN - (now - c.lastPromote)) / 20 + " 秒。"); return 0;
                }
                if (!payEmeralds(player, PROMOTION_COST)) { say(player, "宣传需要3颗绿宝石。"); return 0; }
                c.fans += 8 + c.fame * 2; c.lastPromote = now;
                VoiceDirector.economyAction(level, members, VoiceDirector.Feature.PROMOTION);
                say(player, "宣传成功！增加 " + (8 + c.fame * 2) + " 位粉丝。");
            }
            case "sound", "lights" -> {
                boolean sound = command.equals("sound");
                int lv = sound ? c.soundLevel : c.lightLevel;
                if (lv >= 5) { say(player, "已经达到最高等级5。"); return 0; }
                int cost = upgradeCost(lv);
                if (!payEmeralds(player, cost)) { say(player, "设备升级需要 " + cost + " 颗绿宝石。"); return 0; }
                if (sound) c.soundLevel++; else c.lightLevel++;
                VoiceDirector.economyAction(level, members, sound
                        ? VoiceDirector.Feature.SOUND_UPGRADE : VoiceDirector.Feature.LIGHT_UPGRADE);
                say(player, (sound ? "音响" : "灯光") + "已升级至 Lv." + (lv+1) + "。" + (sound ? "提高音符攻击与票价。" : "提高演出上座率。"));
            }
            default -> { say(player, "未知经营操作：" + command); return 0; }
        }
        c.clamp(); data.setDirty(); if (showDashboard) dashboard(player); return 1;
    }

    /** Localized inventory details rendered by each player's Minecraft client. */
    private static void rewardList(ServerPlayer player) {
        BandCareerData.Career c = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        say(player, "—— 村庄巡演 · 待领取物资 ——");
        if (c.pendingRewards.isEmpty()) { say(player, "暂无待领取物资。"); return; }
        for (Map.Entry<String, Integer> entry : c.pendingRewards.entrySet()) {
            ResourceLocation key = ResourceLocation.tryParse(entry.getKey());
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;
            Item item = BuiltInRegistries.ITEM.get(key);
            player.sendSystemMessage(Component.literal("  · ").append(item.getDescription())
                    .append(Component.literal(" × " + entry.getValue())));
        }
    }

    /** Insert actual reward items without deleting anything that does not fit.
     * Each saved quantity is reduced by precisely the quantity accepted by the
     * inventory; full inventory and unknown item IDs never erase rewards.
     */
    private static int claim(ServerPlayer player) {
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.career(player.getUUID());
        if (career.pendingRewards.isEmpty()) { say(player, "暂无待领取巡演物资。"); return 0; }
        int received = 0;
        for (Map.Entry<String, Integer> reward : new LinkedHashMap<>(career.pendingRewards).entrySet()) {
            ResourceLocation key = ResourceLocation.tryParse(reward.getKey());
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR) continue;
            int outstanding = Math.max(0, reward.getValue());
            int delivered = InventoryPayout.insert(player, item, outstanding);
            if (delivered <= 0) continue;
            received += delivered;
            if (delivered == outstanding) career.pendingRewards.remove(reward.getKey());
            else career.pendingRewards.put(reward.getKey(), outstanding - delivered);
        }
        if (received > 0) {
            InventoryPayout.sync(player);
            data.setDirty();
        }
        int pending = career.pendingCount();
        say(player, "本次实际领取 " + received + " 件巡演物资；剩余待领取 " + pending + " 件。"
                + (pending > 0 ? " 背包空间不足或存在暂时无法识别的物品时，请整理背包后重试。" : ""));
        if (received > 0) {
            EnumMap<MemberRole, BandMemberEntity> present = new EnumMap<>(MemberRole.class);
            for (BandMemberEntity member : player.serverLevel().getEntitiesOfClass(BandMemberEntity.class,
                    player.getBoundingBox().inflate(12D), npc -> npc.isAlive()
                        && player.getUUID().equals(npc.manager()))) present.putIfAbsent(member.role(), member);
            VoiceDirector.economyAction(player.serverLevel(), present, VoiceDirector.Feature.CLAIM_REWARD);
        }
        return received > 0 ? 1 : 0;
    }

    private static boolean ready(ServerPlayer p, ServerLevel level, BlockPos origin,
                                 EnumMap<MemberRole, BandMemberEntity> members) {
        if (members.size() < MemberRole.values().length) {
            say(p, "需要五名成员在你附近约13格内。可以用蓝色小章鱼召集。");
            return false;
        }
        return true;
    }

    /** Advance performances on server ticks, including when the owner logs off. */
    public static void serverTick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (now % 10L != 0L) return;
        BandCareerData data = BandCareerData.get(level);
        Merchandising.tick(server,data,now);
        for (Map.Entry<UUID, BandCareerData.Career> entry : data.careers.entrySet()) {
            BandCareerData.Career c = entry.getValue();
            if (!c.performing()) continue;
            BlockPos stage = new BlockPos(c.stageX, c.stageY, c.stageZ);
            if (!level.hasChunkAt(stage)) {
                VillageAudience.stop(entry.getKey());
                continue; // no off-screen performance exploit, nor lingering crowd AI
            }
            // Musicians may start beside the booking player while pathfinding to
            // a nearby raised stage. Allow a short arrival window rather than
            // cancelling the paid tour on the first 10-tick validity check.
            boolean bandPresent = team(level, stage, entry.getKey()).size() == 5;
            int totalShowTicks = c.showType == 1 ? TOUR_TIME : SHOW_TIME;
            boolean arriving = c.showTicksLeft >= totalShowTicks - 20 * 12;
            if (!bandPresent && arriving) {
                BlockPos bookingOrigin = new BlockPos(c.deskX, c.deskY, c.deskZ);
                bandPresent = team(level, bookingOrigin, entry.getKey()).size() == 5;
            }
            boolean valid = bandPresent
                    && (c.showType != 1 || VillageTour.nearbyAudience(level, stage));
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (!valid) {
                VillageAudience.stop(entry.getKey());
                c.endShowAt = 0;
                c.showType = 0; c.showVillageKey = ""; c.firstVillageTour = false;
                c.showTicksLeft = -1; c.lastLoadedShowTick = 0L;
                if (player != null) say(player, "附近五名成员不齐或村庄观众离场，本场公演取消（筹备费不退）。");
                data.setDirty(); continue;
            }
            MidiBandPlayer.ensureDefaultForShow(level, entry.getKey(), Vec3.atBottomCenterOf(stage),
                    team(level, stage, entry.getKey()));
            if (c.showType == 1) VillageAudience.tick(level, entry.getKey(), stage, now);
            // Only count server ticks while this stage is actually loaded and valid.
            if (c.showTicksLeft >= 0) {
                int elapsed = (int)Math.max(0L, Math.min(10L, now - c.lastLoadedShowTick));
                c.showTicksLeft = Math.max(0, c.showTicksLeft - elapsed);
                c.lastLoadedShowTick = now;
                c.endShowAt = now + c.showTicksLeft;
                data.setDirty();
            }
            if (now >= c.endShowAt) {
                boolean touring = c.showType == 1;
                boolean firstVillage = c.firstVillageTour;
                String villageKey = c.showVillageKey;
                c.endShowAt = 0;
                c.showType = 0; c.showVillageKey = ""; c.firstVillageTour = false;
                c.showTicksLeft = -1; c.lastLoadedShowTick = 0L;
                int nearby = level.getEntitiesOfClass(Villager.class, new AABB(stage).inflate(14), Villager::isAlive).size();
                int cheering = touring ? VillageAudience.arrived(level, entry.getKey()) : 0;
                VillageAudience.stop(entry.getKey());
                int audience = Math.min(200, 12 + c.fans / 6 + c.lightLevel * 14 + nearby * 3 + cheering * 3);
                int score = Math.min(100, 25 + c.skill / 3 + c.bond / 3 + c.morale / 5
                        + c.soundLevel * 4 + c.bandLevel() * 2);
                // Paid real emeralds go into escrow, claimable even if owner is offline.
                // A successful tour always returns more than its 4-emerald fee.
                int earnings = touring
                        ? Math.min(25, 8 + score / 15 + c.bandLevel() / 2
                                + Math.min(4, nearby) + Math.min(3, cheering / 4) + c.soundLevel + (firstVillage ? 3 : 0))
                        : Math.min(8, 3 + score / 35 + c.soundLevel / 2);
                int newFans = Math.max(4, (audience * score) / 170);
                if (touring) newFans = (int)(newFans * 1.35F);
                c.addLoot("minecraft:emerald", earnings);
                c.fans += newFans;
                c.fame += Math.max(1, score / 28); c.shows++;
                int beforeLevel = c.bandLevel();
                int gainedXp = touring ? (35 + score / 2 + (firstVillage ? 35 : 0)) : (16 + score / 7);
                c.grantXp(gainedXp);
                Map<String, Integer> rewardLoot = java.util.Collections.emptyMap();
                if (touring) {
                    c.villageShows++;
                    if (!villageKey.isEmpty()) {
                        c.tourHistory.put(villageKey, now);
                        // Protect world saves against unbounded map growth.
                        if (c.tourHistory.size() > 512) c.tourHistory.remove(c.tourHistory.keySet().iterator().next());
                    }
                    rewardLoot = VillageTour.generateRewards(level, c.bandLevel(), score, firstVillage);
                    for (Map.Entry<String, Integer> reward : rewardLoot.entrySet())
                        c.addLoot(reward.getKey(), reward.getValue());
                }
                // Only goods explicitly listed via the blue octopus can be sold; never scan bags.
                Merchandising.Sale merchSale = touring
                        ? Merchandising.settle(level, stage, entry.getKey(), audience, score, c)
                        : new Merchandising.Sale(0,0,0,0,0,0,0,0,0,0);
                if (touring) Merchandising.scheduleDecor(level,c,villageKey,stage,merchSale);
                // Successful village performances publish exactly one saved post.
                // Cancelled and ordinary shows never reach this path.
                TourPost published = touring ? TourAccount.publish(level, entry.getKey(), c,
                        stage, villageKey, score, audience, cheering, earnings, newFans, merchSale) : null;
                // Voice lines depend on the final *real* show score, not on a random timer.
                EnumMap<MemberRole, BandMemberEntity> resultMembers = team(level, stage, entry.getKey());
                VoiceDirector.performanceResult(level, entry.getKey(), resultMembers, score, c.shows);
                VoiceDirector.bandLevelUp(level, entry.getKey(), resultMembers, beforeLevel, c.bandLevel());
                c.morale -= touring ? 10 : 7; c.bond -= 7; c.clamp(); data.setDirty();
                level.sendParticles(ParticleTypes.FIREWORK, stage.getX()+.5, stage.getY()+2, stage.getZ()+.5,
                        70, 4, 2, 4, .2);
                level.playSound(null, stage, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.RECORDS, 1.6F, 1F);
                if (player != null) {
                    say(player, (touring ? "村庄巡演" : "普通公演") + "结束！评价 " + score + "分 · 观众 " + audience +
                            "人" + (touring ? "（实到打call村民 " + cheering + " 位）" : "") + " · 收入 +" + earnings + " 绿宝石（待领取） · 粉丝 +" + newFans + " · 乐队经验 +" + gainedXp);
                    if (touring) say(player, merchSale.total() > 0
                            ? "本场寄售周边：" + merchSale.summary() + "（请在蓝色小章鱼领取销售收入）；村民可能稍后摆出购买的周边。"
                            : "本场周边售出 0 件。请在蓝色小章鱼的【周边贩售】栏主动上架周边，巡演结束时集中出售。" );
                    if (c.bandLevel() > beforeLevel) say(player, "乐队升级至 Lv." + c.bandLevel() + "！村庄物资奖励品质已提高。");
                    if (touring) say(player, "收获 " + rewardLoot.values().stream().mapToInt(Integer::intValue).sum()
                            + " 件物资，已存入经营面板，点击【领取物资】放进背包。");
                    if (published != null) say(player, "Mujica 官方账号已发布巡演纪念照："
                            + published.likes() + " 赞 · 线上新增粉丝 +" + published.onlineFans()
                            + "。使用蓝色小章鱼查看巡演足迹。");
                    // The client captures its own actual view only when nearby and in-world.
                    // Photo files remain local, never part of SavedData/network payloads.
                    if (published != null) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                            new com.miseru.avemujica.network.TourPhotoPayload(
                                    published.number(), published.photoSeed(), stage.getX(), stage.getY(), stage.getZ()));
                    dashboard(player);
                }
            } else if (now % 20L == 0L) {
                if (!MidiBandPlayer.isSilentForOwner(entry.getKey())) {
                    stageThemeLights(level, stage);
                    level.sendParticles(ParticleTypes.NOTE, stage.getX()+.5, stage.getY()+1.2, stage.getZ()+.5,
                            7 + c.lightLevel * 2, 5, 1, 5, .2);
                }
                if (now % 80L == 0L && !MidiBandPlayer.isSilentForOwner(entry.getKey())
                        && !MidiBandPlayer.hasMusicFor(entry.getKey())) {
                    float backgroundVolume = level.getBlockState(stage).is(ModRegistry.TOUR_STAGE_LEVEL2)
                            ? com.miseru.avemujica.stage.StageAcoustics.concertVolume(1.2F, true) : 1.2F;
                    level.playSound(null, stage, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.RECORDS,
                            backgroundVolume, .7F + (now % 7) * .1F);
                }
            }
        }
    }
}
