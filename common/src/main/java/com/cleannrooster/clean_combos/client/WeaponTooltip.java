package com.cleannrooster.clean_combos.client;

import com.cleannrooster.clean_combos.combat.Moveset;
import com.cleannrooster.clean_combos.combat.Movesets;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

/** Weapon tooltips: one line naming the attack the weapon gets ("Swing attack", "Chop attack", ...). */
public final class WeaponTooltip {

    private WeaponTooltip() {
    }

    public static void append(ItemStack stack, List<Text> lines) {
        if (stack.isEmpty()) {
            return;
        }
        // Fist weapons (claws, gauntlets...) get their line; plain items that merely punch don't.
        if (!Movesets.isArmed(stack)) {
            return;
        }
        Moveset moveset = Movesets.detect(stack);
        lines.add(Text.translatable("clean_combos.tooltip.moveset." + moveset.name().toLowerCase(Locale.ROOT))
            .formatted(Formatting.GOLD));
    }
}
