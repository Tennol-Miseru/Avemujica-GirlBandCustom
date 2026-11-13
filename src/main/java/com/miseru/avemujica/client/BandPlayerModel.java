package com.miseru.avemujica.client;

import com.miseru.avemujica.entity.BandMemberEntity;
import com.miseru.avemujica.entity.MemberRole;
import com.miseru.avemujica.entity.MemberInstrument;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Native Alex/slim PlayerModel with five distinct musician rigs.
 * Smooth idle motion + short note-on impacts, with real MIDI dynamics and
 * GM percussion pitch controlling the drummer's alternating stick strikes.
 * No GeckoLib, custom skeletal model, client packets or background timers.
 */
public final class BandPlayerModel extends PlayerModel<BandMemberEntity> {
    public BandPlayerModel(ModelPart root) {
        super(root, true);  // All five supplied skins use the Alex/slim 3-pixel arms.
    }

    private boolean setupDailyPose(BandMemberEntity member, float time) {
        float bob = Mth.sin(time * .25F) * .06F;
        switch (member.personalityAction()) {
            case 1 -> { // Mutsumi: sitting on a stair/chair with her guitar hugged close.
                if (!member.instrumentType().strings()) return false;
                rightLeg.xRot = -1.30F;
                leftLeg.xRot = -1.30F;
                rightLeg.yRot = -.06F;
                leftLeg.yRot = .06F;
                rightArm.xRot = -.84F;
                leftArm.xRot = -.88F;
                rightArm.zRot = .34F;
                leftArm.zRot = -.32F;
                head.xRot = .22F;
                body.xRot = .08F;
            }
            case 2 -> { // Umiri: a discreet bite of an energy/protein bar.
                rightArm.xRot = -1.60F + bob;
                rightArm.yRot = -.24F;
                leftArm.xRot = -.10F;
                head.xRot = -.08F;
            }
            case 3 -> { // Nyamu: raise the phone and take photos with both hands.
                rightArm.xRot = -1.40F + bob;
                leftArm.xRot = -1.38F - bob * .5F;
                rightArm.yRot = -.32F;
                leftArm.yRot = .32F;
                rightArm.zRot = -.12F;
                leftArm.zRot = .12F;
                head.xRot = -.07F;
            }
            case 4 -> { // Uika: a delighted greeting when Sakiko is close.
                rightArm.xRot = -.48F + bob;
                rightArm.zRot = .22F;
                head.xRot = -.08F;
            }
            case 5 -> { // Sakiko: contemplative, reserved bandleader gesture.
                rightArm.xRot = -.80F;
                leftArm.xRot = -.25F;
                rightArm.yRot = -.35F;
                head.xRot = .20F;
            }
            case 6 -> { // Sakiko: open the music booklet, carefully scan it.
                leftArm.xRot = -1.18F + bob * .25F;
                rightArm.xRot = -1.25F - bob * .25F;
                leftArm.zRot = -.19F;
                rightArm.zRot = .20F;
                head.xRot = .23F;
                head.yRot = Mth.sin(time * .09F) * .08F;
            }
            case 8 -> { // Mutsumi: crouch and look closely at a flower without picking it.
                head.xRot = .36F;
                body.xRot = .18F;
                leftArm.xRot = -.18F;
                rightArm.xRot = -.71F + bob * .35F;
                rightArm.zRot = .12F;
                leftLeg.xRot = .30F;
                rightLeg.xRot = .28F;
            }
            case 9 -> { // Mutsumi: quiet guitar tuning, left fingers on the neck.
                if (!member.instrumentType().strings()) return false;
                leftArm.xRot = -1.11F;
                leftArm.yRot = .19F;
                leftArm.zRot = -.38F;
                rightArm.xRot = -.75F + Mth.sin(time * .35F) * .13F;
                rightArm.zRot = .10F;
                head.xRot = .21F;
            }
            case 10 -> { // Umiri: warm up shoulders with a deliberate stretch.
                rightArm.xRot = -.35F + bob;
                leftArm.xRot = -.31F - bob;
                rightArm.zRot = 1.01F;
                leftArm.zRot = -1.02F;
                head.xRot = -.09F;
                body.xRot = -.03F;
            }
            case 11 -> { // Umiri: pause to drink water.
                rightArm.xRot = -1.66F + bob * .30F;
                rightArm.yRot = -.25F;
                rightArm.zRot = -.10F;
                leftArm.xRot = -.16F;
                head.xRot = -.15F;
            }
            case 12 -> { // Nyamu: fix bangs, check her presentation.
                rightArm.xRot = -1.95F + Mth.sin(time * .18F) * .11F;
                rightArm.yRot = -.16F;
                rightArm.zRot = -.24F;
                leftArm.xRot = -.40F;
                head.zRot = .045F;
            }
            case 13 -> { // Nyamu: organize a group selfie, both hands around phone.
                rightArm.xRot = -1.42F + bob;
                leftArm.xRot = -1.31F - bob;
                rightArm.yRot = -.36F;
                leftArm.yRot = .35F;
                rightArm.zRot = -.12F;
                leftArm.zRot = .12F;
                head.xRot = -.12F;
            }
            case 14 -> { // The invited member responds with a different pose.
                switch (member.role()) {
                    case OBLIVIONIS -> {
                        leftArm.xRot = -.52F;
                        rightArm.xRot = -.27F;
                        head.yRot = -.14F;
                        head.xRot = .06F;
                    }
                    case MORTIS -> {
                        rightArm.xRot = -.22F;
                        head.xRot = .15F;
                    }
                    case TIMORIS -> {
                        rightArm.xRot = -.25F;
                        leftArm.xRot = -.16F;
                        head.yRot = .11F;
                    }
                    case DOLORIS -> {
                        rightArm.xRot = -1.04F;
                        rightArm.zRot = .37F;
                        head.xRot = -.13F;
                    }
                    case AMORIS -> {
                        rightArm.xRot = -.58F;
                        head.zRot = -.09F;
                    }
                }
            }
            case 15 -> { // Uika: quiet vocal warmup, hand to her chest.
                rightArm.xRot = -.56F + Mth.sin(time * .16F) * .12F;
                leftArm.xRot = -.93F;
                leftArm.yRot = .31F;
                leftArm.zRot = -.22F;
                head.xRot = -.17F;
                head.yRot = Mth.sin(time * .12F) * .07F;
            }
            case 16 -> { // Uika: timidly offer a small flower to Sakiko.
                rightArm.xRot = -1.28F;
                rightArm.yRot = -.21F;
                rightArm.zRot = .18F;
                leftArm.xRot = -.62F;
                head.xRot = -.08F;
                body.xRot = -.02F;
            }
            case 17 -> { // Sakiko: visibly surprised, then tries to remain composed.
                rightArm.xRot = -.66F;
                leftArm.xRot = -.30F;
                head.xRot = -.13F;
                head.yRot = -.16F;
                rightArm.yRot = -.25F;
            }
            case 18 -> { // Short rain reaction: shelter eyes from droplets.
                rightArm.xRot = -2.11F + bob * .50F;
                rightArm.zRot = .22F;
                head.xRot = -.22F;
                leftArm.xRot = -.20F;
            }
            case 19 -> { // Umiri: check on an injured bandmate, cool but attentive.
                leftArm.xRot = -.42F;
                rightArm.xRot = -.37F;
                rightArm.yRot = -.20F;
                head.xRot = .18F;
                head.yRot = Mth.sin(time * .10F) * .11F;
            }
            case 20 -> { // Sakiko: sip coffee calmly from a cup.
                rightArm.xRot = -1.47F + bob * .24F;
                rightArm.yRot = -.16F;
                leftArm.xRot = -.14F;
                head.xRot = -.08F;
            }
            case 21 -> { // Mutsumi: small cucumber bite, understated posture.
                rightArm.xRot = -1.54F + bob * .30F;
                rightArm.yRot = -.23F;
                leftArm.xRot = -.19F;
                head.xRot = .10F;
            }
            case 22 -> { // Mutsumi: hold out a cucumber toward a friend.
                rightArm.xRot = -1.05F + bob * .20F;
                rightArm.yRot = -.53F;
                rightArm.zRot = -.14F;
                leftArm.xRot = -.17F;
                head.xRot = -.06F;
            }
            case 23 -> { // Uika: relax and sip from a coffee cup.
                rightArm.xRot = -1.46F + bob * .24F;
                rightArm.yRot = -.17F;
                leftArm.xRot = -.14F;
                head.xRot = -.09F;
            }
            case 24 -> { // Teammate: briefly accept Mutsumi's cucumber gift.
                rightArm.xRot = -.81F + bob * .20F;
                rightArm.yRot = -.28F;
                leftArm.xRot = -.40F;
                head.xRot = .09F;
            }
            case 25 -> { // Uika: quietly lingering beside Sakiko, admiring her music.
                rightArm.xRot = -.38F + bob * .20F;
                leftArm.xRot = -.32F;
                rightArm.zRot = .19F;
                head.xRot = -.11F;
                head.zRot = Mth.sin(time * .11F) * .06F;
                body.xRot = .035F;
            }
            case 26 -> { // Uika: shows the lyrics she wrote on her phone.
                rightArm.xRot = -1.14F + bob * .18F;
                leftArm.xRot = -.77F;
                rightArm.yRot = -.24F;
                leftArm.yRot = .24F;
                head.xRot = .17F;
            }
            case 27 -> { // Sakiko: attentively examines Uika's lyric idea.
                rightArm.xRot = -.88F;
                leftArm.xRot = -1.09F;
                leftArm.yRot = .12F;
                head.xRot = .22F;
                body.xRot = .09F;
            }
            case 28 -> { // Uika: relieved to find injured Sakiko still standing.
                rightArm.xRot = -.87F + bob * .25F;
                leftArm.xRot = -.63F;
                rightArm.zRot = .22F;
                leftArm.zRot = -.10F;
                head.xRot = .13F;
                body.xRot = .10F;
            }
            case 29 -> { // Sakiko: a restrained reassuring gesture.
                rightArm.xRot = -.41F;
                leftArm.xRot = -.23F;
                head.xRot = -.10F;
                head.yRot = -.12F;
            }
            case 30 -> { // Uika: take Sakiko's hand, hold it, then shyly release.
                int phase = member.duoPhase();
                rightArm.xRot = phase == 0 ? -.76F : phase == 3 ? -.48F : -1.17F;
                rightArm.zRot = phase == 0 ? .19F : .55F;
                rightArm.yRot = -.38F;
                leftArm.xRot = phase == 2 ? -.64F : -.20F;
                head.xRot = phase == 2 ? -.18F : -.07F;
                head.zRot = phase == 2 ? -.11F : -.03F;
                body.xRot = phase == 2 ? .11F : .02F;
            }
            case 31 -> { // Sakiko: hesitates before reciprocating the offered hand.
                int phase = member.duoPhase();
                leftArm.xRot = phase <= 1 ? -.22F : phase == 2 ? -1.13F : -.42F;
                leftArm.zRot = phase == 2 ? -.54F : -.12F;
                leftArm.yRot = .28F;
                head.xRot = phase == 1 ? -.14F : phase == 2 ? .19F : .04F;
                rightArm.xRot = -.24F;
                body.yRot = phase == 2 ? -.06F : 0F;
            }
            case 32 -> { // Uika: unmistakable shoulder lean, briefly clinging to Sakiko.
                int phase = member.duoPhase();
                rightArm.xRot = phase == 0 ? -.59F : -1.02F;
                rightArm.zRot = phase == 0 ? .12F : .62F;
                leftArm.xRot = phase >= 1 && phase < 3 ? -.92F : -.16F;
                leftArm.zRot = phase == 2 ? -.46F : -.18F;
                head.zRot = phase == 2 ? -.14F : -.05F;
                head.xRot = phase == 2 ? -.18F : -.04F;
                body.zRot = phase == 2 ? -.10F : -.02F;
            }
            case 33 -> { // Sakiko: initially surprised, then lightly steadies Uika.
                int phase = member.duoPhase();
                rightArm.xRot = phase == 1 ? -.34F : phase == 2 ? -.83F : -.14F;
                rightArm.zRot = phase == 2 ? .42F : .10F;
                leftArm.xRot = -.26F;
                head.xRot = phase == 1 ? -.21F : .12F;
                head.yRot = phase == 1 ? .21F : -.06F;
            }
            case 34 -> { // Uika: a quick, visible two-arm embrace.
                int phase = member.duoPhase();
                rightArm.xRot = phase == 0 ? -.65F : phase == 3 ? -.48F : -1.23F;
                leftArm.xRot = phase == 0 ? -.66F : phase == 3 ? -.43F : -1.23F;
                rightArm.zRot = phase == 2 ? .66F : .25F;
                leftArm.zRot = phase == 2 ? -.66F : -.25F;
                head.xRot = phase == 2 ? -.17F : -.04F;
                head.zRot = phase == 2 ? -.09F : 0F;
                body.xRot = phase == 2 ? .16F : .04F;
            }
            case 35 -> { // Sakiko: catches the surprise hug, then responds gently.
                int phase = member.duoPhase();
                rightArm.xRot = phase == 1 ? -.70F : phase == 2 ? -.97F : -.18F;
                leftArm.xRot = phase == 1 ? -.38F : phase == 2 ? -.91F : -.20F;
                rightArm.zRot = phase == 2 ? .45F : .10F;
                leftArm.zRot = phase == 2 ? -.45F : -.10F;
                head.xRot = phase == 1 ? -.20F : phase == 2 ? .15F : 0F;
                body.xRot = phase == 2 ? .08F : 0F;
            }
            case 36 -> { // Uika: offers a cup; watches Sakiko accept it.
                int phase = member.duoPhase();
                rightArm.xRot = phase == 0 ? -.68F : phase == 2 ? -1.16F : -.93F;
                rightArm.yRot = -.26F;
                rightArm.zRot = .24F;
                leftArm.xRot = phase == 2 ? -.62F : -.15F;
                head.xRot = -.08F;
            }
            case 37 -> { // Sakiko: takes the coffee, then raises it for a sip.
                int phase = member.duoPhase();
                leftArm.xRot = phase < 1 ? -.18F : phase == 1 ? -.94F : -1.38F;
                leftArm.yRot = .24F;
                rightArm.xRot = phase == 2 ? -.45F : -.12F;
                head.xRot = phase == 2 ? -.10F : .08F;
            }
            case 38 -> { // Uika shyly offers a flower, then watches Sakiko accept it.
                int phase = member.duoPhase();
                rightArm.xRot = phase == 0 ? -.70F : phase == 3 ? -.53F : -1.16F;
                rightArm.yRot = -.27F;
                rightArm.zRot = phase == 2 ? .42F : .19F;
                leftArm.xRot = phase == 2 ? -.47F : -.14F;
                head.xRot = -.15F;
                head.zRot = phase == 2 ? -.09F : 0F;
            }
            case 39 -> { // Sakiko accepts the flower instead of posing alone.
                int phase = member.duoPhase();
                leftArm.xRot = phase == 0 ? -.16F : phase == 1 ? -.72F : -1.09F;
                leftArm.zRot = phase == 2 ? -.41F : -.10F;
                rightArm.xRot = -.25F;
                head.xRot = phase == 1 ? -.19F : phase == 2 ? .18F : .03F;
                head.yRot = phase == 2 ? -.08F : .15F;
            }
            case 40 -> { // Sakiko: refined stage-leader pose, calm and theatrical.
                rightArm.xRot = -.62F;
                rightArm.yRot = -.18F;
                rightArm.zRot = .22F;
                leftArm.xRot = -.18F;
                leftArm.yRot = .10F;
                leftArm.zRot = -.12F;
                head.xRot = -.06F;
                head.yRot = Mth.sin(time * .06F) * .08F;
                body.yRot = -.06F;
                body.xRot = .02F;
            }
            case 41 -> { // Uika: gentle lyrical pose, as if softly addressing the audience.
                rightArm.xRot = -.86F + bob * .40F;
                rightArm.yRot = -.28F;
                rightArm.zRot = .28F;
                leftArm.xRot = -.42F;
                leftArm.yRot = .18F;
                leftArm.zRot = -.20F;
                head.xRot = -.10F;
                head.yRot = Mth.sin(time * .10F) * .10F;
                body.xRot = .04F;
                body.yRot = .05F;
            }
            case 42 -> { // Mutsumi: quiet composed pose, hands folded with a shy tilt.
                rightArm.xRot = -.54F;
                leftArm.xRot = -.50F;
                rightArm.yRot = -.24F;
                leftArm.yRot = .24F;
                rightArm.zRot = .10F;
                leftArm.zRot = -.10F;
                head.xRot = .05F;
                head.yRot = -.09F;
                head.zRot = -.06F;
                body.xRot = .03F;
            }
            case 43 -> { // Umiri: cool, balanced equipment-check pose.
                rightArm.xRot = -.34F;
                rightArm.yRot = -.10F;
                rightArm.zRot = .38F;
                leftArm.xRot = -.58F + bob * .25F;
                leftArm.yRot = .30F;
                leftArm.zRot = -.24F;
                head.xRot = -.04F;
                head.yRot = .08F;
                body.yRot = .04F;
            }
            case 44 -> { // Nyamu: playful idol flourish, suitable for photos.
                rightArm.xRot = -1.08F + bob * .35F;
                rightArm.yRot = -.34F;
                rightArm.zRot = -.22F;
                leftArm.xRot = -.30F;
                leftArm.yRot = .22F;
                leftArm.zRot = -.34F;
                head.xRot = -.08F;
                head.yRot = .12F;
                head.zRot = .10F;
                body.xRot = -.02F;
                body.yRot = .05F;
            }
            case 45 -> { // Nyamu: cheerful donut nibble.
                rightArm.xRot = -1.48F + bob;
                rightArm.yRot = -.24F;
                rightArm.zRot = -.14F;
                leftArm.xRot = -.30F;
                head.xRot = -.10F;
                head.zRot = .08F;
            }
            default -> { return false; }
        }
        head.xRot = Mth.clamp(head.xRot, -.35F, .42F);
        head.yRot = Mth.clamp(head.yRot, -.30F, .30F);
        head.zRot = Mth.clamp(head.zRot, -.14F, .14F);
        body.xRot = Mth.clamp(body.xRot, -.12F, .24F);
        body.yRot = Mth.clamp(body.yRot, -.09F, .09F);
        body.zRot = 0F;
        return true;
    }

    private void copyLayers() {
        hat.copyFrom(head);
        jacket.copyFrom(body);
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
        rightPants.copyFrom(rightLeg);
        leftPants.copyFrom(leftLeg);
    }

    private void applyNeutralIdle(float time) {
        head.xRot *= 0.18F;
        head.yRot *= 0.55F;
        head.zRot = 0F;
        body.xRot = body.yRot = body.zRot = 0F;
        float breathe = Mth.sin(time * .08F) * .025F;
        rightArm.xRot = breathe;
        leftArm.xRot = -breathe;
        rightArm.yRot = leftArm.yRot = 0F;
        rightArm.zRot = leftArm.zRot = 0F;
        rightLeg.xRot *= 0.08F;
        leftLeg.xRot *= 0.08F;
        rightLeg.yRot = leftLeg.yRot = 0F;
    }

    private void applyCombatPose(BandMemberEntity member, float time) {
        float noteAge = member.clientNoteAge(time);
        float impact = Mth.clamp(1F - noteAge / 6.0F, 0F, 1F)
                * (0.50F + member.playedVelocity() / 127F * 0.50F);
        head.xRot = -0.06F;
        head.zRot = 0F;
        body.xRot = 0.03F;
        body.yRot = body.zRot = 0F;
        switch (member.instrumentType().pose) {
            case OBLIVIONIS -> {
                rightArm.xRot = -0.95F - impact * .22F;
                leftArm.xRot = -0.95F - impact * .18F;
                rightArm.yRot = -.16F;
                leftArm.yRot = .16F;
                rightArm.zRot = .16F;
                leftArm.zRot = -.16F;
            }
            case DOLORIS -> {
                leftArm.xRot = -0.98F;
                leftArm.yRot = .24F;
                leftArm.zRot = -.32F;
                rightArm.xRot = -0.42F - impact * .82F;
                rightArm.yRot = -.10F;
                rightArm.zRot = .14F;
                head.xRot = -.10F;
            }
            case MORTIS -> {
                leftArm.xRot = -1.10F + impact * .10F;
                leftArm.yRot = .22F;
                leftArm.zRot = -.34F;
                rightArm.xRot = -0.50F - impact * .88F;
                rightArm.yRot = -.08F;
                rightArm.zRot = .10F;
                head.xRot = .12F;
            }
            case TIMORIS -> {
                leftArm.xRot = -0.98F;
                leftArm.yRot = .20F;
                leftArm.zRot = -.26F;
                rightArm.xRot = -0.56F - impact * .56F;
                rightArm.yRot = -.06F;
                rightArm.zRot = .10F;
                head.xRot = 0.05F;
            }
            case AMORIS -> {
                rightArm.xRot = -1.06F + impact * .78F;
                leftArm.xRot = -1.06F + impact * .58F;
                rightArm.yRot = -.10F;
                leftArm.yRot = .10F;
                rightArm.zRot = .10F;
                leftArm.zRot = -.10F;
                rightLeg.xRot = -0.08F;
                leftLeg.xRot = -0.08F;
            }
        }
    }
    private void setupAlternateCombatPose(BandMemberEntity member, float age, boolean moving) {
        float hit = Mth.clamp(1F - member.clientAttackAge(age) / 9F, 0F, 1F);
        body.xRot = body.yRot = body.zRot = 0F;
        head.zRot = 0F;
        switch (member.role()) {
            case OBLIVIONIS -> {
                rightArm.xRot = -.42F - hit * 1.18F;
                rightArm.yRot = -.12F;
                rightArm.zRot = .16F;
                leftArm.xRot = -.20F;
            }
            case DOLORIS -> {
                rightArm.xRot = -.95F + hit * .68F;
                rightArm.yRot = -.35F;
                rightArm.zRot = .12F;
                leftArm.xRot = -.50F - hit * .13F;
                leftArm.yRot = .20F;
            }
            case TIMORIS -> {
                rightArm.xRot = -1.24F + hit * .26F;
                leftArm.xRot = -1.12F + hit * .12F;
                rightArm.yRot = -.18F;
                leftArm.yRot = .24F;
                rightArm.zRot = .10F;
                leftArm.zRot = -.10F;
                head.xRot = -.06F;
            }
            default -> { }
        }
        if (!moving) { rightLeg.xRot *= .08F; leftLeg.xRot *= .08F; }
    }

    @Override
    public void setupAnim(BandMemberEntity member, float limbSwing, float stride,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(member, limbSwing, stride, ageInTicks, netHeadYaw, headPitch);
        // Prevent seated bone offsets carrying into the next walking/idle frame.
        head.y = 0F;
        body.y = 0F;
        rightArm.y = leftArm.y = 2F;
        rightLeg.y = leftLeg.y = 12F;
        // PlayerModel.setupAnim does not guarantee clearing custom torso roll.
        // v0.8.1 accumulated body.zRot every render frame until NPCs flipped 180/360 degrees.
        body.xRot = 0F;
        body.yRot = 0F;
        body.zRot = 0F;
        // Super is authoritative for locomotion.  Never suppress vanilla legs/arm swing
        // while the NPC is walking: the former idle pose scaled leg motion to 8%,
        // causing all five musicians to glide without stepping.
        boolean walking = stride > .035F || member.getDeltaMovement().horizontalDistanceSqr() > .0006D;
        if (member.isUikaRaging()) {
            setupAlternateCombatPose(member, ageInTicks, walking);
            copyLayers();
            return;
        }
        if (!member.isPerforming()) {
            if (member.alternateCombat()) {
                setupAlternateCombatPose(member, ageInTicks, walking);
                copyLayers();
                return;
            }
            boolean attacking = member.clientNoteAge(ageInTicks) < 6.5F && member.playedVelocity() > 0;
            if (attacking) {
                applyCombatPose(member, ageInTicks);
            } else if (walking) {
                // Retain the vanilla locomotion from super.setupAnim, including the legs.
                // Special social poses wait until the character comes to a stop.
                body.xRot = body.yRot = body.zRot = 0F;
                head.zRot = 0F;
            } else if (!setupDailyPose(member, ageInTicks)) {
                applyNeutralIdle(ageInTicks);
            }
            copyLayers();
            return;
        }

        if (member.isSilentPerforming()) {
            // Hold the stage formation and instruments, but no song-locked arm,
            // head, leg or flourish movement. A stationary drummer remains seated.
            rightArm.xRot = member.instrumentType() == MemberInstrument.DRUMS ? -0.82F : -0.35F;
            leftArm.xRot = member.instrumentType() == MemberInstrument.DRUMS ? -0.82F : -0.35F;
            rightArm.yRot = leftArm.yRot = 0F;
            rightArm.zRot = .08F;
            leftArm.zRot = -.08F;
            head.xRot = head.yRot = head.zRot = 0F;
            body.xRot = body.yRot = body.zRot = 0F;
            if (!walking) {
                rightLeg.xRot = leftLeg.xRot = 0F;
                if (member.isDrumSeated()) {
                    body.y += 5F; head.y += 5F; rightArm.y += 5F; leftArm.y += 5F;
                    rightLeg.y += 5F; leftLeg.y += 5F;
                    rightLeg.xRot = leftLeg.xRot = -1.34F;
                    rightLeg.zRot = -.05F; leftLeg.zRot = .05F;
                }
            }
            copyLayers();
            return;
        }
        float time = ageInTicks;
        float noteAge = member.clientNoteAge(time);
        // Short impulse: natural decay to a relaxed pose in approximately 7 ticks.
        float impact = Mth.clamp(1F - noteAge / 7.0F, 0F, 1F)
                * (0.45F + member.playedVelocity() / 127F * 0.55F);
        float groove = member.isMidiPerforming() ? 0.35F : 1F;
        float sway = Mth.sin(time * .105F + member.role().ordinal()) * .035F;
        float flourishAge = member.clientFlourishAge(time);
        // Positive-only smooth envelope; never modifies the NPC or instrument's world yaw.
        float flourish = member.flourishKind() == 0 ? 0F
                : Mth.sin((Mth.clamp(flourishAge, 0F, 26F) / 26F) * Mth.PI)
                  * (flourishAge < 26F ? 1F : 0F);
        boolean flourishAlt = member.flourishKind() == 2;
        int beatNumber = member.playedSequence();
        boolean rightAccent = (beatNumber & 1) == 0;
        boolean uika = member.role() == MemberRole.DOLORIS;
        MemberRole role = uika && !member.isUikaGuitarActive()
                ? MemberRole.DOLORIS : member.instrumentType().pose;

        // Stage performers do not march in place. Their torso/feet keep a
        // composed stance while separate arm poses express their instruments.
        if (!walking) {
            rightLeg.xRot *= 0.15F;
            leftLeg.xRot *= 0.15F;
        }
        rightLeg.yRot = 0F;
        leftLeg.yRot = 0F;
        head.yRot *= 0.55F;
        head.xRot *= 0.5F;
        rightArm.yRot = leftArm.yRot = 0F;
        rightArm.zRot = leftArm.zRot = 0F;
        body.yRot = 0F; // do not turn the torso or the independently anchored instrument
        body.xRot = 0F;
        head.zRot = 0F;

        switch (role) {
            case OBLIVIONIS -> { // Sakiko: upright keyboard technique, alternating hands.
                float eighth = Mth.sin(time * .48F) * .05F * groove;
                rightArm.xRot = -1.13F + eighth + (rightAccent ? impact * .40F : 0F);
                leftArm.xRot = -1.13F - eighth + (!rightAccent ? impact * .40F : 0F);
                rightArm.zRot = .20F;
                leftArm.zRot = -.20F;
                rightArm.yRot = -.14F;
                leftArm.yRot = .14F;
                head.xRot = .14F + Mth.sin(time * .15F) * .035F;
                body.xRot = .065F;
                // Sakiko: run over the keys, or a brief expressive conductor-like lift.
                if (flourishAlt) {
                    rightArm.xRot += flourish * .20F;
                    head.xRot -= flourish * .13F;
                } else {
                    leftArm.xRot -= flourish * .38F;
                    rightArm.xRot -= flourish * .35F;
                }
            }
            case DOLORIS -> {
                float vocal = uika ? Mth.clamp(1F - member.clientVocalAge(time) / 11F, 0F, 1F) : 0F;
                if (!uika || member.isUikaGuitarActive()) {
                    // Picking and fretting only while her dedicated MIDI guitar track sounds.
                    leftArm.xRot = -.96F + Mth.sin(time * .12F) * .04F;
                    leftArm.yRot = .21F;
                    leftArm.zRot = -.39F;
                    rightArm.xRot = -.50F - impact * .85F + Mth.sin(time * .43F) * .11F * groove;
                    rightArm.yRot = -.09F;
                    rightArm.zRot = .15F;
                    head.xRot = -.09F - vocal * .16F;
                    head.zRot = Mth.sin(time * .13F) * .055F;
                    body.xRot = -.02F;
                    head.xRot -= flourish * (flourishAlt ? .23F : .12F);
                    leftArm.zRot -= flourish * .16F;
                    rightArm.xRot -= flourish * .36F;
                } else {
                    // Harp/Voice solo: relaxed arms, quiet head movement and note particles.
                    // No guitar neck grip, strum or instrument prop.
                    leftArm.xRot = -.08F;
                    rightArm.xRot = -.08F;
                    leftArm.zRot = -.04F;
                    rightArm.zRot = .04F;
                    head.xRot = -.06F - vocal * .13F;
                    head.zRot = Mth.sin(time * .13F) * .04F;
                    body.xRot = 0F;
                }
            }
            case MORTIS -> { // Mutsumi: concentrated fretting and down-pick accents.
                // A dense bundled chord must not look like a full arm strike for
                // each simultaneous note. Other non-MIDI combat retains force.
                if (member.isMidiPerforming()) impact *= .55F;
                leftArm.xRot = -1.18F + impact * .10F;
                leftArm.yRot = .22F;
                leftArm.zRot = -.40F - Mth.sin(time * .24F) * .025F;
                rightArm.xRot = -.52F - impact * .76F + Mth.sin(time * .53F) * .07F * groove;
                rightArm.yRot = -.06F;
                rightArm.zRot = .12F;
                head.xRot = .16F;
                body.xRot = .07F;
                rightArm.xRot -= flourish * (flourishAlt ? .40F : .24F);
                leftArm.xRot -= flourish * (flourishAlt ? .18F : .30F);
                head.xRot += flourish * .14F;
            }
            case TIMORIS -> { // Umiri: clearer bass pluck and left-hand grip on the neck.
                leftArm.xRot = -1.02F + Mth.sin(time * .18F) * .03F;
                leftArm.yRot = .16F;
                leftArm.zRot = -.30F;
                rightArm.xRot = -.74F - impact * .58F + Mth.sin(time * .30F) * .06F * groove;
                rightArm.yRot = -.08F;
                rightArm.zRot = .14F;
                head.xRot = .085F + Mth.sin(time * .16F) * .05F;
                rightArm.xRot -= flourish * (flourishAlt ? .28F : .50F);
                leftArm.xRot -= flourish * .18F;
                rightLeg.xRot -= flourish * .10F;
            }
            case AMORIS -> { // Nyamu: seated drummer with both sticks angled into the kit.
                int drumPitch = member.playedPitch();
                boolean snare = drumPitch == 38 || drumPitch == 40;
                boolean kick = drumPitch == 35 || drumPitch == 36;
                float rightHit = (kick || (!snare && rightAccent)) ? impact : impact * .12F;
                float leftHit = (snare || (!kick && !rightAccent)) ? impact : impact * .12F;
                float swing = Mth.sin(time * .43F) * .045F * groove;
                rightArm.xRot = -1.28F + rightHit * 1.00F + swing;
                leftArm.xRot = -1.28F + leftHit * 0.96F - swing;
                rightArm.yRot = -.10F;
                leftArm.yRot = .10F;
                rightArm.zRot = .12F;
                leftArm.zRot = -.12F;
                body.xRot = .04F;
                head.xRot = .02F + Mth.sin(time * .32F) * .05F;
                // The legs bend only when the server has also authorized the
                // stool render. No more air-sitting while moving to a stage mark.
                if (!walking && member.isDrumSeated()) {
                    rightLeg.xRot = -1.36F;
                    leftLeg.xRot = -1.36F;
                    rightLeg.yRot = -.06F;
                    leftLeg.yRot = .06F;
                }
                if (flourishAlt) {
                    rightArm.xRot -= flourish * .43F;
                    leftArm.xRot -= flourish * .50F;
                    rightArm.zRot += flourish * .18F;
                    leftArm.zRot -= flourish * .18F;
                } else {
                    rightArm.xRot -= flourish * .65F;
                    leftArm.xRot -= flourish * .18F;
                    head.xRot -= flourish * .12F;
                }
                if (kick && !walking && member.isDrumSeated()) rightLeg.xRot = -1.28F;
            }
        }
        // Persistent, role-specific motion overlay. It is intentionally layered
        // *after* note-on hits/flourishes so lively gestures coexist with actual
        // MIDI events. Per-entity phase prevents five NPCs bobbing in lockstep.
        // Only MODEL parts move: never mutate entity yaw or render instrument anchors.
        float phase = (member.getId() % 43) * .17F;
        float slow = Mth.sin(time * .115F + phase);
        float headBeat = Mth.sin(time * .23F + phase * .4F);
        float offBeat = Mth.sin(time * .325F + phase * 1.3F);
        float breathe = Mth.sin(time * .071F + phase * .62F);
        switch (role) {
            case OBLIVIONIS -> { // Keyboard: hands and subtle nod only, no turning.
                head.yRot = 0F;
                head.zRot = 0F;
                head.xRot += headBeat * .035F;
                body.yRot = 0F;
                body.zRot = 0F;
                body.xRot += headBeat * .010F;
                leftArm.xRot += offBeat * .038F;
                rightArm.xRot -= offBeat * .038F;
            }
            case DOLORIS -> { // The lively reference: enthusiastic vocals and broad headwork.
                head.yRot += slow * .14F;
                head.zRot += headBeat * .073F;
                head.xRot += headBeat * .105F;
                body.yRot += slow * .035F;
                body.zRot += slow * .023F;
                leftLeg.xRot += headBeat * .040F;
                rightLeg.xRot -= headBeat * .040F;
            }
            case MORTIS -> { // Quiet but clearly alive: glance toward the fretboard, nod, shift weight.
                head.yRot -= slow * .083F;
                head.xRot += headBeat * .106F;
                head.zRot -= breathe * .066F;
                body.xRot += slow * .022F;
                body.zRot += slow * .018F;
                leftLeg.xRot += offBeat * .035F;
                rightArm.xRot += offBeat * .047F;
            }
            case TIMORIS -> { // Tight, deliberate low-frequency groove; occasional side glance.
                head.yRot += slow * .109F;
                head.zRot += breathe * .042F;
                head.xRot += headBeat * .080F;
                body.yRot -= slow * .024F;
                body.xRot += headBeat * .020F;
                rightLeg.xRot += offBeat * .075F;
                rightArm.xRot += offBeat * .035F;
            }
            case AMORIS -> { // Energetic drummer: headbang plus torso/shoulder rhythm.
                head.yRot += slow * .108F;
                head.zRot += offBeat * .106F;
                head.xRot += headBeat * .165F;
                body.xRot += headBeat * .046F;
                body.yRot += slow * .032F;
                body.zRot += slow * .021F;
                rightArm.zRot += offBeat * .046F;
                leftArm.zRot -= offBeat * .046F;
                leftLeg.xRot += headBeat * .038F;
            }
        }
        // Explicitly constrain the rig after combining role, beat and flourish layers.
        // A player torso can sway a few degrees, never perform a full flip.
        body.xRot = Mth.clamp(body.xRot, -.10F, .10F);
        body.yRot = Mth.clamp(body.yRot, -.10F, .10F);
        body.zRot = Mth.clamp(body.zRot, -.045F, .045F);
        head.xRot = Mth.clamp(head.xRot, -.48F, .48F);
        head.yRot = Mth.clamp(head.yRot, -.42F, .42F);
        head.zRot = Mth.clamp(head.zRot, -.20F, .20F);
        // A sitting pose is not just bent knees: lower the hip/torso and head
        // until the pelvis physically meets the stool cushion (~7px off ground).
        // Only shift model parts while the drummer is stationary and performing.
        if (role == MemberRole.AMORIS && !walking && member.isDrumSeated()) {
            float down = 5.0F;
            body.y += down;
            head.y += down;
            rightArm.y += down;
            leftArm.y += down;
            rightLeg.y += down;
            leftLeg.y += down;
            rightLeg.xRot = -1.34F;
            leftLeg.xRot = -1.34F;
            rightLeg.zRot = -.05F;
            leftLeg.zRot = .05F;
        }
        // Second-layer hair/clothes follow the underlying vanilla body parts.
        copyLayers();
    }
}
