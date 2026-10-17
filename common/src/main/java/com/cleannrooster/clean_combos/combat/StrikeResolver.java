package com.cleannrooster.clean_combos.combat;

import com.cleannrooster.clean_combos.targeting.TargetRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server-side, on the STRIKE tick: who a narrow swing hits. The cone is centred on the session's tracked
 * facing and pitch and never consults the target's position on its own — a target that broke tracking
 * can be outside it. The primary target sorts first; the rest by how far off-centre, then by distance.
 */
public final class StrikeResolver {

    private record Hit(LivingEntity entity, double offAngle, double distance) {
    }

    /** Vertical tolerance around the pitched swing plane: generous, since mobs vary so much in height. */
    private static final double VERTICAL_TOLERANCE_DEG = 35.0;

    private StrikeResolver() {
    }

    public static List<LivingEntity> resolve(LivingEntity attacker, @Nullable LivingEntity primary,
                                             PlannedSwing swing, float facingYaw, float facingPitch) {
        WeaponProfile profile = swing.profile();
        Vec3d eye = attacker.getEyePos();
        Box search = attacker.getBoundingBox().expand(profile.range() + 2.0);

        List<Hit> hits = new ArrayList<>();
        for (LivingEntity candidate : attacker.getWorld().getEntitiesByClass(LivingEntity.class, search,
                e -> TargetRules.isStrikeable(attacker, e))) {
            Box box = candidate.getBoundingBox();
            double distance = TargetRules.distanceToBox(eye, box);
            if (distance > profile.range()) {
                continue;
            }
            Vec3d center = box.getCenter();
            double dx = center.x - attacker.getX();
            double dz = center.z - attacker.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            // A wide mob is hittable when any part of it is inside the cone, not just its centre.
            double halfWidthDeg = Math.toDegrees(Math.atan2(box.getLengthX() * 0.5, Math.max(horizontal, 1.0e-3)));
            double offAngle = horizontal < 1.0e-3 ? 0.0
                : Math.abs(MathHelper.wrapDegrees(TargetRules.yawOf(dx, dz) - facingYaw)) - halfWidthDeg;
            if (offAngle > profile.coneHalfAngleDeg()) {
                continue;
            }
            // The arc is tilted to the swing's pitch: a candidate well above or below that plane is missed
            // (allowing for the candidate's own height, so tall and short mobs are judged fairly).
            double halfHeightDeg = Math.toDegrees(Math.atan2(box.getLengthY() * 0.5, Math.max(horizontal, 1.0e-3)));
            double pitchOff = Math.abs(TargetRules.pitchOf(center.subtract(eye)) - facingPitch) - halfHeightDeg;
            if (pitchOff > Math.max(profile.coneHalfAngleDeg(), VERTICAL_TOLERANCE_DEG)) {
                continue;
            }
            if (!TargetRules.hasLineOfSight(attacker, candidate)) {
                continue;
            }
            hits.add(new Hit(candidate, Math.max(0.0, offAngle), distance));
        }

        hits.sort(Comparator.comparing((Hit h) -> h.entity != primary)
            .thenComparingDouble(Hit::offAngle)
            .thenComparingDouble(Hit::distance)
            .thenComparingInt(h -> h.entity.getId()));
        List<LivingEntity> result = new ArrayList<>();
        for (int i = 0; i < hits.size() && i < profile.maxTargets(); i++) {
            result.add(hits.get(i).entity);
        }
        return result;
    }
}
