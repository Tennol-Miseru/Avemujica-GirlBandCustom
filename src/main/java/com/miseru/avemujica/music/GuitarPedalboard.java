package com.miseru.avemujica.music;

import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.network.GuitarPedalboardPayload;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Optional vanilla-note-block pedalboard. Bypass is bit-for-bit the legacy sound path.
 *  Each MIDI note still drives NPC animation once; the added sound layers never call playNote().
 *  These are audio-layer approximations, NOT a DSP distortion/reverb engine or real SoundFont.
 */
public final class GuitarPedalboard {
    private GuitarPedalboard() {}
    public static final List<String> PRESETS = List.of(
            "bypass", "warm_acoustic", "glass_clean", "vintage_crunch", "rock_drive",
            "heavy_metal", "singing_lead", "octave_drive", "dream_chorus");
    public static final List<String> LABELS = List.of(
            "原版直通（不改变音色）", "暖木吉他", "晶莹 Clean", "复古 Crunch", "摇滚 Overdrive",
            "重型 Metal", "高亮 Lead", "低八度 Drive", "梦幻 Chorus");
    public static final List<String> DESCRIPTIONS = List.of(
            "完整保留 v1.1.7 的原声与电吉他叠音", "柔和吉他 + 少许竖琴", "清晰拨弦 + 明亮 Pling",
            "吉他主体 + 中等电子音色", "更明显的 Bit 电子层与拨弦起音",
            "偏厚实的低频层 + 强电子色彩", "明亮主音突出、高音起音更清楚",
            "低八度叠音，适合重型节奏", "吉他 + 柔和风铃，氛围延展");
    private static final String[] GUITAR_IDS = {"doloris_guitar", "mortis"};
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();
    private record Preview(UUID owner, String roleId, String preset, int mix, int ticks) { }
    private static String normalize(String roleId) {
        // Keep legacy commands and saved pedalboard selections working.
        return roleId.equals("doloris") ? "doloris_guitar" : roleId;
    }
    private static boolean valid(String roleId) {
        return roleId.equals("doloris_guitar") || roleId.equals("mortis");
    }

    public static boolean isGuitar(MemberRole role) {
        return role == MemberRole.DOLORIS || role == MemberRole.MORTIS;
    }
    public static String preset(BandCareerData.Career career, String roleId) {
        roleId = normalize(roleId);
        if (!valid(roleId) || career == null) return "bypass";
        String chosen = career.guitarPedalPresets.get(roleId);
        if (chosen == null && roleId.equals("doloris_guitar"))
            chosen = career.guitarPedalPresets.get("doloris"); // old saved effects
        // List.of(...).contains(null) throws NPE on Java 21. Missing saved
        // pedalboard settings are valid and must fall back to bypass.
        return chosen != null && PRESETS.contains(chosen) ? chosen : "bypass";
    }
    public static int mix(BandCareerData.Career career, String roleId) {
        roleId = normalize(roleId);
        if (!valid(roleId) || career == null) return 100;
        Integer value = career.guitarPedalMix.get(roleId);
        if (value == null && roleId.equals("doloris_guitar"))
            value = career.guitarPedalMix.get("doloris");
        return Mth.clamp(value == null ? 100 : value, 0, 100);
    }

    /** Effect is routed by INSTRUMENT VOICE, not member: Uika's singing never enters here. */
    public static boolean play(BandCareerData.Career career, MemberRole role, boolean vocal,
                               net.minecraft.server.level.ServerLevel level, BlockPos pos,
                               float volume, float pitch, boolean wideStage) {
        if (role == MemberRole.DOLORIS && vocal) return false;
        String roleId = role == MemberRole.DOLORIS ? "doloris_guitar"
                : role == MemberRole.MORTIS ? "mortis" : "";
        String selected = preset(career, roleId);
        int wetMix = mix(career, roleId);
        if (selected.equals("bypass") || wetMix == 0) return false;
        playPreset(level, pos, selected, wetMix, volume, pitch, wideStage);
        return true;
    }

    private static void sound(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                              SoundEvent sound, float volume, float pitch) {
        sound(level, pos, sound, volume, pitch, false);
    }

    private static void sound(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                              SoundEvent sound, float volume, float pitch, boolean wideStage) {
        if (volume > 0.004F) level.playSound(null, pos, StageConcertSounds.forStage(sound, wideStage), SoundSource.RECORDS,
                Math.min(com.miseru.avemujica.stage.StageAcoustics.MAX_CONCERT_VOLUME, volume),
                Mth.clamp(pitch, 0.5F, 2F));
    }

    private static void playPreset(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                   String preset, int mix, float volume, float pitch, boolean wideStage) {
        // Wet/dry control only scales the optional layers; the main guitar is always audible.
        float wet = Mth.clamp(mix, 0, 100) / 100F;
        float dry = switch (preset) {
            case "warm_acoustic" -> 0.97F;
            case "glass_clean" -> 0.94F;
            case "vintage_crunch" -> 0.93F;
            case "rock_drive" -> 0.86F;
            case "heavy_metal" -> 0.82F;
            case "singing_lead" -> 0.88F;
            case "octave_drive" -> 0.90F;
            case "dream_chorus" -> 0.94F;
            default -> 1F;
        };
        sound(level, pos, SoundEvents.NOTE_BLOCK_GUITAR.value(), volume * dry, pitch, wideStage);
        switch (preset) {
            case "warm_acoustic" -> sound(level, pos, SoundEvents.NOTE_BLOCK_HARP.value(), volume * wet * 0.17F, pitch, wideStage);
            case "glass_clean" -> sound(level, pos, SoundEvents.NOTE_BLOCK_PLING.value(), volume * wet * 0.24F, pitch, wideStage);
            case "vintage_crunch" -> sound(level, pos, SoundEvents.NOTE_BLOCK_BIT.value(), volume * wet * 0.42F, pitch, wideStage);
            case "rock_drive" -> {
                sound(level, pos, SoundEvents.NOTE_BLOCK_BIT.value(), volume * wet * 0.76F, pitch, wideStage);
                sound(level, pos, SoundEvents.NOTE_BLOCK_PLING.value(), volume * wet * 0.14F, pitch, wideStage);
            }
            case "heavy_metal" -> {
                sound(level, pos, SoundEvents.NOTE_BLOCK_BIT.value(), volume * wet * 0.92F, pitch, wideStage);
                sound(level, pos, SoundEvents.NOTE_BLOCK_BASS.value(), volume * wet * 0.18F, pitch * 0.5F, wideStage);
            }
            case "singing_lead" -> {
                sound(level, pos, SoundEvents.NOTE_BLOCK_BIT.value(), volume * wet * 0.54F, pitch, wideStage);
                sound(level, pos, SoundEvents.NOTE_BLOCK_PLING.value(), volume * wet * 0.28F, pitch, wideStage);
            }
            case "octave_drive" -> {
                sound(level, pos, SoundEvents.NOTE_BLOCK_BIT.value(), volume * wet * 0.55F, pitch, wideStage);
                sound(level, pos, SoundEvents.NOTE_BLOCK_GUITAR.value(), volume * wet * 0.28F, pitch * 0.5F, wideStage);
            }
            case "dream_chorus" -> {
                sound(level, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), volume * wet * 0.23F, pitch, wideStage);
                sound(level, pos, SoundEvents.NOTE_BLOCK_GUITAR.value(), volume * wet * 0.13F, pitch * 1.012F, wideStage);
            }
            default -> { }
        }
    }

    public static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BandCareerData.Career career = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        ServerPlayNetworking.send(player, new GuitarPedalboardPayload(
                List.of(GUITAR_IDS), List.of(
                        preset(career, "doloris_guitar"), preset(career, "mortis")),
                List.of(mix(career, "doloris_guitar"), mix(career, "mortis"))));
        return 1;
    }
    public static int set(CommandSourceStack source, String roleId, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String voice = normalize(roleId.toLowerCase(Locale.ROOT));
        String value = name.toLowerCase(Locale.ROOT);
        if (!valid(voice) || !PRESETS.contains(value)) {
            source.sendFailure(Component.literal("效果器只支持 doloris_guitar/mortis，预设无效")); return 0;
        }
        BandCareerData data = BandCareerData.get(player.serverLevel());
        data.career(player.getUUID()).guitarPedalPresets.put(voice, value);
        data.setDirty();
        source.sendSuccess(() -> Component.literal("[Ave Mujica] " + voice + " 效果器 → " + LABELS.get(PRESETS.indexOf(value))), false);
        return 1;
    }
    public static int setMix(CommandSourceStack source, String roleId, int percent) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String voice = normalize(roleId.toLowerCase(Locale.ROOT));
        if (!valid(voice)) { source.sendFailure(Component.literal("只支持 doloris_guitar/mortis")); return 0; }
        BandCareerData data = BandCareerData.get(player.serverLevel());
        data.career(player.getUUID()).guitarPedalMix.put(voice, Mth.clamp(percent, 0, 100));
        data.setDirty();
        source.sendSuccess(() -> Component.literal("[Ave Mujica] " + voice + " 效果器混合比例 " + percent + "%"), false);
        return 1;
    }
    public static int reset(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BandCareerData data = BandCareerData.get(player.serverLevel());
        BandCareerData.Career career = data.career(player.getUUID());
        career.guitarPedalPresets.clear(); career.guitarPedalMix.clear();
        data.setDirty();
        return open(source);
    }
    public static int preview(CommandSourceStack source, String roleId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String voice = normalize(roleId.toLowerCase(Locale.ROOT));
        if (!valid(voice)) { source.sendFailure(Component.literal("只支持 doloris_guitar/mortis")); return 0; }
        BandCareerData.Career career = BandCareerData.get(player.serverLevel()).career(player.getUUID());
        PREVIEWS.put(player.getUUID(), new Preview(player.getUUID(), voice, preset(career, voice), mix(career, voice), 0));
        return 1;
    }
    public static void serverTick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Preview>> iterator = PREVIEWS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Preview> e = iterator.next();
            Preview p = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(p.owner());
            if (player == null || p.ticks() > 26) { iterator.remove(); continue; }
            if (p.ticks() % 6 == 0 && p.ticks() <= 18) {
                int index = p.ticks() / 6;
                int[] notes = {52, 59, 64, 59};
                float pitch = (float)Math.pow(2D, (notes[index] - 57D) / 12D);
                BlockPos pos = player.blockPosition();
                if (p.preset().equals("bypass") || p.mix() == 0) {
                    // Live bypass audition uses the player's existing selected instrument.
                    BandCareerData.Career c = BandCareerData.get(player.serverLevel()).career(player.getUUID());
                    String instrument = p.roleId().equals("doloris_guitar")
                            ? MidiInstrumentSettings.forUikaGuitar(c)
                            : MidiInstrumentSettings.forRole(c, MemberRole.MORTIS);
                    SoundEvent original = instrument.equals("auto") ? SoundEvents.NOTE_BLOCK_GUITAR.value()
                            : MidiInstrumentSettings.sound(instrument, p.roleId().equals("doloris_guitar"));
                    sound(player.serverLevel(), pos, original, .62F, pitch);
                    if (instrument.equals("electric") || instrument.equals("electric_soft"))
                        sound(player.serverLevel(), pos, SoundEvents.NOTE_BLOCK_BIT.value(),
                                .62F * (p.roleId().equals("doloris_guitar")
                                        ? (instrument.equals("electric") ? .62F : .38F)
                                        : (instrument.equals("electric") ? .66F : .32F)), pitch);
                } else playPreset(player.serverLevel(), pos, p.preset(), p.mix(), .62F, pitch, false);
            }
            e.setValue(new Preview(p.owner(), p.roleId(), p.preset(), p.mix(), p.ticks() + 1));
        }
    }
}
