package com.cleannrooster.clean_combos.client.anim;

import com.cleannrooster.clean_combos.combat.SwingGeometry;
import com.cleannrooster.clean_combos.combat.SwingStep;
import com.cleannrooster.clean_combos.combat.Moveset.PoseFamily;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Turns a {@link SwingGeometry} sample into model rotations. The held weapon is turned onto the blade
 * line — the line the trail is drawn on — with its edge facing the way it travels, pivoting about the hilt
 * so the hand stays on it. The lead arm is aimed a cocked wrist's angle ahead of that line, so the hand
 * leads the cut and the blade trails it. Nothing here has its own timing.
 *
 * <p>The body is turned to the swing's facing while a session runs (see LivingEntityClientMixin). In the
 * renderer's entity-local space that makes forward = -Z, up = +Y, right = +X; PlayerAnimator then applies
 * the {@code body} bone there as Rz·Ry·Rx around the root the arms hang from, and the model's own flip
 * turns local (x, y, z) into model (-x, -y, z). Directions are undone through the body rotation, so a
 * body clip can twist the whole player without moving the arm or weapon off the blade line.
 */
public final class SwingPoses {

    public record Pose(float pitch, float yaw, float roll) {
    }

    /** How far below the blade line a fully drawn-back thrust or punch holds the arm. */
    private static final float RETRACT_DROP_DEG = 50.0f;
    /** Yaw that brings a two-handed grip's support arm across toward the lead hand. */
    private static final float SUPPORT_CONVERGE = 0.45f;

    /**
     * A handheld item sprite in model space: handle at the bottom-left, tip at the top-right, and the
     * cutting side (where an axe's head sits) toward the upper-left.
     */
    static final Vector3f ITEM_BLADE = new Vector3f(1.0f, 1.0f, 0.0f).normalize();
    static final Vector3f ITEM_EDGE = new Vector3f(-1.0f, 1.0f, 0.0f).normalize();

    /**
     * How far the arm leads the blade along the direction of travel. A straight arm-and-blade line looks
     * like the weapon is lying along the forearm; with the wrist cocked the hand leads the cut, the blade
     * trails it at an angle, and the fist visibly closes on the hilt. The blade itself stays on the swing
     * line either way.
     */
    private static final double WRIST_LEAD_DEG = 35.0;

    /**
     * A held item's third-person display, as far as posing needs it.
     *
     * @param rotation the display transform's rotation
     * @param blade    which way the blade points (handle to tip) as held, in the item-bone frame
     * @param edge     which way its cutting side faces as held
     * @param grip     where the hand closes on the item, in the item-bone frame under the model's own display —
     *                 the point the item bone must rotate about so the hand stays on the hilt
     */
    public record ItemDisplay(Matrix3f rotation, Vector3f blade, Vector3f edge, Vector3f grip) {
    }

    private SwingPoses() {
    }

    /**
     * @param bodyRotation the {@code body} bone's rotation this frame (radians, PlayerAnimator's value
     *                     space), so the arm can be aimed in the rotated frame
     */
    public static Pose leadArm(PoseFamily family, SwingStep step, float progress, float pitch, Vector3f bodyRotation) {
        Vec3d dir = bladeDirection(family, step, progress, pitch);
        if (SwingGeometry.sweeps(family)) {
            Vec3d edge = SwingGeometry.edgeDirection(family, step, canonicalFrame(pitch), progress);
            double lead = Math.toRadians(WRIST_LEAD_DEG);
            dir = dir.multiply(Math.cos(lead)).add(edge.multiply(Math.sin(lead)));
        } else {
            // Drawn back below the line, rising onto it as the strike extends.
            double drop = Math.toRadians(RETRACT_DROP_DEG * (1.0f - SwingGeometry.extension(progress)) / 0.65f);
            dir = new Vec3d(dir.x, dir.y * Math.cos(drop) - dir.z * Math.sin(drop),
                dir.z * Math.cos(drop) + dir.y * Math.sin(drop));
        }
        Vector3f model = toModel(dir, bodyRotation);
        return aim(model.x, model.y, model.z);
    }

    /** Model-space blade line (body undone) — where the weapon should point. */
    public static Vector3f bladeModel(PoseFamily family, SwingStep step, float progress, float pitch,
                                      Vector3f bodyRotation) {
        return toModel(bladeDirection(family, step, progress, pitch), bodyRotation);
    }

    /** Model-space direction of travel (body undone) — where the weapon's edge should face. */
    public static Vector3f edgeModel(PoseFamily family, SwingStep step, float progress, float pitch,
                                     Vector3f bodyRotation) {
        return toModel(SwingGeometry.edgeDirection(family, step, canonicalFrame(pitch), progress), bodyRotation);
    }

    /**
     * The item-bone rotation (PlayerAnimator's {@code rightItem}/{@code leftItem}, applied as Rz·Ry·Rx)
     * that lays the weapon along {@code blade} with its edge toward {@code edge}, given the arm's actual
     * rotation this frame and the item's display rotation.
     *
     * <p>Vanilla's chain from arm to sprite is: arm (Rz·Ry·Rx about its pivot), X −90°, Y 180°, the item
     * bone, then the model's third-person display transform. Everything but the item bone is known, so
     * the item bone is the rotation taking the displayed sprite's (blade, edge) frame onto the targets
     * expressed in the post-arm frame.
     *
     * @return euler angles (x, y, z) for the item bone, or null when blade and edge are degenerate
     */
    public static @Nullable Vector3f itemRotation(Vector3f blade, Vector3f edge, Pose arm, ItemDisplay display) {
        Matrix3f armFrame = new Matrix3f().rotationZYX(arm.roll(), arm.yaw(), arm.pitch())
            .rotateX(-MathHelper.HALF_PI).rotateY(MathHelper.PI);
        Matrix3f target = frame(armFrame.transformTranspose(new Vector3f(blade)),
            armFrame.transformTranspose(new Vector3f(edge)));
        Matrix3f source = frame(new Vector3f(display.blade()), new Vector3f(display.edge()));
        if (target == null || source == null) {
            return null;
        }
        return target.mul(source.transpose()).getEulerAnglesZYX(new Vector3f());
    }

    /**
     * A stack's third-person display, read from its actual baked model so resource packs and modded
     * weapons with their own display settings come out right. Null for built-in models (tridents,
     * shields), whose geometry isn't a flat sprite.
     */
    public static @Nullable ItemDisplay itemDisplay(ItemStack stack, LivingEntity holder, boolean leftArm) {
        if (stack.isEmpty()) {
            return null;
        }
        BakedModel model = MinecraftClient.getInstance().getItemRenderer().getModel(stack, holder.getWorld(), holder, 0);
        if (model == null || model.isBuiltin()) {
            return null;
        }
        MatrixStack scratch = new MatrixStack();
        model.getTransformation().getTransformation(leftArm
            ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND
            : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND).apply(leftArm, scratch);
        Matrix4f transform = scratch.peek().getPositionMatrix();
        Matrix3f rotation = rotationOf(transform);
        if (rotation == null) {
            return null;
        }
        // Where the blade lies in the model: vanilla's icon layout (handle bottom-left, tip top-right, cutting side
        // up-left) carried back through the model's own GUI rotation, the one that makes it look like a normal
        // icon in the inventory. Identity for vanilla items; for a model built lying flat or upright it turns the
        // layout to match, so a weapon modelled in any orientation is posed by its real blade.
        MatrixStack gui = new MatrixStack();
        model.getTransformation().getTransformation(ModelTransformationMode.GUI).apply(false, gui);
        Matrix3f guiRotation = rotationOf(gui.peek().getPositionMatrix());
        Matrix3f iconToModel = guiRotation != null ? guiRotation.transpose() : new Matrix3f();
        Vector3f heldBlade = rotation.transform(iconToModel.transform(new Vector3f(ITEM_BLADE))).normalize();
        Vector3f heldEdge = rotation.transform(iconToModel.transform(new Vector3f(ITEM_EDGE))).normalize();
        // The hand sits at the bone origin, and the model's display puts its handle there — wherever the
        // item's author chose, at any size or offset. So the grip is the point on the displayed blade line
        // (through the model's centre, which ItemRenderer shifts onto the display origin) closest to the hand.
        // A fixed sprite pixel only fits vanilla-sized handheld sprites; modded models with their own scale or
        // offset then pivoted off the handle and slid along it. The line runs through the model's actual geometry,
        // not its box's centre: a model built against one side of its box (a flat sword lying at the bottom)
        // would otherwise pivot about empty space and slide out of the hand whenever the weapon turns.
        Vector3f centre = transform.transformPosition(geometryOffset(model));
        Vector3f grip = new Vector3f(heldBlade).mul(-centre.dot(heldBlade)).add(centre);
        return new ItemDisplay(rotation, heldBlade, heldEdge, grip);
    }

    private static final Map<BakedModel, Vector3f> GEOMETRY_OFFSETS = new WeakHashMap<>();
    private static final int VERTEX_INTS = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.getVertexSizeByte() / 4;

    /**
     * Where the middle of a model's actual geometry sits relative to its box's centre, in blocks — zero for vanilla
     * items, whose sprite is centred. Read once per model from its quads' vertex bounds.
     */
    private static Vector3f geometryOffset(BakedModel model) {
        Vector3f cached = GEOMETRY_OFFSETS.get(model);
        if (cached != null) {
            return new Vector3f(cached);
        }
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        Random random = Random.create(42L);
        List<Direction> faces = new ArrayList<>(Arrays.asList(Direction.values()));
        faces.add(null);
        for (Direction face : faces) {
            for (BakedQuad quad : model.getQuads(null, face, random)) {
                int[] data = quad.getVertexData();
                for (int v = 0; v + 2 < data.length; v += VERTEX_INTS) {
                    Vector3f point = new Vector3f(Float.intBitsToFloat(data[v]), Float.intBitsToFloat(data[v + 1]),
                        Float.intBitsToFloat(data[v + 2]));
                    min.min(point);
                    max.max(point);
                }
            }
        }
        Vector3f offset = min.x <= max.x ? new Vector3f(min).add(max).mul(0.5f).sub(0.5f, 0.5f, 0.5f) : new Vector3f();
        GEOMETRY_OFFSETS.put(model, offset);
        return new Vector3f(offset);
    }

    /** The rotation part of a display transform (its scale divided out), or null if it collapses an axis. */
    private static @Nullable Matrix3f rotationOf(Matrix4f transform) {
        Matrix3f rotation = new Matrix3f();
        transform.get3x3(rotation);
        Vector3f column = new Vector3f();
        for (int i = 0; i < 3; i++) {
            rotation.getColumn(i, column);
            if (column.lengthSquared() < 1.0e-8f) {
                return null;
            }
            rotation.setColumn(i, column.normalize());
        }
        return rotation;
    }

    /**
     * The item-bone translation, in PlayerAnimator's pixel units, that turns a rotation about the bone's
     * origin into one about the hilt. PlayerAnimator translates and then rotates, so a point p lands at
     * offset + R·p; keeping the grip g fixed needs offset = g − R·g.
     */
    public static Vector3f gripPivotOffset(Quaternionf rotation, ItemDisplay display) {
        Vector3f rotated = rotation.transform(new Vector3f(display.grip()));
        return new Vector3f(display.grip()).sub(rotated).mul(16.0f);
    }

    /** Orthonormal frame with {@code a} as the first column and {@code b} (made perpendicular) as the second. */
    private static @Nullable Matrix3f frame(Vector3f a, Vector3f b) {
        if (a.lengthSquared() < 1.0e-8f) {
            return null;
        }
        a.normalize();
        b.fma(-a.dot(b), a);
        if (b.lengthSquared() < 1.0e-6f) {
            return null;
        }
        b.normalize();
        return new Matrix3f(a, b, new Vector3f(a).cross(b));
    }

    /** The body is turned to the swing's yaw but not its pitch, so poses work in a yaw-0 frame that keeps the pitch. */
    private static SwingGeometry.Frame canonicalFrame(float pitch) {
        return SwingGeometry.Frame.of(Vec3d.ZERO, 0.0f, pitch);
    }

    /** Blade direction in the canonical frame: yaw 0 faces +Z, right = -X, up = +Y. */
    private static Vec3d bladeDirection(PoseFamily family, SwingStep step, float progress, float pitch) {
        return SwingGeometry.bladeDirection(family, step, canonicalFrame(pitch), progress);
    }

    /** Canonical-frame direction -> model space, with the body bone's rotation undone. */
    private static Vector3f toModel(Vec3d dir, Vector3f bodyRotation) {
        // (right, up, -forward) = (-x, y, -z) is the renderer's entity-local space.
        Vector3f local = new Vector3f((float) -dir.x, (float) dir.y, (float) -dir.z);
        new Quaternionf().rotateZ(bodyRotation.z).rotateY(bodyRotation.y).rotateX(bodyRotation.x)
            .conjugate().transform(local);
        return new Vector3f(-local.x, -local.y, local.z);
    }

    /**
     * Rotation that points a downward-hanging arm along model-space direction {@code (x, y, z)}.
     * ModelPart applies Rz·Ry·Rx, which takes the arm's (0, 1, 0) to (sin p sin y, cos p, sin p cos y).
     */
    private static Pose aim(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-6) {
            return new Pose(0.0f, 0.0f, 0.0f);
        }
        x /= length;
        y /= length;
        z /= length;
        float pitch = (float) -Math.acos(MathHelper.clamp(y, -1.0, 1.0));
        float yaw = Math.abs(Math.sin(pitch)) < 1.0e-4 ? 0.0f : (float) Math.atan2(-x, -z);
        return new Pose(pitch, yaw, 0.0f);
    }

    /** The support arm of a two-handed grip: the lead arm's line, angled across so the hands meet. */
    public static Pose supportArm(Pose lead, boolean leadIsRight) {
        return new Pose(lead.pitch(), lead.yaw() + (leadIsRight ? SUPPORT_CONVERGE : -SUPPORT_CONVERGE), 0.0f);
    }

    /** Whole-model twist following the blade: a fraction of its sideways travel, or the thrust's shoulder drive. */
    public static float torsoYaw(PoseFamily family, SwingStep step, float progress, float pitch, boolean leadIsRight) {
        if (!SwingGeometry.sweeps(family)) {
            float drive = MathHelper.lerp(SwingGeometry.extension(progress), 0.25f, -0.2f);
            return leadIsRight ? drive : -drive;
        }
        Vec3d dir = bladeDirection(family, step, progress, pitch);
        return (float) Math.atan2(-dir.x, Math.max(1.0e-3, dir.z)) * 0.3f;
    }

    public static Pose blend(Pose vanilla, Pose swing, float weight) {
        return new Pose(
            MathHelper.lerp(weight, vanilla.pitch(), swing.pitch()),
            MathHelper.lerp(weight, vanilla.yaw(), swing.yaw()),
            MathHelper.lerp(weight, vanilla.roll(), swing.roll()));
    }
}
