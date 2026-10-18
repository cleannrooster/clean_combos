package com.cleannrooster.clean_combos.server;

import com.cleannrooster.clean_combos.api.AttackInfo;
import com.cleannrooster.clean_combos.api.CleanCombosApi;
import com.cleannrooster.clean_combos.api.StrikeInfo;
import com.cleannrooster.clean_combos.api.StrikeModifier;
import com.cleannrooster.clean_combos.combat.AttackSession;
import com.cleannrooster.clean_combos.combat.CombatSounds;
import com.cleannrooster.clean_combos.combat.ComboSlot;
import com.cleannrooster.clean_combos.combat.ComboTracker;
import com.cleannrooster.clean_combos.combat.HandStats;
import com.cleannrooster.clean_combos.combat.Hitstop;
import com.cleannrooster.clean_combos.combat.PlannedSwing;
import com.cleannrooster.clean_combos.combat.Strike;
import com.cleannrooster.clean_combos.combat.StrikeResolver;
import com.cleannrooster.clean_combos.combat.SwingHand;
import com.cleannrooster.clean_combos.combat.SwingGeometry;
import com.cleannrooster.clean_combos.combat.SwingPlanner;
import com.cleannrooster.clean_combos.combat.SwingTracker;
import com.cleannrooster.clean_combos.combat.WeaponProfile;
import com.cleannrooster.clean_combos.network.AttackStartC2S;
import com.cleannrooster.clean_combos.network.AttackStartS2C;
import com.cleannrooster.clean_combos.network.HitstopS2C;
import com.cleannrooster.clean_combos.targeting.TargetRules;
import dev.architectury.networking.NetworkManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The authoritative side of every player swing: validates start requests, runs sessions on the server
 * tick, and resolves strikes. Movement stays client-owned; the server only decides what gets hit.
 */
public final class ServerCombat {

    /**
     * Blocks past the allowed distance a start request may arrive from (latency, client prediction). A
     * snapping swing is allowed from as far as the weapon's acquire range, since the snap happens after.
     */
    private static final double START_RANGE_SLACK = 1.25;
    private static final int CHAIN_TOLERANCE_TICKS = 6;
    /** Blocks a client's claimed snap distance may differ from the server's own measurement. */
    private static final float SNAP_TOLERANCE = 1.0f;
    private static final int CANCEL_TOLERANCE_TICKS = 3;
    private static final double BROADCAST_RANGE = 64.0;
    /**
     * Degrees the client's claimed click-time look may differ from the server's copy, which can trail by a
     * tick of mouse movement. Past this the server's own is used.
     */
    private static final float LOOK_TOLERANCE = 30.0f;

    private static final Map<UUID, AttackSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, ComboTracker> COMBOS = new HashMap<>();

    private ServerCombat() {
    }

    public static void onAttackStart(ServerPlayerEntity player, AttackStartC2S request) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        AttackSession current = SESSIONS.get(player.getUuid());
        if (current != null && !current.canBeCancelled(CANCEL_TOLERANCE_TICKS)) {
            return;
        }
        long now = player.getWorld().getTime();
        ComboTracker combo = COMBOS.computeIfAbsent(player.getUuid(), id -> new ComboTracker());
        // A jump attack stands outside the one-two: always the first swing's hand and side, combo untouched.
        ComboSlot slot = request.airborne() ? ComboSlot.FIRST
            : request.slot() == ComboSlot.SECOND && !combo.canChain(now, CHAIN_TOLERANCE_TICKS)
                ? ComboSlot.FIRST : request.slot();

        WeaponProfile profile = SwingPlanner.profileFor(player, slot);
        if (profile == null) {
            return;
        }

        // An invalid target downgrades to an in-place swing rather than a rejection: the client is
        // already animating, and the cone still decides what (if anything) is hit.
        LivingEntity target = null;
        float startYaw = request.facingYaw();
        float startPitch = MathHelper.clamp(request.facingPitch(), -SwingTracker.MAX_PITCH, SwingTracker.MAX_PITCH);
        float snapDistance = 0.0f;
        if (request.targetId() >= 0) {
            Entity entity = player.getWorld().getEntityById(request.targetId());
            boolean snapping = request.snapDistance() > 0.0f;
            if (entity instanceof LivingEntity living && TargetRules.isStrikeable(player, living)
                    && TargetRules.reachDistance(player, living) <= START_RANGE_SLACK
                        + (snapping ? profile.acquireRange() : profile.range())
                    && TargetRules.hasLineOfSight(player, living)) {
                target = living;
                startYaw = TargetRules.yawToward(player, living);
                startPitch = TargetRules.pitchToward(player, living);
                if (snapping) {
                    // The request arrives at click time, before the push, so the server can measure the gap
                    // itself; the client's figure is only trusted to within a block of that.
                    float measured = SwingGeometry.snapDistance(TargetRules.reachDistance(player, living), profile.range());
                    snapDistance = MathHelper.clamp(request.snapDistance(), Math.max(0.0f, measured - SNAP_TOLERANCE),
                        measured + SNAP_TOLERANCE);
                }
            }
        }

        // The client saw the jump at click time; the server's ground state can lag a tick behind it, so the
        // client's word is taken — it only changes the swing's shape, not its reach or damage.
        // Like the jump, the run is the client's word: it only changes the swing's shape, not its reach or damage.
        PlannedSwing swing = SwingPlanner.plan(player, slot, snapDistance, request.airborne(), request.sprinting());
        if (swing == null || !CleanCombosApi.fireAttackStart(AttackInfo.of(player, swing))) {
            return;
        }

        // Mid-swing turning is measured from the click-time look, so client and server turn the swing alike.
        float lookYaw = Math.abs(MathHelper.wrapDegrees(request.lookYaw() - player.getYaw())) <= LOOK_TOLERANCE
            ? request.lookYaw() : player.getYaw();
        float lookPitch = Math.abs(request.lookPitch() - player.getPitch()) <= LOOK_TOLERANCE
            ? request.lookPitch() : player.getPitch();
        SESSIONS.put(player.getUuid(), new AttackSession(swing, target, startYaw, startPitch, lookYaw, lookPitch));
        if (!request.airborne()) {
            combo.onSwingStarted(slot, now + swing.effectiveWindupTicks() + swing.effectiveRecoveryTotalTicks(),
                swing.profile().chainGraceTicks());
        }
        broadcast(player, new AttackStartS2C(player.getId(), target != null ? target.getId() : -1, slot,
            swing.snapDistance(), startYaw, startPitch, request.airborne(), request.sprinting(),
            swing.effectiveWindupTicks(),
            swing.effectiveRecoveryLockTicks(),
            swing.effectiveRecoveryTotalTicks()));
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, AttackSession>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AttackSession> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.isRemoved()) {
                it.remove();
                continue;
            }
            AttackSession session = entry.getValue();
            if (session.tick(player)) {
                strike(player, session);
            }
            if (session.isFinished()) {
                it.remove();
            }
        }
    }

    private static void strike(ServerPlayerEntity player, AttackSession session) {
        PlannedSwing swing = session.swing();
        SwingHand hand = swing.hand() == SwingHand.BOTH ? SwingHand.MAIN : swing.hand();
        // The weapon is frozen at start; if it left the hand mid-swing, the swing hits nothing.
        if (!ItemStack.areItemsEqual(HandStats.stackFor(player, hand), swing.weapon())) {
            return;
        }
        AttackInfo attack = AttackInfo.of(player, swing);
        boolean critical = HandStats.wouldCrit(player);
        List<Strike> strikes = new ArrayList<>();
        for (LivingEntity candidate : StrikeResolver.resolve(player, session.target(), swing, session.facingYaw(),
                session.facingPitch())) {
            StrikeInfo info = new StrikeInfo(attack, candidate, critical);
            StrikeModifier modifier = CleanCombosApi.fireStrike(info);
            if (!modifier.isCancelled()) {
                strikes.add(new Strike(candidate, info, modifier.damageMultiplier()));
            }
        }
        if (strikes.isEmpty()) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, player.getSoundCategory(), 1.0f, 1.0f);
            CleanCombosApi.fireMiss(attack);
            return;
        }
        List<LivingEntity> targets = strikes.stream().map(Strike::target).toList();
        HandStats.attackWith(player, hand, session.facingYaw(), strikes, SwingPlanner.gripDamage(swing.grip()));
        SoundEvent hit = CombatSounds.hitFor(swing);
        if (hit != null) {
            LivingEntity first = targets.get(0);
            player.getWorld().playSound(null, first.getX(), first.getBodyY(0.5), first.getZ(), hit,
                player.getSoundCategory(), 1.0f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        }

        int hitstop = Hitstop.ticksFor(swing);
        if (hitstop > 0) {
            session.applyHitstop(hitstop);
            ComboTracker combo = COMBOS.get(player.getUuid());
            if (combo != null) {
                combo.extendChain(hitstop);
            }
            broadcast(player, new HitstopS2C(player.getId(), hitstop,
                targets.stream().mapToInt(LivingEntity::getId).toArray()));
        }
    }

    private static void broadcast(ServerPlayerEntity attacker, CustomPayload payload) {
        double rangeSq = BROADCAST_RANGE * BROADCAST_RANGE;
        for (ServerPlayerEntity other : attacker.getServerWorld().getPlayers()) {
            if (other != attacker && other.squaredDistanceTo(attacker) <= rangeSq) {
                NetworkManager.sendToPlayer(other, payload);
            }
        }
    }

    /** The swing {@code player} is making now, or null. Read-only, for the public API. */
    public static @Nullable AttackSession sessionFor(UUID player) {
        return SESSIONS.get(player);
    }

    /** The combo state of {@code player}, or null if they haven't attacked. Read-only, for the public API. */
    public static @Nullable ComboTracker comboFor(UUID player) {
        return COMBOS.get(player);
    }

    public static void forget(UUID player) {
        SESSIONS.remove(player);
        COMBOS.remove(player);
    }

    public static void clear() {
        SESSIONS.clear();
        COMBOS.clear();
    }
}
