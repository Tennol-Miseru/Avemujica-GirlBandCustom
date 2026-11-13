package com.miseru.avemujica.music;

import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.entity.MemberInstrument;
import com.miseru.avemujica.network.MidiInstrumentSettingsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Server-authoritative, per-player, per-role MIDI sound configuration. */
public final class MidiInstrumentSettings {
    private MidiInstrumentSettings() {}
    public static final List<String> PRESETS = List.of(
            "auto", "guitar", "electric", "electric_soft", "ogg_custom", "pling", "harp", "bass", "bit",
            "flute", "chime", "bell", "xylophone", "iron_xylophone", "banjo", "didgeridoo", "drums");

    public static String defaultFor(MemberRole role) {
        // String IDs avoid coupling persistence and new future members to enum ordinals.
        return switch (role.id) {
            case "doloris" -> "harp"; // New default; explicit saved player presets remain respected.
            case "mortis" -> "electric";
            case "timoris" -> "bass";
            case "amoris" -> "drums";
            case "oblivionis" -> "auto";
            default -> "auto"; // new bandmate can be registered without migrating existing saves
        };
    }

    public static final String UIKA_GUITAR_ID = "doloris_guitar";
    public static final List<String> UIKA_VOCAL_PRESETS = List.of("harp", "ogg_custom");
    public static final List<String> UIKA_GUITAR_PRESETS = List.of("guitar", "electric", "electric_soft", "ogg_custom");

    /** Independent of both Doloris's voice/harp and Mortis's guitar choices. */
    public static String forUikaGuitar(BandCareerData.Career career) {
        MemberInstrument physical = career.instrumentFor(MemberRole.DOLORIS);
        if (physical != MemberInstrument.GUITAR) return physical.preset;
        String chosen = career.instrumentPresets.get(UIKA_GUITAR_ID);
        // The previously defaulted distorted OGG sounds harsh when many notes overlap.
        // Use the established GUITAR+BIT electric blend instead; saved choices win.
        // Retired built-in OGG is automatically mapped to the vanilla electric blend.
        return chosen != null && UIKA_GUITAR_PRESETS.contains(chosen) ? chosen : "electric";
    }

    public static String forRole(BandCareerData.Career career, MemberRole role) {
        // Singing is opt-in OGG; legacy guitar/synth choices on the voice track
        // still fall back to HARP. Her separate guitar has its own saved preset.
        if (role == MemberRole.DOLORIS) {
            String voice = career.instrumentPresets.get(role.id);
            return voice != null && UIKA_VOCAL_PRESETS.contains(voice) ? voice : "harp";
        }
        MemberInstrument physical = career.instrumentFor(role);
        if (physical != MemberInstrument.defaultFor(role)) return physical.preset;
        String configured = career.instrumentPresets.get(role.id);
        // Legacy ogg_builtin saves must never produce missing or silent samples.
        return configured != null && PRESETS.contains(configured) ? configured : defaultFor(role);
    }

    public static SoundEvent sound(String preset) {
        return switch (preset) {
            case "guitar", "electric", "electric_soft" -> SoundEvents.NOTE_BLOCK_GUITAR.value();
            case "ogg_custom" -> MortisSampleSounds.CUSTOM;
            case "pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "bass" -> SoundEvents.NOTE_BLOCK_BASS.value();
            case "bit" -> SoundEvents.NOTE_BLOCK_BIT.value();
            case "flute" -> SoundEvents.NOTE_BLOCK_FLUTE.value();
            case "chime" -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case "bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "xylophone" -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
            case "iron_xylophone" -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();
            case "banjo" -> SoundEvents.NOTE_BLOCK_BANJO.value();
            case "didgeridoo" -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
            case "drums" -> SoundEvents.NOTE_BLOCK_SNARE.value();
            default -> SoundEvents.NOTE_BLOCK_HARP.value();
        };
    }

    /** Route each custom sample to its own event ID; neither guitar can replace vocals. */
    public static SoundEvent sound(String preset, boolean uikaGuitar) {
        return sound(preset, uikaGuitar, false);
    }

    public static SoundEvent sound(String preset, boolean uikaGuitar, boolean uikaVocal) {
        if (preset.equals("ogg_custom")) {
            if (uikaVocal) return MortisSampleSounds.UIKA_VOCAL_CUSTOM;
            if (uikaGuitar) return MortisSampleSounds.UIKA_CUSTOM;
        }
        return sound(preset);
    }

    public static int open(CommandSourceStack source) throws CommandSyntaxException { 
        ServerPlayer player = source.getPlayerOrException();
        BandCareerData.Career career = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        List<String> ids = new ArrayList<>(), chosen = new ArrayList<>();
        List<Integer> volumes = new ArrayList<>();
        for (MemberRole role : MemberRole.values()) {
            ids.add(role.id);
            chosen.add(forRole(career, role));
            volumes.add(volumePercent(career, role.id));
            if (role == MemberRole.DOLORIS) {
                ids.add(UIKA_GUITAR_ID);
                chosen.add(forUikaGuitar(career));
                volumes.add(volumePercent(career, UIKA_GUITAR_ID));
            }
        }
        ServerPlayNetworking.send(player, new MidiInstrumentSettingsPayload(ids, chosen, volumes));
        return 1;
    }

    public static int set(CommandSourceStack source, String roleId, String preset) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MemberRole resolved = null;
        for (MemberRole r : MemberRole.values()) if (r.id.equalsIgnoreCase(roleId)) resolved = r;
        String choice = preset.toLowerCase(Locale.ROOT);
        if (roleId.equalsIgnoreCase(UIKA_GUITAR_ID)) {
            if (!UIKA_GUITAR_PRESETS.contains(choice)) {
                source.sendFailure(Component.literal("初华电吉他支持 guitar / electric / electric_soft / ogg_custom"));
                return 0;
            }
            BandCareerData data = BandCareerData.get(player.serverLevel());
            data.career(player.getUUID()).instrumentPresets.put(UIKA_GUITAR_ID, choice);
            data.setDirty();
            source.sendSuccess(() -> Component.literal("初华电吉他轨音色设为 " + choice + "（人声设置不变）"), false);
            return 1;
        }
        if (resolved == MemberRole.DOLORIS) {
            if (!UIKA_VOCAL_PRESETS.contains(choice)) {
                source.sendFailure(Component.literal("初华人声仅支持 harp（原版竖琴）或 ogg_custom（自选单音人声）。"));
                return 0;
            }
            BandCareerData data = BandCareerData.get(player.serverLevel());
            data.career(player.getUUID()).instrumentPresets.put(MemberRole.DOLORIS.id, choice);
            data.setDirty();
            source.sendSuccess(() -> Component.literal("初华人声音色设为 " + choice + "（不影响初华电吉他）"), false);
            return 1;
        }
        if (resolved == null || !PRESETS.contains(choice)
                || (choice.startsWith("ogg_") && resolved != MemberRole.MORTIS)) {
            source.sendFailure(Component.literal("无效的成员或音色。使用 /avemujica midi instrument 查看可选项。"));
            return 0;
        }
        BandCareerData data = BandCareerData.get(player.serverLevel());
        data.career(player.getUUID()).instrumentPresets.put(resolved.id, choice);
        data.setDirty();
        final MemberRole role = resolved;
        source.sendSuccess(() -> Component.literal("[Ave Mujica] " + role.display + " 的音色已设为 " + choice + "（立即生效）"), false);
        return 1;
    }

    /** Per-owner mix level, including voice and Uika guitar as independent channels. */
    public static int volumePercent(BandCareerData.Career career, String roleId) {
        return net.minecraft.util.Mth.clamp(career.instrumentVolumes.getOrDefault(roleId, 100), 0, 200);
    }

    public static int setVolume(CommandSourceStack source, String roleId, int percent)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean valid = roleId.equals(UIKA_GUITAR_ID);
        for (MemberRole role : MemberRole.values()) if (role.id.equals(roleId)) valid = true;
        if (!valid || percent < 0 || percent > 200 || percent % 10 != 0) {
            source.sendFailure(Component.literal("无效音量：请选择成员并使用 0～200（10% 步进）。"));
            return 0;
        }
        BandCareerData data = BandCareerData.get(player.serverLevel());
        data.career(player.getUUID()).instrumentVolumes.put(roleId, percent);
        data.setDirty();
        source.sendSuccess(() -> Component.literal("[Ave Mujica] " + roleId + " 音量设为 " + percent + "%"), false);
        return 1;
    }

    /** Audition at A2 (MIDI note 45); chosen sample is server-broadcast for consistency. */
    public static int preview(CommandSourceStack source, String roleId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MemberRole role = null;
        for (MemberRole candidate : MemberRole.values())
            if (candidate.id.equalsIgnoreCase(roleId)) { role = candidate; break; }
        if (role == null && !roleId.equalsIgnoreCase(UIKA_GUITAR_ID)) {
            source.sendFailure(Component.literal("成员不存在"));
            return 0;
        }
        BandCareerData.Career career = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        String preset = role == null ? forUikaGuitar(career) : forRole(career, role);
        boolean uikaGuitar = roleId.equalsIgnoreCase(UIKA_GUITAR_ID);
        SoundEvent sample = preset.equals("auto") ? SoundEvents.NOTE_BLOCK_PLING.value()
                : sound(preset, uikaGuitar, role == MemberRole.DOLORIS);
        BlockPos pos = BlockPos.containing(player.position());
        player.serverLevel().playSound(null, pos, sample, SoundSource.RECORDS, 0.85F * volumePercent(career, roleId) / 100F, 1F);
        // Audition the actual electric blend, not only its dry acoustic component.
        if (preset.equals("electric") || preset.equals("electric_soft")) {
            float wet = uikaGuitar ? (preset.equals("electric") ? 0.62F : 0.38F)
                    : (preset.equals("electric") ? 0.66F : 0.32F);
            player.serverLevel().playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(),
                    SoundSource.RECORDS, 0.85F * wet * volumePercent(career, roleId) / 100F, 1F);
        }
        source.sendSuccess(() -> Component.literal("正在试听 " + roleId + " / " + preset), false);
        return 1;
    }

    public static int reset(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BandCareerData data = BandCareerData.get(player.serverLevel());
        data.career(player.getUUID()).instrumentPresets.clear();
        data.setDirty();
        source.sendSuccess(() -> Component.literal("[Ave Mujica] 已重置五人、初华人声及独立吉他声部音色。"), false);
        return open(source);
    }
}
