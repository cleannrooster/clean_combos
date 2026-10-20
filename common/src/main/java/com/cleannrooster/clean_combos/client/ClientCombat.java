package com.cleannrooster.clean_combos.client;

import com.cleannrooster.clean_combos.client.approach.LungeController;
import com.cleannrooster.clean_combos.client.fx.HitShake;
import com.cleannrooster.clean_combos.client.targeting.AcquisitionParams;
import com.cleannrooster.clean_combos.client.targeting.TargetAcquirer;
import com.cleannrooster.clean_combos.client.trail.TrailEffects;
import com.cleannrooster.clean_combos.combat.AttackSession;
import com.cleannrooster.clean_combos.combat.ComboSlot;
import com.cleannrooster.clean_combos.combat.ComboTracker;
import com.cleannrooster.clean_combos.combat.Hitstop;
import com.cleannrooster.clean_combos.combat.PlannedSwing;
import com.cleannrooster.clean_combos.api.AttackInfo;
import com.cleannrooster.clean_combos.api.CleanCombosApi;
import com.cleannrooster.clean_combos.combat.CombatSounds;
import com.cleannrooster.clean_combos.combat.SwingGeometry;
import com.cleannrooster.clean_combos.combat.StrikeResolver;
import com.cleannrooster.clean_combos.combat.SwingPlanner;
import com.cleannrooster.clean_combos.combat.WeaponProfile;
import com.cleannrooster.clean_combos.config.CleanCombosConfig;
import com.cleannrooster.clean_combos.network.AttackStartC2S;
import com.cleannrooster.clean_combos.network.AttackStartS2C;
import com.cleannrooster.clean_combos.network.HitstopS2C;
import com.cleannrooster.clean_combos.targeting.SwingIntent;
import com.cleannrooster.clean_combos.targeting.TargetRules;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Client half of the combat loop: turns clicks into swings (snapping in when out of reach), predicts the
 * local session for the lock and animation, mirrors other players' sessions from the server, and owns
 * the input lock.
 */
public final class ClientCombat {

    /** Fraction of screen height around the cursor in which enemies are auto-picked. */
    private static final double MAX_SCREEN_OFFSET = 0.10;
    private static final float STICKINESS = 1.25f;
    /** A click during the lock fires when the lock ends, if it ends within this many ticks. */
    private static final int BUFFER_TICKS = 10;

    private static final ComboTracker COMBO = new ComboTracker();
    private static final Map<Integer, AttackSession> REMOTE = new HashMap<>();
    private static @Nullable AttackSession own;
    private static long bufferedClickTime = -1;
    /**
     * Sprinting stays off after an attack until the player starts it again — releases and re-presses forward,
     * or presses sprint afresh — so holding forward out of a combo walks, and the next attack isn't a running
     * thrust by accident.
     */
    private static boolean sprintHeldOff;
    private static boolean sprintKeyWasDown;

    private ClientCombat() {
    }

    /** Called in place of vanilla's attack. @return true if the click was consumed */
    public static boolean onAttackClick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        long now = player.getWorld().getTime();
        ComboSlot slot = COMBO.nextSlot(now);
        WeaponProfile profile = SwingPlanner.profileFor(player, slot, wouldRunThrust(player));
        if (profile == null) {
            return false;
        }
        if (own != null && !own.canBeCancelled()) {
            bufferedClickTime = now;
            return true;
        }
        SwingIntent intent = TargetAcquirer.snapshotOnClick(player);
        // No enemy picked and a block within reach under the crosshair: let vanilla mine it.
        if (intent.target() == null && client.crosshairTarget != null
                && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            return false;
        }
        LivingEntity target = intent.target();
        if (target == null) {
            beginSwing(player, null, slot, 0.0f,
                TargetRules.yawOf(intent.fallbackFacing().x, intent.fallbackFacing().z),
                TargetRules.pitchOf(intent.fallbackFacing()));
            return true;
        }
        double reach = TargetRules.reachDistance(player, target);
        float snap = reach > profile.range() ? SwingGeometry.snapDistance(reach, profile.range()) : 0.0f;
        PlannedSwing swing = beginSwing(player, target, slot, snap, TargetRules.yawToward(player, target),
            TargetRules.pitchToward(player, target));
        if (swing != null && swing.approached()) {
            LungeController.begin(target, profile.range(), swing.lungeTicks());
        }
        return true;
    }

    /** Sprinting and actually moving forward, not just holding the sprint state while standing or backing off. */
    private static boolean isSprintingForward(ClientPlayerEntity player) {
        return player.isSprinting() && player.input.movementForward > 0.0f;
    }

    /** An attack started now would be a running thrust: sprinting forward on the ground (a sprint-jump chops). */
    private static boolean wouldRunThrust(ClientPlayerEntity player) {
        return isSprintingForward(player) && !SwingPlanner.isAirborne(player);
    }

    private static @Nullable PlannedSwing beginSwing(ClientPlayerEntity player, @Nullable LivingEntity target,
                                                     ComboSlot slot, float snapDistance, float yaw, float pitch) {
        boolean airborne = SwingPlanner.isAirborne(player);
        if (airborne) {
            // A jump attack stands outside the one-two: always the first swing's hand and side, combo untouched.
            slot = ComboSlot.FIRST;
        }
        boolean sprinting = isSprintingForward(player);
        PlannedSwing swing = SwingPlanner.plan(player, slot, snapDistance, airborne, sprinting);
        if (swing == null || !CleanCombosApi.fireAttackStart(AttackInfo.of(player, swing))) {
            return null;
        }
        LungeController.cancel();
        own = new AttackSession(swing, target, yaw, pitch, player.getYaw(), player.getPitch());
        TrailEffects.spawn(player, own);
        if (!airborne) {
            COMBO.onSwingStarted(slot, player.getWorld().getTime() + swing.effectiveWindupTicks()
                + swing.effectiveRecoveryTotalTicks(), swing.profile().chainGraceTicks());
        }
        bufferedClickTime = -1;
        sprintHeldOff = true;
        // A sprint key already held down isn't a fresh press; only pressing it again restarts sprinting.
        sprintKeyWasDown = MinecraftClient.getInstance().options.sprintKey.isPressed();
        NetworkManager.sendToServer(new AttackStartC2S(target != null ? target.getId() : -1, yaw, pitch,
            player.getYaw(), player.getPitch(), slot, swing.snapDistance(), airborne, sprinting));
        return swing;
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            reset();
            return;
        }
        long now = player.getWorld().getTime();

        WeaponProfile next = SwingPlanner.profileFor(player, COMBO.nextSlot(now), wouldRunThrust(player));
        if (next == null) {
            TargetAcquirer.clear();
        } else {
            TargetAcquirer.tick(player, new AcquisitionParams(next.acquireRange(), next.range(),
                MAX_SCREEN_OFFSET, STICKINESS));
        }

        LungeController.tick(player);

        if (own != null) {
            if (own.tick(player)) {
                predictHitstop(player, own);
            }
            playWhoosh(player, own);
            if (own.isFinished()) {
                own = null;
            }
        }

        if (bufferedClickTime >= 0) {
            if (now - bufferedClickTime > BUFFER_TICKS) {
                bufferedClickTime = -1;
            } else if (own == null || own.canBeCancelled()) {
                bufferedClickTime = -1;
                onAttackClick(client);
            }
        }

        tickRemote(client.world);
        TrailEffects.tick();
        HitShake.tick();
    }

    private static void tickRemote(ClientWorld world) {
        Iterator<Map.Entry<Integer, AttackSession>> it = REMOTE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, AttackSession> entry = it.next();
            if (!(world.getEntityById(entry.getKey()) instanceof LivingEntity attacker) || !attacker.isAlive()) {
                it.remove();
                continue;
            }
            entry.getValue().tick(attacker);
            playWhoosh(attacker, entry.getValue());
            if (entry.getValue().isFinished()) {
                it.remove();
            }
        }
    }

    /**
     * The swing's whoosh, on the tick its fast part begins. Every client plays it for every session it runs, the
     * local player's and other players' mirrored ones alike, so nothing extra crosses the network.
     */
    private static void playWhoosh(LivingEntity attacker, AttackSession session) {
        if (session.wasPausedLastTick()) {
            return;
        }
        int fastPartTick = Math.max(1, MathHelper.ceil(SwingGeometry.strikeStart(session.swing().effectiveWindupTicks())));
        if (session.elapsed() != fastPartTick) {
            return;
        }
        attacker.getWorld().playSound(attacker.getX(), attacker.getBodyY(0.6), attacker.getZ(),
            CombatSounds.swingFor(session.swing()), attacker.getSoundCategory(), 0.8f,
            0.9f + attacker.getRandom().nextFloat() * 0.2f, false);
    }

    /**
     * Hitstop for our own swing, decided locally at the strike tick with the server's own strike resolver, so
     * the freeze lands on the frame of impact instead of a round trip later.
     */
    private static void predictHitstop(ClientPlayerEntity player, AttackSession session) {
        int ticks = Hitstop.ticksFor(session.swing());
        if (ticks <= 0) {
            return;
        }
        List<LivingEntity> victims = StrikeResolver.resolve(player, session.target(), session.swing(),
            session.facingYaw(), session.facingPitch());
        if (victims.isEmpty()) {
            return;
        }
        session.applyHitstop(ticks);
        COMBO.extendChain(ticks);
        for (LivingEntity victim : victims) {
            HitShake.start(victim.getId(), ticks);
        }
    }

    /** Another player's swing connected (per the server): freeze it and shake its victims. */
    public static void onRemoteHitstop(HitstopS2C payload) {
        AttackSession session = REMOTE.get(payload.attackerId());
        if (session != null) {
            session.applyHitstop(payload.ticks());
        }
        for (int victim : payload.victimIds()) {
            HitShake.start(victim, payload.ticks());
        }
    }

    public static void onRemoteStart(AttackStartS2C payload) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || !(world.getEntityById(payload.attackerId()) instanceof LivingEntity attacker)) {
            return;
        }
        LivingEntity target = payload.targetId() >= 0 && world.getEntityById(payload.targetId()) instanceof LivingEntity t
            ? t : null;
        PlannedSwing swing = SwingPlanner.planWithTicks(attacker, payload.slot(), payload.snapDistance(),
            payload.airborne(), payload.sprinting(), payload.windupTicks(), payload.lockTicks(),
            payload.recoveryTicks());
        if (swing != null) {
            AttackSession session = new AttackSession(swing, target, payload.startYaw(), payload.startPitch(),
                attacker.getYaw(), attacker.getPitch());
            REMOTE.put(attacker.getId(), session);
            TrailEffects.spawn(attacker, session);
        }
    }

    public static @Nullable AttackSession sessionFor(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity == client.player) {
            return own;
        }
        return REMOTE.get(entity.getId());
    }

    /** The local player's combo state; other players' isn't known on this client. */
    public static @Nullable ComboTracker comboFor(Entity entity) {
        return entity == MinecraftClient.getInstance().player ? COMBO : null;
    }

    /** Every live session with its attacker: the local player's and mirrored remote ones. */
    public static void forEachSession(BiConsumer<LivingEntity, AttackSession> action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (own != null && client.player != null) {
            action.accept(client.player, own);
        }
        if (client.world != null) {
            REMOTE.forEach((id, session) -> {
                if (client.world.getEntityById(id) instanceof LivingEntity attacker) {
                    action.accept(attacker, session);
                }
            });
        }
    }

    /** Body yaw override while a session runs; the camera and head stay with the mouse. */
    public static @Nullable Float bodyFacing(LivingEntity entity) {
        AttackSession session = sessionFor(entity);
        return session != null ? session.facingYaw() : null;
    }

    /** True while the next click would land a chained second swing — what the combo cue shows. */
    public static boolean isChainOpen() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && COMBO.nextSlot(player.getWorld().getTime()) == ComboSlot.SECOND;
    }

    /**
     * True while the player's control is held by a swing: from the click to the strike. After the hit the
     * player moves, jumps and uses items freely while the recovery animation plays out; only the next attack
     * still waits for the swing's cycle ({@link AttackSession#canBeCancelled}), so attack speed stays honest.
     */
    public static boolean isLocked() {
        return own != null && !own.hasStruck();
    }

    public static boolean suppressesBlockBreaking() {
        return isLocked();
    }

    /**
     * Runs after vanilla fills this tick's movement input. The snap owns the player's movement outright, so
     * input is zeroed while it pushes; for the rest of the attack lock the player can walk at the configured
     * fraction of normal speed (not sprinting, not jumping) until the recovery lock ends.
     */
    public static void modifyInput(Input input) {
        if (LungeController.isActive()) {
            input.movementForward = 0.0f;
            input.movementSideways = 0.0f;
            input.pressingForward = false;
            input.pressingBack = false;
            input.pressingLeft = false;
            input.pressingRight = false;
            input.jumping = false;
        } else if (isLocked()) {
            float speed = CleanCombosConfig.get().attackMoveSpeed;
            input.movementForward *= speed;
            input.movementSideways *= speed;
            input.jumping = false;
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null && player.isSprinting()) {
                player.setSprinting(false);
            }
        }
        if (sprintHeldOff) {
            MinecraftClient client = MinecraftClient.getInstance();
            boolean sprintDown = client.options.sprintKey.isPressed();
            boolean freshPress = sprintDown && !sprintKeyWasDown;
            sprintKeyWasDown = sprintDown;
            // Released only once the attack has let go of movement: the snap zeroes forward input itself.
            if (!isLocked() && !LungeController.isActive() && (freshPress || !input.pressingForward)) {
                sprintHeldOff = false;
            } else if (client.player != null && client.player.isSprinting()) {
                client.player.setSprinting(false);
            }
        }
    }

    /** True while sprinting is held off after an attack (vanilla can't start a sprint). */
    public static boolean blocksSprint() {
        return sprintHeldOff;
    }

    public static void reset() {
        own = null;
        REMOTE.clear();
        COMBO.reset();
        bufferedClickTime = -1;
        sprintHeldOff = false;
        LungeController.cancel();
        TargetAcquirer.clear();
        TrailEffects.clear();
        HitShake.clear();
    }
}
