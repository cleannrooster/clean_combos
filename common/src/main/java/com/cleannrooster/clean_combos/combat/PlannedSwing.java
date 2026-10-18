package com.cleannrooster.clean_combos.combat;

import net.minecraft.item.ItemStack;

/**
 * A swing resolved at click time and frozen for its whole life. Server and client compute it
 * independently from the same inputs; the server's copy is authoritative.
 *
 * @param snapDistance         blocks the swing snaps to reach its target; 0 for none
 * @param lungeTicks           ticks the snap pushes for, from its distance; 0 for none
 * @param effectiveWindupTicks base windup + the snap's share of the approach penalty, scaled by attack speed,
 *                             and never shorter than the push plus the blade's fast part
 * @param airborne             started in the air (a jump attack)
 * @param running              started sprinting forward, turning a swing into a thrust
 */
public record PlannedSwing(
        ComboSlot slot,
        Grip grip,
        SwingHand hand,
        ItemStack weapon,
        WeaponProfile profile,
        SwingStep step,
        float snapDistance,
        int lungeTicks,
        int effectiveWindupTicks,
        int effectiveRecoveryLockTicks,
        int effectiveRecoveryTotalTicks,
        boolean airborne,
        boolean running) {

    /** True when the swing snaps to its target. */
    public boolean approached() {
        return lungeTicks > 0;
    }
}
