package com.miseru.avemujica.entity;

import com.miseru.avemujica.career.BandManagement;
import com.miseru.avemujica.career.MerchConsignment;
import com.miseru.avemujica.team.PlayerBandRoster;
import com.miseru.avemujica.music.MidiBandPlayer;
import com.miseru.avemujica.voice.VoiceDirector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;

/** Server-side orders from the owner's octopus or Sakiko's personal panel. */
public final class TeamOrders {
    private TeamOrders() { }
    private static final double RADIUS = 48D;

    /** An active, opted-in member of this player's roster, not a retired copy. */
    public static boolean canCommand(ServerLevel level, UUID owner, BandMemberEntity member) {
        return member.isAlive() && member.level() == level && owner.equals(member.manager())
                && PlayerBandRoster.summonEnabled(level, owner, member.role())
                && PlayerBandRoster.isCurrent(member);
    }

    /** Use the same player-centered roster for command buttons, display and MIDI selection. */
    public static EnumMap<MemberRole, BandMemberEntity> nearbyOwned(ServerPlayer player) {
        var level = player.serverLevel();
        var owner = player.getUUID();
        var found = new EnumMap<MemberRole, BandMemberEntity>(MemberRole.class);
        for (BandMemberEntity member : level.getEntitiesOfClass(BandMemberEntity.class,
                player.getBoundingBox().inflate(RADIUS, 12D, RADIUS),
                npc -> canCommand(level, owner, npc))) {
            var current = found.get(member.role());
            if (current == null || player.distanceToSqr(member) < player.distanceToSqr(current))
                found.put(member.role(), member);
        }
        return found;
    }

    /** Octopus orders use the player's UUID and location, not another member as a proxy leader. */
    public static int issueFromOctopus(ServerPlayer player, TeamMode mode) {
        if (!player.isAlive() || !MerchConsignment.hasOctopus(player) || mode == TeamMode.NONE) return 0;
        var nearby = nearbyOwned(player);
        if (nearby.isEmpty()) {
            player.displayClientMessage(Component.literal("附近没有可指挥的乐队成员，请先用小章鱼召集。"), false);
            return 0;
        }
        Vec3 stageCenter = BandManagement.preferredFormationCenter(player.serverLevel(),
                player.blockPosition(), player.position().add(2.6D, 0D, 3.0D));
        return apply(player, player.getUUID(), stageCenter, nearby, mode, "蓝色小章鱼");
    }

    /** Nearest bandmate of each role. Avoids commandeering a second, nearby band. */
    private static EnumMap<MemberRole, BandMemberEntity> gather(BandMemberEntity leader, boolean explicit) {
        ServerLevel level = (ServerLevel) leader.level();
        EnumMap<MemberRole, BandMemberEntity> found = new EnumMap<>(MemberRole.class);
        found.put(MemberRole.OBLIVIONIS, leader);
        UUID owner = leader.manager();
        for (BandMemberEntity member : level.getEntitiesOfClass(BandMemberEntity.class,
                leader.getBoundingBox().inflate(RADIUS, 16D, RADIUS), other -> other != leader && other.isAlive())) {
            if (member.role() == MemberRole.OBLIVIONIS || !Objects.equals(owner, member.manager())
                    || (owner != null && !canCommand(level, owner, member))) continue;
            if (!explicit && (member.teamOptOut() ||
                    (member.teamLeader() != null && !member.teamLeader().equals(leader.getUUID())))) continue;
            BandMemberEntity current = found.get(member.role());
            if (current == null || leader.distanceToSqr(member) < leader.distanceToSqr(current))
                found.put(member.role(), member);
        }
        return found;
    }

    public static int issue(ServerPlayer player, BandMemberEntity leader, TeamMode mode) {
        if (leader.role() != MemberRole.OBLIVIONIS || !leader.isAlive()
                || leader.level() != player.level() || player.distanceToSqr(leader) >= 64D
                || !leader.canBeManagedBy(player) || mode == TeamMode.NONE) return 0;
        // An unowned leader is claimed by the player issuing her first order.
        boolean claiming = leader.manager() == null;
        EnumMap<MemberRole, BandMemberEntity> nearby = gather(leader, true);
        if (claiming) {
            PlayerBandRoster.restoreOrRegister(leader, player.getUUID());
            // In a newly spawned unclaimed band, only claim eligible unowned NPCs.
            for (BandMemberEntity npc : nearby.values())
                if (npc.manager() == null) PlayerBandRoster.restoreOrRegister(npc, player.getUUID());
        }
        nearby.values().removeIf(npc -> !canCommand(player.serverLevel(), player.getUUID(), npc));
        if (nearby.isEmpty()) return 0;
        Vec3 stageCenter = BandManagement.preferredFormationCenter(player.serverLevel(),
                leader.blockPosition(), leader.position().add(2.6D, 0D, 3.0D));
        return apply(player, leader.getUUID(), stageCenter, nearby, mode, "祥子");
    }

    private static int apply(ServerPlayer player, UUID controller, Vec3 stageCenter,
                             EnumMap<MemberRole, BandMemberEntity> nearby, TeamMode mode, String source) {
        // Existing ticketed concerts and MIDI must stop before a conflicting order.
        boolean showCancelled = BandManagement.cancelActiveShow(player);
        boolean midiStopped = MidiBandPlayer.stopForOwner(player.getUUID());
        MidiBandPlayer.allowAutomaticForOwner(player.getUUID());
        for (BandMemberEntity npc : nearby.values()) npc.applyTeamOrder(mode, controller, stageCenter);
        boolean performing = mode == TeamMode.REHEARSAL || mode == TeamMode.PERFORMANCE;
        if (performing) MidiBandPlayer.ensureDefaultForTeam(player.serverLevel(), player.getUUID(), stageCenter, nearby);
        VoiceDirector.teamOrder(player.serverLevel(), nearby, mode);
        int count = nearby.size();
        player.displayClientMessage(Component.literal("[Ave Mujica] " + source + "：" + mode.label
                + "（已通知 " + count + "/5 名成员）"
                + (showCancelled ? "；原售票公演已取消，不退款" : "")
                + (midiStopped ? "；原 MIDI 已停止" : "")
                + (performing ? "；使用当前全队曲目" : "")), false);
        return count;
    }

    /** Catch up freshly loaded members without overriding individually exempt members. */
    public static void refresh(BandMemberEntity leader) {
        if (!(leader.level() instanceof ServerLevel) || leader.role() != MemberRole.OBLIVIONIS
                || leader.teamMode() == TeamMode.NONE || leader.manager() == null
                || !leader.getUUID().equals(leader.teamLeader())) return;
        Vec3 center = leader.teamStageCenter();
        if (center == null) center = leader.position().add(2.6D, 0D, 3.0D);
        for (BandMemberEntity npc : gather(leader, false).values()) {
            if (npc == leader) continue;
            if (!leader.getUUID().equals(npc.teamLeader()) || npc.teamMode() != leader.teamMode())
                npc.applyTeamOrder(leader.teamMode(), leader.getUUID(), center);
        }
    }
}
