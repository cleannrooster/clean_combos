package com.cleannrooster.clean_combos.combat;

/** IDLE -> APPROACH -> WINDUP -> STRIKE (one tick) -> RECOVERY -> IDLE. No interrupts once WINDUP starts. */
public enum AttackPhase {
    IDLE,
    APPROACH,
    WINDUP,
    STRIKE,
    RECOVERY
}
