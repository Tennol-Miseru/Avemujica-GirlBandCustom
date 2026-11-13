package com.miseru.avemujica.client;

import com.miseru.avemujica.AveMujicaMod;
import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import java.util.EnumMap;

/**
 * All five NPCs now use the vanilla 64x64 classic-player skin layout.
 * No custom geometry or animation engine is required for character rendering.
 * The model remains a MobRenderer so saved band-member entities, equipment,
 * hit boxes and behavior are unchanged.
 */
public final class BandMemberRenderer extends MobRenderer<BandMemberEntity, BandPlayerModel> {
    private static final EnumMap<MemberRole, ResourceLocation> SKINS = new EnumMap<>(MemberRole.class);

    static {
        for (MemberRole role : MemberRole.values()) {
            SKINS.put(role, ResourceLocation.fromNamespaceAndPath(AveMujicaMod.MODID,
                    "textures/entity/skins/" + role.id + ".png"));
        }
    }

    public BandMemberRenderer(EntityRendererProvider.Context context) {
        // Alex (3-pixel) slim arms to match the user-supplied 64x64 skins.
        super(context, new BandPlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.36F);
        addLayer(new PerformanceInstrumentLayer(this, context.getItemRenderer()));
    }

    @Override
    public void render(BandMemberEntity member, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (member.personalityAction() == 1 && !member.isPerforming() && member.instrumentType().strings()) {
            poseStack.pushPose();
            poseStack.translate(0D, -.33D, 0D);
            super.render(member, yaw, partialTick, poseStack, buffers, light);
            poseStack.popPose();
        } else {
            super.render(member, yaw, partialTick, poseStack, buffers, light);
        }
    }

    @Override
    protected boolean shouldShowName(BandMemberEntity member) {
        // Vanilla may show a custom name when the cursor points at an NPC even
        // if CustomNameVisible is false. The octopus switch must hide it fully.
        return member.isCustomNameVisible() && super.shouldShowName(member);
    }

    @Override
    public ResourceLocation getTextureLocation(BandMemberEntity entity) {
        return SKINS.get(entity.role());
    }
}
