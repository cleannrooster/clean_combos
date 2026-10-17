package com.cleannrooster.clean_combos.combat;

/**
 * Everything combat needs from a moveset as one particular wielder uses it: the moveset's built-in shape and
 * rules ({@link Movesets}), with reach taken from the wielder's entity-interaction range.
 *
 * @param range                true reach at the strike tick: interaction range plus the moveset's bonus
 * @param acquireRange         furthest the attacker will snap to reach a clicked target
 * @param approachPenaltyTicks windup added by a full-length snap (shorter snaps pay a share)
 * @param chainGraceTicks      ticks after recovery ends in which a click chains into SECOND
 */
public record WeaponProfile(
        Moveset moveset,
        SwingStep first,
        SwingStep second,
        MovesetTiming timing,
        double range,
        double acquireRange,
        float coneHalfAngleDeg,
        int maxTargets,
        int approachPenaltyTicks,
        int chainGraceTicks,
        TrackingBudget tracking) {

    public SwingStep step(ComboSlot slot) {
        return slot == ComboSlot.FIRST ? first : second;
    }
}
