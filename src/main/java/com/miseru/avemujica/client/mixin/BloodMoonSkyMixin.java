package com.miseru.avemujica.client.mixin;

import com.miseru.avemujica.client.MasqueradeSkyState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Changes only celestial textures; no world clock/weather or global texture rewrites. */
@Mixin(LevelRenderer.class)
public abstract class BloodMoonSkyMixin {
    private static final ResourceLocation RED_MOON = ResourceLocation.fromNamespaceAndPath(
            "avemujica", "textures/environment/blood_moon_phases.png");
    private static final ResourceLocation RED_DAY_MOON = ResourceLocation.fromNamespaceAndPath(
            "avemujica", "textures/environment/blood_day_moon.png");
    private static final ResourceLocation NORMAL_MOON = ResourceLocation.withDefaultNamespace(
            "textures/environment/moon_phases.png");
    private static final ResourceLocation NORMAL_SUN = ResourceLocation.withDefaultNamespace(
            "textures/environment/sun.png");

    private static boolean masqueradeActive() {
        return MasqueradeSkyState.active();
    }

    @Redirect(method = "renderSky", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;MOON_LOCATION:Lnet/minecraft/resources/ResourceLocation;"), require = 0)
    private static ResourceLocation ave$moon() {
        return masqueradeActive() ? RED_MOON : NORMAL_MOON;
    }

    @Redirect(method = "renderSky", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;SUN_LOCATION:Lnet/minecraft/resources/ResourceLocation;"), require = 0)
    private static ResourceLocation ave$sun() {
        return masqueradeActive() ? RED_DAY_MOON : NORMAL_SUN;
    }
}
