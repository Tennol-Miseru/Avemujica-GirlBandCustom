package com.miseru.avemujica.voice;

import com.miseru.avemujica.AveMujicaMod;
import com.miseru.avemujica.entity.MemberRole;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import java.util.EnumMap;

/** 80 distinct voice assets, sourced strictly from the matching role's folder.
 * Index slots correspond to the source CSV's Growth/Live/Result identifiers. */
public final class VoiceLibrary {
    private VoiceLibrary() {}
    public enum Cue {
        LEVEL_UP, SPECIAL_TRAINING, SKILL_UP, AWAKENING,
        LIVE_CLEAR, LIVE_FC, LIVE_AP,
        SCORE_SS, SCORE_S, SCORE_A, SCORE_B, SCORE_C, SCORE_D,
        BATTLE_FIRST, BATTLE_HIGH, BATTLE_LOW
    }
    private static final EnumMap<MemberRole, EnumMap<Cue, SoundEvent>> EVENTS = new EnumMap<>(MemberRole.class);
    private static final EnumMap<MemberRole, int[]> DURATIONS = new EnumMap<>(MemberRole.class);
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        for (MemberRole role : MemberRole.values()) {
            EnumMap<Cue, SoundEvent> events = new EnumMap<>(Cue.class);
            int start = switch (role) {
                case DOLORIS -> 81;
                case MORTIS -> 97;
                case TIMORIS -> 113;
                case AMORIS -> 129;
                case OBLIVIONIS -> 145;
            };
            for (Cue cue : Cue.values()) {
                int fileNumber = start + cue.ordinal();
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                        AveMujicaMod.MODID, "voice." + role.id + ".v" + String.format(java.util.Locale.ROOT, "%03d", fileNumber));
                SoundEvent event = Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                        SoundEvent.createFixedRangeEvent(id, 36.0F));
                events.put(cue, event);
            }
            EVENTS.put(role, events);
        }
        DURATIONS.put(MemberRole.DOLORIS, new int[] {40, 37, 45, 32, 37, 46, 50, 85, 64, 56, 41, 69, 63, 75, 67, 52});
        DURATIONS.put(MemberRole.MORTIS, new int[] {30, 38, 51, 34, 49, 41, 48, 51, 30, 47, 67, 68, 59, 68, 30, 31});
        DURATIONS.put(MemberRole.TIMORIS, new int[] {30, 30, 39, 55, 39, 57, 67, 47, 64, 70, 41, 73, 86, 75, 58, 54});
        DURATIONS.put(MemberRole.AMORIS, new int[] {30, 36, 44, 54, 77, 85, 51, 64, 79, 59, 36, 33, 65, 93, 63, 69});
        DURATIONS.put(MemberRole.OBLIVIONIS, new int[] {40, 30, 47, 48, 65, 47, 43, 67, 70, 50, 64, 43, 62, 70, 77, 51});
        {
            EnumMap<DailyCue, SoundEvent> clips = new EnumMap<>(DailyCue.class);
            EnumMap<DailyCue, Integer> durations = new EnumMap<>(DailyCue.class);
            java.util.Map<Integer, DailyCue> ids = new java.util.HashMap<>();
            registerDaily(clips, durations, ids, MemberRole.DOLORIS, DailyCue.COMMON_1, 106, 61);
            registerDaily(clips, durations, ids, MemberRole.DOLORIS, DailyCue.COMMON_2, 107, 52);
            registerDaily(clips, durations, ids, MemberRole.DOLORIS, DailyCue.COMMON_3, 108, 110);
            registerDaily(clips, durations, ids, MemberRole.DOLORIS, DailyCue.LIVE_PREP_1, 120, 86);
            registerDaily(clips, durations, ids, MemberRole.DOLORIS, DailyCue.LIVE_PREP_2, 121, 42);
            registerDaily(clips, durations, ids, MemberRole.DOLORIS, DailyCue.GREETING, 531, 48);
            registerDaily(clips, durations, ids, MemberRole.DOLORIS, DailyCue.RANK_GAIN, 556, 64);
            registerDaily(clips, durations, ids, MemberRole.DOLORIS, DailyCue.SHOP, 581, 72);
            registerDaily(clips, durations, ids, MemberRole.DOLORIS, DailyCue.LEVEL_GAIN, 606, 45);
            DAILY_EVENTS.put(MemberRole.DOLORIS, clips);
            DAILY_DURATIONS.put(MemberRole.DOLORIS, durations);
            DAILY_SOURCE_IDS.put(MemberRole.DOLORIS, ids);
        }
        {
            EnumMap<DailyCue, SoundEvent> clips = new EnumMap<>(DailyCue.class);
            EnumMap<DailyCue, Integer> durations = new EnumMap<>(DailyCue.class);
            java.util.Map<Integer, DailyCue> ids = new java.util.HashMap<>();
            registerDaily(clips, durations, ids, MemberRole.MORTIS, DailyCue.COMMON_1, 127, 76);
            registerDaily(clips, durations, ids, MemberRole.MORTIS, DailyCue.COMMON_2, 128, 61);
            registerDaily(clips, durations, ids, MemberRole.MORTIS, DailyCue.COMMON_3, 129, 78);
            registerDaily(clips, durations, ids, MemberRole.MORTIS, DailyCue.LIVE_PREP_1, 141, 67);
            registerDaily(clips, durations, ids, MemberRole.MORTIS, DailyCue.LIVE_PREP_2, 142, 43);
            registerDaily(clips, durations, ids, MemberRole.MORTIS, DailyCue.GREETING, 532, 49);
            registerDaily(clips, durations, ids, MemberRole.MORTIS, DailyCue.RANK_GAIN, 557, 71);
            registerDaily(clips, durations, ids, MemberRole.MORTIS, DailyCue.SHOP, 582, 46);
            registerDaily(clips, durations, ids, MemberRole.MORTIS, DailyCue.LEVEL_GAIN, 607, 26);
            DAILY_EVENTS.put(MemberRole.MORTIS, clips);
            DAILY_DURATIONS.put(MemberRole.MORTIS, durations);
            DAILY_SOURCE_IDS.put(MemberRole.MORTIS, ids);
        }
        {
            EnumMap<DailyCue, SoundEvent> clips = new EnumMap<>(DailyCue.class);
            EnumMap<DailyCue, Integer> durations = new EnumMap<>(DailyCue.class);
            java.util.Map<Integer, DailyCue> ids = new java.util.HashMap<>();
            registerDaily(clips, durations, ids, MemberRole.TIMORIS, DailyCue.COMMON_1, 148, 87);
            registerDaily(clips, durations, ids, MemberRole.TIMORIS, DailyCue.COMMON_2, 149, 127);
            registerDaily(clips, durations, ids, MemberRole.TIMORIS, DailyCue.COMMON_3, 150, 57);
            registerDaily(clips, durations, ids, MemberRole.TIMORIS, DailyCue.LIVE_PREP_1, 162, 106);
            registerDaily(clips, durations, ids, MemberRole.TIMORIS, DailyCue.LIVE_PREP_2, 163, 74);
            registerDaily(clips, durations, ids, MemberRole.TIMORIS, DailyCue.GREETING, 533, 62);
            registerDaily(clips, durations, ids, MemberRole.TIMORIS, DailyCue.RANK_GAIN, 558, 69);
            registerDaily(clips, durations, ids, MemberRole.TIMORIS, DailyCue.SHOP, 583, 75);
            registerDaily(clips, durations, ids, MemberRole.TIMORIS, DailyCue.LEVEL_GAIN, 608, 35);
            DAILY_EVENTS.put(MemberRole.TIMORIS, clips);
            DAILY_DURATIONS.put(MemberRole.TIMORIS, durations);
            DAILY_SOURCE_IDS.put(MemberRole.TIMORIS, ids);
        }
        {
            EnumMap<DailyCue, SoundEvent> clips = new EnumMap<>(DailyCue.class);
            EnumMap<DailyCue, Integer> durations = new EnumMap<>(DailyCue.class);
            java.util.Map<Integer, DailyCue> ids = new java.util.HashMap<>();
            registerDaily(clips, durations, ids, MemberRole.AMORIS, DailyCue.COMMON_1, 169, 83);
            registerDaily(clips, durations, ids, MemberRole.AMORIS, DailyCue.COMMON_2, 170, 99);
            registerDaily(clips, durations, ids, MemberRole.AMORIS, DailyCue.COMMON_3, 171, 84);
            registerDaily(clips, durations, ids, MemberRole.AMORIS, DailyCue.LIVE_PREP_1, 183, 82);
            registerDaily(clips, durations, ids, MemberRole.AMORIS, DailyCue.LIVE_PREP_2, 184, 73);
            registerDaily(clips, durations, ids, MemberRole.AMORIS, DailyCue.GREETING, 534, 66);
            registerDaily(clips, durations, ids, MemberRole.AMORIS, DailyCue.RANK_GAIN, 559, 73);
            registerDaily(clips, durations, ids, MemberRole.AMORIS, DailyCue.SHOP, 584, 65);
            registerDaily(clips, durations, ids, MemberRole.AMORIS, DailyCue.LEVEL_GAIN, 609, 27);
            DAILY_EVENTS.put(MemberRole.AMORIS, clips);
            DAILY_DURATIONS.put(MemberRole.AMORIS, durations);
            DAILY_SOURCE_IDS.put(MemberRole.AMORIS, ids);
        }
        {
            EnumMap<DailyCue, SoundEvent> clips = new EnumMap<>(DailyCue.class);
            EnumMap<DailyCue, Integer> durations = new EnumMap<>(DailyCue.class);
            java.util.Map<Integer, DailyCue> ids = new java.util.HashMap<>();
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.COMMON_1, 190, 97);
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.COMMON_2, 191, 126);
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.COMMON_3, 192, 102);
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.LIVE_PREP_1, 204, 83);
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.LIVE_PREP_2, 205, 82);
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.THANKS, 206, 84);
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.GREETING, 535, 64);
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.RANK_GAIN, 560, 49);
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.SHOP, 585, 87);
            registerDaily(clips, durations, ids, MemberRole.OBLIVIONIS, DailyCue.LEVEL_GAIN, 610, 45);
            DAILY_EVENTS.put(MemberRole.OBLIVIONIS, clips);
            DAILY_DURATIONS.put(MemberRole.OBLIVIONIS, durations);
            DAILY_SOURCE_IDS.put(MemberRole.OBLIVIONIS, ids);
        }
    }

    /** Daily event categories: independent from the original 80 growth/live clips. */
    public enum DailyCue {
        COMMON_1, COMMON_2, COMMON_3, LIVE_PREP_1, LIVE_PREP_2,
        GREETING, RANK_GAIN, SHOP, LEVEL_GAIN, THANKS
    }
    private static final EnumMap<MemberRole, EnumMap<DailyCue, SoundEvent>> DAILY_EVENTS = new EnumMap<>(MemberRole.class);
    private static final EnumMap<MemberRole, EnumMap<DailyCue, Integer>> DAILY_DURATIONS = new EnumMap<>(MemberRole.class);
    private static final EnumMap<MemberRole, java.util.Map<Integer, DailyCue>> DAILY_SOURCE_IDS = new EnumMap<>(MemberRole.class);

    private static void registerDaily(EnumMap<DailyCue, SoundEvent> events,
                                      EnumMap<DailyCue, Integer> lengths,
                                      java.util.Map<Integer, DailyCue> ids,
                                      MemberRole role, DailyCue cue, int number, int duration) {
        String clip = "d" + String.format(java.util.Locale.ROOT, "%03d", number);
        ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath(
                AveMujicaMod.MODID, "voice." + role.id + "." + clip);
        SoundEvent sound = Registry.register(BuiltInRegistries.SOUND_EVENT, soundId,
                SoundEvent.createFixedRangeEvent(soundId, 36.0F));
        events.put(cue, sound);
        lengths.put(cue, duration);
        ids.put(number, cue);
    }

    public static SoundEvent dailySound(MemberRole role, DailyCue cue) {
        var map = DAILY_EVENTS.get(role);
        return map == null ? null : map.get(cue);
    }
    public static int dailyDurationTicks(MemberRole role, DailyCue cue) {
        var map = DAILY_DURATIONS.get(role);
        return map == null ? 80 : map.getOrDefault(cue, 80);
    }
    public static DailyCue dailyCueBySourceId(MemberRole role, int number) {
        var map = DAILY_SOURCE_IDS.get(role);
        return map == null ? null : map.get(number);
    }

    public static SoundEvent sound(MemberRole role, Cue cue) {
        EnumMap<Cue, SoundEvent> sounds = EVENTS.get(role);
        return sounds == null ? null : sounds.get(cue);
    }
    public static int durationTicks(MemberRole role, Cue cue) {
        int[] values = DURATIONS.get(role);
        return values == null ? 80 : values[cue.ordinal()];
    }
    /** Refuse mismatched clips: 81–96 is Uika only, etc. */
    public static Cue cueBySourceId(MemberRole role, int sourceId) {
        int first = switch (role) {
            case DOLORIS -> 81;
            case MORTIS -> 97;
            case TIMORIS -> 113;
            case AMORIS -> 129;
            case OBLIVIONIS -> 145;
        };
        int index = sourceId - first;
        return index >= 0 && index < Cue.values().length ? Cue.values()[index] : null;
    }
}
