package com.miseru.avemujica.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The concert light is rendered BEFORE entities, and NEVER writes depth.
 *
 * Depth test still respects the already-drawn opaque terrain. Crucially, the
 * following character/held-item passes can draw over the light, because the
 * light did not mark the framebuffer's depth buffer as occupied.
 *
 * This is deliberately not a post-process/stencil hack; that would require
 * injecting into render targets maintained by Minecraft and shader mods.
 */
public final class StageLightRenderType extends RenderType {
    // The factory is exposed through avemujica.accesswidener; state constants
    // are inherited from the protected RenderStateShard API.
    private StageLightRenderType() {
        super("avemujica:stage_light_dummy", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 0, false, false, () -> { }, () -> { });
    }

    public static RenderType colorOnly(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                // THE important distinction from entityTranslucent(...).
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false);
        return RenderType.create("avemujica:stage_light_" + texture.getPath(),
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                4096, false, true, state);
    }
}
