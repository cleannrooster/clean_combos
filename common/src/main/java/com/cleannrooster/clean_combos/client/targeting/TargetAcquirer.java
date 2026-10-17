package com.cleannrooster.clean_combos.client.targeting;

import com.cleannrooster.clean_combos.targeting.SwingIntent;
import com.cleannrooster.clean_combos.targeting.TargetRules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which enemy a click would mean, re-decided every client tick (visceral_combat's TargetAcquisition,
 * measured in screen pixels from the cursor instead of degrees from the crosshair).
 *
 * <p>Monsters near the cursor are auto-picked; anything else strikeable is picked only when the cursor is
 * directly on it, so hunting a cow works but a companion beside a zombie is never chosen by accident.
 */
public final class TargetAcquirer {

    private static final double OFFSET_WEIGHT = 3.0;
    private static final double DISTANCE_WEIGHT = 1.0;
    private static final double IN_REACH_BONUS = 0.5;
    private static final int LOS_CHECK_LIMIT = 3;

    private record Candidate(LivingEntity entity, double score) {
    }

    private static @Nullable LivingEntity hovered;

    private TargetAcquirer() {
    }

    public static @Nullable LivingEntity hovered() {
        return hovered;
    }

    public static void clear() {
        hovered = null;
    }

    public static void tick(ClientPlayerEntity player, AcquisitionParams params) {
        if (!ScreenProjector.hasFrame() || !player.isAlive() || player.isSpectator()) {
            clear();
            return;
        }
        List<Candidate> candidates = new ArrayList<>();
        Box search = player.getBoundingBox().expand(params.acquireRange() + 1.0);
        for (LivingEntity entity : player.getWorld().getEntitiesByClass(LivingEntity.class, search,
                e -> TargetRules.isStrikeable(player, e))) {
            double distance = TargetRules.reachDistance(player, entity);
            if (distance > params.acquireRange()) {
                continue;
            }
            ScreenRect rect = ScreenProjector.project(entity.getBoundingBox()).orElse(null);
            if (rect == null) {
                continue;
            }
            double offset = ScreenProjector.offset(rect);
            boolean underCursor = offset <= 0.0;
            if (offset > params.maxScreenOffset()
                    || !(underCursor || TargetRules.isHostileCandidate(player, entity))) {
                continue;
            }
            double offsetScore = 1.0 - offset / Math.max(1.0e-4, params.maxScreenOffset());
            double distanceScore = 1.0 - MathHelper.clamp(distance / params.acquireRange(), 0.0, 1.0);
            double inReach = distance <= params.trueReach() ? 1.0 : 0.0;
            double score = offsetScore * OFFSET_WEIGHT + distanceScore * DISTANCE_WEIGHT + inReach * IN_REACH_BONUS;
            candidates.add(new Candidate(entity, entity == hovered ? score * params.stickiness() : score));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed()
            .thenComparingInt(c -> c.entity().getId()));

        hovered = null;
        int checked = 0;
        for (Candidate candidate : candidates) {
            if (checked++ >= LOS_CHECK_LIMIT) {
                break;
            }
            if (TargetRules.hasLineOfSight(player, candidate.entity())) {
                hovered = candidate.entity();
                break;
            }
        }
    }

    /** What a click right now means: the hovered enemy, or an in-place swing toward the cursor. */
    public static SwingIntent snapshotOnClick(ClientPlayerEntity player) {
        LivingEntity target = hovered != null && hovered.isAlive() && !hovered.isRemoved() ? hovered : null;
        return new SwingIntent(target, fallbackFacing(player));
    }

    /** Look direction in first person; toward the cursor's ground point when the cursor is free. */
    private static Vec3d fallbackFacing(ClientPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        if (net.minecraft.client.MinecraftClient.getInstance().mouse.isCursorLocked()) {
            return look;
        }
        ScreenProjector.Ray ray = ScreenProjector.cursorRay();
        if (ray == null || ray.direction().y > -1.0e-3) {
            return look;
        }
        double t = (player.getY() - ray.origin().y) / ray.direction().y;
        Vec3d point = ray.origin().add(ray.direction().multiply(t));
        Vec3d toPoint = new Vec3d(point.x - player.getX(), 0.0, point.z - player.getZ());
        return toPoint.lengthSquared() < 1.0e-4 ? look : toPoint;
    }
}
