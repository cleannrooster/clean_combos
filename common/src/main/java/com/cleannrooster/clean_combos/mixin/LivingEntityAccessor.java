package com.cleannrooster.clean_combos.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    /** Vanilla's attack-cooldown clock; filled before each strike so damage isn't cooldown-scaled. */
    @Accessor("lastAttackedTicks")
    void clean_combos$setLastAttackedTicks(int ticks);
}
