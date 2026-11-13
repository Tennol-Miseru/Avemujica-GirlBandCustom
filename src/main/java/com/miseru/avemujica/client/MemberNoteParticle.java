package com.miseru.avemujica.client;

import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.particle.MemberNoteParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A vanilla-note-shaped particle with exact RGB tint, including unsaturated gray. */
public final class MemberNoteParticle extends TextureSheetParticle {
    private MemberNoteParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites, int color) {
        super(level, x, y, z);
        this.setColor(((color >> 16) & 255) / 255F,
                ((color >> 8) & 255) / 255F, (color & 255) / 255F);
        this.quadSize = 0.19F;
        this.lifetime = 23;
        this.gravity = 0F;
        this.friction = 0.86F;
        // Vanilla music notes drift upward; user-specified color does not depend on speed.
        this.xd = dx * 0.35D + (this.random.nextDouble() - .5D) * .018D;
        this.yd = .070D + Math.max(-.015D, Math.min(.03D, dy * .1D));
        this.zd = dz * 0.35D + (this.random.nextDouble() - .5D) * .018D;
        this.pickSprite(sprites);
    }

    @Override public void tick() {
        super.tick();
        if (this.age > this.lifetime - 6) {
            this.alpha = Math.max(0F, (this.lifetime - this.age) / 6F);
        }
    }

    @Override public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private static void register(SimpleParticleType type, MemberRole role) {
        ParticleFactoryRegistry.getInstance().register(type, sprites ->
                (options, level, x, y, z, dx, dy, dz) ->
                        new MemberNoteParticle(level, x, y, z, dx, dy, dz,
                                sprites, role == MemberRole.TIMORIS ? 0x969696 : role.themeColor));
    }

    public static void registerAll() {
        register(MemberNoteParticles.SAKIKO, MemberRole.OBLIVIONIS);
        register(MemberNoteParticles.UIKA, MemberRole.DOLORIS);
        register(MemberNoteParticles.MUTSUMI, MemberRole.MORTIS);
        register(MemberNoteParticles.UMIRI, MemberRole.TIMORIS);
        register(MemberNoteParticles.NYAMU, MemberRole.AMORIS);
    }
}
