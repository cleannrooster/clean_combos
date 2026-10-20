package com.cleannrooster.clean_combos.mixin;

import com.cleannrooster.clean_combos.combat.HandStats;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    /**
     * Swings are narrow by design: during an clean_combos strike, vanilla's sweep finds no extra victims.
     * The strike resolver has already chosen every target.
     */
    @WrapOperation(method = "attack",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private <T extends Entity> List<T> clean_combos$noSweep(World world, Class<T> type, Box box,
                                                             Operation<List<T>> original) {
        return HandStats.strikeInProgress ? List.of() : original.call(world, type, box);
    }

    /**
     * A Clean Combos strike makes its own sounds (the swing's whoosh and hit), so vanilla's attack sounds —
     * strong, weak, crit, knockback, sweep, no-damage — are muted while it resolves. Attacks left to vanilla
     * keep theirs.
     */
    @WrapOperation(method = "attack",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"))
    private void clean_combos$muteAttackSound(World world, PlayerEntity source, double x, double y, double z,
                                              SoundEvent sound, SoundCategory category, float volume, float pitch,
                                              Operation<Void> original) {
        if (!HandStats.strikeInProgress) {
            original.call(world, source, x, y, z, sound, category, volume, pitch);
        }
    }

    @WrapOperation(method = "attack",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;)V"))
    private void clean_combos$muteAttackSoundShort(World world, PlayerEntity source, double x, double y, double z,
                                                   SoundEvent sound, SoundCategory category, Operation<Void> original) {
        if (!HandStats.strikeInProgress) {
            original.call(world, source, x, y, z, sound, category);
        }
    }
}
