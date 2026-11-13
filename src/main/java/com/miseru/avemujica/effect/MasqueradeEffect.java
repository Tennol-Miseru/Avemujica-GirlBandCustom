package com.miseru.avemujica.effect;

import com.miseru.avemujica.AveMujicaMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** The client uses the synced status effect as its blood-moon visual switch. */
public final class MasqueradeEffect extends MobEffect {
    public static final Holder<MobEffect> EFFECT = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID, "masquerade"),
            new MasqueradeEffect());

    private MasqueradeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB02B4E);
    }

    public static void register() { /* trigger class initialization */ }
}
