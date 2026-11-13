package com.miseru.avemujica.entity;

import com.miseru.avemujica.career.BandCareerData;
import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Fictional short-range arcade projectile, with no ammo or real-world ballistics. */
public final class RifleRoundEntity extends ThrowableItemProjectile {
    public RifleRoundEntity(EntityType<? extends RifleRoundEntity> type, Level level) {
        super(type, level);
    }
    public RifleRoundEntity(Level level, LivingEntity owner) {
        super(ModRegistry.RIFLE_ROUND, owner, level);
    }
    @Override protected Item getDefaultItem() { return Items.IRON_NUGGET; }
    @Override protected double getDefaultGravity() { return 0.001D; }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) {
            level().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), .0D, .0D, .0D);
        } else if (tickCount > 32) discard();
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity victim = hit.getEntity();
        if (level().isClientSide || !(victim instanceof Monster monster)) return;
        BandMemberEntity owner = getOwner() instanceof BandMemberEntity npc ? npc : null;
        double scaling = owner == null ? 1.0D : owner.attackTrainingMultiplier();
        if (owner != null && level() instanceof ServerLevel server)
            scaling *= BandCareerData.get(server).combatMultiplier(owner.manager());
        monster.hurt(damageSources().thrown(this, getOwner()), (float)(5.4D * scaling));
        monster.knockback(.35D, getX() - monster.getX(), getZ() - monster.getZ());
    }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide) {
            if (level() instanceof ServerLevel server)
                server.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(),
                        5, .12D, .12D, .12D, .025D);
            discard();
        }
    }
}
