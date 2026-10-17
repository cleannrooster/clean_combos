package com.cleannrooster.clean_combos.neoforge;

import com.cleannrooster.clean_combos.CleanCombosClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

@OnlyIn(Dist.CLIENT)
final class CleanCombosClientNeoForge {

    private CleanCombosClientNeoForge() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(CleanCombosClientNeoForge::onRenderLevelStage);
    }

    /** AFTER_PARTICLES and AFTER_LEVEL stand in for Fabric's AFTER_TRANSLUCENT and END. */
    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            CleanCombosClient.renderWorld(event.getPoseStack(), event.getCamera(),
                event.getPartialTick().getTickDelta(false));
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            CleanCombosClient.onWorldRendered(event.getModelViewMatrix(), event.getProjectionMatrix(),
                event.getCamera().getPos());
        }
    }
}
