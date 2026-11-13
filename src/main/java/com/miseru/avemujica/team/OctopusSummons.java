package com.miseru.avemujica.team;

import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.career.BandManagement;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.item.SummonMemberItem;
import com.miseru.avemujica.voice.VoiceDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One member appears every 12 ticks. Never duplicate a previously registered unloaded UUID. */
public final class OctopusSummons {
    private OctopusSummons() { }
    private static final MemberRole[] ORDER = {MemberRole.OBLIVIONIS, MemberRole.DOLORIS,
            MemberRole.MORTIS, MemberRole.TIMORIS, MemberRole.AMORIS};
    private record Slot(MemberRole role, UUID existing, Vec3 dest) { }
    private static final class Session {
        final ServerLevel level;
        final List<Slot> slots;
        int index;
        long nextTick;
        Session(ServerLevel level, List<Slot> slots, long nextTick) {
            this.level = level; this.slots = slots; this.nextTick = nextTick;
        }
    }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_START = new HashMap<>();

    public static boolean busy(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }

    private static EnumMap<MemberRole, BandMemberEntity> findLoaded(ServerPlayer player) {
        EnumMap<MemberRole, BandMemberEntity> found = new EnumMap<>(MemberRole.class);
        ServerLevel level = player.serverLevel();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof BandMemberEntity member) || !member.isAlive()
                    || !player.getUUID().equals(member.manager()) || !PlayerBandRoster.isCurrent(member)) continue;
            BandMemberEntity old = found.get(member.role());
            if (old == null || player.distanceToSqr(member) < player.distanceToSqr(old))
                found.put(member.role(), member);
        }
        return found;
    }

    public static BandMemberEntity nearestSakiko(ServerPlayer player) {
        BandMemberEntity best = null;
        for (BandMemberEntity member : player.serverLevel().getEntitiesOfClass(BandMemberEntity.class,
                player.getBoundingBox().inflate(8D), npc -> npc.role() == MemberRole.OBLIVIONIS
                && com.miseru.avemujica.entity.TeamOrders.canCommand(player.serverLevel(), player.getUUID(), npc))) {
            if (best == null || player.distanceToSqr(member) < player.distanceToSqr(best)) best = member;
        }
        return best != null && player.distanceToSqr(best) < 64D ? best : null;
    }

    private static Vec3 safe(ServerLevel level, Vec3 around, int index) {
        // Preferred 5-point formation, with additional nearby fallback candidates.
        int[][] offsets = {{0,2},{-2,3},{2,3},{-3,5},{3,5},
                {-2,1},{2,1},{0,5},{-4,3},{4,3}};
        for (int n = 0; n < offsets.length; n++) {
            int[] o = offsets[(index + n) % offsets.length];
            BlockPos initial = BlockPos.containing(around.x + o[0], around.y, around.z + o[1]);
            for (int delta : new int[]{0,1,-1,2,-2}) {
                BlockPos feet = initial.offset(0, delta, 0);
                BlockState at = level.getBlockState(feet);
                BlockState above = level.getBlockState(feet.above());
                if (!level.hasChunkAt(feet) || !level.getFluidState(feet).isEmpty()
                        || !level.getFluidState(feet.above()).isEmpty()
                        || !at.getCollisionShape(level, feet).isEmpty()
                        || !above.getCollisionShape(level, feet.above()).isEmpty()
                        || level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) continue;
                Vec3 spot = Vec3.atBottomCenterOf(feet);
                if (!level.getEntitiesOfClass(BandMemberEntity.class,
                        new AABB(spot.x - .4D, spot.y, spot.z - .4D,
                                spot.x + .4D, spot.y + 1.8D, spot.z + .4D)).isEmpty()) continue;
                return spot;
            }
        }
        return null;
    }

    public static boolean begin(ServerPlayer player) {
        if (busy(player)) return false;
        long now = player.serverLevel().getGameTime();
        long previous = LAST_START.getOrDefault(player.getUUID(), -100000L);
        if (now >= previous && now - previous < 20L * 6L) {
            player.displayClientMessage(Component.literal("蓝色小章鱼正在休息，请稍后再召集。"), false);
            return false;
        }
        ServerLevel level = player.serverLevel();
        BandCareerData data = BandCareerData.get(level);
        BandCareerData.Career career = data.career(player.getUUID());
        EnumMap<MemberRole, BandMemberEntity> loaded = findLoaded(player);
        // Migrate currently loaded legacy NPCs before making a restore decision.
        for (BandMemberEntity npc : loaded.values()) PlayerBandRoster.observe(npc);

        List<Slot> slots = new ArrayList<>();
        Set<BlockPos> reserved = new HashSet<>();
        int newCount = 0, missingUnloaded = 0, legacyNotBackedUp = 0, coolingDown = 0;
        for (int i = 0; i < ORDER.length; i++) {
            MemberRole role = ORDER[i];
            if (!career.summonEnabled(role.id)) continue; // user opted out via character customization
            BandMemberEntity npc = loaded.get(role);
            UUID registered = career.octopusMembers.get(role.id);
            if (npc == null && !PlayerBandRoster.hasRecoverableProfile(level, player.getUUID(), role)) {
                legacyNotBackedUp++;
                continue; // old NPC may still be in an unloaded chunk; don't lose its skill.
            }
            // Recall is always free. Only an actual death may have a revival timer.
            if (npc == null) {
                int wait = PlayerBandRoster.revivalRemainingSeconds(level, player.getUUID(), role);
                if (wait > 0) {
                    coolingDown++;
                    player.displayClientMessage(Component.literal(role.display
                            + "的复活冷却还剩 " + wait + " 秒。"), false);
                    continue;
                }
            }
            // Any missing NPC is recoverable. A prior identity or saved profile
            // makes this a FREE replacement, even if the original chunk is unloaded.
            if (npc == null && (registered != null || career.recruited)) missingUnloaded++;
            Vec3 dest = safe(level, player.position(), i);
            if (dest == null) {
                player.displayClientMessage(Component.literal("附近没有安全落脚点，请前往开阔地再召集。"), false);
                return false;
            }
            // Prevent two bandmates receiving exactly the same landing block.
            BlockPos landing = BlockPos.containing(dest);
            if (!reserved.add(landing)) {
                boolean foundAlternative = false;
                for (int retry = i + 1; retry < i + 12; retry++) {
                    Vec3 alternative = safe(level, player.position(), retry);
                    if (alternative != null && reserved.add(BlockPos.containing(alternative))) {
                        dest = alternative;
                        foundAlternative = true;
                        break;
                    }
                }
                if (!foundAlternative) {
                    player.displayClientMessage(Component.literal("可用的安全站位不足五处，请走到更开阔的位置。"), false);
                    return false;
                }
            }
            if (npc == null && !career.recruited && !PlayerBandRoster.hasIdentity(level, player.getUUID(), role)) newCount++;
            slots.add(new Slot(role, npc == null ? null : npc.getUUID(), dest));
        }
        if (legacyNotBackedUp > 0)
            player.displayClientMessage(Component.literal("有 " + legacyNotBackedUp
                    + " 名旧版成员尚未建立玩家档案。请先加载她们的原区块再召集，以免永久丢失原训练数据。"), false);
        if (slots.isEmpty()) {
            if (ORDER.length > 0 && java.util.Arrays.stream(ORDER).noneMatch(r -> career.summonEnabled(r.id)))
                player.displayClientMessage(Component.literal("所有成员均设置为暂不召集；请在角色自定义中开启。"), false);
            if (coolingDown > 0) player.displayClientMessage(Component.literal(
                    "请等待队员的复活冷却结束；已存活的队员仍可正常召集。"), false);
            return false;
        }
        // Existing member recall is free; brand new recruitment follows the emerald economy.
        int cost = newCount == 0 ? 0 : (career.recruited
                ? newCount * BandManagement.REPLACEMENT_COST : BandManagement.FIRST_RECRUIT_COST);
        if (cost > 0 && !player.getAbilities().instabuild && BandManagement.emeralds(player) < cost) {
            player.displayClientMessage(Component.literal("补齐成员需要 " + cost + " 颗绿宝石；召回已有成员免费。"), false);
            return false;
        }
        if (cost > 0 && !BandManagement.payEmeralds(player, cost)) return false;
        if (newCount > 0) career.recruited = true;
        data.setDirty();
        LAST_START.put(player.getUUID(), now);
        SESSIONS.put(player.getUUID(), new Session(level, slots, now + 2L));
        player.displayClientMessage(Component.literal("祥子：正在依次召集 " + slots.size() + " 名成员…"
                + (cost > 0 ? "（支付 " + cost + " 绿宝石）" : "（免费召回）")
                + (coolingDown > 0 ? "；" + coolingDown + " 名成员仍在复活冷却中" : "")), false);
        return true;
    }

    private static void themeArrival(ServerLevel level, Vec3 dest, MemberRole role) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(
                role.colorR(), role.colorG(), role.colorB()), 1.6F);
        level.sendParticles(dust, dest.x, dest.y + 1.1D, dest.z, 65, .5D, .8D, .5D, .03D);
        for (int h = 0; h <= 5; h++) {
            level.sendParticles(dust, dest.x, dest.y + h * .38D, dest.z,
                    6, .18D, .10D, .18D, .02D);
        }
        level.sendParticles(ParticleTypes.END_ROD, dest.x, dest.y + .8D, dest.z,
                14, .3D, .8D, .3D, .02D);
        level.playSound(null, BlockPos.containing(dest), SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 1.0F, .78F + role.ordinal() * .1F);
    }

    public static void serverTick(MinecraftServer server) {
        var it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID owner = entry.getKey();
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player == null || player.serverLevel() != session.level) { it.remove(); continue; }
            if (session.level.getGameTime() < session.nextTick) continue;
            if (session.index >= session.slots.size()) { it.remove(); continue; }
            Slot slot = session.slots.get(session.index++);
            // Owner may change the toggle during the 12-tick staggered summon session.
            if (!PlayerBandRoster.summonEnabled(session.level, owner, slot.role)) {
                session.nextTick += 12L;
                if (session.index >= session.slots.size()) it.remove();
                continue;
            }
            // All recovery paths share the same persistent, per-role revival gate.
            BandMemberEntity npc = PlayerBandRoster.summonOrRecall(session.level, slot.dest, slot.role, owner);
            if (npc == null) {
                int wait = PlayerBandRoster.revivalRemainingSeconds(session.level, owner, slot.role);
                if (wait > 0) player.displayClientMessage(Component.literal(slot.role.display
                        + "的复活冷却还剩 " + wait + " 秒。"), false);
                session.nextTick += 12L;
                continue;
            }
            npc.assignTask(0);
            PlayerBandRoster.snapshot(npc, false);
            npc.setYRot(player.getYRot());
            themeArrival(session.level, slot.dest, slot.role);
            VoiceDirector.feature(npc, VoiceDirector.Feature.SUMMON);
            session.nextTick += 12L;
            if (session.index >= session.slots.size()) {
                player.displayClientMessage(Component.literal("蓝色小章鱼：本次成员召集完成。"), false);
                it.remove();
            }
        }
    }
}
