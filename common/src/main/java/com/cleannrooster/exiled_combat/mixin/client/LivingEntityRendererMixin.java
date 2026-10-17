package com.cleannrooster.exiled_combat.mixin.client;

import com.cleannrooster.exiled_combat.client.fx.HitShake;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    /** Hitstop's victim shake. The dispatcher pushes the matrix stack around this call, so the nudge stays local. */
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"))
    private void exiled_combat$hitShake(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                        VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        HitShake.apply(entity, matrices, tickDelta);
    }
}
