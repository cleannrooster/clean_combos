package com.cleannrooster.clean_combos.api;

import com.cleannrooster.clean_combos.combat.AttackPhase;
import com.cleannrooster.clean_combos.combat.AttackSession;
import com.cleannrooster.clean_combos.combat.ComboSlot;
import com.cleannrooster.clean_combos.combat.ComboTracker;
import com.cleannrooster.clean_combos.combat.Moveset;
import com.cleannrooster.clean_combos.combat.Movesets;
import com.cleannrooster.clean_combos.combat.WeaponType;
import com.cleannrooster.clean_combos.combat.WeaponTypes;
import com.cleannrooster.clean_combos.server.ServerCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * The public entry point for other mods: everything here is stable across 1.x. Classes outside the {@code api}
 * package are internal and may change. See {@code docs/API.md} for a walkthrough.
 *
 * <h2>Classification</h2>
 * {@link #weaponType}, {@link #moveset}: what an item is and how it attacks. Packs and mods can override both
 * with item tags ({@code #clean_combos:weapon_type/<type>}, {@code #clean_combos:moveset/<moveset>},
 * {@code #clean_combos:vanilla_attack}).
 *
 * <h2>Attack state</h2>
 * {@link #isAttacking}, {@link #activeAttack}, {@link #attackPhase}, {@link #attackProgress},
 * {@link #nextComboSlot}, {@link #isChainOpen}. On the server for any player; on a client for every player in
 * view (other players' attacks are mirrored), combo state for the local player only.
 *
 * <h2>Events</h2>
 * {@link #onAttackStart} (cancellable), {@link #onStrike} (per target: scale damage or cancel), {@link #onMiss},
 * and {@link #currentStrike} while a hit resolves. Strikes and misses fire on the server only.
 */
public final class CleanCombosApi {

    /** Allow or refuse an attack as it starts. Fires on the attacking client and on the server. */
    @FunctionalInterface
    public interface AttackStartListener {
        /** @return false to refuse the attack (stamina, cooldowns...); register on both sides to stay in sync */
        boolean allow(AttackInfo attack);
    }

    /** One target about to be hit. Server only. */
    @FunctionalInterface
    public interface StrikeListener {
        void onStrike(StrikeInfo strike, StrikeModifier modifier);
    }

    /** An attack that hit nothing. Server only. */
    @FunctionalInterface
    public interface MissListener {
        void onMiss(AttackInfo attack);
    }

    private static final List<AttackStartListener> START_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<StrikeListener> STRIKE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<MissListener> MISS_LISTENERS = new CopyOnWriteArrayList<>();

    /** The client's lookups, installed by client init; absent on a dedicated server. */
    private static @Nullable Function<LivingEntity, @Nullable AttackSession> clientSessions;
    private static @Nullable Function<LivingEntity, @Nullable ComboTracker> clientCombos;
    private static @Nullable StrikeInfo currentStrike;

    private CleanCombosApi() {
    }

    // --- Classification -------------------------------------------------------------------------------------

    /** What kind of weapon {@code stack} is (sword, rapier, mace, claw, fist...). */
    public static WeaponType weaponType(ItemStack stack) {
        return WeaponTypes.of(stack);
    }

    /** How {@code stack} attacks (swing, chop, thrust, fist), or null when it's left to vanilla. */
    public static @Nullable Moveset moveset(ItemStack stack) {
        return Movesets.detect(stack);
    }

    /** The damage kind {@code stack} deals with its normal attack (not a jump or running attack). */
    public static @Nullable DamageKind damageKind(ItemStack stack) {
        Moveset moveset = Movesets.detect(stack);
        return moveset != null ? DamageKind.of(WeaponTypes.of(stack), moveset) : null;
    }

    // --- Attack state ---------------------------------------------------------------------------------------

    public static boolean isAttacking(LivingEntity entity) {
        return sessionFor(entity) != null;
    }

    /** The attack {@code entity} is making now, or null between attacks. */
    public static @Nullable AttackInfo activeAttack(LivingEntity entity) {
        AttackSession session = sessionFor(entity);
        return session != null ? AttackInfo.of(entity, session.swing()) : null;
    }

    /** The weapon type of the attack {@code entity} is making now, or null. */
    public static @Nullable WeaponType activeWeaponType(LivingEntity entity) {
        AttackInfo attack = activeAttack(entity);
        return attack != null ? attack.weaponType() : null;
    }

    /** The moveset of the attack {@code entity} is making now, situation included, or null. */
    public static @Nullable Moveset activeMoveset(LivingEntity entity) {
        AttackInfo attack = activeAttack(entity);
        return attack != null ? attack.moveset() : null;
    }

    /** Windup, the strike tick, or recovery; null between attacks. */
    public static @Nullable AttackPhase attackPhase(LivingEntity entity) {
        AttackSession session = sessionFor(entity);
        return session != null ? session.phase() : null;
    }

    /** How far through its attack {@code entity} is, 0 to 1 (0 between attacks). The hit lands at {@link #strikeProgress}. */
    public static float attackProgress(LivingEntity entity) {
        AttackSession session = sessionFor(entity);
        return session != null ? Math.min(1.0f, session.elapsed() / (float) Math.max(1, session.totalTicks())) : 0.0f;
    }

    /** The {@link #attackProgress} at which the current attack's hit lands (0 between attacks). */
    public static float strikeProgress(LivingEntity entity) {
        AttackSession session = sessionFor(entity);
        return session != null
            ? session.swing().effectiveWindupTicks() / (float) Math.max(1, session.totalTicks()) : 0.0f;
    }

    /** Which hit of the combo the next attack would be. On a client, the local player only (else FIRST). */
    public static ComboSlot nextComboSlot(LivingEntity entity) {
        ComboTracker combo = comboFor(entity);
        return combo != null ? combo.nextSlot(entity.getWorld().getTime()) : ComboSlot.FIRST;
    }

    /** True while the next attack would be the combo's second hit. */
    public static boolean isChainOpen(LivingEntity entity) {
        return nextComboSlot(entity) == ComboSlot.SECOND;
    }

    // --- Events ---------------------------------------------------------------------------------------------

    public static void onAttackStart(AttackStartListener listener) {
        START_LISTENERS.add(listener);
    }

    public static void onStrike(StrikeListener listener) {
        STRIKE_LISTENERS.add(listener);
    }

    public static void onMiss(MissListener listener) {
        MISS_LISTENERS.add(listener);
    }

    /**
     * The hit resolving right now, or null. Set around vanilla's own attack call for each target, so damage,
     * enchantment and death hooks can tell a Clean Combos hit — and its kind — from any other player attack.
     * Server thread only.
     */
    public static @Nullable StrikeInfo currentStrike() {
        return currentStrike;
    }

    // --- Internal hooks (called by Clean Combos itself) -----------------------------------------------------

    /** Internal. */
    public static boolean fireAttackStart(AttackInfo attack) {
        for (AttackStartListener listener : START_LISTENERS) {
            if (!listener.allow(attack)) {
                return false;
            }
        }
        return true;
    }

    /** Internal. */
    public static StrikeModifier fireStrike(StrikeInfo strike) {
        StrikeModifier modifier = new StrikeModifier();
        for (StrikeListener listener : STRIKE_LISTENERS) {
            listener.onStrike(strike, modifier);
        }
        return modifier;
    }

    /** Internal. */
    public static void fireMiss(AttackInfo attack) {
        for (MissListener listener : MISS_LISTENERS) {
            listener.onMiss(attack);
        }
    }

    /** Internal. */
    public static void setCurrentStrike(@Nullable StrikeInfo strike) {
        currentStrike = strike;
    }

    /** Internal: client init installs the client-side lookups. */
    public static void installClient(Function<LivingEntity, @Nullable AttackSession> sessions,
                                     Function<LivingEntity, @Nullable ComboTracker> combos) {
        clientSessions = sessions;
        clientCombos = combos;
    }

    private static @Nullable AttackSession sessionFor(LivingEntity entity) {
        AttackSession session;
        if (!entity.getWorld().isClient()) {
            session = ServerCombat.sessionFor(entity.getUuid());
        } else {
            session = clientSessions != null ? clientSessions.apply(entity) : null;
        }
        return session != null && !session.isFinished() ? session : null;
    }

    private static @Nullable ComboTracker comboFor(LivingEntity entity) {
        if (!entity.getWorld().isClient()) {
            return ServerCombat.comboFor(entity.getUuid());
        }
        return clientCombos != null ? clientCombos.apply(entity) : null;
    }
}
