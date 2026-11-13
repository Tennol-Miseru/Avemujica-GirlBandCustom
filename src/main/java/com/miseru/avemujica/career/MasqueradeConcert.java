package com.miseru.avemujica.career;

import com.miseru.avemujica.effect.MasqueradeEffect;
import com.miseru.avemujica.network.MasqueradeSkyPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.entity.TeamMode;
import com.miseru.avemujica.music.MidiBandPlayer;
import com.miseru.avemujica.team.PlayerBandRoster;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Five Lv.15 musicians, ONE owner, in a genuine simultaneous performance.
 * An isolated solo, rehearsal or a team with a missing member never triggers.
 */
public final class MasqueradeConcert {
    private MasqueradeConcert() { }
    public static final int MAX_TRAINING_RANK = 15;
    private static final double RADIUS = 28D;
    // Players who were SENT the visual effect, not all players receiving concert buffs.
    private static final Set<UUID> PREVIOUS = new HashSet<>();
    private static final Set<UUID> IN_CONCERT_RANGE = new HashSet<>();

    /** Called after a preference toggle; applies immediately while music continues. */
    public static void preferenceChanged(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean show = IN_CONCERT_RANGE.contains(id) && RedMoonPreference.enabled(player);
        if (show) {
            PREVIOUS.add(id);
        } else {
            PREVIOUS.remove(id);
        }
        ServerPlayNetworking.send(player, new MasqueradeSkyPayload(show));
    }

    /** Avoid stale visual sessions when the same player reconnects. */
    public static void playerDisconnected(UUID id) {
        PREVIOUS.remove(id);
        IN_CONCERT_RANGE.remove(id);
    }

    public static void serverTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld.getGameTime() % 20L != 0L) return;
        Set<UUID> current = new HashSet<>();
        Set<UUID> nearbyConcerts = new HashSet<>();
        // Always scrub the old custom potion in upgraded worlds, even when the
        // player is no longer near a band. The sky now syncs by packet instead.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasEffect(MasqueradeEffect.EFFECT)) p.removeEffect(MasqueradeEffect.EFFECT);
        }
        for (ServerLevel level : server.getAllLevels()) {
            Map<UUID, EnumMap<MemberRole, BandMemberEntity>> bands = new HashMap<>();
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof BandMemberEntity npc) || !npc.isAlive() || npc.manager() == null) continue;
                if (!PlayerBandRoster.isCurrent(npc) || npc.trainingRank() < MAX_TRAINING_RANK || !npc.isPerforming()
                        || npc.teamMode() == TeamMode.REHEARSAL) continue;
                bands.computeIfAbsent(npc.manager(), id -> new EnumMap<>(MemberRole.class))
                        .putIfAbsent(npc.role(), npc);
            }
            for (Map.Entry<UUID, EnumMap<MemberRole, BandMemberEntity>> entry : bands.entrySet()) {
                var members = entry.getValue();
                // Stopping MIDI requests a silent performance, not a blood-moon aura.
                if (MidiBandPlayer.isSilentForOwner(entry.getKey())) continue;
                if (members.size() != MemberRole.values().length) continue;
                BandMemberEntity leader = members.get(MemberRole.OBLIVIONIS);
                if (leader == null) continue;
                boolean trueConcert = false;
                boolean closeFormation = true;
                for (MemberRole role : MemberRole.values()) {
                    BandMemberEntity npc = members.get(role);
                    if (npc == null || npc.distanceToSqr(leader) > 15D * 15D) {
                        closeFormation = false; break;
                    }
                    if (npc.teamMode() == TeamMode.PERFORMANCE) trueConcert = true;
                }
                // Paid performances / real MIDI performances also count as concerts.
                if (!trueConcert) trueConcert = BandManagement.isPerforming(level, entry.getKey(), leader.blockPosition())
                        || MidiBandPlayer.hasMusicFor(entry.getKey());
                if (!closeFormation || !trueConcert) continue;
                for (ServerPlayer player : level.players()) {
                    if (player.isSpectator() || player.distanceToSqr(leader) > RADIUS * RADIUS) continue;
                    nearbyConcerts.add(player.getUUID());
                    if (RedMoonPreference.enabled(player)) current.add(player.getUUID());
                    // Refresh the synced effect every second while all five musicians qualify.
                    // Legacy v1.1.21 used a potion status as the sky switch.
                    // Remove it when upgrading; custom sky toggle is now a hidden payload.
                    if (player.hasEffect(MasqueradeEffect.EFFECT)) player.removeEffect(MasqueradeEffect.EFFECT);
                    // The entrance bell belongs to the concert, not the moon texture setting.
                    if (!IN_CONCERT_RANGE.contains(player.getUUID())) {
                        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
                                SoundSource.RECORDS, 1.2F, .65F);
                    }
                    if (current.contains(player.getUUID()) && !PREVIOUS.contains(player.getUUID())) {
                        ServerPlayNetworking.send(player, new MasqueradeSkyPayload(true));
                    }
                    // Do not remove existing stronger vanilla potion effects. The
                    // short durations naturally expire after the concert stops.
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 35, 0, true, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 35, 0, true, false, true));
                    // Unlike strength/resistance, Regeneration I relies on its remaining
                    // duration reaching a multiple of 50 ticks to heal. Reapplying it
                    // every 20 ticks would reset that clock before it ever heals.
                    // Keep stronger/longer external potions intact, and refresh our
                    // short effect only as it approaches expiry.
                    MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
                    if (regeneration == null || MasqueradeRegenerationPolicy.shouldRenew(
                            regeneration.getDuration(), regeneration.getAmplifier())) {
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                                MasqueradeRegenerationPolicy.DURATION_TICKS, 0, true, false, true));
                    }
                }
            }
        }
        for (UUID id : PREVIOUS) if (!current.contains(id)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.removeEffect(MasqueradeEffect.EFFECT);
                ServerPlayNetworking.send(player, new MasqueradeSkyPayload(false));
            }
        }
        PREVIOUS.clear();
        PREVIOUS.addAll(current);
        IN_CONCERT_RANGE.clear();
        IN_CONCERT_RANGE.addAll(nearbyConcerts);
    }
}
