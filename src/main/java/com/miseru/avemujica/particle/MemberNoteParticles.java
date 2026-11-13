package com.miseru.avemujica.particle;

import com.miseru.avemujica.AveMujicaMod;
import com.miseru.avemujica.entity.MemberRole;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Separate network-safe particle IDs for five distinct note tints.
 * Use the vanilla note sprite; no custom sound or MIDI changes are required. */
public final class MemberNoteParticles {
    private MemberNoteParticles() {}

    private static SimpleParticleType register(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, name),
                FabricParticleTypes.simple());
    }

    public static final SimpleParticleType SAKIKO = register("note_oblivionis");
    public static final SimpleParticleType UIKA = register("note_doloris");
    public static final SimpleParticleType MUTSUMI = register("note_mortis");
    public static final SimpleParticleType UMIRI = register("note_timoris");
    public static final SimpleParticleType NYAMU = register("note_amoris");

    public static SimpleParticleType forRole(MemberRole role) {
        return switch (role) {
            case OBLIVIONIS -> SAKIKO;
            case DOLORIS -> UIKA;
            case MORTIS -> MUTSUMI;
            case TIMORIS -> UMIRI;
            case AMORIS -> NYAMU;
        };
    }

    /** Keeps original per-event count and spread; only note tint changes. */
    public static void emit(ServerLevel level, MemberRole role, double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        level.sendParticles(forRole(role), x, y, z, count, dx, dy, dz, speed);
    }

    /** Called from common mod initializer to guarantee registration on dedicated servers. */
    public static void register() { }
}
