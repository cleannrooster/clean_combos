package com.cleannrooster.clean_combos.client.approach;

import com.cleannrooster.clean_combos.combat.SwingGeometry;
import com.cleannrooster.clean_combos.targeting.TargetRules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The PoE2-style snap: when a clicked enemy is out of reach, the swing starts at once and the player is
 * pushed to optimal range during its windup. It is part of the attack, not a walk — movement input is
 * locked for its whole length and cannot cancel it.
 *
 * <p>Movement goes through {@link ClientPlayerEntity#move} so collisions and step-up apply and the server
 * accepts the result as ordinary client movement. The push follows the target if it moves; its length
 * comes from the snap's distance, so a short snap is over in a tick or two, and the plan guarantees it
 * ends before the blade's fast part begins.
 */
public final class LungeController {

    /** Fastest a single tick may push, in blocks — the first tick of a long snap. */
    private static final double MAX_SPEED = 1.75;
    /** A push that achieves less than this share of its step hit a wall; stop pushing into it. */
    private static final double BLOCKED_FRACTION = 0.2;

    private static @Nullable LivingEntity target;
    private static double optimalReach;
    private static int totalTicks;
    private static int ticksDone;

    private LungeController() {
    }

    /** @param lungeTicks the planned push length ({@link com.cleannrooster.clean_combos.combat.PlannedSwing#lungeTicks}) */
    public static void begin(LivingEntity newTarget, double range, int lungeTicks) {
        target = newTarget;
        optimalReach = Math.max(0.5, range - SwingGeometry.SNAP_MARGIN);
        totalTicks = Math.max(1, lungeTicks);
        ticksDone = 0;
    }

    public static boolean isActive() {
        return target != null;
    }

    public static void cancel() {
        target = null;
    }

    /** One tick of push. Runs from the client tick, after the player's own movement. */
    public static void tick(ClientPlayerEntity player) {
        LivingEntity current = target;
        if (current == null) {
            return;
        }
        if (!current.isAlive() || current.isRemoved() || current.getWorld() != player.getWorld()
                || ticksDone >= totalTicks) {
            cancel();
            return;
        }
        double gap = TargetRules.reachDistance(player, current) - optimalReach;
        Vec3d toTarget = new Vec3d(current.getX() - player.getX(), 0.0, current.getZ() - player.getZ());
        if (gap <= 0.02 || toTarget.lengthSquared() < 1.0e-4) {
            cancel();
            return;
        }
        // Cover this tick's share of what is left on the eased schedule. Re-measuring the gap every tick
        // follows a moving target; the last tick always closes whatever remains.
        float covered = SwingGeometry.lungeEase((float) ticksDone / totalTicks);
        float next = SwingGeometry.lungeEase((float) (ticksDone + 1) / totalTicks);
        double share = covered >= 1.0f ? 1.0 : (next - covered) / (1.0f - covered);
        double step = Math.min(gap * share, MAX_SPEED);
        Vec3d before = player.getPos();
        player.move(MovementType.SELF, toTarget.normalize().multiply(step));
        ticksDone++;
        Vec3d moved = player.getPos().subtract(before);
        if (moved.horizontalLength() < step * BLOCKED_FRACTION) {
            cancel();
        }
    }
}
