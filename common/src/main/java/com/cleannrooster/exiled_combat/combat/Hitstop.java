package com.cleannrooster.exiled_combat.combat;

import com.cleannrooster.exiled_combat.config.ExiledCombatConfig;

/** How long a connecting swing freezes: the config's base, a tick longer for heavy chops and jump attacks. */
public final class Hitstop {

    private Hitstop() {
    }

    public static int ticksFor(PlannedSwing swing) {
        int base = ExiledCombatConfig.get().hitstopTicks;
        if (base <= 0) {
            return 0;
        }
        Moveset moveset = swing.profile().moveset();
        return base + (moveset == Moveset.CHOP || moveset == Moveset.AERIAL ? 1 : 0);
    }
}
