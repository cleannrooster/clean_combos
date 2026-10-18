package com.cleannrooster.clean_combos.combat;

import com.cleannrooster.clean_combos.api.CleanCombosTags;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;

import java.util.List;
import java.util.Locale;

/**
 * Sorts items into {@link WeaponType}s. Like {@link Movesets}, nothing to configure: every item gets a type the
 * moment it exists.
 *
 * <p>Order: an empty hand is a fist; then {@code #clean_combos:weapon_type/<type>} tags; tridents and maces by
 * class; then the item id's name, most specific first
 * (a "dagger" that extends {@code SwordItem} is a dagger, a "greatsword" isn't a plain sword, a "pickaxe" isn't
 * an axe); then vanilla classes and tags; then the moveset it would attack with. Names only count for items
 * that deal melee damage, so a "sword_display" block or a "club" banner stays out.
 */
public final class WeaponTypes {

    private record Hint(WeaponType type, String... words) {
    }

    /** Checked in order; the first word found in the item id's path wins. */
    private static final List<Hint> NAME_HINTS = List.of(
        new Hint(WeaponType.TOOL, "pickaxe", "shovel", "spade", "hoe", "paxel", "mattock"),
        new Hint(WeaponType.GREATSWORD, "greatsword", "great_sword", "claymore", "zweihander", "flamberge"),
        new Hint(WeaponType.RAPIER, "rapier", "estoc", "epee"),
        new Hint(WeaponType.KATANA, "katana", "odachi", "tachi", "wakizashi"),
        new Hint(WeaponType.DAGGER, "dagger", "knife", "dirk", "stiletto", "tanto", "athame"),
        new Hint(WeaponType.SCYTHE, "scythe", "sickle"),
        new Hint(WeaponType.HALBERD, "halberd", "glaive", "poleaxe", "pole_axe", "bardiche", "guisarme"),
        new Hint(WeaponType.HAMMER, "warhammer", "war_hammer", "hammer", "maul"),
        new Hint(WeaponType.MACE, "mace", "flail", "morningstar", "morning_star", "club", "cudgel"),
        new Hint(WeaponType.TRIDENT, "trident"),
        new Hint(WeaponType.SPEAR, "spear", "lance", "pike", "javelin"),
        new Hint(WeaponType.STAFF, "quarterstaff", "staff"),
        new Hint(WeaponType.CLAW, "claw", "talon", "katar"),
        new Hint(WeaponType.FIST, "fist", "gauntlet", "bracer", "knuckle", "cestus", "caestus", "glove", "handwrap",
            "hand_wrap", "tekko"),
        new Hint(WeaponType.SWORD, "sword", "saber", "sabre", "cutlass", "scimitar", "blade"),
        new Hint(WeaponType.AXE, "axe"));

    private WeaponTypes() {
    }

    public static WeaponType of(ItemStack stack) {
        if (stack.isEmpty()) {
            return WeaponType.FIST;
        }
        for (WeaponType tagged : WeaponType.values()) {
            if (stack.isIn(CleanCombosTags.weaponType(tagged))) {
                return tagged;
            }
        }
        Item item = stack.getItem();
        if (item instanceof TridentItem) {
            return WeaponType.TRIDENT;
        }
        if (item instanceof MaceItem) {
            return WeaponType.MACE;
        }
        boolean melee = Movesets.dealsMeleeDamage(stack);
        if (melee) {
            String path = Registries.ITEM.getId(item).getPath().toLowerCase(Locale.ROOT);
            for (Hint hint : NAME_HINTS) {
                for (String word : hint.words()) {
                    if (path.contains(word)) {
                        return hint.type();
                    }
                }
            }
        }
        if (item instanceof SwordItem || stack.isIn(ItemTags.SWORDS)) {
            return WeaponType.SWORD;
        }
        if (item instanceof AxeItem || stack.isIn(ItemTags.AXES)) {
            return WeaponType.AXE;
        }
        if (item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof HoeItem
                || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES)) {
            return WeaponType.TOOL;
        }
        Moveset moveset = Movesets.detect(stack);
        if (moveset == null) {
            return melee ? WeaponType.OTHER : WeaponType.FIST;
        }
        return switch (moveset) {
            case FIST -> WeaponType.FIST;
            case THRUST -> WeaponType.SPEAR;
            case CHOP, AERIAL -> WeaponType.AXE;
            case SWING -> WeaponType.OTHER;
        };
    }
}
