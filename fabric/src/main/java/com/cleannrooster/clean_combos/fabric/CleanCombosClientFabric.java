package com.cleannrooster.clean_combos.fabric;

import com.cleannrooster.clean_combos.CleanCombosClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

@Environment(EnvType.CLIENT)
public class CleanCombosClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CleanCombosClient.init();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> CleanCombosClient.renderWorld(
            context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(false)));
        WorldRenderEvents.END.register(context -> CleanCombosClient.onWorldRendered(
            context.positionMatrix(), context.projectionMatrix(), context.camera().getPos()));
    }
}
