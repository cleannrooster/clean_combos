package com.cleannrooster.exiled_combat.mixin;

import com.cleannrooster.exiled_combat.combat.HandStats;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    /**
     * Swings are narrow by design: during an exiled_combat strike, vanilla's sweep finds no extra victims.
     * The strike resolver has already chosen every target.
     */
    @WrapOperation(method = "attack",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private <T extends Entity> List<T> exiled_combat$noSweep(World world, Class<T> type, Box box,
                                                             Operation<List<T>> original) {
        return HandStats.strikeInProgress ? List.of() : original.call(world, type, box);
    }
}
