package com.miseru.avemujica.client;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Optional Iris bridge. No hard Iris dependency and no shader-pack-specific GL
 * or framebuffer accesses. Shader state is queried at draw time because the
 * player may enable/disable a pack while the world remains loaded.
 */
public final class ShaderPackCompat {
    private ShaderPackCompat() { }

    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    private static final Object IRIS_API;
    private static final Method IS_PACK_IN_USE;
    private static final Method IS_SHADOW_PASS;

    static {
        Object api = null;
        Method pack = null;
        Method shadow = null;
        if (IRIS_LOADED) {
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                pack = type.getMethod("isShaderPackInUse");
                shadow = type.getMethod("isRenderingShadowPass");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // An unsupported/older Iris version should use the safer
                // standard-render-layer fallback, not a direct framebuffer pass.
            }
        }
        IRIS_API = api;
        IS_PACK_IN_USE = pack;
        IS_SHADOW_PASS = shadow;
    }

    public static boolean isShaderPackActive() {
        if (!IRIS_LOADED) return false;
        if (IRIS_API == null || IS_PACK_IN_USE == null) return true;
        try {
            return Boolean.TRUE.equals(IS_PACK_IN_USE.invoke(IRIS_API));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    public static boolean isShadowPass() {
        if (!IRIS_LOADED || IRIS_API == null || IS_SHADOW_PASS == null) return false;
        try {
            return Boolean.TRUE.equals(IS_SHADOW_PASS.invoke(IRIS_API));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
