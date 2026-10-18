package com.cleannrooster.clean_combos.api;

import com.cleannrooster.clean_combos.combat.Moveset;
import com.cleannrooster.clean_combos.combat.WeaponType;

import java.util.Locale;

/**
 * The physical kind of a Clean Combos hit. Classification only: nothing in Clean Combos treats the kinds
 * differently. Add-ons read it from {@link AttackInfo#kind()} (resistances, effects, sounds...).
 *
 * <p>The attack decides first and the weapon decides edged or blunt: a thrust pierces, a swing or chop slashes,
 * and a blunt weapon bludgeons either way. A punch bludgeons, except with claws, which slash. Grip doesn't
 * change the kind — a two-handed axe still slashes — but travels with the hit in {@link AttackInfo#grip()}.
 */
public enum DamageKind {
    SLASHING,
    PIERCING,
    BLUDGEONING;

    /** Lower-case name, e.g. {@code "piercing"}: stable across versions. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The kind an attack with {@code moveset} (as made, situation included) deals with a {@code type} weapon. */
    public static DamageKind of(WeaponType type, Moveset moveset) {
        if (moveset == Moveset.FIST) {
            return type == WeaponType.CLAW ? SLASHING : BLUDGEONING;
        }
        if (!isEdged(type)) {
            return BLUDGEONING;
        }
        return moveset == Moveset.THRUST ? PIERCING : SLASHING;
    }

    /** Weapons with a blade or point; everything else (maces, hammers, staves, fists, tools) is blunt. */
    public static boolean isEdged(WeaponType type) {
        return switch (type) {
            case SWORD, GREATSWORD, KATANA, RAPIER, DAGGER, AXE, SCYTHE, HALBERD, SPEAR, TRIDENT, CLAW -> true;
            case MACE, HAMMER, STAFF, FIST, TOOL, OTHER -> false;
        };
    }
}
