package com.cleannrooster.exiled_combat.client.anim;

import com.cleannrooster.exiled_combat.ExiledCombat;
import com.cleannrooster.exiled_combat.combat.PlannedSwing;
import com.cleannrooster.exiled_combat.combat.SwingGeometry;
import com.cleannrooster.exiled_combat.combat.SwingSide;
import com.cleannrooster.exiled_combat.combat.Moveset;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The hand-authored body/head/leg clips that play under the procedural arms, and how each is
 * time-warped onto a swing. All are authored left-to-right; a swing starting on the RIGHT plays them
 * mirrored.
 *
 * <p>Each attack clip is pinned at four authored moments, in seconds, read off its keyframes: wound up,
 * blade through centre (where the body's twist or pitch crosses neutral), follow-through, end. These map
 * piecewise-linearly onto the swing's own phases — wound up at the fast part's start, centre on the
 * strike tick, follow-through at the fast part's end, end at the end of recovery — so the body lands with
 * the blade at any windup length or attack speed.
 */
public enum BodyClip {
    SWING("swing_left_to_right", 0.375f, 0.506f, 0.75f, 1.0f),
    SLAM("slam_left_to_right", 0.625f, 0.672f, 0.75f, 1.0f),
    /** Lunge uses only two moments: fully extended at the end of the push, then settled. */
    LUNGE("lunge_left_to_right", 0.25f, 0.25f, 0.25f, 0.75f);

    private static final float TICKS_PER_SECOND = 20.0f;

    private final Identifier id;
    private final float woundUp;
    private final float centre;
    private final float followThrough;
    private final float end;

    BodyClip(String name, float woundUpSec, float centreSec, float followThroughSec, float endSec) {
        this.id = Identifier.of(ExiledCombat.MOD_ID, name);
        this.woundUp = woundUpSec * TICKS_PER_SECOND;
        this.centre = centreSec * TICKS_PER_SECOND;
        this.followThrough = followThroughSec * TICKS_PER_SECOND;
        this.end = endSec * TICKS_PER_SECOND;
    }

    /** Looked up per use: PlayerAnimator replaces its registry on resource reload. */
    public @Nullable KeyframeAnimation animation() {
        IPlayable playable = PlayerAnimationRegistry.getAnimation(id);
        return playable instanceof KeyframeAnimation keyframes ? keyframes : null;
    }

    /**
     * Swing plays the swing clip; chop and jump attacks the slam clip; thrust and fists are procedural until a
     * clip exists.
     */
    public static @Nullable BodyClip forMoveset(Moveset moveset) {
        return switch (moveset) {
            case SWING -> SWING;
            case CHOP, AERIAL -> SLAM;
            default -> null;
        };
    }

    /** Clips are authored left-to-right, so a swing that starts on the right plays them mirrored. */
    public static boolean mirrored(PlannedSwing swing) {
        return swing.step().side() == SwingSide.RIGHT;
    }

    /** Clip tick for a session at {@code elapsed} ticks, warped onto the swing's phases. */
    public float attackTick(PlannedSwing swing, float elapsed) {
        int windup = swing.effectiveWindupTicks();
        float total = windup + swing.effectiveRecoveryTotalTicks();
        float strikeStart = SwingGeometry.strikeStart(windup);
        float strikeEnd = Math.min(SwingGeometry.strikeEnd(windup), total - 0.01f);
        if (elapsed <= strikeStart) {
            return remap(elapsed, 0.0f, strikeStart, 0.0f, woundUp);
        }
        if (elapsed <= windup) {
            return remap(elapsed, strikeStart, windup, woundUp, centre);
        }
        if (elapsed <= strikeEnd) {
            return remap(elapsed, windup, strikeEnd, centre, followThrough);
        }
        return remap(elapsed, strikeEnd, total, followThrough, end);
    }

    /** Lunge clip tick: extended across the push, then its authored return stretched to end at {@code settleBy}. */
    public float lungeTick(float elapsed, int pushTicks, float settleBy) {
        if (elapsed <= pushTicks) {
            return remap(elapsed, 0.0f, pushTicks, 0.0f, woundUp);
        }
        return remap(elapsed, pushTicks, Math.max(settleBy, pushTicks + 1.0f), woundUp, end);
    }

    private static float remap(float value, float from0, float from1, float to0, float to1) {
        if (from1 - from0 < 1.0e-4f) {
            return to1;
        }
        return MathHelper.lerp(MathHelper.clamp((value - from0) / (from1 - from0), 0.0f, 1.0f), to0, to1);
    }
}
