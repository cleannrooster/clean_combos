package com.cleannrooster.exiled_combat.client.trail;

import net.minecraft.util.math.MathHelper;

/** Small RGB/HSL helpers for deriving trail colors. Packed colors are 0xRRGGBB. */
public final class ColorLift {

    private ColorLift() {
    }

    /** @return {hue 0-1, saturation 0-1, lightness 0-1} */
    public static float[] toHsl(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float lightness = (max + min) * 0.5f;
        float delta = max - min;
        if (delta < 1.0e-5f) {
            return new float[]{0.0f, 0.0f, lightness};
        }
        float saturation = delta / (1.0f - Math.abs(2.0f * lightness - 1.0f));
        float hue;
        if (max == r) {
            hue = ((g - b) / delta) % 6.0f;
        } else if (max == g) {
            hue = (b - r) / delta + 2.0f;
        } else {
            hue = (r - g) / delta + 4.0f;
        }
        hue /= 6.0f;
        if (hue < 0.0f) {
            hue += 1.0f;
        }
        return new float[]{hue, MathHelper.clamp(saturation, 0.0f, 1.0f), lightness};
    }

    public static int fromHsl(float hue, float saturation, float lightness) {
        float chroma = (1.0f - Math.abs(2.0f * lightness - 1.0f)) * saturation;
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        float x = chroma * (1.0f - Math.abs(h % 2.0f - 1.0f));
        float r;
        float g;
        float b;
        if (h < 1.0f) {
            r = chroma; g = x; b = 0.0f;
        } else if (h < 2.0f) {
            r = x; g = chroma; b = 0.0f;
        } else if (h < 3.0f) {
            r = 0.0f; g = chroma; b = x;
        } else if (h < 4.0f) {
            r = 0.0f; g = x; b = chroma;
        } else if (h < 5.0f) {
            r = x; g = 0.0f; b = chroma;
        } else {
            r = chroma; g = 0.0f; b = x;
        }
        float m = lightness - chroma * 0.5f;
        return pack(r + m, g + m, b + m);
    }

    /** {@code from} moved {@code amount} of the way toward {@code to}. */
    public static int mix(int from, int to, float amount) {
        return pack(
            MathHelper.lerp(amount, (from >> 16 & 0xFF) / 255.0f, (to >> 16 & 0xFF) / 255.0f),
            MathHelper.lerp(amount, (from >> 8 & 0xFF) / 255.0f, (to >> 8 & 0xFF) / 255.0f),
            MathHelper.lerp(amount, (from & 0xFF) / 255.0f, (to & 0xFF) / 255.0f));
    }

    private static int pack(float r, float g, float b) {
        return channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float value) {
        return Math.round(MathHelper.clamp(value, 0.0f, 1.0f) * 255.0f);
    }
}
