package com.cleannrooster.clean_combos.mixin.client;

import com.cleannrooster.clean_combos.client.ClientCombat;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityClientMixin {

    /** While a swing runs, the body faces the swing's tracked yaw; the camera and head stay free. */
    @Inject(method = "turnHead", at = @At("HEAD"), cancellable = true)
    private void clean_combos$turnHead(float bodyRotation, float headRotation, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        Float facing = ClientCombat.bodyFacing(self);
        if (facing != null) {
            self.bodyYaw = facing;
            cir.setReturnValue(headRotation);
        }
    }
}
