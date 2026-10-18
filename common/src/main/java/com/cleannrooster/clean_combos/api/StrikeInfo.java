package com.cleannrooster.clean_combos.api;

import net.minecraft.entity.LivingEntity;

/**
 * One target being struck by an attack.
 *
 * @param attack   the attack (weapon, kind, grip...)
 * @param target   who is being hit
 * @param critical vanilla's crit conditions hold (falling, not sprinting, on the ground...), so vanilla will
 *                 deal its crit; read before the hit lands
 */
public record StrikeInfo(AttackInfo attack, LivingEntity target, boolean critical) {
}
