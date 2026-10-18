package com.cleannrooster.clean_combos.combat;

import com.cleannrooster.clean_combos.targeting.TargetRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Where a swing faces, every tick of it. Two things steer it:
 *
 * <ul>
 *   <li><b>The wielder's own turning.</b> However far they have turned their look since the swing started is
 *       added straight on, for the whole swing: turn mid-swing and the arc, the strike cone and the trail turn
 *       with you.</li>
 *   <li><b>Tracking</b> (visceral_combat's AttackTracking): during WINDUP, a clamped, breakable correction
 *       toward the target on top of that. Every correction is bounded by a per-tick rate and an envelope
 *       centred on where the wielder is steering; a target that leaves what those can cover has dodged, and
 *       the correction stays where it was.</li>
 * </ul>
 *
 * <p>Pitch works the same way within its own envelope, so the arc tilts to meet a tall or short target. Only
 * sideways movement counts as a dodge.
 */
public final class SwingTracker {

    private static final float BASELINE_WINDUP_TICKS = 8.0f;
    private static final float SMOOTHING_GAIN = 0.35f;
    private static final float RATE_RAMP_TICKS = 2.0f;
    private static final double ESCAPE_DISTANCE_SLACK = 1.5;
    /** How far pitch tracking may correct from where the wielder is steering, in degrees. */
    private static final float PITCH_ENVELOPE = 45.0f;
    /** Swings never tilt past this, up or down. */
    public static final float MAX_PITCH = 70.0f;

    private final float startYaw;
    private final float startPitch;
    /** The wielder's look when the swing started; turning is measured from here. */
    private final float lookYaw;
    private final float lookPitch;
    private final float envelope;
    private final float escapeAngle;
    private final float ratePerTick;
    private final double breakDistance;
    /** How far the wielder has turned since the start, as of the last tick. */
    private float turnYaw;
    private float turnPitch;
    /** Tracking's correction on top of the wielder's turning. */
    private float trackYaw;
    private float prevTrackYaw;
    private float trackPitch;
    private float prevTrackPitch;
    private int ticks;
    private boolean broken;

    /**
     * @param startYaw   the swing's facing at the start (toward the target, or along the look for none)
     * @param lookYaw    the wielder's own look yaw at the start
     * @param reach      how far the target may be before tracking breaks, minus slack: the weapon's range, or
     *                   its acquire range for a swing that snaps in to reach its target
     */
    public SwingTracker(float startYaw, float startPitch, float lookYaw, float lookPitch, TrackingBudget budget,
                        int windupTicks, double reach) {
        this.startYaw = startYaw;
        this.startPitch = MathHelper.clamp(startPitch, -MAX_PITCH, MAX_PITCH);
        this.lookYaw = lookYaw;
        this.lookPitch = lookPitch;
        this.envelope = budget.yawEnvelopeDeg();
        this.escapeAngle = budget.yawEnvelopeDeg() * budget.escapeFactor();
        // Long windups turn slower per tick — a committed heavy swing shouldn't steer like a jab.
        float durationScale = MathHelper.clamp(BASELINE_WINDUP_TICKS / Math.max(1, windupTicks), 0.5f, 1.25f);
        this.ratePerTick = budget.yawRatePerTick() * durationScale;
        this.breakDistance = reach + ESCAPE_DISTANCE_SLACK;
    }

    /** A windup tick: follow the wielder's turning, then correct toward the target. */
    public void tick(LivingEntity attacker, @Nullable LivingEntity target) {
        hold(attacker);
        if (broken || target == null) {
            return;
        }
        if (!target.isAlive() || target.isRemoved()) {
            broken = true;
            return;
        }
        ticks++;
        float steeredYaw = startYaw + turnYaw;
        float desired = TargetRules.yawToward(attacker, target);
        if (Math.abs(MathHelper.wrapDegrees(desired - steeredYaw)) > escapeAngle
                || TargetRules.reachDistance(attacker, target) > breakDistance) {
            broken = true;
            return;
        }
        float rate = ratePerTick * Math.min(1.0f, ticks / RATE_RAMP_TICKS);
        float error = MathHelper.wrapDegrees(desired - (steeredYaw + trackYaw));
        float step = MathHelper.clamp(error * SMOOTHING_GAIN, -rate, rate);
        trackYaw = MathHelper.clamp(trackYaw + step, -envelope, envelope);

        float steeredPitch = startPitch + turnPitch;
        float pitchError = TargetRules.pitchToward(attacker, target) - (steeredPitch + trackPitch);
        float pitchStep = MathHelper.clamp(pitchError * SMOOTHING_GAIN, -rate, rate);
        // Never wind the correction past what the pitch limit would let show.
        trackPitch = MathHelper.clamp(MathHelper.clamp(trackPitch + pitchStep, -PITCH_ENVELOPE, PITCH_ENVELOPE),
            -MAX_PITCH - steeredPitch, MAX_PITCH - steeredPitch);
    }

    /**
     * A tick tracking no longer runs (recovery, hitstop): the wielder's turning still applies, the correction
     * holds, and its interpolation settles instead of replaying.
     */
    public void hold(LivingEntity attacker) {
        prevTrackYaw = trackYaw;
        prevTrackPitch = trackPitch;
        turnYaw = MathHelper.wrapDegrees(attacker.getYaw() - lookYaw);
        turnPitch = attacker.getPitch() - lookPitch;
    }

    /** Facing as of the last tick — what the strike resolves along, identically on server and client. */
    public float facing() {
        return startYaw + turnYaw + trackYaw;
    }

    /**
     * Facing for rendering: the wielder's turning read live at {@code tickDelta}, so the arc stays glued to a
     * turning camera instead of trailing it by a tick, and tracking's correction interpolated.
     */
    public float facing(LivingEntity attacker, float tickDelta) {
        return startYaw + MathHelper.wrapDegrees(attacker.getYaw(tickDelta) - lookYaw)
            + MathHelper.lerp(tickDelta, prevTrackYaw, trackYaw);
    }

    public float pitch() {
        return clampPitch(startPitch + turnPitch + trackPitch);
    }

    public float pitch(LivingEntity attacker, float tickDelta) {
        return clampPitch(startPitch + attacker.getPitch(tickDelta) - lookPitch
            + MathHelper.lerp(tickDelta, prevTrackPitch, trackPitch));
    }

    public boolean isBroken() {
        return broken;
    }

    private static float clampPitch(float pitch) {
        return MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH);
    }
}
