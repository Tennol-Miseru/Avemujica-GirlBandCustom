package com.miseru.avemujica.client;

import java.util.List;

/**
 * Dimensions for the physical, six-sided wooden frame of a multi-block poster.
 *
 * Single-block posters continue to use their baked vanilla block model. Larger
 * posters hide that 1-block model and are framed entirely by the block-entity
 * renderer. Keeping frame geometry separate from the photo quad ensures that
 * changing photo aspect/crop cannot stretch or erase the surrounding trim.
 */
public final class PosterFrameGeometry {
    private PosterFrameGeometry() { }

    public record Cuboid(float x0, float y0, float z0,
                         float x1, float y1, float z1) {
        public boolean hasVolume() {
            return x1 > x0 && y1 > y0 && z1 > z0;
        }
    }

    // A visibly thick frame; the picture is inset ~0.03 block behind its outer lip.
    public static final float FRONT = .365F;
    public static final float BACK = .635F;
    public static final float FRONT_ART = .401F;
    public static final float BACK_ART = .599F;

    public static List<Cuboid> build(int width, int height) {
        if (width < 1 || width > 4 || height < 1 || height > 4)
            throw new IllegalArgumentException("Poster dimensions must be 1–4 blocks");

        final float outerLeft = .025F;
        final float outerRight = width - .025F;
        final float outerBottom = .025F;
        final float outerTop = height - .025F;
        final float left = PosterCrop.apertureLeft(width, height);
        final float right = PosterCrop.apertureRight(width, height);
        final float bottom = PosterCrop.apertureBottom(width, height);
        final float top = PosterCrop.apertureTop(width, height);

        // Four non-overlapping rails, joined at the corners: no z-fighting from
        // overlapping front panels, and no missing edges in side-on views.
        return List.of(
                new Cuboid(outerLeft, outerBottom, FRONT, outerRight, bottom, BACK),
                new Cuboid(outerLeft, top, FRONT, outerRight, outerTop, BACK),
                new Cuboid(outerLeft, bottom, FRONT, left, top, BACK),
                new Cuboid(right, bottom, FRONT, outerRight, top, BACK),
                // Wooden core behind both image surfaces (double-sided picture).
                new Cuboid(left, bottom, .49F, right, top, .51F)
        );
    }
}
