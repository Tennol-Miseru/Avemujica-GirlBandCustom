package com.miseru.avemujica.client;

/** Shared crop geometry: the source image is NEVER stretched to fill the billboard. */
public final class PosterCrop {
    private PosterCrop() { }
    public record Region(float u0, float v0, float u1, float v1) {
        public float width() { return u1-u0; }
        public float height() { return v1-v0; }
    }
    public static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    /** Match the source pixel aspect to the billboard aperture aspect, then crop. */
    public static Region calculate(int imageW, int imageH, float targetAspect,
                                   float zoom, float centerX, float centerY) {
        if (imageW <= 0 || imageH <= 0 || !Float.isFinite(targetAspect) || targetAspect <= 0)
            return new Region(0, 0, 1, 1);
        float sourceAspect = imageW / (float)imageH;
        float z = clamp(Float.isFinite(zoom) ? zoom : 1, 1, 4);
        float w = Math.min(1F, targetAspect / sourceAspect) / z;
        float h = Math.min(1F, sourceAspect / targetAspect) / z;
        float cx = clamp(Float.isFinite(centerX) ? centerX : .5F, w/2, 1-w/2);
        float cy = clamp(Float.isFinite(centerY) ? centerY : .5F, h/2, 1-h/2);
        return new Region(cx-w/2, cy-h/2, cx+w/2, cy+h/2);
    }
    public static float apertureLeft(int width, int height) {
        return width == 1 && height == 1 ? 2F/16F : .18F;
    }
    public static float apertureRight(int width, int height) {
        return width == 1 && height == 1 ? 14F/16F : width-.18F;
    }
    public static float apertureBottom(int width, int height) {
        return width == 1 && height == 1 ? 5F/16F : .18F;
    }
    public static float apertureTop(int width, int height) {
        return width == 1 && height == 1 ? 12F/16F : height-.18F;
    }
    public static float apertureAspect(int width, int height) {
        return (apertureRight(width, height)-apertureLeft(width, height)) /
               (apertureTop(width, height)-apertureBottom(width, height));
    }
}
