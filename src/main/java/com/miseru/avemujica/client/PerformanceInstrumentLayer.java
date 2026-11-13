package com.miseru.avemujica.client;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Renders large performance instruments around the NPC while performing, rather than
 * showing the tiny held-item layer. Also supports a short combat-emphasis pose where
 * members briefly pull out their instrument while firing note projectiles.
 */
public final class PerformanceInstrumentLayer extends RenderLayer<BandMemberEntity, BandPlayerModel> {
    private final ItemRenderer itemRenderer;
    private final ItemStack leadGuitar = new ItemStack(ModRegistry.GUITAR);
    private final ItemStack rhythmGuitar = new ItemStack(ModRegistry.RHYTHM_GUITAR);
    private final ItemStack bass = new ItemStack(ModRegistry.BASS);
    private final ItemStack[] leadFinishes = InstrumentColorItems.variants(ModRegistry.GUITAR);
    private final ItemStack[] rhythmFinishes = InstrumentColorItems.variants(ModRegistry.RHYTHM_GUITAR);
    private final ItemStack[] bassFinishes = InstrumentColorItems.variants(ModRegistry.BASS);
    private final ItemStack[] keyboardFinishes = InstrumentColorItems.variants(ModRegistry.STAGE_KEYBOARD_ITEM);
    private final ItemStack[] drumFinishes = InstrumentColorItems.variants(ModRegistry.DRUM_KIT_ITEM);
    private final ItemStack drumStick = new ItemStack(ModRegistry.DRUMSTICK);
    private final ItemStack sword = new ItemStack(Items.IRON_SWORD);
    private final ItemStack chainsaw = new ItemStack(ModRegistry.CHAINSAW);
    private final ItemStack rifle = new ItemStack(ModRegistry.RIFLE);
    private final ItemStack keyboard = new ItemStack(ModRegistry.STAGE_KEYBOARD_ITEM);
    private final ItemStack drumKit = new ItemStack(ModRegistry.DRUM_KIT_ITEM);
    private final ItemStack stool = new ItemStack(ModRegistry.STAGE_STOOL_ITEM);
    private final ItemStack phone = new ItemStack(ModRegistry.PHONE);
    private final ItemStack proteinBar = new ItemStack(ModRegistry.PROTEIN_BAR);
    private final ItemStack donut = new ItemStack(ModRegistry.DONUT);
    private final ItemStack coffee = new ItemStack(ModRegistry.COFFEE);
    private final ItemStack cucumber = new ItemStack(ModRegistry.CUCUMBER);
    private final ItemStack songBook = new ItemStack(Items.WRITABLE_BOOK);
    private final ItemStack waterBottle = new ItemStack(Items.POTION);
    private final ItemStack flower = new ItemStack(Items.DANDELION);

    public PerformanceInstrumentLayer(RenderLayerParent<BandMemberEntity, BandPlayerModel> parent, ItemRenderer itemRenderer) {
        super(parent);
        this.itemRenderer = itemRenderer;
    }

    private static final float GUITAR_BODY_X = 0.035F;
    private static final float GUITAR_BODY_Y = 0.61F;
    private static final float GUITAR_BODY_Z = -0.35F;
    private static final float GUITAR_MESH_PIVOT_X = 0.5F;
    private static final float GUITAR_MESH_PIVOT_Y = 0.5F - 6.5F / 16F;
    private static final float KEYBOARD_Y = 0.75F;
    private static final float KEYBOARD_Z = -0.82F;
    private static final float DRUM_Y = 0.84F;
    private static final float DRUM_Z = -0.95F;

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       BandMemberEntity member, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // Sakiko-down rage always shows Uika's chainsaw, including mid-performance.
        if (member.isUikaRaging()) {
            renderAlternateWeapon(poseStack, buffer, packedLight, member);
            return;
        }
        boolean combatAccent = !member.isPerforming() && member.clientNoteAge(ageInTicks) < 6.5F && member.playedVelocity() > 0;
        if (!member.isPerforming()) {
            if (member.alternateCombat() && member.personalityAction() == 0) {
                renderAlternateWeapon(poseStack, buffer, packedLight, member);
                return;
            }
            if (combatAccent) {
                renderCombatInstrument(poseStack, buffer, packedLight, member);
                return;
            }
            switch (member.personalityAction()) {
                case 1 -> {
                    if (member.instrumentType().strings()) renderHuggedGuitar(poseStack, buffer, packedLight, member);
                }
                case 2 -> renderHandProp(poseStack, buffer, packedLight, member, proteinBar,
                        -.015F, .11F, -.03F, .44F);
                case 3, 13 -> renderHandProp(poseStack, buffer, packedLight, member, phone,
                        -.015F, .12F, -.015F, .34F);
                case 5, 6 -> renderHandProp(poseStack, buffer, packedLight, member, songBook,
                        -.015F, .15F, -.035F, .65F);
                case 9 -> {
                    if (member.instrumentType().strings()) renderFrontInstrument(poseStack, buffer, packedLight, member,
                            selectedStrings(member), GUITAR_BODY_X, GUITAR_BODY_Y, GUITAR_BODY_Z, .74F, -50F);
                }
                case 11 -> renderHandProp(poseStack, buffer, packedLight, member, waterBottle,
                        -.018F, .11F, -.015F, .57F);
                case 16 -> renderHandProp(poseStack, buffer, packedLight, member, flower,
                        -.015F, .13F, -.05F, .60F);
                case 45 -> renderHandProp(poseStack, buffer, packedLight, member, donut,
                        -.015F, .12F, -.025F, .52F);
                case 20 -> renderHandProp(poseStack, buffer, packedLight, member, coffee,
                        -.02F, .08F, -.02F, .56F);
                case 21 -> renderHandProp(poseStack, buffer, packedLight, member, cucumber,
                        -.01F, .10F, -.03F, .58F);
                case 22 -> renderHandProp(poseStack, buffer, packedLight, member, cucumber,
                        -.01F, .12F, -.03F, .58F); // Mutsumi's gift
                case 23 -> renderHandProp(poseStack, buffer, packedLight, member, coffee,
                        -.02F, .08F, -.02F, .56F); // Uika
                case 26 -> renderHandProp(poseStack, buffer, packedLight, member, phone,
                        -.015F, .12F, -.015F, .38F); // Uika's lyrics on her phone
                case 27 -> renderHandProp(poseStack, buffer, packedLight, member, songBook,
                        -.015F, .15F, -.035F, .65F); // Sakiko considering lyrics
                case 36 -> { // Cup physically reaches Sakiko, then Uika lowers her hand.
                    if (member.duoPhase() < 3) renderHandProp(poseStack, buffer, packedLight, member, coffee,
                            -.02F, .08F, -.02F, .56F);
                }
                case 37 -> { // Recipient visually takes over the cup midway through the scene.
                    if (member.duoPhase() >= 2) renderHandProp(poseStack, buffer, packedLight, member, coffee,
                            -.02F, .08F, -.02F, .56F);
                }
                case 38 -> { // The flower is first in Uika's hand.
                    if (member.duoPhase() < 2) renderHandProp(poseStack, buffer, packedLight, member, flower,
                            -.015F, .13F, -.05F, .60F);
                }
                case 39 -> { // It moves to Sakiko only once she accepts it.
                    if (member.duoPhase() >= 2) renderHandProp(poseStack, buffer, packedLight, member, flower,
                            -.015F, .13F, -.05F, .60F);
                }
            }
            return;
        }

        if (member.role() == MemberRole.DOLORIS
                && (member.isSilentPerforming() || !member.isUikaGuitarActive())) return;
        renderSelectedInstrument(poseStack, buffer, packedLight, member, false);
    }

    private void renderAlternateWeapon(PoseStack poseStack, MultiBufferSource buffer, int light,
                                       BandMemberEntity member) {
        switch (member.role()) {
            case OBLIVIONIS -> renderHandWeapon(poseStack, buffer, light, member, sword,
                    .95F, -83F);
            // Both long weapons follow the animated RIGHT ARM. These coordinates
            // reference the GRIP in the custom JSON model (16 units = 1 block).
            case DOLORIS -> renderRightHandLongWeapon(poseStack, buffer, light, member,
                    chainsaw, -1.7F, 8.8F, 7.9F, .68F, -7F, -5F, .415F, -.055F);
            case TIMORIS -> renderRightHandLongWeapon(poseStack, buffer, light, member,
                    rifle, 3.6F, 8.2F, 8.0F, .68F, -2F, -3F, .275F, -.175F);
            default -> { }
        }
    }

    /** Real right-hand attachment: arm-bone transform -> palm -> item orientation
     * -> model grip. No entity-centred, floating chest anchor survives here.
     * The +X model axis points along the muzzle/chain, rotated toward the
     * character's front; arm animation drives every subsequent transform.
     */
    private void renderRightHandLongWeapon(PoseStack poseStack, MultiBufferSource buffer,
                                            int light, BandMemberEntity member,
                                            ItemStack weapon, float gripX, float gripY,
                                            float gripZ, float scale, float rollDegrees,
                                            float elevationDegrees, float handAnchorY,
                                            float handAnchorZ) {
        poseStack.pushPose();
        getParentModel().rightArm.translateAndRotate(poseStack);
        // Near the palm of the player-model right arm, not at world torso Y.
        // v1.1.109: rifle keeps the v1.1.107 lift and moves further forward
        // from Z -0.125 to -0.175 (another 0.05 block in model space).
        // ModelPart +Y points down; chainsaw stays at its original Y/Z anchor.
        poseStack.translate(-.245F, handAnchorY, handAnchorZ);
        // Follow the right palm, but cancel the arm's pitch on the LONG prop.
        // Without this, combat arm poses rotate the muzzle/blade nearly vertical.
        // The hand anchor still follows the arm's animation and translation.
        // Reverse all arm Euler rotations (Z, Y, X) while retaining the palm's
        // animated translation; otherwise the long models sweep through the head.
        poseStack.mulPose(Axis.ZP.rotation(-getParentModel().rightArm.zRot));
        poseStack.mulPose(Axis.YP.rotation(-getParentModel().rightArm.yRot));
        poseStack.mulPose(Axis.XP.rotation(-getParentModel().rightArm.xRot));
        // Both source model barrels/blades point along +X; +90 yaw maps that to forward -Z.
        poseStack.mulPose(Axis.YP.rotationDegrees(90F));
        poseStack.mulPose(Axis.XP.rotationDegrees(elevationDegrees));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rollDegrees));
        // Both authored long-weapon meshes are upside-down under the +X -> -Z
        // third-person hand transform. Roll about the LOCAL long (+X) axis,
        // not world pitch/yaw: the muzzle remains forward, with the right-hand
        // grip still pinned to the same palm point during combat animations.
        poseStack.mulPose(Axis.XP.rotationDegrees(180F));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-gripX / 16F, -gripY / 16F, -gripZ / 16F);
        renderStatic(member, weapon, poseStack, buffer, light);
        poseStack.popPose();
    }

    private void renderHandWeapon(PoseStack poseStack, MultiBufferSource buffer, int light,
                                  BandMemberEntity member, ItemStack weapon, float scale,
                                  float pitch) {
        renderHandWeapon(poseStack, buffer, light, member, weapon, scale, pitch,
                0F, .64F, -.045F);
    }

    private void renderHandWeapon(PoseStack poseStack, MultiBufferSource buffer, int light,
                                  BandMemberEntity member, ItemStack weapon, float scale,
                                  float pitch, float handX, float handY, float handZ) {
        poseStack.pushPose();
        getParentModel().rightArm.translateAndRotate(poseStack);
        poseStack.translate(handX, handY, handZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(-20F));
        poseStack.scale(scale, scale, scale);
        itemRenderer.renderStatic(member, weapon, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                false, poseStack, buffer, member.level(), light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, member.getId());
        poseStack.popPose();
    }

    private void renderCombatInstrument(PoseStack poseStack, MultiBufferSource buffer, int packedLight, BandMemberEntity member) {
        renderSelectedInstrument(poseStack, buffer, packedLight, member, true);
    }

    private void renderSelectedInstrument(PoseStack poseStack, MultiBufferSource buffer, int light,
                                          BandMemberEntity member, boolean combat) {
        if (!member.usesPerformanceInstrument()) return;
        switch (member.instrumentType()) {
            case GUITAR -> renderFrontInstrument(poseStack, buffer, light, member,
                    leadGuitar, GUITAR_BODY_X, GUITAR_BODY_Y, GUITAR_BODY_Z, combat ? .80F : .82F, -49F);
            case ELECTRIC -> renderFrontInstrument(poseStack, buffer, light, member,
                    rhythmGuitar, GUITAR_BODY_X, GUITAR_BODY_Y, GUITAR_BODY_Z, combat ? .80F : .82F, -51F);
            case BASS -> renderFrontInstrument(poseStack, buffer, light, member,
                    bass, GUITAR_BODY_X, GUITAR_BODY_Y + (combat ? .01F : .02F), GUITAR_BODY_Z,
                    combat ? .78F : .79F, -45F);
            case KEYBOARD -> {
                if (combat) renderBattleKeyboard(poseStack, buffer, light, member);
                else if (member.isKeyboardLocked()) renderKeyboard(poseStack, buffer, light, member);
            }
            case DRUMS -> renderDrumPerformance(poseStack, buffer, light, member);
        }
    }

    private ItemStack selectedStrings(BandMemberEntity member) {
        return switch (member.instrumentType()) {
            case GUITAR -> leadGuitar;
            case BASS -> bass;
            default -> rhythmGuitar;
        };
    }

    private void renderFrontInstrument(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                       BandMemberEntity member, ItemStack stack,
                                       float x, float y, float z, float scale, float neckAngle) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(180F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(neckAngle));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(GUITAR_MESH_PIVOT_X, GUITAR_MESH_PIVOT_Y, 0F);
        renderStatic(member, coloredStrings(stack, member), poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private ItemStack coloredStrings(ItemStack stack, BandMemberEntity member) {
        return InstrumentColorItems.at(stack == leadGuitar ? leadFinishes
                : stack == bass ? bassFinishes : rhythmFinishes, member.instrumentColor());
    }

    private void renderKeyboard(PoseStack poseStack, MultiBufferSource buffer, int packedLight, BandMemberEntity member) {
        poseStack.pushPose();
        poseStack.translate(0.0F, KEYBOARD_Y, KEYBOARD_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(180F));
        poseStack.scale(1.45F, 1.45F, 1.45F);
        renderStatic(member, InstrumentColorItems.at(keyboardFinishes, member.instrumentColor()), poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private void renderBattleKeyboard(PoseStack poseStack, MultiBufferSource buffer, int packedLight, BandMemberEntity member) {
        poseStack.pushPose();
        poseStack.translate(0.0F, .86F, -.58F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180F));
        poseStack.scale(1.05F, 1.05F, 1.05F);
        renderStatic(member, InstrumentColorItems.at(keyboardFinishes, member.instrumentColor()), poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private void renderDrumPerformance(PoseStack poseStack, MultiBufferSource buffer, int packedLight, BandMemberEntity member) {
        // Drum kit and stool must appear together. During navigation Nyamu
        // holds both sticks but cannot squat on an invisible off-stage chair.
        if (member.isDrumSeated()) {
            renderDrumStool(poseStack, buffer, packedLight, member);
            poseStack.pushPose();
            poseStack.translate(0.0F, DRUM_Y, DRUM_Z);
            poseStack.mulPose(Axis.XP.rotationDegrees(180F));
            poseStack.scale(1.58F, 1.58F, 1.58F);
            renderStatic(member, InstrumentColorItems.at(drumFinishes, member.instrumentColor()), poseStack, buffer, packedLight);
            poseStack.popPose();
        }
        renderDrumstickInHand(poseStack, buffer, packedLight, member, true);
        renderDrumstickInHand(poseStack, buffer, packedLight, member, false);
    }

    private void renderDrumStool(PoseStack poseStack, MultiBufferSource buffer, int packedLight, BandMemberEntity member) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 1.28F, -.10F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180F));
        poseStack.scale(.78F, .78F, .78F);
        renderStatic(member, stool, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    /** A drumstick extends 18px along its +Y axis after JSON item centering.
     * Its GRIP center is Y=4px, so translate by 0.5-4/16 AFTER scaling/rotation;
     * the tip is Y=19px. Point toward the kit, not sideways into the air. */
    private void renderDrumstickInHand(PoseStack poseStack, MultiBufferSource buffer, int light,
                                       BandMemberEntity member, boolean right) {
        poseStack.pushPose();
        if (right) getParentModel().rightArm.translateAndRotate(poseStack);
        else getParentModel().leftArm.translateAndRotate(poseStack);
        // Model arm pivots at shoulder. Move to lower palm before orienting stick.
        poseStack.translate(0F, .63F, -.025F);
        // v1.0.9: old X=+42 degrees pointed the textured tips backwards/down.
        // Raise BOTH sticks 100 degrees in local arm space; leave the item/GUI model intact.
        poseStack.mulPose(Axis.XP.rotationDegrees(-58F));
        poseStack.mulPose(Axis.YP.rotationDegrees(right ? -6F : 6F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(right ? -14F : 14F));
        poseStack.scale(.63F, .63F, .63F);
        poseStack.translate(0F, 0.5F - 4F / 16F, 0F);
        renderStatic(member, drumStick, poseStack, buffer, light);
        poseStack.popPose();
    }

    private void renderHuggedGuitar(PoseStack poseStack, MultiBufferSource buffer,
                                    int packedLight, BandMemberEntity member) {
        poseStack.pushPose();
        poseStack.translate(.03D, .81D, -.29D);
        poseStack.mulPose(Axis.XP.rotationDegrees(180F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-62F));
        poseStack.scale(.67F, .67F, .67F);
        poseStack.translate(GUITAR_MESH_PIVOT_X, GUITAR_MESH_PIVOT_Y, 0F);
        renderStatic(member, coloredStrings(selectedStrings(member), member), poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private void renderHandProp(PoseStack poseStack, MultiBufferSource buffer,
                                int packedLight, BandMemberEntity member, ItemStack stack,
                                float dx, float dy, float dz, float size) {
        poseStack.pushPose();
        getParentModel().rightArm.translateAndRotate(poseStack);
        poseStack.translate(dx, .48D + dy, dz);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180F));
        poseStack.scale(size, size, size);
        itemRenderer.renderStatic(member, stack, ItemDisplayContext.FIXED, false, poseStack,
                buffer, member.level(), packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, member.getId());
        poseStack.popPose();
    }

    private void renderStatic(BandMemberEntity member, ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        itemRenderer.renderStatic(member, stack, ItemDisplayContext.NONE, false, poseStack, buffer, member.level(), packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, member.getId());
    }
}
