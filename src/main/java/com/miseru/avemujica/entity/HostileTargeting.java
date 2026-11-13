package com.miseru.avemujica.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;

/**
 * Vanilla hostile mobs normally only target players, villagers, golems etc.
 * Add band members as normal attack targets after a hostile mob enters a level.
 * Existing higher-priority vanilla goals are preserved (including player aggro).
 */
public final class HostileTargeting {
    private HostileTargeting() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof Monster monster) || entity instanceof NeutralMob) return;
            // priority 3: respects mobs' existing top-priority player targeting.
            monster.targetSelector.addGoal(3,
                    new NearestAttackableTargetGoal<>(monster, BandMemberEntity.class,
                            10, true, false, band -> band.isAlive()));
        });
    }
}
