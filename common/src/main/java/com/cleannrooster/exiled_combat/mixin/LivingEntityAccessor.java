package com.cleannrooster.exiled_combat.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    /** Vanilla's attack-cooldown clock; filled before each strike so damage isn't cooldown-scaled. */
    @Accessor("lastAttackedTicks")
    void exiled_combat$setLastAttackedTicks(int ticks);
}
