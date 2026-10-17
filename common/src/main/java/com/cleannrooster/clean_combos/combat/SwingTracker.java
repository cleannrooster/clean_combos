package com.cleannrooster.clean_combos.combat;

import com.cleannrooster.clean_combos.targeting.TargetRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Clamped, breakable turn toward the target during WINDUP (visceral_combat's AttackTracking, applied to
 * the session's facing instead of the camera). Every yaw correction is bounded by a per-tick rate and a
 * total envelope centred on the starting yaw; a target that leaves what those can cover has dodged,
 * and the swing finishes along its last facing.
 *
 * <p>Pitch follows the target's height at the same rate, within its own envelope, so the arc tilts to meet
 * a tall or short target. Only sideways movement counts as a dodge.
 */
public final class SwingTracker {

    private static final float BASELINE_WINDUP_TICKS = 8.0f;
    private static final float SMOOTHING_GAIN = 0.35f;
    private static final float RATE_RAMP_TICKS = 2.0f;
    private static final double ESCAPE_DISTANCE_SLACK = 1.5;
    /** How far pitch may follow the target from where the swing started, in degrees. */
    private static final float PITCH_ENVELOPE = 45.0f;
    /** Swings never tilt past this, up or down. */
    public static final float MAX_PITCH = 70.0f;

    private final float startYaw;
    private final float startPitch;
    private final float envelope;
    private final float escapeAngle;
    private final float ratePerTick;
    private final double breakDistance;
    private float facing;
    private float prevFacing;
    private float pitch;
    private float prevPitch;
    private int ticks;
    private boolean broken;

    /**
     * @param reach how far the target may be before tracking breaks, minus slack: the weapon's range, or
     *              its acquire range for a swing that snaps in to reach its target
     */
    public SwingTracker(float startYaw, float startPitch, TrackingBudget budget, int windupTicks, double reach) {
        this.startYaw = startYaw;
        this.startPitch = MathHelper.clamp(startPitch, -MAX_PITCH, MAX_PITCH);
        this.facing = startYaw;
        this.prevFacing = startYaw;
        this.pitch = this.startPitch;
        this.prevPitch = this.startPitch;
        this.envelope = budget.yawEnvelopeDeg();
        this.escapeAngle = budget.yawEnvelopeDeg() * budget.escapeFactor();
        // Long windups turn slower per tick — a committed heavy swing shouldn't steer like a jab.
        float durationScale = MathHelper.clamp(BASELINE_WINDUP_TICKS / Math.max(1, windupTicks), 0.5f, 1.25f);
        this.ratePerTick = budget.yawRatePerTick() * durationScale;
        this.breakDistance = reach + ESCAPE_DISTANCE_SLACK;
    }

    public float tick(LivingEntity attacker, @Nullable LivingEntity target) {
        prevFacing = facing;
        prevPitch = pitch;
        if (broken || target == null) {
            return facing;
        }
        if (!target.isAlive() || target.isRemoved()) {
            broken = true;
            return facing;
        }
        ticks++;
        float desired = TargetRules.yawToward(attacker, target);
        if (Math.abs(MathHelper.wrapDegrees(desired - startYaw)) > escapeAngle
                || TargetRules.reachDistance(attacker, target) > breakDistance) {
            broken = true;
            return facing;
        }
        float rate = ratePerTick * Math.min(1.0f, ticks / RATE_RAMP_TICKS);
        float error = MathHelper.wrapDegrees(desired - facing);
        float step = MathHelper.clamp(error * SMOOTHING_GAIN, -rate, rate);
        float offset = MathHelper.clamp(MathHelper.wrapDegrees(facing + step - startYaw), -envelope, envelope);
        facing = startYaw + offset;

        float pitchError = TargetRules.pitchToward(attacker, target) - pitch;
        float pitchStep = MathHelper.clamp(pitchError * SMOOTHING_GAIN, -rate, rate);
        pitch = MathHelper.clamp(MathHelper.clamp(pitch + pitchStep, startPitch - PITCH_ENVELOPE,
            startPitch + PITCH_ENVELOPE), -MAX_PITCH, MAX_PITCH);
        return facing;
    }

    public float facing() {
        return facing;
    }

    /** Facing interpolated across the last tick, for rendering. */
    public float facing(float tickDelta) {
        return prevFacing + MathHelper.wrapDegrees(facing - prevFacing) * tickDelta;
    }

    public float pitch() {
        return pitch;
    }

    public float pitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPitch, pitch);
    }

    /** Called on ticks the tracker no longer runs, so the interpolation settles instead of replaying. */
    public void hold() {
        prevFacing = facing;
        prevPitch = pitch;
    }

    public boolean isBroken() {
        return broken;
    }
}
