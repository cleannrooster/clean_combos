package com.cleannrooster.exiled_combat.client.trail;

/**
 * The three colors one ribbon is drawn in, derived from a single material color.
 *
 * @param edge  pale, for the razor edge
 * @param body  saturated, for the colored mass
 * @param trail deeper and more saturated still, for the rough wake — never dark
 */
public record TrailPalette(int edge, int body, int trail) {

    /** The body keeps enough depth to show its hue rather than washing out to the edge's white. */
    private static final float BODY_MAX_LIGHTNESS = 0.72f;
    private static final float TRAIL_MAX_LIGHTNESS = 0.62f;

    /**
     * @param baseRgb       the material color, 0xRRGGBB
     * @param minLightness  floor applied to every derived color
     * @param edgeWhiteness share of white blended into the edge color
     */
    public static TrailPalette fromBase(int baseRgb, float minLightness, float edgeWhiteness) {
        float[] hsl = ColorLift.toHsl(baseRgb);
        float lifted = Math.max(hsl[2], minLightness);

        int edge = ColorLift.mix(ColorLift.fromHsl(hsl[0], hsl[1], lifted), 0xFFFFFF,
            Math.max(0.0f, Math.min(1.0f, edgeWhiteness)));
        int body = ColorLift.fromHsl(hsl[0],
            Math.min(1.0f, hsl[1] * 1.15f + 0.05f),
            clampLightness(lifted, minLightness, BODY_MAX_LIGHTNESS));
        int trail = ColorLift.fromHsl(hsl[0],
            Math.min(1.0f, hsl[1] * 1.3f + 0.12f),
            clampLightness(lifted - 0.12f, minLightness, TRAIL_MAX_LIGHTNESS));
        return new TrailPalette(edge, body, trail);
    }

    private static float clampLightness(float lightness, float floor, float ceiling) {
        return Math.max(floor, Math.min(lightness, Math.max(floor, ceiling)));
    }
}
