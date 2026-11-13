package com.miseru.avemujica.career;

import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.network.CharacterDailyActionPayload;
import com.miseru.avemujica.network.CharacterSummonChoicePayload;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.team.PlayerBandRoster;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.List;
import com.miseru.avemujica.network.CharacterCustomizationStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Per-owner authoritative switches. Resource overrides remain strictly client local. */
public final class CharacterCustomization {
    private CharacterCustomization() { }
    public static void open(ServerPlayer player) {
        if (!MerchConsignment.hasOctopus(player)) {
            player.displayClientMessage(Component.literal("[Mujica] 请持有蓝色小章鱼。"), false);
            return;
        }
        send(player, true);
    }
    public static void choose(ServerPlayer player, CharacterDailyActionPayload packet) {
        if (!MerchConsignment.hasOctopus(player)) return;
        if (packet.roleIndex() < 0 || packet.roleIndex() >= MemberRole.values().length) return;
        MemberRole role = MemberRole.values()[packet.roleIndex()];
        BandCareerData data = BandCareerData.get(player.serverLevel());
        data.career(player.getUUID()).dailyActionsEnabled.put(role.id, packet.enabled());
        data.setDirty();
        send(player, false);
    }
    /** Hide at once, without death or revival cooldown. Enabling permits the NEXT group summon. */
    public static void chooseSummon(ServerPlayer player, CharacterSummonChoicePayload packet) {
        if (!MerchConsignment.hasOctopus(player)) return;
        if (packet.roleIndex() < 0 || packet.roleIndex() >= MemberRole.values().length) return;
        MemberRole role = MemberRole.values()[packet.roleIndex()];
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career c = data.career(player.getUUID());
        boolean previous = c.summonEnabled(role.id);
        if (previous == packet.enabled()) { send(player, false); return; }
        c.memberSummonEnabled.put(role.id, packet.enabled());
        data.setDirty();
        if (!packet.enabled()) {
            // Do not remove entities during getAllEntities iteration. Snapshot FIRST;
            // discard is never a death, so all growth and cooldowns stay unchanged.
            List<BandMemberEntity> toRetire = new ArrayList<>();
            for (ServerLevel level : player.getServer().getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof BandMemberEntity npc && npc.isAlive()
                            && role == npc.role() && player.getUUID().equals(npc.manager()))
                        toRetire.add(npc);
                }
            }
            for (BandMemberEntity npc : toRetire) {
                PlayerBandRoster.snapshot(npc, false);
                npc.getNavigation().stop();
                npc.discard();
            }
            // Already-unloaded old entities must not reappear when their chunks load.
            // Only invalidate a canonical UUID if we have a backup to revive from.
            PlayerBandRoster.retireHiddenIdentity(player.serverLevel(), player.getUUID(), role);
            player.displayClientMessage(Component.literal("[Mujica] " + role.display
                    + "已暂不召集并从世界撤离；养成记录已保留。"), false);
        } else {
            player.displayClientMessage(Component.literal("[Mujica] " + role.display
                    + "已允许召集。下次点击小章鱼的召集按钮即可返回。"), false);
        }
        send(player, false);
    }
    private static void send(ServerPlayer player, boolean open) {
        var c = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        boolean[] enabled = new boolean[MemberRole.values().length];
        boolean[] summonEnabled = new boolean[MemberRole.values().length];
        for (MemberRole role : MemberRole.values()) {
            enabled[role.ordinal()] = c.dailyActionsEnabled(role.id);
            summonEnabled[role.ordinal()] = c.summonEnabled(role.id);
        }
        ServerPlayNetworking.send(player, new CharacterCustomizationStatePayload(open, enabled, summonEnabled));
        MemberInstruments.send(player);
    }
}
