package com.miseru.avemujica.client;

import com.miseru.avemujica.block.StagePart;
import com.miseru.avemujica.block.StagePartBlock;
import com.miseru.avemujica.block.TourStageBlock;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.registry.ModRegistry;
import com.miseru.avemujica.stage.StageFormation;
import com.miseru.avemujica.stage.StageStructure;
import com.miseru.avemujica.stage.StageStructureLevel2;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tier-II stage lighting, rendered ahead of characters with COLOR-ONLY writes.
 *
 * The former block-entity layer submitted transparent geometry at the same
 * time as the transparent logo: the light could be sorted in front of actors.
 * Register the stage as visible from the logo block-entity renderer, then draw
 * its lights in BEFORE_ENTITIES, flush immediately, with depth writes OFF.
 * Characters, weapons and other opaque entities draw AFTER that flush and
 * therefore cannot be depth-occluded by the shafts, at any camera angle.
 *
 * This is an atmospheric light illustration, NOT a change to Minecraft's
 * real coloured light engine. It adds no particle, block or entity.
 */
public final class StageBeamRenderer {
    private StageBeamRenderer() { }

    private static final ResourceLocation SHAFT = ResourceLocation.fromNamespaceAndPath(
            "avemujica", "textures/entity/level2_stage_beam.png");
    private static final ResourceLocation FLOOR = ResourceLocation.fromNamespaceAndPath(
            "avemujica", "textures/entity/level2_stage_pool.png");
    // In Iris, only the engine-managed floor pools are drawn. No wall decal
    // wash: it obscured the high-resolution Ave Mujica emblem.
    private static final ResourceLocation SHADER_POOL = ResourceLocation.fromNamespaceAndPath(
            "avemujica", "textures/entity/level2_stage_pool_shader.png");
    private static final RenderType SHAFT_PASS = StageLightRenderType.colorOnly(SHAFT);
    private static final RenderType FLOOR_PASS = StageLightRenderType.colorOnly(FLOOR);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int MAX_VISIBLE_STAGES = 12;
    private static final long EXPIRY_TICKS = 40L;
    private static final double MAX_DISTANCE_SQR = 112D * 112D;

    // Client render thread only. The block entity discovers real controllers
    // without scanning thousands of unrelated world blocks every frame.
    private static final Map<BlockPos, Long> VISIBLE = new HashMap<>();
    private static Level knownLevel;
    private static final ByteBufferBuilder BUFFER = new ByteBufferBuilder(64 * 1024);

    public static void register() {
        WorldRenderEvents.BEFORE_ENTITIES.register(StageBeamRenderer::beforeEntities);
    }

    public static void reset() {
        VISIBLE.clear();
        knownLevel = null;
    }

    /** Called only from the valid level-two logo block entity's renderer. */
    public static void notice(Level level, BlockPos controller) {
        if (knownLevel != level) {
            reset();
            knownLevel = level;
        }
        if (VISIBLE.size() >= MAX_VISIBLE_STAGES && !VISIBLE.containsKey(controller)) {
            // Bound the amount of cached work on servers with many placed stages.
            VISIBLE.entrySet().removeIf(e -> level.getGameTime() - e.getValue() > EXPIRY_TICKS);
            if (VISIBLE.size() >= MAX_VISIBLE_STAGES) return;
        }
        VISIBLE.put(controller.immutable(), level.getGameTime());
    }

    private static void beforeEntities(WorldRenderContext context) {
        // Iris shader packs can move/replace the framebuffer that was active in
        // BEFORE_ENTITIES. Color-only writes into it may be lost after deferred
        // composition. In that case StageLogoRenderer submits a safe fallback
        // through the engine-managed translucent block-entity buffer instead.
        if (ShaderPackCompat.isShaderPackActive()) return;
        Level level = context.world();
        if (level == null || knownLevel != level || VISIBLE.isEmpty()) return;
        Vec3 cam = context.camera().getPosition();
        long now = level.getGameTime();
        boolean hasDrawing = false;
        // WorldRenderContext.matrixStack() is NULL before entity rendering in
        // 1.21.1. Construct our own stack from the supplied position matrix.
        PoseStack pose = new PoseStack();
        pose.mulPose(context.positionMatrix());
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(BUFFER);

        for (Iterator<Map.Entry<BlockPos, Long>> it = VISIBLE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Long> entry = it.next();
            BlockPos root = entry.getKey();
            if (now - entry.getValue() > EXPIRY_TICKS) {
                it.remove();
                continue;
            }
            if (root.distToCenterSqr(cam.x, cam.y, cam.z) > MAX_DISTANCE_SQR || !level.hasChunkAt(root))
                continue;
            BlockState state = level.getBlockState(root);
            if (!state.is(ModRegistry.TOUR_STAGE_LEVEL2)) {
                it.remove();
                continue;
            }
            Direction facing = state.getValue(TourStageBlock.FACING);
            // Old 1.1.77 stages don't have the new logo: don't place new lighting
            // onto their legacy rose. Match the logo renderer's version marker.
            int[] behind = StageStructure.rotate(0, StageStructureLevel2.BACK, facing);
            BlockPos marker = root.offset(behind[0], 19, behind[1]);
            if (!level.hasChunkAt(marker)) continue;
            BlockState backing = level.getBlockState(marker);
            if (!backing.is(ModRegistry.STAGE_PART)
                    || backing.getValue(StagePartBlock.PART) != StagePart.LV2_RED) continue;

            AABB band = new AABB(root).inflate(18.5D, 23D, 18.5D);
            boolean active = !level.getEntitiesOfClass(BandMemberEntity.class, band,
                    musician -> musician.isAlive() && musician.isPerforming()
                            && !musician.isSilentPerforming()).isEmpty();
            if (!active) continue;

            pose.pushPose();
            pose.translate(root.getX() + .5D - cam.x, root.getY() - cam.y,
                    root.getZ() + .5D - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(switch (facing) {
                case EAST -> 90F;
                case NORTH -> 180F;
                case WEST -> 270F;
                default -> 0F;
            }));
            drawStage(pose, buffers);
            pose.popPose();
            hasDrawing = true;
        }
        // Never leave these quads in the world buffer. Their only guarantee
        // comes from being FLUSHED before any NPC/player/item is drawn.
        if (hasDrawing) buffers.endBatch();
    }

    /**
     * Shader-pack fallback: render ONLY the coloured ground pools through the
     * engine-managed block entity translucent queue. Unlike the old workaround,
     * no luminous panels are painted across the rear nether-brick/logo wall.
     * The pools hug solid floors so figures never receive a foreground overlay.
     * Caller has already rotated to controller-local coordinates.
     */
    public static void drawShaderFallback(PoseStack pose, MultiBufferSource buffers,
                                          Level level, BlockPos root) {
        if (!ShaderPackCompat.isShaderPackActive() || ShaderPackCompat.isShadowPass()) return;
        if (!isPlaying(level, root)) return;

        VertexConsumer pools = buffers.getBuffer(RenderType.entityTranslucent(SHADER_POOL));
        for (MemberRole role : MemberRole.values()) {
            StageFormation.Mark mark = StageFormation.local(role, true);
            float x = (float) mark.x();
            float y = (float) (mark.y() + 1.0D) + .016F;
            float z = (float) mark.z();
            int red = (role.themeColor >>> 16) & 255;
            int green = (role.themeColor >>> 8) & 255;
            int blue = role.themeColor & 255;
            boolean high = role == MemberRole.OBLIVIONIS || role == MemberRole.AMORIS;
            float width = high ? 2.4F : 3.5F;
            float length = high ? 1.25F : 2.0F;
            float centerZ = high ? -3.3F : z - .35F;
            quad(pools, pose,
                    x - width, y, centerZ - length, 0F, 0F, 235,
                    x - width, y, centerZ + length, 0F, 1F, 235,
                    x + width, y, centerZ + length, 1F, 1F, 235,
                    x + width, y, centerZ - length, 1F, 0F, 235,
                    red, green, blue);
        }
        // Do not flush buffers or obtain the logo's render type here; the
        // caller submits the real emblem immediately afterwards.
    }

    private static boolean isPlaying(Level level, BlockPos root) {
        AABB region = new AABB(root).inflate(18.5D, 23D, 18.5D);
        return !level.getEntitiesOfClass(BandMemberEntity.class, region,
                musician -> musician.isAlive() && musician.isPerforming()
                        && !musician.isSilentPerforming()).isEmpty();
    }

    private static void drawStage(PoseStack pose, MultiBufferSource buffers) {
        // PASS 1: all beam geometry, with SHAFT_PASS still the active buffer.
        VertexConsumer shaft = buffers.getBuffer(SHAFT_PASS);
        for (MemberRole role : MemberRole.values()) {
            StageFormation.Mark mark = StageFormation.local(role, true);
            float x = (float) mark.x();
            float y = (float) (mark.y() + 1.0D);
            float z = (float) mark.z();
            float roof = StageStructureLevel2.ROOF_Y - .35F;
            float lampZ = -6.35F;
            int red = (role.themeColor >>> 16) & 255;
            int green = (role.themeColor >>> 8) & 255;
            int blue = role.themeColor & 255;

            // Five broad, tapered shafts, not five skinny rectangles. Actors
            // are rendered later and the lights do not write to depth.
            float radius = 4.15F;
            for (int i = 0; i < 3; i++) {
                float skew = (i - 1) * .65F;
                quad(shaft, pose,
                        x - .35F, roof, lampZ + skew, 0F, 0F, 124,
                        x + .35F, roof, lampZ + skew, 1F, 0F, 124,
                        x + radius, y + .18F, z + .9F + skew, 1F, 1F, 45,
                        x - radius, y + .18F, z + .9F + skew, 0F, 1F, 45,
                        red, green, blue);
            }
            // Side wings preserve the beam silhouette at diagonal angles.
            for (int side : new int[]{-1, 1}) {
                quad(shaft, pose,
                        x + side * .15F, roof, lampZ - .15F, 0F, 0F, 82,
                        x + side * .65F, roof, lampZ + .15F, 1F, 0F, 82,
                        x + side * (radius + .2F), y + .12F, z + 1.35F, 1F, 1F, 28,
                        x + side * 1.2F, y + .12F, z + .3F, 0F, 1F, 28,
                        red, green, blue);
            }
        }

        // PASS 2: requesting FLOOR_PASS now flushes SHAFT_PASS. We will never
        // use 'shaft' again after this point, so the shared builder is safe.
        VertexConsumer pools = buffers.getBuffer(FLOOR_PASS);
        for (MemberRole role : MemberRole.values()) {
            StageFormation.Mark mark = StageFormation.local(role, true);
            float x = (float) mark.x();
            float y = (float) (mark.y() + 1.0D);
            float z = (float) mark.z();
            int red = (role.themeColor >>> 16) & 255;
            int green = (role.themeColor >>> 8) & 255;
            int blue = role.themeColor & 255;
            boolean high = role == MemberRole.OBLIVIONIS || role == MemberRole.AMORIS;
            float width = high ? 2.55F : 3.7F;
            float length = high ? 1.4F : 2.3F;
            float centerZ = high ? -3.3F : z - .35F;
            float poolY = y + .016F;
            quad(pools, pose,
                    x - width, poolY, centerZ - length, 0F, 0F, 175,
                    x - width, poolY, centerZ + length, 0F, 1F, 175,
                    x + width, poolY, centerZ + length, 1F, 1F, 175,
                    x + width, poolY, centerZ - length, 1F, 0F, 175,
                    red, green, blue);
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack pose,
                             float x1, float y1, float z1, float u1, float v1, int a1,
                             float x2, float y2, float z2, float u2, float v2, int a2,
                             float x3, float y3, float z3, float u3, float v3, int a3,
                             float x4, float y4, float z4, float u4, float v4, int a4,
                             int red, int green, int blue) {
        emit(consumer, pose, x1, y1, z1, u1, v1, red, green, blue, a1);
        emit(consumer, pose, x2, y2, z2, u2, v2, red, green, blue, a2);
        emit(consumer, pose, x3, y3, z3, u3, v3, red, green, blue, a3);
        emit(consumer, pose, x4, y4, z4, u4, v4, red, green, blue, a4);
    }

    private static void emit(VertexConsumer consumer, PoseStack pose,
                             float x, float y, float z, float u, float v,
                             int red, int green, int blue, int alpha) {
        consumer.addVertex(pose.last().pose(), x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose.last(), 0F, 1F, 0F);
    }
}
