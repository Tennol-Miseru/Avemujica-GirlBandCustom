package com.miseru.avemujica.career;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.network.InstrumentColorChoosePayload;
import com.miseru.avemujica.network.InstrumentColorStatePayload;
import com.miseru.avemujica.team.PlayerBandRoster;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Blue-octopus instrument paint shop; purely cosmetic and owned by the player. */
public final class InstrumentColors {
    private InstrumentColors() { }

    public static void open(ServerPlayer player) {
        if (!MerchConsignment.hasOctopus(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Mujica] 手持蓝色小章鱼才能更换乐器外观。"), false);
            return;
        }
        send(player, true);
    }

    public static void choose(ServerPlayer player, InstrumentColorChoosePayload choice) {
        if (!MerchConsignment.hasOctopus(player)) return;
        if (choice.roleIndex() < 0 || choice.roleIndex() >= MemberRole.values().length
                || !InstrumentPalette.valid(choice.colorIndex())) return;
        MemberRole role = MemberRole.values()[choice.roleIndex()];
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.career(player.getUUID());
        if (choice.colorIndex() == 0) career.instrumentColors.remove(role.id);
        else career.instrumentColors.put(role.id, choice.colorIndex());
        data.setDirty();
        // Apply immediately to loaded canonical NPCs; observe() handles unloaded members.
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof BandMemberEntity member && member.isAlive()
                        && player.getUUID().equals(member.manager()) && member.role() == role
                        && PlayerBandRoster.isCurrent(member)) {
                    member.setInstrumentColor(choice.colorIndex());
                    PlayerBandRoster.snapshot(member, false);
                }
            }
        }
        send(player, false);
    }

    public static void send(ServerPlayer player, boolean open) {
        BandCareerData.Career c = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        int[] colors = new int[MemberRole.values().length];
        for (MemberRole role : MemberRole.values())
            colors[role.ordinal()] = InstrumentPalette.safe(c.instrumentColors.getOrDefault(role.id, 0));
        ServerPlayNetworking.send(player, new InstrumentColorStatePayload(open, colors));
        MemberInstruments.send(player);
    }
}
