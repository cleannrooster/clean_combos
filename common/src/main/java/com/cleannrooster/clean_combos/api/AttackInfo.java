package com.cleannrooster.clean_combos.api;

import com.cleannrooster.clean_combos.combat.ComboSlot;
import com.cleannrooster.clean_combos.combat.Grip;
import com.cleannrooster.clean_combos.combat.Moveset;
import com.cleannrooster.clean_combos.combat.PlannedSwing;
import com.cleannrooster.clean_combos.combat.SwingHand;
import com.cleannrooster.clean_combos.combat.WeaponType;
import com.cleannrooster.clean_combos.combat.WeaponTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/**
 * One attack as Clean Combos made it: who, with what, how. The same for every target the attack hits.
 *
 * @param attacker   who is attacking
 * @param weapon     the swinging hand's item (empty for a bare fist); a copy, don't modify
 * @param weaponType what kind of weapon it is
 * @param moveset    how this attack is made, situation included: {@link Moveset#AERIAL} for a jump attack, and
 *                   {@link Moveset#THRUST} for a running sword attack
 * @param kind       the damage kind (see {@link DamageKind})
 * @param grip       one-handed, two-handed or dual-wield
 * @param hand       the hand swinging ({@link SwingHand#BOTH} when two-handed)
 * @param slot       first or second hit of the combo (a jump attack is always first)
 * @param jumpAttack the attack started in the air
 * @param running    the attack started sprinting forward and became a running thrust
 */
public record AttackInfo(LivingEntity attacker, ItemStack weapon, WeaponType weaponType, Moveset moveset,
                         DamageKind kind, Grip grip, SwingHand hand, ComboSlot slot, boolean jumpAttack,
                         boolean running) {

    /** Internal: describe a planned swing. */
    public static AttackInfo of(LivingEntity attacker, PlannedSwing swing) {
        WeaponType type = WeaponTypes.of(swing.weapon());
        Moveset moveset = swing.profile().moveset();
        return new AttackInfo(attacker, swing.weapon(), type, moveset, DamageKind.of(type, moveset), swing.grip(),
            swing.hand(), swing.slot(), swing.airborne(), swing.running());
    }
}
