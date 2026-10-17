package com.cleannrooster.exiled_combat.mixin.client;

import com.cleannrooster.exiled_combat.client.fx.ComboCue;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityClientMixin {

    /** Outline colour: the combo cue pulses the hovered target while a chained swing is available. */
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void exiled_combat$comboCue(CallbackInfoReturnable<Integer> cir) {
        Integer color = ComboCue.outlineColor((Entity) (Object) this);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
