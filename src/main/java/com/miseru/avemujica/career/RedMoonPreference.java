package com.miseru.avemujica.career;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.team.PlayerBandRoster;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Player-owned, persistent, visual-only preference for the full-band red moon. */
public final class RedMoonPreference {
    private RedMoonPreference() { }

    /** Unlock after all five TRAINING ranks reach Lv.15, not career Lv.10. */
    public static boolean unlocked(ServerPlayer player) {
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.career(player.getUUID());
        if (career.redMoonControlUnlocked) return true;
        for (MemberRole role : MemberRole.values()) {
            int rank = -1;
            // Prefer current loaded entity over potentially older per-role snapshots.
            for (ServerLevel level : player.getServer().getAllLevels()) {
                BandMemberEntity member = PlayerBandRoster.findCurrentLoaded(level, player.getUUID(), role);
                if (member != null) { rank = member.trainingRank(); break; }
            }
            if (rank < 0) {
                CompoundTag profile = career.memberProfiles.get(role.id);
                if (profile != null) rank = profile.getInt("AveTrainingRank");
            }
            if (rank < MasqueradeConcert.MAX_TRAINING_RANK) return false;
        }
        career.redMoonControlUnlocked = true;
        data.setDirty();
        return true;
    }

    public static boolean enabled(ServerPlayer player) {
        return BandCareerData.get(player.serverLevel()).career(player.getUUID()).redMoonEnabled;
    }

    /** Server checks ownership, rank unlock and persistence; no client-side bypass. */
    public static boolean toggle(ServerPlayer player) {
        if (!unlocked(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "红月表现需要五位成员全部训练到 Lv.15 才能切换。"), false);
            return enabled(player);
        }
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.career(player.getUUID());
        career.redMoonEnabled = !career.redMoonEnabled;
        data.setDirty();
        // Correct the current sky immediately even during an active concert.
        MasqueradeConcert.preferenceChanged(player);
        return career.redMoonEnabled;
    }
}
