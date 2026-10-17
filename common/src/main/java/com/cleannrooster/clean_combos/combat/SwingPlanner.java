package com.cleannrooster.clean_combos.combat;

import com.cleannrooster.clean_combos.config.CleanCombosConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the hands at call time and turns (slot, snap distance) into a frozen {@link PlannedSwing}.
 * Grip, hand and weapon are always re-derived from the current hands, never from the previous swing,
 * which is what lets a weapon swap inside the chain window keep the chain.
 *
 * <p>Grip comes from the hands alone, no input: an empty off hand two-hands a swing or thrust weapon (chops
 * stay one-handed, fists dual-punch); a non-weapon off-hand item means one-handed; two weapons dual wield.
 * Timing comes from the swinging hand's attack speed, adjusted by the grip.
 */
public final class SwingPlanner {

    /** Vanilla's attack time: a full attack-cooldown cycle is {@code 20 / attackSpeed} ticks. */
    private static final float TICKS_PER_ATTACK_AT_SPEED_ONE = 20.0f;

    private SwingPlanner() {
    }

    /** The resolved hands: grip plus the moveset each hand would swing with ({@code off} null unless dual). */
    public record Hands(Grip grip, Moveset main, @Nullable Moveset off) {
    }

    /**
     * @return null when the main-hand item is left to vanilla (the vanilla mace by default, or a
     *         {@code "vanilla"} config override): this system stands aside and vanilla attacks as usual
     */
    public static @Nullable Hands hands(LivingEntity entity) {
        Moveset main = Movesets.detect(entity.getMainHandStack());
        if (main == null) {
            return null;
        }
        ItemStack offStack = entity.getOffHandStack();
        if (offStack.isEmpty()) {
            if (main.isFist()) {
                return new Hands(Grip.DUAL_WIELD, main, Moveset.FIST);
            }
            return new Hands(main.twoHandsWhenFree() ? Grip.TWO_HANDED : Grip.ONE_HANDED, main, null);
        }
        // An off-hand item left to vanilla counts as a non-weapon: the main weapon is simply one-handed.
        Moveset off = Movesets.detect(offStack);
        if (off != null && !main.isFist() && !off.isFist()) {
            return new Hands(Grip.DUAL_WIELD, main, off);
        }
        return new Hands(Grip.ONE_HANDED, main, null);
    }

    public static SwingHand handFor(ComboSlot slot, Grip grip) {
        return switch (grip) {
            case TWO_HANDED -> SwingHand.BOTH;
            case DUAL_WIELD -> slot == ComboSlot.FIRST ? SwingHand.MAIN : SwingHand.OFF;
            case ONE_HANDED -> SwingHand.MAIN;
        };
    }

    /** The profile of the moveset that swings in {@code slot}; drives acquisition and approach range. */
    public static @Nullable WeaponProfile profileFor(LivingEntity entity, ComboSlot slot) {
        Hands hands = hands(entity);
        if (hands == null) {
            return null;
        }
        boolean off = handFor(slot, hands.grip()) == SwingHand.OFF;
        return Movesets.profile(off && hands.off() != null ? hands.off() : hands.main(), entity);
    }

    /**
     * Whether an attack started now counts as a jump attack: off the ground from a jump or a fall — not
     * flying, gliding, swimming, climbing or riding.
     */
    public static boolean isAirborne(LivingEntity entity) {
        if (entity.isOnGround() || entity.hasVehicle() || entity.isClimbing() || entity.isTouchingWater()
                || entity.isFallFlying()) {
            return false;
        }
        return !(entity instanceof PlayerEntity player && player.getAbilities().flying);
    }

    /** Attack-speed multiplier the grip gives (config). */
    public static float gripSpeed(Grip grip) {
        CleanCombosConfig config = CleanCombosConfig.get();
        return switch (grip) {
            case TWO_HANDED -> config.twoHandedSpeed;
            case DUAL_WIELD -> config.dualWieldSpeed;
            case ONE_HANDED -> 1.0f;
        };
    }

    /** Attack-damage multiplier the grip gives (config). */
    public static float gripDamage(Grip grip) {
        CleanCombosConfig config = CleanCombosConfig.get();
        return switch (grip) {
            case TWO_HANDED -> config.twoHandedDamage;
            case DUAL_WIELD -> config.dualWieldDamage;
            case ONE_HANDED -> 1.0f;
        };
    }

    /**
     * @param snapDistance blocks the swing snaps to reach its target ({@link SwingGeometry#snapDistance}),
     *                     0 for none. A short snap costs little: its push is brief and it pays only a share
     *                     of the approach penalty, so the cut lands soon after arriving.
     * @param airborne     the attack was started in the air: every moveset but thrust becomes a downward chop
     *                     ({@link Moveset#AERIAL}). The weapon, grip and attack speed are unchanged.
     */
    public static @Nullable PlannedSwing plan(LivingEntity attacker, ComboSlot slot, float snapDistance,
                                              boolean airborne) {
        Hands hands = hands(attacker);
        if (hands == null) {
            return null;
        }
        SwingHand hand = handFor(slot, hands.grip());
        Moveset moveset = hand == SwingHand.OFF && hands.off() != null ? hands.off() : hands.main();
        if (airborne && moveset != Moveset.THRUST) {
            moveset = Moveset.AERIAL;
        }
        WeaponProfile profile = Movesets.profile(moveset, attacker);
        SwingStep step = profile.step(slot);

        double attackSpeed = HandStats.attackSpeed(attacker, hand) * gripSpeed(hands.grip());
        float cycle = swingCycleTicks(profile.timing(), slot, attackSpeed);
        float windupShare = slot == ComboSlot.FIRST ? profile.timing().firstWindupShare()
            : profile.timing().secondWindupShare();

        int lunge = SwingGeometry.lungeTicks(snapDistance);
        float penalty = lunge > 0 ? profile.approachPenaltyTicks() * SwingGeometry.snapPenaltyShare(snapDistance) : 0.0f;
        int windup = Math.max(2, Math.round(cycle * windupShare + penalty));
        if (lunge > 0) {
            // The push must end before the blade's fast part (at most 2.5 ticks before the strike) begins.
            windup = Math.max(windup, lunge + 3);
        }
        int lock = Math.max(1, Math.round(cycle * (1.0f - windupShare)));
        // The arm keeps settling a little past the lock; it no longer holds the player back.
        int total = lock + Math.max(2, lock);
        return new PlannedSwing(slot, hands.grip(), hand, HandStats.stackFor(attacker, hand).copy(),
            profile, step, lunge > 0 ? snapDistance : 0.0f, lunge, windup, lock, total);
    }

    /**
     * Ticks from this swing's click to the next swing being allowed. The pair of swings averages vanilla's
     * attack time scaled by the config's pace; the one-two beat (scaled by {@code comboRhythm}) gives the
     * first swing more of the pair and the second less, so a combo's total time doesn't depend on rhythm.
     */
    public static float swingCycleTicks(MovesetTiming timing, ComboSlot slot, double attackSpeed) {
        CleanCombosConfig config = CleanCombosConfig.get();
        float average = Math.max(timing.minCycleTicks(),
            (float) (TICKS_PER_ATTACK_AT_SPEED_ONE / Math.max(0.05, attackSpeed)) * config.attackPace);
        float beat = timing.beat() * MathHelper.clamp(config.comboRhythm, 0.0f, 1.0f);
        return average * (slot == ComboSlot.FIRST ? 1.0f + beat : 1.0f - beat);
    }

    /** A remote player's swing as the server timed it; only the timings come from the packet. */
    public static @Nullable PlannedSwing planWithTicks(LivingEntity attacker, ComboSlot slot, float snapDistance,
                                                       boolean airborne, int windup, int lock, int total) {
        PlannedSwing local = plan(attacker, slot, snapDistance, airborne);
        if (local == null) {
            return null;
        }
        return new PlannedSwing(slot, local.grip(), local.hand(), local.weapon(), local.profile(), local.step(),
            local.snapDistance(), local.lungeTicks(), windup, lock, total);
    }
}
