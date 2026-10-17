package com.cleannrooster.exiled_combat.targeting;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * What a click meant. A null target means "attack in place": no approach, cone along
 * {@code fallbackFacing} (cursor ground point in iso, look direction in first person).
 */
public record SwingIntent(@Nullable LivingEntity target, Vec3d fallbackFacing) {
}
