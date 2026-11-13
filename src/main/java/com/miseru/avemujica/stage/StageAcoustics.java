package com.miseru.avemujica.stage;

import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Acoustic profile applied only when an actual tier-two stage is nearby. */
public final class StageAcoustics {
    private StageAcoustics() { }

    // Original note balance and the user's six mixer faders are not changed.
    // Amplification remains as in v1.1.80. Unlike ordinary 16*max(1, volume)
    // delivery, stage-only events now ALSO have a 112-block server packet radius
    // and a matching 112-block client attenuation distance (StageConcertSounds).
    public static final float LEVEL_TWO_GAIN = 2.35F;
    public static final float MAX_CONCERT_VOLUME = 3.4F;

    public static float concertVolume(float unamplified, boolean levelTwo) {
        if (!levelTwo || unamplified <= 0F) return unamplified;
        return Math.min(MAX_CONCERT_VOLUME, unamplified * LEVEL_TWO_GAIN);
    }

    /** Non-MIDI backup performance has lower original volume; lift it independently. */
    public static float scriptedVolume(float base, boolean levelTwo, boolean rehearsal) {
        if (!levelTwo || base <= 0F) return base;
        return Math.max(rehearsal ? 1.45F : 2.1F, concertVolume(base, true));
    }

    /** Called once per playback, never once per MIDI note. Does not load chunks. */
    public static boolean nearLevelTwo(ServerLevel level, Vec3 center) {
        BlockPos anchor = BlockPos.containing(center);
        // Onstage members may stand up to 12 blocks from the actual controller.
        for (int dy = -5; dy <= 3; dy++) {
            for (int dx = -18; dx <= 18; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    BlockPos at = anchor.offset(dx,dy,dz);
                    if (level.hasChunkAt(at) && level.getBlockState(at).is(ModRegistry.TOUR_STAGE_LEVEL2))
                        return true;
                }
            }
        }
        return false;
    }
}
