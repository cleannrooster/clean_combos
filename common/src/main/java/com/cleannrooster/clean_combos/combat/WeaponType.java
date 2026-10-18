package com.cleannrooster.clean_combos.combat;

import java.util.Locale;

/**
 * What kind of weapon an item is — a sword, a rapier, a mace — as opposed to its {@link Moveset}, which is how it
 * attacks. Several types share a moveset (a sword and a katana both swing). Detected by {@link WeaponTypes}; for
 * other mods to read through {@link com.cleannrooster.clean_combos.api.CleanCombosApi}. Stable: part of the API.
 */
public enum WeaponType {
    SWORD,
    GREATSWORD,
    KATANA,
    RAPIER,
    DAGGER,
    AXE,
    MACE,
    HAMMER,
    SCYTHE,
    HALBERD,
    SPEAR,
    TRIDENT,
    STAFF,
    /** Claws, talons and katars: fist weapons with an edge (they slash where a fist bludgeons). */
    CLAW,
    /** An empty hand, a non-weapon held in it, or a blunt fist weapon (gauntlets, knuckles...). */
    FIST,
    /** Pickaxes, shovels and hoes. */
    TOOL,
    /** Deals melee damage but matches nothing above. */
    OTHER;

    /** Lower-case name, e.g. {@code "greatsword"}: stable across versions, for configs and other mods. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Display name key, e.g. {@code clean_combos.weapon_type.greatsword}. */
    public String translationKey() {
        return "clean_combos.weapon_type." + id();
    }
}
