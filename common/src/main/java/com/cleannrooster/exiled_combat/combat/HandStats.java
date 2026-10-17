package com.cleannrooster.exiled_combat.combat;

import com.cleannrooster.exiled_combat.ExiledCombat;
import com.cleannrooster.exiled_combat.mixin.LivingEntityAccessor;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Per-hand combat stats. Vanilla only applies main-hand attribute modifiers and reads enchantments off
 * the main hand, so an off-hand strike runs vanilla's attack with the two hands swapped for its
 * duration — stacks and their attribute modifiers both.
 */
public final class HandStats {

    /** Attack speed assumed for wielders without the attribute (a sword's). */
    private static final double DEFAULT_ATTACK_SPEED = 1.6;
    private static final Identifier GRIP_DAMAGE_ID = Identifier.of(ExiledCombat.MOD_ID, "grip_damage");

    /** Set while {@link #attackWith} runs, so the sweep mixin knows to drop vanilla's sweep targets. */
    public static boolean strikeInProgress;

    private HandStats() {
    }

    public static ItemStack stackFor(LivingEntity entity, SwingHand hand) {
        return hand == SwingHand.OFF ? entity.getOffHandStack() : entity.getMainHandStack();
    }

    /**
     * The entity's attack speed (attacks per second) when attacking with {@code hand}: the attribute as it
     * stands, with the main-hand item's own speed swapped for the off-hand item's on an off-hand swing — the
     * same as if the off-hand item were in the main hand, which is how its strike resolves. Everything else
     * that changes attack speed (gear, effects) carries through.
     */
    public static double attackSpeed(LivingEntity entity, SwingHand hand) {
        if (!entity.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            return DEFAULT_ATTACK_SPEED;
        }
        double current = entity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (hand != SwingHand.OFF) {
            return Math.max(0.05, current);
        }
        return Math.max(0.05, current - addedSpeed(entity.getMainHandStack()) + addedSpeed(entity.getOffHandStack()));
    }

    /**
     * The entity's attack speed if {@code stack} were in its main hand — what a tooltip should quote for an item
     * that may not be held at all.
     */
    public static double attackSpeedWith(LivingEntity entity, ItemStack stack) {
        if (!entity.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            return DEFAULT_ATTACK_SPEED;
        }
        double current = entity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
        return Math.max(0.05, current - addedSpeed(entity.getMainHandStack()) + addedSpeed(stack));
    }

    private static double addedSpeed(ItemStack stack) {
        double[] sum = {0.0};
        stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(EntityAttributes.GENERIC_ATTACK_SPEED)
                    && modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                sum[0] += modifier.value();
            }
        });
        return sum[0];
    }

    /**
     * Hit every target with vanilla's player attack, as the swinging hand, facing {@code facingYaw}.
     * Vanilla supplies crits, enchantments, knockback, durability and stats; the cooldown is filled
     * first so damage isn't scaled by vanilla's attack-speed bar, each target's i-frames are cleared so
     * rapid swings land in full, and sweeping is suppressed by mixin. The grip's damage multiplier rides
     * along as a temporary attack-damage modifier, so it stacks with vanilla's own maths.
     */
    public static void attackWith(PlayerEntity player, SwingHand hand, float facingYaw, List<LivingEntity> targets,
                                  float damageMultiplier) {
        float savedYaw = player.getYaw();
        player.setYaw(facingYaw);
        Runnable strike = () -> {
            strikeInProgress = true;
            EntityAttributeInstance damage = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            boolean gripApplied = damage != null && Math.abs(damageMultiplier - 1.0f) > 1.0e-4f;
            if (gripApplied) {
                damage.addTemporaryModifier(new EntityAttributeModifier(GRIP_DAMAGE_ID, damageMultiplier - 1.0,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            try {
                for (LivingEntity target : targets) {
                    ((LivingEntityAccessor) player).exiled_combat$setLastAttackedTicks(10_000);
                    // Swings ignore i-frames: with the counter cleared, vanilla's damage takes the full-hit path
                    // instead of only the excess over the last hit. It re-arms them afterwards as usual, so
                    // every other damage source still respects them.
                    target.timeUntilRegen = 0;
                    player.attack(target);
                }
            } finally {
                if (gripApplied) {
                    damage.removeModifier(GRIP_DAMAGE_ID);
                }
                strikeInProgress = false;
            }
        };
        try {
            if (hand == SwingHand.OFF) {
                runWithHandsSwapped(player, strike);
            } else {
                strike.run();
            }
        } finally {
            player.setYaw(savedYaw);
        }
    }

    private static void runWithHandsSwapped(PlayerEntity player, Runnable action) {
        PlayerInventory inventory = player.getInventory();
        int slot = inventory.selectedSlot;
        ItemStack main = inventory.main.get(slot);
        ItemStack off = inventory.offHand.get(0);
        Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> mainMods = modifiers(main);
        Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> offMods = modifiers(off);

        player.getAttributes().removeModifiers(mainMods);
        player.getAttributes().addTemporaryModifiers(offMods);
        inventory.main.set(slot, off);
        inventory.offHand.set(0, main);
        try {
            action.run();
        } finally {
            inventory.main.set(slot, main);
            inventory.offHand.set(0, off);
            player.getAttributes().removeModifiers(offMods);
            player.getAttributes().addTemporaryModifiers(mainMods);
        }
    }

    private static Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> modifiers(ItemStack stack) {
        Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> map = HashMultimap.create();
        stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, map::put);
        return map;
    }
}
