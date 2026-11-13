package com.miseru.avemujica.music;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Stable registry IDs: sound samples load through Minecraft's ordinary resource system. */
public final class MortisSampleSounds {
    private MortisSampleSounds() {}

    public static final SoundEvent CUSTOM = register("mortis_guitar_custom");
    public static final SoundEvent UIKA_CUSTOM = register("uika_guitar_custom");
    /** Single-note vowel singing sample, independent from the two guitar samples and dialogue voices. */
    public static final SoundEvent UIKA_VOCAL_CUSTOM = register("uika_vocal_custom");

    private static SoundEvent register(String id) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, id);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, key,
                SoundEvent.createVariableRangeEvent(key));
    }

    public static void initialize() { /* Trigger static SoundEvent registration. */ }
}
