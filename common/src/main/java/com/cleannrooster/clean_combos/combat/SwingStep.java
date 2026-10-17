package com.cleannrooster.clean_combos.combat;

/**
 * The shape of one swing of a moveset's two-swing combo. Timing isn't part of it: that comes from the
 * wielder's attack speed when the swing is planned.
 */
public record SwingStep(SwingSide side, SwingArc arc) {
}
