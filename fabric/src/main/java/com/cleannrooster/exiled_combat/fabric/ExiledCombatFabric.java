package com.cleannrooster.exiled_combat.fabric;

import com.cleannrooster.exiled_combat.ExiledCombat;
import net.fabricmc.api.ModInitializer;

public class ExiledCombatFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ExiledCombat.init();
    }
}
