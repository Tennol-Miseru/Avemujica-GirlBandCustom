package com.miseru.avemujica.block;

import com.miseru.avemujica.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores a local animation clock for the optional stage light fixture.
 * Legacy save compatibility only. The unverified dragon-ray renderer was
 * retired: existing lamps keep their IDs and toggleable light state, but
 * no new lamps can be crafted or obtained from the mod's creative tab.
 */
public final class ConcertLightOrbBlockEntity extends BlockEntity {
    private int age;

    public ConcertLightOrbBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CONCERT_LIGHT_ORB_ENTITY, pos, state);
    }

    public int animationAge() {
        return age;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ConcertLightOrbBlockEntity orb) {
        if (state.getValue(ConcertLightOrbBlock.LIT)) {
            orb.age = (orb.age + 1) & 0x7FFFFFFF;
        }
    }
}
