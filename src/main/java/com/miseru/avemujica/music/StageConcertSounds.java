package com.miseru.avemujica.music;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Concert-only counterparts to original note-block sound events.
 *
 * Two INDEPENDENT ranges matter in Java 1.21.1:
 *  - SoundEvent fixed range controls which players receive a packet on server.
 *  - sounds.json attenuation_distance controls how far the CLIENT fades audio.
 * Both are set to 112 blocks for these concert-only sounds. Normal note blocks,
 * UI previews, voice dialogue, combat and the level-one stage are unchanged.
 * Event samples refer to vanilla note/ OGG files, not bundled duplicates.
 */
public final class StageConcertSounds {
    private StageConcertSounds() { }
    public static final float RANGE = 112F;
    private static final Map<SoundEvent, SoundEvent> WIDE = new IdentityHashMap<>();

    private static SoundEvent register(String name, SoundEvent original) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "concert_" + name);
        SoundEvent wide = Registry.register(BuiltInRegistries.SOUND_EVENT, key,
                SoundEvent.createFixedRangeEvent(key, RANGE));
        WIDE.put(original, wide);
        return wide;
    }

    // Keep every allowed preset, percussion sample and effect layer mapped.
    private static final SoundEvent HARP = register("harp", SoundEvents.NOTE_BLOCK_HARP.value());
    private static final SoundEvent GUITAR = register("guitar", SoundEvents.NOTE_BLOCK_GUITAR.value());
    private static final SoundEvent BASS = register("bass", SoundEvents.NOTE_BLOCK_BASS.value());
    private static final SoundEvent PLING = register("pling", SoundEvents.NOTE_BLOCK_PLING.value());
    private static final SoundEvent BIT = register("bit", SoundEvents.NOTE_BLOCK_BIT.value());
    private static final SoundEvent FLUTE = register("flute", SoundEvents.NOTE_BLOCK_FLUTE.value());
    private static final SoundEvent CHIME = register("chime", SoundEvents.NOTE_BLOCK_CHIME.value());
    private static final SoundEvent BELL = register("bell", SoundEvents.NOTE_BLOCK_BELL.value());
    private static final SoundEvent XYLOPHONE = register("xylophone", SoundEvents.NOTE_BLOCK_XYLOPHONE.value());
    private static final SoundEvent IRON_XYLOPHONE = register("iron_xylophone", SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value());
    private static final SoundEvent BANJO = register("banjo", SoundEvents.NOTE_BLOCK_BANJO.value());
    private static final SoundEvent DIDGERIDOO = register("didgeridoo", SoundEvents.NOTE_BLOCK_DIDGERIDOO.value());
    private static final SoundEvent BASEDRUM = register("basedrum", SoundEvents.NOTE_BLOCK_BASEDRUM.value());
    private static final SoundEvent SNARE = register("snare", SoundEvents.NOTE_BLOCK_SNARE.value());
    private static final SoundEvent HAT = register("hat", SoundEvents.NOTE_BLOCK_HAT.value());
    private static final SoundEvent MORTIS_CUSTOM = register("mortis_custom", MortisSampleSounds.CUSTOM);
    private static final SoundEvent UIKA_CUSTOM = register("uika_custom", MortisSampleSounds.UIKA_CUSTOM);
    private static final SoundEvent UIKA_VOCAL_CUSTOM = register("uika_vocal_custom", MortisSampleSounds.UIKA_VOCAL_CUSTOM);

    public static SoundEvent forStage(SoundEvent original, boolean isLevelTwo) {
        if (!isLevelTwo) return original;
        return WIDE.getOrDefault(original, original);
    }

    public static void initialize() { /* Forces all SoundEvent registrations on mod startup. */ }
}
