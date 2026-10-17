package com.cleannrooster.exiled_combat.neoforge;

import com.cleannrooster.exiled_combat.ExiledCombat;
import com.cleannrooster.exiled_combat.ExiledCombatClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(ExiledCombat.MOD_ID)
public class ExiledCombatNeoForge {
    public ExiledCombatNeoForge(IEventBus modEventBus) {
        ExiledCombat.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ExiledCombatClient.init();
            ExiledCombatClientNeoForge.init();
        }
    }
}
