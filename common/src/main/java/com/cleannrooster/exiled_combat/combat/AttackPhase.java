package com.cleannrooster.exiled_combat.combat;

/** IDLE -> APPROACH -> WINDUP -> STRIKE (one tick) -> RECOVERY -> IDLE. No interrupts once WINDUP starts. */
public enum AttackPhase {
    IDLE,
    APPROACH,
    WINDUP,
    STRIKE,
    RECOVERY
}
