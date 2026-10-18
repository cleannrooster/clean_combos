package com.cleannrooster.clean_combos.client.anim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation.KeyFrame;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation.StateCollection;
import dev.kosmx.playerAnim.core.util.Ease;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

import java.util.List;

/**
 * Samples a PlayerAnimator {@link KeyframeAnimation} loaded from a Blockbench (Bedrock/GeckoLib) file at
 * an arbitrary fractional tick on our own clock, matching how Blockbench previews it:
 * <ul>
 *   <li>A keyframe's easing shapes the segment leading <em>into</em> it, as in Blockbench and GeckoLib.
 *       PlayerAnimator's parser keeps each easing on its own keyframe but marks the file
 *       {@code isEasingBefore = false}, which would apply it to the segment after — the wrong one.</li>
 *   <li>{@code catmullrom} keys are a true Catmull-Rom spline through the neighbouring keys.
 *       {@link Ease#CATMULLROM}'s {@code invoke} is not an easing curve (it returns 1 at t=0 and swings
 *       negative), so it can't be fed to a lerp.</li>
 * </ul>
 *
 * <p>Values are the authored offsets from rest: PlayerAnimator's loader stores each key as the authored value
 * plus the bone's rest value ({@code defaultValue}), which is subtracted back out. Measuring from the clip's
 * first frame instead only works for clips that start at rest — the lunge starts already posed, and that
 * subtracted its whole pose away.
 */
final class ClipSampler {

    private ClipSampler() {
    }

    /** Offset of {@code bone}'s rotation (radians) or position at {@code tick}; zero for absent bones. */
    static Vector3f sample(KeyframeAnimation animation, String bone, TransformType type, float tick) {
        StateCollection part = animation.getPartOptional(bone).orElse(null);
        if (part == null || !part.isEnabled()) {
            return new Vector3f();
        }
        return switch (type) {
            case ROTATION -> new Vector3f(axis(part.pitch, tick), axis(part.yaw, tick), axis(part.roll, tick));
            case POSITION -> new Vector3f(axis(part.x, tick), axis(part.y, tick), axis(part.z, tick));
            default -> new Vector3f();
        };
    }

    private static float axis(StateCollection.State state, float tick) {
        if (!state.isEnabled()) {
            return 0.0f;
        }
        List<KeyFrame> frames = state.getKeyFrames();
        if (frames.isEmpty()) {
            return 0.0f;
        }
        return valueAt(frames, tick) - state.defaultValue;
    }

    private static float valueAt(List<KeyFrame> frames, float tick) {
        int after = 0;
        while (after < frames.size() && frames.get(after).tick <= tick) {
            after++;
        }
        if (after == 0) {
            return frames.get(0).value;
        }
        if (after == frames.size()) {
            return frames.get(after - 1).value;
        }
        KeyFrame from = frames.get(after - 1);
        KeyFrame to = frames.get(after);
        if (to.tick == from.tick) {
            return to.value;
        }
        float t = (tick - from.tick) / (to.tick - from.tick);
        if (to.ease == Ease.CATMULLROM) {
            float p0 = frames.get(Math.max(0, after - 2)).value;
            float p3 = frames.get(Math.min(frames.size() - 1, after + 1)).value;
            return catmullRom(p0, from.value, to.value, p3, t);
        }
        return MathHelper.lerp(to.ease.invoke(t, to.easingArg), from.value, to.value);
    }

    /** Uniform Catmull-Rom between p1 and p2; end keys stand in for their missing neighbours. */
    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (2.0f * p1 + (p2 - p0) * t + (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2
            + (3.0f * p1 - p0 - 3.0f * p2 + p3) * t3);
    }
}
