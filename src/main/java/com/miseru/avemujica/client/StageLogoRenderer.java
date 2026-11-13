package com.miseru.avemujica.client;

import com.miseru.avemujica.block.StageLogoBlockEntity;
import com.miseru.avemujica.block.StagePart;
import com.miseru.avemujica.block.StagePartBlock;
import com.miseru.avemujica.block.TourStageBlock;
import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.stage.StageStructure;
import com.miseru.avemujica.stage.StageStructureLevel2;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A single transparent, high-resolution emblem mapped across 19 x 8.3 blocks.
 * Unlike a sprite-per-block mosaic, it preserves the uploaded PNG's gradient,
 * alpha, and original geometry with no seams or replacement of nether bricks.
 */
public final class StageLogoRenderer implements BlockEntityRenderer<StageLogoBlockEntity> {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(
            "avemujica", "textures/entity/level2_stage_logo.png");
    private static final float WIDTH = 19.0F;
    private static final float HEIGHT = WIDTH * 830.0F / 1896.0F;
    private static final float Y_BOTTOM = 9.0F;
    // Wall is at local z=-8, its outward (front-facing) surface is z=-7.
    private static final float WALL_FACE_Z = -7.5F + 0.0125F;

    public StageLogoRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(StageLogoBlockEntity entity, float tickDelta, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (entity.getLevel() == null) return;
        BlockState controller = entity.getBlockState();
        if (!controller.is(ModRegistry.TOUR_STAGE_LEVEL2)) return;
        Direction front = controller.getValue(TourStageBlock.FACING);

        // Previously placed v1.1.77 stages should keep their old rose until
        // collected and rebuilt. Do not overlay the new logo on that old rose.
        int[] behind = StageStructure.rotate(0, StageStructureLevel2.BACK, front);
        BlockPos marker = entity.getBlockPos().offset(behind[0], 19, behind[1]);
        if (!entity.getLevel().hasChunkAt(marker)) return;
        BlockState backing = entity.getLevel().getBlockState(marker);
        if (!backing.is(ModRegistry.STAGE_PART)
                || backing.getValue(StagePartBlock.PART) != StagePart.LV2_RED) return;

        pose.pushPose();
        // Rotate about the controller block's center, so the same logo faces
        // the audience in all four build orientations.
        pose.translate(0.5D, 0D, 0.5D);
        float yaw = switch (front) {
            case EAST -> 90.0F;
            case NORTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        pose.mulPose(Axis.YP.rotationDegrees(yaw));

        // With Iris shaders, draw ONLY the floor pools through the standard
        // translucent block-entity queue. Do not cover the nether brick wall
        // or purple-thorn emblem with flat colour panels.
        StageBeamRenderer.drawShaderFallback(pose, buffers,
                entity.getLevel(), entity.getBlockPos());
        VertexConsumer v = buffers.getBuffer(RenderType.entityTranslucent(LOGO));
        float l = -WIDTH * 0.5F;
        float r = WIDTH * 0.5F;
        float b = Y_BOTTOM;
        float t = Y_BOTTOM + HEIGHT;
        int light = 0xF000F0; // Full-bright logo; transparent parts show actual brick shading.
        emit(v, pose, l, b, WALL_FACE_Z, 0F, 1F, light);
        emit(v, pose, r, b, WALL_FACE_Z, 1F, 1F, light);
        emit(v, pose, r, t, WALL_FACE_Z, 1F, 0F, light);
        emit(v, pose, l, t, WALL_FACE_Z, 0F, 0F, light);
        // When shaders are OFF, keep the original color-only, pre-entity beams.
        StageBeamRenderer.notice(entity.getLevel(), entity.getBlockPos());
        pose.popPose();
    }

    private static void emit(VertexConsumer v, PoseStack pose, float x, float y, float z,
                             float u, float vtex, int light) {
        v.addVertex(pose.last().pose(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, vtex)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose.last(), 0F, 0F, 1F);
    }

    @Override
    public boolean shouldRenderOffScreen(StageLogoBlockEntity entity) {
        // The decal extends 19 blocks from its controller; do not cull the
        // block entity just because the controller is outside the frustum.
        return true;
    }
}
