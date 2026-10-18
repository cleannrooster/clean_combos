package com.cleannrooster.clean_combos.combat;

import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One swing in flight, owned by one entity: WINDUP for {@code effectiveWindupTicks}, STRIKE on the tick
 * after, then RECOVERY. No interrupts once started. The same class runs on the server (authoritative,
 * resolves the strike) and on clients (lock, body facing, animation).
 */
public final class AttackSession {

    private final PlannedSwing swing;
    private final @Nullable LivingEntity target;
    private final SwingTracker tracker;
    private int elapsed;
    /** Ticks left in the current hitstop; the session's clock doesn't advance while it runs. */
    private int hitstop;
    private boolean pausedLastTick;

    /**
     * @param lookYaw   the attacker's own look yaw when the swing started: turning away from it mid-swing
     *                  turns the swing with them
     * @param lookPitch likewise for pitch
     */
    public AttackSession(PlannedSwing swing, @Nullable LivingEntity target, float startYaw, float startPitch,
                         float lookYaw, float lookPitch) {
        this.swing = swing;
        this.target = target;
        this.tracker = new SwingTracker(startYaw, startPitch, lookYaw, lookPitch, swing.profile().tracking(),
            swing.effectiveWindupTicks(),
            swing.approached() ? swing.profile().acquireRange() : swing.profile().range());
    }

    /**
     * Advance one tick. Tracking runs through windup and the strike tick itself, so the last correction
     * still shapes the cone; the attacker's own turning applies every tick, hitstop included.
     *
     * @return true on the strike tick
     */
    public boolean tick(LivingEntity attacker) {
        if (hitstop > 0) {
            // Frozen on impact: the whole remaining timeline (recovery lock included) shifts back.
            hitstop--;
            pausedLastTick = true;
            tracker.hold(attacker);
            return false;
        }
        pausedLastTick = false;
        elapsed++;
        int windup = swing.effectiveWindupTicks();
        if (elapsed <= windup) {
            tracker.tick(attacker, target);
        } else {
            tracker.hold(attacker);
        }
        return elapsed == windup;
    }

    public AttackPhase phase() {
        int windup = swing.effectiveWindupTicks();
        if (elapsed < windup) {
            return AttackPhase.WINDUP;
        }
        return elapsed == windup ? AttackPhase.STRIKE : AttackPhase.RECOVERY;
    }

    public int elapsed() {
        return elapsed;
    }

    /** Elapsed ticks with render interpolation, clamped to the session length; held still during hitstop. */
    public float elapsed(float partialTick) {
        return Math.min(elapsed + (hitstop > 0 ? 0.0f : partialTick), totalTicks());
    }

    /** Freeze the swing for {@code ticks} on impact. Overlapping hitstops don't stack. */
    public void applyHitstop(int ticks) {
        hitstop = Math.max(hitstop, ticks);
    }

    public boolean isInHitstop() {
        return hitstop > 0;
    }

    /** True when the last {@link #tick} was spent frozen — for clocks that run alongside this one. */
    public boolean wasPausedLastTick() {
        return pausedLastTick;
    }

    public int totalTicks() {
        return swing.effectiveWindupTicks() + swing.effectiveRecoveryTotalTicks();
    }

    /**
     * True from the strike tick on: the hit has resolved (or whiffed). Control returns to the player here;
     * the recovery animation keeps playing, but nothing about it holds them.
     */
    public boolean hasStruck() {
        return elapsed >= swing.effectiveWindupTicks();
    }

    /** True once the swing's cycle is over and the next swing may start — the attack-speed gate. */
    public boolean canBeCancelled() {
        return canBeCancelled(0);
    }

    /** True once the recovery lock has elapsed (minus {@code toleranceTicks} for latency slack). */
    public boolean canBeCancelled(int toleranceTicks) {
        return elapsed >= swing.effectiveWindupTicks() + swing.effectiveRecoveryLockTicks() - toleranceTicks;
    }

    public boolean isFinished() {
        return elapsed >= totalTicks();
    }

    public PlannedSwing swing() {
        return swing;
    }

    public @Nullable LivingEntity target() {
        return target;
    }

    public float facingYaw() {
        return tracker.facing();
    }

    /** Facing for rendering, following the attacker's turning live (see {@link SwingTracker#facing(LivingEntity, float)}). */
    public float facingYaw(LivingEntity attacker, float tickDelta) {
        return tracker.facing(attacker, tickDelta);
    }

    public float facingPitch() {
        return tracker.pitch();
    }

    public float facingPitch(LivingEntity attacker, float tickDelta) {
        return tracker.pitch(attacker, tickDelta);
    }
}
