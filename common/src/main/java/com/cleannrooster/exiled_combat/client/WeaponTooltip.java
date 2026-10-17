package com.cleannrooster.exiled_combat.client;

import com.cleannrooster.exiled_combat.combat.ComboSlot;
import com.cleannrooster.exiled_combat.combat.Grip;
import com.cleannrooster.exiled_combat.combat.HandStats;
import com.cleannrooster.exiled_combat.combat.Moveset;
import com.cleannrooster.exiled_combat.combat.Movesets;
import com.cleannrooster.exiled_combat.combat.SwingPlanner;
import com.cleannrooster.exiled_combat.combat.WeaponProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Honest weapon tooltips: the moveset, then each grip the weapon can actually be used in with its real first
 * and second swing times and damage change, then reach. Every number comes from the same functions the planner
 * uses, with the hovering player's own attack speed and the server's config, so the tooltip can't disagree
 * with what happens in game. The grip the player is in right now is marked.
 */
public final class WeaponTooltip {

    private WeaponTooltip() {
    }

    public static void append(ItemStack stack, List<Text> lines) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || stack.isEmpty()) {
            return;
        }
        Moveset moveset = Movesets.detect(stack);
        if (moveset.isFist()) {
            return;
        }
        WeaponProfile profile = Movesets.profile(moveset, player);
        double attackSpeed = HandStats.attackSpeedWith(player, stack);
        Grip current = currentGrip(player, stack);

        lines.add(Text.translatable("exiled_combat.tooltip.moveset." + moveset.name().toLowerCase(Locale.ROOT))
            .formatted(Formatting.GOLD));
        lines.add(gripLine(Grip.ONE_HANDED, profile, attackSpeed, current));
        if (moveset.twoHandsWhenFree()) {
            lines.add(gripLine(Grip.TWO_HANDED, profile, attackSpeed, current));
        }
        lines.add(gripLine(Grip.DUAL_WIELD, profile, attackSpeed, current));
        lines.add(Text.translatable("exiled_combat.tooltip.reach", String.format(Locale.ROOT, "%.1f", profile.range()))
            .formatted(Formatting.DARK_GRAY));
    }

    private static Text gripLine(Grip grip, WeaponProfile profile, double attackSpeed, @Nullable Grip current) {
        double speed = attackSpeed * SwingPlanner.gripSpeed(grip);
        float first = SwingPlanner.swingCycleTicks(profile.timing(), ComboSlot.FIRST, speed) / 20.0f;
        float second = SwingPlanner.swingCycleTicks(profile.timing(), ComboSlot.SECOND, speed) / 20.0f;
        boolean active = grip == current;
        MutableText line = Text.literal(active ? " ▸ " : "   ")
            .append(Text.translatable("exiled_combat.tooltip.grip." + grip.name().toLowerCase(Locale.ROOT)))
            .append(Text.literal(String.format(Locale.ROOT, "  %.2fs → %.2fs", first, second)));
        int damagePercent = Math.round((SwingPlanner.gripDamage(grip) - 1.0f) * 100.0f);
        if (damagePercent != 0) {
            line.append(Text.literal("  "))
                .append(Text.translatable("exiled_combat.tooltip.damage",
                    (damagePercent > 0 ? "+" : "−") + Math.abs(damagePercent)));
        }
        return line.formatted(active ? Formatting.WHITE : Formatting.GRAY);
    }

    /** The grip this stack is held in right now, or null when it isn't in either hand. */
    private static @Nullable Grip currentGrip(ClientPlayerEntity player, ItemStack stack) {
        if (stack != player.getMainHandStack() && stack != player.getOffHandStack()) {
            return null;
        }
        return SwingPlanner.hands(player).grip();
    }
}
