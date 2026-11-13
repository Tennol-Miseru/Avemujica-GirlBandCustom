package com.miseru.avemujica.team;

import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import com.miseru.avemujica.item.SummonMemberItem;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A member's identity and progress belong to the OWNER (server SavedData), not to
 * the lifetime of a specific entity. SavedData is keyed by player's UUID and role.
 *
 * A recreated NPC gets a NEW entity UUID, replacing the canonical mapping. Old
 * copies returning from unloaded chunks are automatically retired, never allowed
 * to overwrite the player's saved profile or coexist as controllable bandmates.
 */
public final class PlayerBandRoster {
    private PlayerBandRoster() { }

    /** Only a previously deceased member may be subject to this cooldown. */
    public static long revivalRemainingTicks(ServerLevel level, UUID owner, MemberRole role) {
        BandCareerData.Career career = BandCareerData.get(level).career(owner);
        long now = level.getServer().overworld().getGameTime();
        return RevivalCooldownPolicy.remainingTicks(career.awaitingRevival.contains(role.id),
                career.revivalReadyAt.getOrDefault(role.id, 0L), now);
    }

    public static int revivalRemainingSeconds(ServerLevel level, UUID owner, MemberRole role) {
        long ticks = revivalRemainingTicks(level, owner, role);
        return (int)Math.min(Integer.MAX_VALUE, (ticks + 19L) / 20L);
    }

    public static void register() {
        // Loading a previously retired entity must not give it even one active tick.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof BandMemberEntity member) observe(member);
        });
    }

    /** Disabled members are excluded from ALL owner-driven summon pathways. */
    public static boolean summonEnabled(ServerLevel level, UUID owner, MemberRole role) {
        return BandCareerData.get(level).career(owner).summonEnabled(role.id);
    }

    /** Prevent stale unloaded entity UUIDs from automatically returning after hiding. */
    public static void retireHiddenIdentity(ServerLevel level, UUID owner, MemberRole role) {
        BandCareerData data = BandCareerData.get(level);
        BandCareerData.Career c = data.career(owner);
        if (!c.summonEnabled(role.id) && c.memberProfiles.containsKey(role.id)) {
            c.octopusMembers.put(role.id, UUID.randomUUID());
            data.setDirty();
        }
        // No existing backup: leave the canonical UUID in place. When that
        // unloaded original reappears, observe() will snapshot before discarding.
    }

    public static boolean hasProfile(ServerLevel level, UUID owner, MemberRole role) {
        BandCareerData.Career career = BandCareerData.get(level).career(owner);
        return career.memberProfiles.containsKey(role.id);
    }

    /** Existing players of earlier versions may have only an entity UUID, no snapshot yet. */
    public static boolean hasIdentity(ServerLevel level, UUID owner, MemberRole role) {
        BandCareerData.Career career = BandCareerData.get(level).career(owner);
        return career.memberProfiles.containsKey(role.id) || career.octopusMembers.containsKey(role.id);
    }

    /** Never erase an older UNLOADED member when only its UUID is known. */
    public static boolean hasRecoverableProfile(ServerLevel level, UUID owner, MemberRole role) {
        BandCareerData.Career career = BandCareerData.get(level).career(owner);
        return career.memberProfiles.containsKey(role.id) || !career.octopusMembers.containsKey(role.id);
    }

    /** Display is managed per owner's band; this is not a client-only setting. */
    public static boolean nameTagsVisible(ServerLevel level, UUID owner) {
        return BandCareerData.get(level).career(owner).memberNameTagsVisible;
    }

    /** Server-authoritative change, applied at once to loaded members in every dimension.
     * Unloaded and later summoned members are synchronized by observe/restoreOrRegister.
     */
    public static boolean toggleNameTags(ServerPlayer player) {
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.career(player.getUUID());
        career.memberNameTagsVisible = !career.memberNameTagsVisible;
        data.setDirty();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof BandMemberEntity member && member.isAlive()
                        && player.getUUID().equals(member.manager()) && isCurrent(member)) {
                    member.setCustomNameVisible(career.memberNameTagsVisible);
                }
            }
        }
        return career.memberNameTagsVisible;
    }

    public static boolean isCurrent(BandMemberEntity member) {
        UUID owner = member.manager();
        if (owner == null || !(member.level() instanceof ServerLevel level)) return true;
        BandCareerData.Career career = BandCareerData.get(level).career(owner);
        UUID canonical = career.octopusMembers.get(member.role().id);
        return canonical == null || canonical.equals(member.getUUID());
    }

    /**
     * Called early in the server entity tick. Migrates pre-1.0.1 currently loaded
     * members into the player profile, and rejects old copies after a respawn.
     */
    public static boolean observe(BandMemberEntity member) {
        if (!(member.level() instanceof ServerLevel level) || member.manager() == null) return true;
        BandCareerData data = BandCareerData.get(level);
        BandCareerData.Career career = data.career(member.manager());
        UUID expected = career.octopusMembers.get(member.role().id);
        if (expected != null && !expected.equals(member.getUUID())) {
            // Do not save an outdated entity while removing it.
            member.discard();
            return false;
        }
        if (!career.summonEnabled(member.role().id)) {
            // Covers members in unloaded chunks at the moment the owner switches off.
            // If this is the sole original and we have no profile yet, save it FIRST.
            snapshot(member, false);
            career.octopusMembers.put(member.role().id, UUID.randomUUID());
            data.setDirty();
            member.discard();
            return false;
        }
        if (expected == null) {
            career.octopusMembers.put(member.role().id, member.getUUID());
            data.setDirty();
        }
        // setRole() and legacy entity NBT may restore the historical "always visible"
        // setting. Reconcile only on a mismatch to avoid redundant network updates.
        if (member.isCustomNameVisible() != career.memberNameTagsVisible)
            member.setCustomNameVisible(career.memberNameTagsVisible);
        int color = career.instrumentColors.getOrDefault(member.role().id, 0);
        if (member.instrumentColor() != color) member.setInstrumentColor(color);
        var instrument = career.instrumentFor(member.role());
        boolean enabled = career.usesInstrument(member.role());
        if (member.instrumentType() != instrument || member.usesPerformanceInstrument() != enabled)
            member.applyPerformanceInstrument(instrument, enabled);
        if (!career.memberProfiles.containsKey(member.role().id) || member.tickCount % 40 == 0)
            snapshot(member, false);
        return true;
    }

    /** Capture persistent data on death, unloading, progression and periodic ticks. */
    public static void snapshot(BandMemberEntity member, boolean death) {
        if (!(member.level() instanceof ServerLevel level) || member.manager() == null) return;
        BandCareerData data = BandCareerData.get(level);
        BandCareerData.Career career = data.career(member.manager());
        UUID expected = career.octopusMembers.get(member.role().id);
        if (expected != null && !expected.equals(member.getUUID())) return;
        CompoundTag state = new CompoundTag();
        member.addAdditionalSaveData(state);
        state.putString("AveMember", member.role().id);
        state.putUUID("AveManager", member.manager());
        // A dead member must not respawn with zero HP. All advancements survive.
        if (death || member.getHealth() <= 0) state.putFloat("Health", member.getMaxHealth());
        career.octopusMembers.put(member.role().id, member.getUUID());
        career.memberProfiles.put(member.role().id, state);
        if (death) career.awaitingRevival.add(member.role().id);
        data.setDirty();
    }

    /** Find a currently authoritative, LOADED NPC in the requested dimension. */
    public static BandMemberEntity findCurrentLoaded(ServerLevel level, UUID owner, MemberRole role) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof BandMemberEntity npc && npc.isAlive()
                    && owner.equals(npc.manager()) && npc.role() == role && isCurrent(npc))
                return npc;
        }
        return null;
    }

    /**
     * All user-driven summons follow the same rule: recall an existing loaded
     * entity, or revive its SAVED role profile and replace the canonical UUID.
     */
    public static BandMemberEntity summonOrRecall(ServerLevel level, Vec3 destination,
                                                   MemberRole role, UUID owner) {
        if (!summonEnabled(level, owner, role)) return null;
        BandMemberEntity existing = findCurrentLoaded(level, owner, role);
        if (existing != null) {
            existing.getNavigation().stop();
            existing.teleportTo(destination.x, destination.y, destination.z);
            snapshot(existing, false);
            return existing;
        }
        if (!hasRecoverableProfile(level, owner, role) || revivalRemainingTicks(level, owner, role) > 0L)
            return null;
        BandMemberEntity fresh = SummonMemberItem.spawn(level, destination, role);
        if (fresh == null) return null;
        restoreOrRegister(fresh, owner);
        return fresh;
    }

    /**
     * Called immediately after a freshly created entity is given an owner.
     * IMPORTANT: restore BEFORE overwriting the canonical entity UUID or snapshot.
     */
    public static boolean restoreOrRegister(BandMemberEntity fresh, UUID owner) {
        if (!(fresh.level() instanceof ServerLevel level) || owner == null) return false;
        BandCareerData data = BandCareerData.get(level);
        BandCareerData.Career career = data.career(owner);
        MemberRole role = fresh.role();
        if (!career.summonEnabled(role.id)) {
            fresh.discard();
            return false;
        }
        CompoundTag backup = career.memberProfiles.get(role.id);
        boolean restored = backup != null;
        if (restored) {
            CompoundTag state = backup.copy();
            state.putUUID("AveManager", owner);
            state.putString("AveMember", role.id);
            // New physical entity, same bandmate: no stale pathfinding/team leader.
            state.putInt("AveTask", 0);
            state.putInt("AveTeamMode", 0);
            state.remove("AveTeamLeader");
            state.remove("AveTeamStageX"); state.remove("AveTeamStageY"); state.remove("AveTeamStageZ");
            state.remove("AveDutyX"); state.remove("AveDutyY"); state.remove("AveDutyZ");
            state.remove("Leash");
            state.putShort("DeathTime", (short)0);
            if (state.getFloat("Health") <= 0F) state.putFloat("Health", 1F);
            fresh.readAdditionalSaveData(state);
            // Entity creation must be safe even if the previous one died at 0 HP.
            fresh.setHealth(fresh.getMaxHealth());
        }
        fresh.setManager(owner);
        fresh.setCustomNameVisible(career.memberNameTagsVisible);
        fresh.setInstrumentColor(career.instrumentColors.getOrDefault(role.id, 0));
        fresh.applyPerformanceInstrument(career.instrumentFor(role), career.usesInstrument(role));
        career.octopusMembers.put(role.id, fresh.getUUID());
        // The timer starts after a successful revival, not when an unloaded member is recalled.
        if (restored && career.awaitingRevival.remove(role.id)) {
            long now = level.getServer().overworld().getGameTime();
            career.revivalReadyAt.put(role.id, RevivalCooldownPolicy.cooldownEnd(now));
        }
        data.setDirty();
        snapshot(fresh, false);
        return restored;
    }

    /**
     * Death is not deletion of identity: only the physical entity goes away.
     * Any previously loaded original will be retired after a replacement is created.
     */
    public static void onDeath(BandMemberEntity member) {
        snapshot(member, true);
    }

    public static void onRemoval(BandMemberEntity member, Entity.RemovalReason reason) {
        snapshot(member, reason == Entity.RemovalReason.KILLED || member.getHealth() <= 0F);
    }
}
