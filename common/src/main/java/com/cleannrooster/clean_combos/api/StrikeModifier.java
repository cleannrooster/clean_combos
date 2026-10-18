package com.cleannrooster.clean_combos.api;

/**
 * What strike listeners can change about one target's hit: scale its damage, or cancel it. Multipliers from
 * several listeners multiply together; any listener cancelling cancels the hit on that target.
 */
public final class StrikeModifier {

    private float damageMultiplier = 1.0f;
    private boolean cancelled;

    /** Scale this target's damage (applied to vanilla's attack damage, alongside the grip's multiplier). */
    public void multiplyDamage(float multiplier) {
        damageMultiplier *= Math.max(0.0f, multiplier);
    }

    /** Don't hit this target at all. */
    public void cancel() {
        cancelled = true;
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
