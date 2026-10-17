package com.cleannrooster.clean_combos.combat;

import com.cleannrooster.clean_combos.CleanCombos;
import com.cleannrooster.clean_combos.config.CleanCombosConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Spears reach further: while a thrust weapon is in the main hand, the player's entity-interaction range gets
 * the config's {@code spearReachBonus} (+1 block by default). It is a real attribute modifier, so vanilla
 * interaction honours it too, and everything in this mod that reads reach follows.
 *
 * <p>Kept in step every player tick on both sides, so it appears the tick a spear is drawn and goes when it
 * is put away.
 */
public final class SpearReach {

    private static final Identifier MODIFIER_ID = Identifier.of(CleanCombos.MOD_ID, "spear_reach");

    private SpearReach() {
    }

    public static void tick(PlayerEntity player) {
        EntityAttributeInstance range = player.getAttributeInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
        if (range == null) {
            return;
        }
        double wanted = bonusFor(Movesets.detect(player.getMainHandStack()));
        EntityAttributeModifier current = range.getModifier(MODIFIER_ID);
        if (wanted > 0.0) {
            if (current == null || current.value() != wanted) {
                range.removeModifier(MODIFIER_ID);
                range.addTemporaryModifier(new EntityAttributeModifier(MODIFIER_ID, wanted,
                    EntityAttributeModifier.Operation.ADD_VALUE));
            }
        } else if (current != null) {
            range.removeModifier(MODIFIER_ID);
        }
    }

    /** The reach a moveset grants on top of the wielder's own: the spear bonus for thrusts, nothing else. */
    public static double bonusFor(Moveset moveset) {
        return moveset == Moveset.THRUST ? CleanCombosConfig.get().spearReachBonus : 0.0;
    }

    /** How much of {@code entity}'s interaction range is this modifier right now (0 when not applied). */
    public static double appliedBonus(LivingEntity entity) {
        EntityAttributeInstance range = entity.getAttributeInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
        EntityAttributeModifier current = range != null ? range.getModifier(MODIFIER_ID) : null;
        return current != null ? current.value() : 0.0;
    }
}
