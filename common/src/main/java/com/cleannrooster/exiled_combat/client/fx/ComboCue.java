package com.cleannrooster.exiled_combat.client.fx;

import com.cleannrooster.exiled_combat.client.ClientCombat;
import com.cleannrooster.exiled_combat.client.targeting.TargetAcquirer;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The combo cue: while a chained second swing is available, the hovered target's outline pulses between
 * white and gold — the next click lands the quick second swing. No HUD; the target itself says it.
 */
public final class ComboCue {

    private static final int REST = 0xFFFFFF;
    private static final int CUE = 0xFFC830;
    private static final float PULSES_PER_SECOND = 3.0f;

    private ComboCue() {
    }

    /** The outline colour to use for {@code entity}, or null to leave vanilla's. */
    public static @Nullable Integer outlineColor(Entity entity) {
        if (entity != TargetAcquirer.hovered() || !ClientCombat.isChainOpen()) {
            return null;
        }
        float seconds = (Util.getMeasuringTimeMs() % 100_000L) / 1000.0f;
        float t = 0.5f + 0.5f * MathHelper.sin(seconds * PULSES_PER_SECOND * MathHelper.TAU);
        return lerp(REST, CUE, t);
    }

    private static int lerp(int from, int to, float t) {
        int r = Math.round(MathHelper.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(MathHelper.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(MathHelper.lerp(t, from & 0xFF, to & 0xFF));
        return r << 16 | g << 8 | b;
    }
}
