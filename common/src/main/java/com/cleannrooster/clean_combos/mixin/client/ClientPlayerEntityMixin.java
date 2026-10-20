package com.cleannrooster.clean_combos.mixin.client;

import com.cleannrooster.clean_combos.client.ClientCombat;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    /**
     * After an attack, sprinting doesn't restart on its own from a held sprint key: walking out of a combo stays
     * a walk until the player starts sprinting again (see {@link ClientCombat#blocksSprint}).
     */
    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void clean_combos$holdSprintOff(CallbackInfoReturnable<Boolean> cir) {
        if (ClientCombat.blocksSprint()) {
            cir.setReturnValue(false);
        }
    }
}
