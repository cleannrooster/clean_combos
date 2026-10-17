package com.cleannrooster.clean_combos.server;

import com.cleannrooster.clean_combos.combat.AttackSession;
import com.cleannrooster.clean_combos.combat.ComboSlot;
import com.cleannrooster.clean_combos.combat.ComboTracker;
import com.cleannrooster.clean_combos.combat.HandStats;
import com.cleannrooster.clean_combos.combat.Hitstop;
import com.cleannrooster.clean_combos.combat.PlannedSwing;
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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

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
        ComboSlot slot = request.slot() == ComboSlot.SECOND && !combo.canChain(now, CHAIN_TOLERANCE_TICKS)
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
        PlannedSwing swing = SwingPlanner.plan(player, slot, snapDistance, request.airborne());
        if (swing == null) {
            return;
        }

        SESSIONS.put(player.getUuid(), new AttackSession(swing, target, startYaw, startPitch));
        combo.onSwingStarted(slot, now + swing.effectiveWindupTicks() + swing.effectiveRecoveryTotalTicks(),
            swing.profile().chainGraceTicks());
        broadcast(player, new AttackStartS2C(player.getId(), target != null ? target.getId() : -1, slot,
            swing.snapDistance(), startYaw, startPitch, request.airborne(), swing.effectiveWindupTicks(),
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
        List<LivingEntity> targets = StrikeResolver.resolve(player, session.target(), swing, session.facingYaw(),
            session.facingPitch());
        if (targets.isEmpty()) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, player.getSoundCategory(), 1.0f, 1.0f);
            return;
        }
        HandStats.attackWith(player, hand, session.facingYaw(), targets, SwingPlanner.gripDamage(swing.grip()));

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

    public static void forget(UUID player) {
        SESSIONS.remove(player);
        COMBOS.remove(player);
    }

    public static void clear() {
        SESSIONS.clear();
        COMBOS.clear();
    }
}
