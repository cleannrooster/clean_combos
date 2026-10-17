package com.cleannrooster.exiled_combat.combat;

import com.cleannrooster.exiled_combat.combat.Moveset.PoseFamily;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The one description of where the blade is during a swing. The procedural pose, the trail and the
 * debug overlay all sample this, so they cannot drift apart from each other or from the hit.
 *
 * <p>Two guarantees are built in rather than tuned:
 * <ul>
 *   <li>The fast part of the swing is centred on the STRIKE tick, and its easing is symmetric, so blade
 *       progress is exactly 0.5 on the tick damage resolves.</li>
 *   <li>The sweep angle is piecewise — start to 0, then 0 to end — so progress 0.5 is always angle 0,
 *       dead centre of the facing the strike cone is aimed along. Arc values in a profile change how
 *       the swing looks, never when or where it connects.</li>
 * </ul>
 *
 * <p>Angles are in the swing's plane, positive toward {@link #planeAxis}. Profiles author the right-side
 * swing; a LEFT swing mirrors the angles.
 */
public final class SwingGeometry {

    /**
     * The swing's orientation for one instant: shoulder-height origin, facing yaw and pitch. Up is the
     * frame's own up (tilted with the pitch), so a whole arc tilts to meet a tall or short target.
     */
    public record Frame(Vec3d origin, Vec3d forward, Vec3d up, Vec3d right) {

        public static Frame of(LivingEntity attacker, float facingYaw, float facingPitch, float tickDelta) {
            Vec3d pos = attacker.getLerpedPos(tickDelta);
            double height = attacker.getStandingEyeHeight() - attacker.getHeight() * 0.15;
            return of(pos.add(0.0, height, 0.0), facingYaw, facingPitch);
        }

        public static Frame of(Vec3d origin, float facingYaw, float facingPitch) {
            Vec3d forward = Vec3d.fromPolar(facingPitch, facingYaw).normalize();
            Vec3d up = Vec3d.fromPolar(facingPitch - 90.0f, facingYaw).normalize();
            return new Frame(origin, forward, up, forward.crossProduct(up).normalize());
        }
    }

    public enum Stage {
        RAISE,
        STRIKE,
        RECOVER
    }

    /**
     * @param progress blade progress through the sweep, 0..1; exactly 0.5 on the strike tick
     * @param weight   how much the swing pose overrides vanilla, 0..1 (eases in and out)
     */
    public record Sample(Stage stage, float progress, float weight) {
    }

    /** Snaps stop this far inside the weapon's range. */
    public static final double SNAP_MARGIN = 0.35;
    private static final int MAX_LUNGE_TICKS = 9;
    /** Average snap speed in blocks per tick (the ease-out starts at about twice this). */
    private static final float SNAP_AVERAGE_SPEED = 0.9f;
    /** Snap length at which the full approach penalty applies; shorter snaps pay proportionally less. */
    private static final float FULL_PENALTY_SNAP_DISTANCE = 6.0f;

    private SwingGeometry() {
    }

    /** Half the duration of the fast part of the swing, in ticks, either side of the strike tick. */
    public static float strikeHalfWidth(int windupTicks) {
        return MathHelper.clamp(windupTicks * 0.3f, 1.0f, 2.5f);
    }

    public static float strikeStart(int windupTicks) {
        return windupTicks - strikeHalfWidth(windupTicks);
    }

    public static float strikeEnd(int windupTicks) {
        return windupTicks + strikeHalfWidth(windupTicks);
    }

    /**
     * How far a snap has to carry the attacker: from their reach to the weapon's range, less a margin so a
     * target's next step doesn't put it straight back out of range. 0 when already close enough.
     */
    public static float snapDistance(double reachDistance, double weaponRange) {
        return (float) Math.max(0.0, reachDistance - Math.max(0.5, weaponRange - SNAP_MARGIN));
    }

    /**
     * Ticks the snap pushes for: proportional to its distance at a steady average speed, so a short snap
     * is over in a tick or two instead of being drawn out. 0 for no snap.
     */
    public static int lungeTicks(float snapDistance) {
        if (snapDistance <= 0.02f) {
            return 0;
        }
        return MathHelper.clamp((int) Math.ceil(snapDistance / SNAP_AVERAGE_SPEED), 1, MAX_LUNGE_TICKS);
    }

    /** Share of a weapon's approach penalty a snap of this distance costs: all of it only for a long one. */
    public static float snapPenaltyShare(float snapDistance) {
        return MathHelper.clamp(snapDistance / FULL_PENALTY_SNAP_DISTANCE, 0.0f, 1.0f);
    }

    /**
     * Share of the snap distance covered at {@code fraction} of the push: an ease-out, fastest at the
     * click and decelerating into the strike, so it reads as a snap that flows into the swing rather than
     * a constant-speed slide that stops dead.
     */
    public static float lungeEase(float fraction) {
        float inv = 1.0f - MathHelper.clamp(fraction, 0.0f, 1.0f);
        return 1.0f - inv * inv;
    }

    public static Sample sample(PlannedSwing swing, float elapsed) {
        int windup = swing.effectiveWindupTicks();
        float start = strikeStart(windup);
        float end = strikeEnd(windup);
        if (elapsed < start) {
            return new Sample(Stage.RAISE, 0.0f, easeOutCubic(start <= 0.0f ? 1.0f : elapsed / start));
        }
        if (elapsed < end) {
            return new Sample(Stage.STRIKE, smooth((elapsed - start) / (end - start)), 1.0f);
        }
        float span = Math.max(1.0f, windup + swing.effectiveRecoveryTotalTicks() - end);
        return new Sample(Stage.RECOVER, 1.0f, 1.0f - smooth((elapsed - end) / span));
    }

    public static float sideSign(SwingStep step) {
        return step.side() == SwingSide.LEFT ? -1.0f : 1.0f;
    }

    /** Blade angle in the swing plane at {@code progress}: start -> 0 at 0.5 -> end, mirrored for LEFT. */
    public static float sweepAngleDeg(SwingStep step, float progress) {
        float p = MathHelper.clamp(progress, 0.0f, 1.0f);
        float angle = p < 0.5f
            ? MathHelper.lerp(p * 2.0f, step.arc().startDeg(), 0.0f)
            : MathHelper.lerp((p - 0.5f) * 2.0f, 0.0f, step.arc().endDeg());
        return angle * sideSign(step);
    }

    /** How far a thrust or punch is extended, 0..1: drawn back, full exactly at the strike, slight give after. */
    public static float extension(float progress) {
        float p = MathHelper.clamp(progress, 0.0f, 1.0f);
        return p < 0.5f ? MathHelper.lerp(smooth(p * 2.0f), 0.35f, 1.0f) : 1.0f - 0.15f * ((p - 0.5f) * 2.0f);
    }

    public static boolean sweeps(PoseFamily family) {
        return family == PoseFamily.HORIZONTAL || family == PoseFamily.OVERHEAD;
    }

    /** The in-plane direction positive angles turn toward, tilted by the arc's plane tilt. */
    public static Vec3d planeAxis(PoseFamily family, SwingStep step, Frame frame) {
        double tilt = Math.toRadians(step.arc().planeTiltDeg() * sideSign(step));
        return switch (family) {
            case OVERHEAD -> frame.up().multiply(Math.cos(tilt)).add(frame.right().multiply(Math.sin(tilt)));
            default -> frame.right().multiply(Math.cos(tilt)).add(frame.up().multiply(Math.sin(tilt)));
        };
    }

    /** The plane's normal: the direction a drawn sweep has thickness in. */
    public static Vec3d planeNormal(PoseFamily family, SwingStep step, Frame frame) {
        return frame.forward().crossProduct(planeAxis(family, step, frame)).normalize();
    }

    public static Vec3d bladeDirection(PoseFamily family, SwingStep step, Frame frame, float progress) {
        double angle = Math.toRadians(sweepAngleDeg(step, progress));
        return frame.forward().multiply(Math.cos(angle))
            .add(planeAxis(family, step, frame).multiply(Math.sin(angle)));
    }

    /**
     * The way the blade is travelling at {@code progress} — where its edge should face. For a sweep this
     * is the tangent of the arc (taken one-sided at the ends, so during windup the edge is already set for
     * the coming cut). A thrust travels along its own line, so its edges face out of the swing plane's
     * sides instead.
     */
    public static Vec3d edgeDirection(PoseFamily family, SwingStep step, Frame frame, float progress) {
        if (sweeps(family)) {
            float p0 = Math.max(0.0f, progress - 0.01f);
            float p1 = Math.min(1.0f, progress + 0.01f);
            Vec3d delta = bladeDirection(family, step, frame, p1).subtract(bladeDirection(family, step, frame, p0));
            if (delta.lengthSquared() > 1.0e-10) {
                return delta.normalize();
            }
        }
        return planeAxis(family, step, frame);
    }

    /**
     * World point on the swept surface at blade progress {@code progress} and blade fraction {@code t}
     * (0 at the origin, 1 at {@code reach}). A thrust extends instead of sweeping, so its reach scales
     * with {@link #extension}.
     */
    public static Vec3d surfacePoint(PoseFamily family, SwingStep step, Frame frame, float progress, float t,
                                     double reach) {
        double radius = reach * MathHelper.clamp(t, 0.0f, 1.0f);
        if (!sweeps(family)) {
            radius *= extension(progress);
        }
        return frame.origin().add(bladeDirection(family, step, frame, progress).multiply(radius));
    }

    private static float easeOutCubic(float t) {
        float inv = 1.0f - MathHelper.clamp(t, 0.0f, 1.0f);
        return 1.0f - inv * inv * inv;
    }

    private static float smooth(float t) {
        float c = MathHelper.clamp(t, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }
}
