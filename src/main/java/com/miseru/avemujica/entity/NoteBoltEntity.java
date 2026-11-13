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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class NoteBoltEntity extends ThrowableItemProjectile {
    public NoteBoltEntity(EntityType<? extends NoteBoltEntity> type, Level level) {
        super(type, level);
    }

    public NoteBoltEntity(Level level, LivingEntity owner) {
        super(ModRegistry.NOTE_BOLT, owner, level);
    }

    @Override
    protected Item getDefaultItem() { return ModRegistry.NOTE_ICON; }

    @Override
    protected double getDefaultGravity() { return 0.005D; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.NOTE, getX(), getY(), getZ(), 0D, 0.02D, 0D);
        } else if (tickCount > 55) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        Entity victim = result.getEntity();
        if (!(victim instanceof Monster mob)) return;
        BandMemberEntity shooter = getOwner() instanceof BandMemberEntity band ? band : null;
        MemberRole role = shooter == null ? MemberRole.DOLORIS : shooter.role();
        boolean fullBand = shooter != null && shooter.concertActive();
        double trained = shooter != null && level() instanceof ServerLevel server
                ? BandCareerData.get(server).combatMultiplier(shooter.manager()) : 1.0D;
        float damage = (float)(role.damage * (fullBand ? 1.5F : 1F) * trained
                * (shooter == null ? 1D : shooter.attackTrainingMultiplier()));
        mob.hurt(damageSources().thrown(this, getOwner()), damage);
        double force = role.knockback * (fullBand ? 1.3D : 1D);
        mob.knockback(force, getX() - mob.getX(), getZ() - mob.getZ());
        if (role == MemberRole.TIMORIS) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 55, 1));
        if (role == MemberRole.AMORIS && level() instanceof ServerLevel server) {
            for (Monster other : server.getEntitiesOfClass(Monster.class, mob.getBoundingBox().inflate(2.5D),
                    enemy -> enemy.isAlive() && enemy != mob)) {
                other.hurt(damageSources().thrown(this, getOwner()), 2F);
                other.knockback(1.3, getX() - other.getX(), getZ() - other.getZ());
            }
        }
        if (role == MemberRole.OBLIVIONIS && shooter != null) shooter.heal(.4F);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            if (level() instanceof ServerLevel server)
                server.sendParticles(ParticleTypes.NOTE, getX(), getY(), getZ(), 5, .2, .2, .2, .02);
            discard();
        }
    }
}
