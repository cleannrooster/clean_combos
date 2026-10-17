package com.cleannrooster.exiled_combat.mixin.client;

import com.cleannrooster.exiled_combat.client.ClientCombat;
import com.cleannrooster.exiled_combat.client.targeting.TargetAcquirer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /** A click with a profiled weapon becomes a swing; returning true also skips block breaking this tick. */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void exiled_combat$doAttack(CallbackInfoReturnable<Boolean> cir) {
        if (ClientCombat.onAttackClick((MinecraftClient) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void exiled_combat$handleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (ClientCombat.suppressesBlockBreaking()) {
            ci.cancel();
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void exiled_combat$doItemUse(CallbackInfo ci) {
        if (ClientCombat.isLocked()) {
            ci.cancel();
        }
    }

    /** The hovered target gets the spectator-style outline, showing what a click will hit. */
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void exiled_combat$hasOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == TargetAcquirer.hovered()) {
            cir.setReturnValue(true);
        }
    }
}
