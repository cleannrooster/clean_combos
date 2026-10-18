package com.cleannrooster.clean_combos.api;

import com.cleannrooster.clean_combos.CleanCombos;
import com.cleannrooster.clean_combos.combat.Moveset;
import com.cleannrooster.clean_combos.combat.WeaponType;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * Item tags that classify weapons ahead of automatic detection, for weapon mods and modpacks. Put an item in:
 * <ul>
 *   <li>{@code #clean_combos:weapon_type/<type>} — its {@link WeaponType} ({@code sword}, {@code rapier},
 *       {@code claw}...)</li>
 *   <li>{@code #clean_combos:moveset/<moveset>} — how it attacks: {@code swing}, {@code chop}, {@code thrust} or
 *       {@code fist}</li>
 *   <li>{@code #clean_combos:vanilla_attack} — left to vanilla's own attack entirely (like the vanilla mace)</li>
 * </ul>
 * The server config's {@code movesetOverrides} still wins over the moveset tags.
 */
public final class CleanCombosTags {

    public static final TagKey<Item> VANILLA_ATTACK = tag("vanilla_attack");

    private CleanCombosTags() {
    }

    public static TagKey<Item> weaponType(WeaponType type) {
        return tag("weapon_type/" + type.id());
    }

    /** The tag for a taggable moveset ({@code swing}, {@code chop}, {@code thrust}, {@code fist}). */
    public static TagKey<Item> moveset(Moveset moveset) {
        return tag("moveset/" + moveset.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.of(RegistryKeys.ITEM, Identifier.of(CleanCombos.MOD_ID, path));
    }
}
