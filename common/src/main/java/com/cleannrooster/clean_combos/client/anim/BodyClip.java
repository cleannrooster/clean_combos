package com.cleannrooster.clean_combos.client.anim;

import com.cleannrooster.clean_combos.CleanCombos;
import com.cleannrooster.clean_combos.combat.Grip;
import com.cleannrooster.clean_combos.combat.PlannedSwing;
import com.cleannrooster.clean_combos.combat.SwingGeometry;
import com.cleannrooster.clean_combos.combat.SwingSide;
import com.cleannrooster.clean_combos.combat.Moveset;
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
 *
 * <p>Swing and thrust also have a two-handed variant (same file, {@code _2h} animation, same key moments),
 * played when the weapon is gripped two-handed.
 */
public enum BodyClip {
    SWING("swing_left_to_right", "swing_left_to_right_2h", 0.375f, 0.506f, 0.75f, 1.0f),
    SLAM("slam_left_to_right", null, 0.625f, 0.672f, 0.75f, 1.0f),
    /** Wound up at 0.375 s (drawn back, body turned away); full extension — the strike — at 0.75 s; held to 1 s. */
    THRUST("thrust_left_to_right", "thrust_left_to_right_2h", 0.375f, 0.75f, 0.875f, 1.0f),
    /**
     * Fists. Cocked at 0.333 s (arm drawn, body turned away); the punch lands at 0.375 s as the arm snaps through
     * and the body's twist crosses neutral; fully followed through at 0.708 s.
     */
    UNARMED("unarmed_left_to_right", null, 0.333f, 0.375f, 0.708f, 1.0f),
    /** Lunge uses only two moments: fully extended at the end of the push (0.25 s), then settled (0.5 s). */
    LUNGE("lunge_left_to_right", null, 0.25f, 0.25f, 0.25f, 0.5f);

    private static final float TICKS_PER_SECOND = 20.0f;

    private final Identifier id;
    private final @Nullable Identifier twoHandedId;
    private final float woundUp;
    private final float centre;
    private final float followThrough;
    private final float end;

    BodyClip(String name, @Nullable String twoHandedName, float woundUpSec, float centreSec, float followThroughSec,
             float endSec) {
        this.id = Identifier.of(CleanCombos.MOD_ID, name);
        this.twoHandedId = twoHandedName != null ? Identifier.of(CleanCombos.MOD_ID, twoHandedName) : null;
        this.woundUp = woundUpSec * TICKS_PER_SECOND;
        this.centre = centreSec * TICKS_PER_SECOND;
        this.followThrough = followThroughSec * TICKS_PER_SECOND;
        this.end = endSec * TICKS_PER_SECOND;
    }

    /** Looked up per use: PlayerAnimator replaces its registry on resource reload. */
    public @Nullable KeyframeAnimation animation() {
        return lookup(id);
    }

    /** The clip {@code swing} plays: the two-handed variant for a two-handed grip, when there is one. */
    public @Nullable KeyframeAnimation animationFor(PlannedSwing swing) {
        KeyframeAnimation twoHanded = playsTwoHanded(swing) ? lookup(twoHandedId) : null;
        return twoHanded != null ? twoHanded : animation();
    }

    /** True when {@code swing} plays the two-handed variant: gripped two-handed and the variant exists. */
    public boolean playsTwoHanded(PlannedSwing swing) {
        return swing.grip() == Grip.TWO_HANDED && twoHandedId != null && lookup(twoHandedId) != null;
    }

    private static @Nullable KeyframeAnimation lookup(@Nullable Identifier id) {
        IPlayable playable = id != null ? PlayerAnimationRegistry.getAnimation(id) : null;
        return playable instanceof KeyframeAnimation keyframes ? keyframes : null;
    }

    /**
     * Swing plays the swing clip, chop and jump attacks the slam clip, thrust the thrust clip, fists the unarmed
     * clip.
     */
    public static @Nullable BodyClip forMoveset(Moveset moveset) {
        return switch (moveset) {
            case SWING -> SWING;
            case CHOP, AERIAL -> SLAM;
            case THRUST -> THRUST;
            case FIST -> UNARMED;
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
