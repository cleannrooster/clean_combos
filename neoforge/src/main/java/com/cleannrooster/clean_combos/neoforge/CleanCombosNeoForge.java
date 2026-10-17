package com.cleannrooster.clean_combos.neoforge;

import com.cleannrooster.clean_combos.CleanCombos;
import com.cleannrooster.clean_combos.CleanCombosClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(CleanCombos.MOD_ID)
public class CleanCombosNeoForge {
    public CleanCombosNeoForge(IEventBus modEventBus) {
        CleanCombos.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CleanCombosClient.init();
            CleanCombosClientNeoForge.init();
        }
    }
}
