package com.cleannrooster.exiled_combat.combat;

/**
 * Where the blade travels, in degrees in the swing plane, authored for a right-side swing (a LEFT swing
 * mirrors it). The blade goes start -> 0 -> end, and {@link SwingGeometry} puts 0 — dead ahead, the
 * centre of the strike cone — on the strike tick, so these shape the look without moving the hit.
 *
 * <p>Sweeps (swing, chop, long/short two-handers): positive start is to the right for horizontal swings
 * and above/behind for overhead ones; negative end is the far side or the ground. Thrusts and punches
 * only drift a few degrees, and extend along the line instead.
 *
 * @param planeTiltDeg rolls the swing plane around the facing: a diagonal instead of a flat cut
 */
public record SwingArc(float startDeg, float endDeg, float planeTiltDeg) {
}
