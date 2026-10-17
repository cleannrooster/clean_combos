package com.cleannrooster.exiled_combat.mixin.client;

import com.cleannrooster.exiled_combat.client.ClientCombat;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    /** After vanilla reads the keys: zero movement while locked, or steer toward an approach target. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void exiled_combat$tick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        ClientCombat.modifyInput((Input) (Object) this);
    }
}
