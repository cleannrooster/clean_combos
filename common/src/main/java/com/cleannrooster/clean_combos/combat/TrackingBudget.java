package com.cleannrooster.clean_combos.combat;

/**
 * Limits on how far a swing may turn toward its target during WINDUP (adopted from visceral_combat's
 * AttackCommitment). The rate is scaled inversely with windup length when the session starts.
 *
 * @param escapeFactor a target beyond {@code yawEnvelopeDeg * escapeFactor} breaks tracking for the swing
 */
public record TrackingBudget(float yawEnvelopeDeg, float yawRatePerTick, float escapeFactor) {
}
