package com.cleannrooster.clean_combos.fabric;

import com.cleannrooster.clean_combos.CleanCombos;
import net.fabricmc.api.ModInitializer;

public class CleanCombosFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CleanCombos.init();
    }
}
