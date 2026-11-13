package com.miseru.avemujica.career;

import com.miseru.avemujica.entity.MemberInstrument;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.network.MemberInstrumentChoicePayload;
import com.miseru.avemujica.network.MemberInstrumentStatePayload;
import com.miseru.avemujica.team.PlayerBandRoster;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class MemberInstruments {
    private MemberInstruments() {}

    public static void choose(ServerPlayer player, MemberInstrumentChoicePayload request) {
        if (!MerchConsignment.hasOctopus(player)) return;
        int roleIndex = request.roleIndex(), choice = request.choice();
        if (roleIndex < 0 || roleIndex >= MemberRole.values().length) return;
        MemberRole role = MemberRole.values()[roleIndex];
        boolean toggle = choice == MemberInstrumentChoicePayload.DISABLE_UIKA
                || choice == MemberInstrumentChoicePayload.ENABLE_UIKA;
        if (toggle ? role != MemberRole.DOLORIS : choice < 0 || choice >= MemberInstrument.values().length) return;

        var data = BandCareerData.get(player.serverLevel());
        var career = data.career(player.getUUID());
        if (toggle) {
            career.uikaInstrumentEnabled = choice == MemberInstrumentChoicePayload.ENABLE_UIKA;
        } else {
            var instrument = MemberInstrument.at(choice, role);
            if (instrument == MemberInstrument.defaultFor(role)) career.memberInstruments.remove(role.id);
            else career.memberInstruments.put(role.id, instrument);
        }
        data.setDirty();
        for (var level : player.serverLevel().getServer().getAllLevels()) {
            var member = PlayerBandRoster.findCurrentLoaded(level, player.getUUID(), role);
            if (member == null) continue;
            member.applyPerformanceInstrument(career.instrumentFor(role), career.usesInstrument(role));
            PlayerBandRoster.snapshot(member, false);
        }
        send(player);
    }

    public static void send(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, MemberInstrumentStatePayload.TYPE)) return;
        var career = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        int[] instruments = new int[MemberRole.values().length];
        for (MemberRole role : MemberRole.values()) instruments[role.ordinal()] = career.instrumentFor(role).ordinal();
        ServerPlayNetworking.send(player, new MemberInstrumentStatePayload(career.uikaInstrumentEnabled, instruments));
    }
}
