package com.cleannrooster.clean_combos.combat;

/**
 * How a moveset spends its time, independent of how fast the wielder is. A swing's cycle — click to the next
 * swing being allowed — averages {@code 20 / attackSpeed × pace} ticks over the combo's two swings; the
 * one-two beat gives the first swing more of the pair and the second less.
 *
 * @param beat              how uneven the pair is: the first swing takes (1 + beat) / 2 of it, the second
 *                          (1 − beat) / 2. Scaled by the config's {@code comboRhythm}.
 * @param firstWindupShare  share of the first swing's cycle spent winding up (the rest is the recovery lock)
 * @param secondWindupShare same for the second swing
 * @param minCycleTicks     floor on a swing's average cycle, so very fast hands (fists) stay readable
 */
public record MovesetTiming(float beat, float firstWindupShare, float secondWindupShare, float minCycleTicks) {
}
