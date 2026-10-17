package com.cleannrooster.clean_combos.combat;

/**
 * How the two hands combine. DUAL_WIELD when both hands hold a profiled weapon (or fists with a free off
 * hand). TWO_HANDED for two-handed weapons, which suppress the off hand, and for a one-handed weapon with
 * a free off hand, which is gripped in both (except chops). ONE_HANDED when the off hand holds a
 * non-weapon item, or for a chop with a free off hand.
 */
public enum Grip {
    ONE_HANDED,
    DUAL_WIELD,
    TWO_HANDED
}
