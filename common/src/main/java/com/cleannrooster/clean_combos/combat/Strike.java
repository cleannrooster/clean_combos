package com.cleannrooster.clean_combos.combat;

import com.cleannrooster.clean_combos.api.StrikeInfo;
import net.minecraft.entity.LivingEntity;

/**
 * One target to hit, after the strike listeners have had their say.
 *
 * @param damageMultiplier the listeners' combined multiplier for this target (1 for none)
 */
public record Strike(LivingEntity target, StrikeInfo info, float damageMultiplier) {
}
