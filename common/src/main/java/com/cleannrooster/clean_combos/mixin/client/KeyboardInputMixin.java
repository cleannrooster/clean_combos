package com.cleannrooster.clean_combos.mixin.client;

import com.cleannrooster.clean_combos.client.ClientCombat;
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
    private void clean_combos$tick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        ClientCombat.modifyInput((Input) (Object) this);
    }
}
