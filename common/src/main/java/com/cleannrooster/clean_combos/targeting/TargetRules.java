package com.cleannrooster.clean_combos.targeting;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.UUID;

/**
 * Target rules and geometry shared by the client pick, the server's start check and the strike.
 *
 * <p>Two levels of "valid": anything {@link #isStrikeable} can be hit by a swing that reaches it, but
 * only a {@link #isHostileCandidate} is auto-picked from near the cursor — a wrong auto-pick walks the
 * player into a companion or a villager, which is far worse than a missed highlight.
 */
public final class TargetRules {

    private TargetRules() {
    }

    public static boolean isStrikeable(LivingEntity attacker, Entity entity) {
        if (!(entity instanceof LivingEntity living) || living == attacker
                || !living.isAlive() || !living.isAttackable() || living.isSpectator()
                || living.getVehicle() == attacker || attacker.getVehicle() == living
                || attacker.isTeammate(living)) {
            return false;
        }
        if (living instanceof ArmorStandEntity stand && stand.isMarker()) {
            return false;
        }
        if (living instanceof Tameable tameable) {
            UUID owner = tameable.getOwnerUuid();
            if (owner != null && owner.equals(attacker.getUuid())) {
                return false;
            }
        }
        return true;
    }

    /** Monsters only: mob targets and anger are server-side state the client can't see. */
    public static boolean isHostileCandidate(LivingEntity attacker, LivingEntity entity) {
        return isStrikeable(attacker, entity) && entity instanceof Monster
            && !(attacker instanceof PlayerEntity player && entity.isInvisibleTo(player));
    }

    /** Distance from {@code point} to the nearest point of {@code box}; 0 when inside. */
    public static double distanceToBox(Vec3d point, Box box) {
        double dx = MathHelper.clamp(point.x, box.minX, box.maxX) - point.x;
        double dy = MathHelper.clamp(point.y, box.minY, box.maxY) - point.y;
        double dz = MathHelper.clamp(point.z, box.minZ, box.maxZ) - point.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double reachDistance(LivingEntity attacker, LivingEntity target) {
        return distanceToBox(attacker.getEyePos(), target.getBoundingBox());
    }

    public static boolean hasLineOfSight(LivingEntity from, LivingEntity to) {
        Vec3d eye = from.getEyePos();
        Vec3d aim = to.getBoundingBox().getCenter();
        return from.getWorld().raycast(new RaycastContext(eye, aim,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, from))
            .getType() == HitResult.Type.MISS;
    }

    /** Minecraft yaw (degrees) that faces along {@code dx, dz}. */
    public static float yawOf(double dx, double dz) {
        return (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
    }

    /** Minecraft pitch (degrees, negative is up) from {@code from}'s eyes to the centre of {@code to}. */
    public static float pitchToward(LivingEntity from, Entity to) {
        Vec3d delta = to.getBoundingBox().getCenter().subtract(from.getEyePos());
        return pitchOf(delta);
    }

    /** Minecraft pitch (degrees, negative is up) of a direction. */
    public static float pitchOf(Vec3d direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return (float) -(MathHelper.atan2(direction.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
    }

    public static float yawToward(Entity from, Entity to) {
        Vec3d target = to.getBoundingBox().getCenter();
        return yawOf(target.x - from.getX(), target.z - from.getZ());
    }
}
