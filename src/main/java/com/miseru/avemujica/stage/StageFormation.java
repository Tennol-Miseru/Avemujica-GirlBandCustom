package com.miseru.avemujica.stage;

import com.miseru.avemujica.entity.MemberRole;
import net.minecraft.core.Direction;

/**
 * The five musicians' default performance marks in stage-local coordinates.
 *
 * <p>Positive Z points towards the audience when the stage faces SOUTH.
 * The stage controller is the origin; the caller supplies a center already
 * elevated one block above the controller for a walkable stage deck.
 * Marks are rotated together with the structure, not mirrored ad hoc.
 *
 * <p>At Level 2, Sakiko and Nyamu stand at (+/-12, Y+2, -4) on
 * the raised galleries. The upper platforms no longer generate instrument
 * props; the two members render their instruments when performing.  The other three stand at Y=1
 * on the main deck. No blocks are moved by this formation system.
 */
public final class StageFormation {
    private StageFormation() { }

    /** Local offset from the stage floor center; Y is additional elevation. */
    public record Mark(double x, double y, double z) { }

    public static Mark local(MemberRole role, boolean levelTwo) {
        if (levelTwo) {
            return switch (role) {
                case OBLIVIONIS -> new Mark(-12.0D, 2.0D, -4.0D); // Sakiko: keyboard (rear left)
                case AMORIS    -> new Mark( 12.0D, 2.0D, -4.0D); // Nyamu: drums (rear right)
                case MORTIS    -> new Mark( -5.0D, 0.0D,  3.0D); // Mutsumi: guitar (front left)
                case DOLORIS   -> new Mark(  0.0D, 0.0D,  4.0D); // Uika: vocals / guitar (center)
                case TIMORIS   -> new Mark(  5.0D, 0.0D,  3.0D); // Umiri: bass (front right)
            };
        }
        // Preserve original 11x9 level-one stage placement.
        return switch (role) {
            case OBLIVIONIS -> new Mark(-2.6D, 0.0D, -3.0D);
            case AMORIS    -> new Mark( 2.6D, 0.0D, -3.0D);
            case MORTIS    -> new Mark(-3.3D, 0.0D,  1.3D);
            case DOLORIS   -> new Mark(-0.6D, 0.0D,  2.2D);
            case TIMORIS   -> new Mark( 2.8D, 0.0D,  1.3D);
        };
    }

    /** One transform shared by AI navigation, musical effects and all four orientations. */
    public static Mark rotated(MemberRole role, boolean levelTwo, Direction front) {
        Mark local = local(role, levelTwo);
        int[] xz = StageStructure.rotate(
                (int) Math.round(local.x() * 10.0D),
                (int) Math.round(local.z() * 10.0D), front);
        return new Mark(xz[0] / 10.0D, local.y(), xz[1] / 10.0D);
    }
}
