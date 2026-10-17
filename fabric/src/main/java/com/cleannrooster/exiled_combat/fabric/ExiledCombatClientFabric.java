package com.cleannrooster.exiled_combat.fabric;

import com.cleannrooster.exiled_combat.ExiledCombatClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

@Environment(EnvType.CLIENT)
public class ExiledCombatClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ExiledCombatClient.init();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> ExiledCombatClient.renderWorld(
            context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(false)));
        WorldRenderEvents.END.register(context -> ExiledCombatClient.onWorldRendered(
            context.positionMatrix(), context.projectionMatrix(), context.camera().getPos()));
    }
}
