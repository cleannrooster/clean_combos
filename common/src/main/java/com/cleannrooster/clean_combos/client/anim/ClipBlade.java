package com.cleannrooster.clean_combos.client.anim;

import com.cleannrooster.clean_combos.combat.AttackSession;
import com.cleannrooster.clean_combos.combat.PlannedSwing;
import com.cleannrooster.clean_combos.combat.SwingGeometry;
import com.cleannrooster.clean_combos.combat.SwingHand;
import com.cleannrooster.clean_combos.config.CleanCombosConfig;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Clip-only animation (config {@code clipOnlyAnimation}): where the weapon really is when the attack clip
 * plays whole, arms included, with the weapon in vanilla's hold.
 *
 * <p>Forward kinematics through the same chain the renderer uses: the clip's body rotation, the lead arm
 * (vanilla's item-holding pose plus the clip's {@code right_arm}) about its shoulder, vanilla's held-item
 * turn and hand offset, then the item's own third-person display. The result is the hand position and the
 * blade and edge directions in the world, at any blade progress — what the trail is drawn from, so it follows
 * the swing the clip performs rather than the authored arc.
 *
 * <p>Clips are authored with the right hand leading, left to right. A right-to-left swing reflects them across
 * the body's midplane, arms included, whichever hand leads: the direction of the cut decides the reflection,
 * not the hand. So an off-hand second swing is the main-hand one's mirror image, and the two cross in an X.
 * A mirrored two-handed clip also swaps the arm tracks — the weapon arm plays the mirrored left_arm, the other
 * the mirrored right_arm — a true mirror image, so both hands stay on the handle. That alone would cut the first
 * hit's diagonal again (the support arm is near the weapon arm's mirror image, so the weapon arm keeps its
 * original orientation, and vanilla's hold is right-handed), so the weapon is also turned in the hand just
 * enough for the blade to be the original blade's mirror image ({@link #mirroredBlade}) — the X.
 */
public final class ClipBlade {

    /** Vanilla's arm pitch while holding an item (BipedEntityModel's ITEM arm pose). */
    private static final float VANILLA_HOLD_PITCH = -MathHelper.PI / 10.0f;
    /** Player model pixels above the feet the model's origin (the neck) sits, before the 0.9375 render scale. */
    private static final double NECK_PIXELS = 24.0;
    private static final double MODEL_SCALE = 0.9375 / 16.0;
    private static final double STANDARD_HEIGHT = 1.8;
    private static final Vector3f ITEM_BLADE = new Vector3f(1.0f, 1.0f, 0.0f).normalize();
    private static final Vector3f ITEM_EDGE = new Vector3f(-1.0f, 1.0f, 0.0f).normalize();
    /** Vanilla's handheld third-person rotation (0, -90, 55), for models whose display can't be read. */
    private static final Matrix3f DEFAULT_RIGHT_DISPLAY = new Matrix3f().rotationXYZ(0.0f,
        -MathHelper.HALF_PI, 55.0f * MathHelper.RADIANS_PER_DEGREE);
    private static final Matrix3f DEFAULT_LEFT_DISPLAY = new Matrix3f().rotationXYZ(0.0f,
        MathHelper.HALF_PI, -55.0f * MathHelper.RADIANS_PER_DEGREE);

    /** The weapon at one instant, in world space. */
    public record Pose(Vec3d hand, Vec3d blade, Vec3d edge) {
    }

    private final PlannedSwing swing;
    private final BodyClip clip;
    private final KeyframeAnimation animation;
    private final boolean mirror;
    private final boolean swapArms;
    private final boolean leadIsRight;
    private final Matrix3f display;
    private final Vec3d neck;
    private final SwingGeometry.Frame yawFrame;
    private float cachedProgress = Float.NaN;
    private @Nullable Pose cached;

    private ClipBlade(PlannedSwing swing, BodyClip clip, KeyframeAnimation animation, boolean leadIsRight,
                      Matrix3f display, Vec3d neck, SwingGeometry.Frame yawFrame) {
        this.swing = swing;
        this.clip = clip;
        this.animation = animation;
        this.mirror = BodyClip.mirrored(swing);
        this.swapArms = swapsArms(clip, swing);
        this.leadIsRight = leadIsRight;
        this.display = display;
        this.neck = neck;
        this.yawFrame = yawFrame;
    }

    /** True when {@code swing} plays its clip whole: clip-only animation is on and the moveset has a clip. */
    public static boolean drives(PlannedSwing swing) {
        return CleanCombosConfig.get().clipOnlyAnimation && BodyClip.forMoveset(swing.profile().moveset()) != null;
    }

    public static boolean leadIsRight(LivingEntity attacker, PlannedSwing swing) {
        boolean mainIsRight = attacker.getMainArm() == Arm.RIGHT;
        return swing.hand() == SwingHand.OFF ? !mainIsRight : mainIsRight;
    }

    /** True for a mirrored two-handed clip, whose arm tracks swap (see the class notes). */
    static boolean swapsArms(BodyClip clip, PlannedSwing swing) {
        return BodyClip.mirrored(swing) && clip.playsTwoHanded(swing);
    }

    /** The clip track the weapon arm plays. */
    static String leadTrack(boolean swapArms) {
        return swapArms ? "leftArm" : "rightArm";
    }

    /** The clip track the other arm plays. */
    static String otherTrack(boolean swapArms) {
        return swapArms ? "rightArm" : "leftArm";
    }

    /**
     * Where a mirrored two-handed swing's blade should point: the unmirrored clip's weapon (right arm, vanilla
     * hold, the item's display) reflected across the body's midplane. Body-local model space, the space the
     * arm and item bones are posed in.
     *
     * @return {blade, edge}
     */
    static Vector3f[] mirroredBlade(KeyframeAnimation animation, float tick, Matrix3f display) {
        Matrix3f armFrame = armFrame(armTrack(animation, "rightArm", tick, false)[0]);
        Vector3f blade = armFrame.transform(display.transform(new Vector3f(ITEM_BLADE)));
        Vector3f edge = armFrame.transform(display.transform(new Vector3f(ITEM_EDGE)));
        blade.x = -blade.x;
        edge.x = -edge.x;
        return new Vector3f[]{blade, edge};
    }

    /** The held item's frame for an arm at clip rotation {@code clipRotation} over vanilla's holding pose. */
    private static Matrix3f armFrame(Vector3f clipRotation) {
        Vector3f rotation = new Vector3f(clipRotation).add(VANILLA_HOLD_PITCH, 0.0f, 0.0f);
        // ModelPart applies Rz·Ry·Rx about the shoulder; vanilla then turns to the held item's frame.
        return new Matrix3f().rotationZYX(rotation.z, rotation.y, rotation.x)
            .rotateX(-MathHelper.HALF_PI).rotateY(MathHelper.PI);
    }

    /**
     * One of the clip's arm tracks ({@code "rightArm"}: the lead arm's motion, {@code "leftArm"}: the other's)
     * at {@code tick}, reflected across the midplane if asked.
     *
     * @return {rotation, position}
     */
    static Vector3f[] armTrack(KeyframeAnimation animation, String track, float tick, boolean reflect) {
        Vector3f rotation = ClipSampler.sample(animation, track, TransformType.ROTATION, tick);
        Vector3f position = ClipSampler.sample(animation, track, TransformType.POSITION, tick);
        if (reflect) {
            rotation.set(rotation.x, -rotation.y, -rotation.z);
            position.set(-position.x, position.y, position.z);
        }
        return new Vector3f[]{rotation, position};
    }

    /** The weapon path for {@code session} this frame, or null when the swing isn't clip-driven. */
    public static @Nullable ClipBlade of(LivingEntity attacker, AttackSession session, float tickDelta) {
        PlannedSwing swing = session.swing();
        if (!drives(swing)) {
            return null;
        }
        BodyClip clip = BodyClip.forMoveset(swing.profile().moveset());
        KeyframeAnimation animation = clip != null ? clip.animationFor(swing) : null;
        if (animation == null) {
            return null;
        }
        boolean leadIsRight = leadIsRight(attacker, swing);
        SwingPoses.ItemDisplay itemDisplay = SwingPoses.itemDisplay(swing.weapon(), attacker, !leadIsRight);
        Matrix3f display = itemDisplay != null ? itemDisplay.rotation()
            : leadIsRight ? DEFAULT_RIGHT_DISPLAY : DEFAULT_LEFT_DISPLAY;
        double neckHeight = NECK_PIXELS * MODEL_SCALE * attacker.getHeight() / STANDARD_HEIGHT;
        Vec3d neck = attacker.getLerpedPos(tickDelta).add(0.0, neckHeight, 0.0);
        // The body only turns to the swing's yaw, but the hit is aimed with its pitch too, so the clip's weapon
        // path is tilted about the neck by the facing pitch: the trail angles with the aim like the strike does.
        SwingGeometry.Frame yawFrame = SwingGeometry.Frame.of(neck, session.facingYaw(attacker, tickDelta),
            session.facingPitch(attacker, tickDelta));
        return new ClipBlade(swing, clip, animation, leadIsRight, display, neck, yawFrame);
    }

    /** Where the hand and blade are at blade progress {@code progress} (0..1, 0.5 on the strike tick). */
    public Pose at(float progress) {
        if (cached != null && progress == cachedProgress) {
            return cached;
        }
        float tick = clip.attackTick(swing, elapsedAt(progress));

        Vector3f bodyRotation = ClipSampler.sample(animation, "body", TransformType.ROTATION, tick);
        if (mirror) {
            bodyRotation.set(bodyRotation.x, -bodyRotation.y, -bodyRotation.z);
        }
        Quaternionf body = new Quaternionf().rotateZ(bodyRotation.z).rotateY(bodyRotation.y).rotateX(bodyRotation.x);

        Vector3f[] arm = armTrack(animation, leadTrack(swapArms), tick, mirror);
        Matrix3f armFrame = armFrame(arm[0]);
        Vector3f shoulder = new Vector3f(leadIsRight ? -5.0f : 5.0f, 2.0f, 0.0f).add(arm[1]);
        Vector3f hand = armFrame.transform(new Vector3f(leadIsRight ? 1.0f : -1.0f, 2.0f, -10.0f)).add(shoulder);
        Vector3f blade;
        Vector3f edge;
        if (swapArms) {
            Vector3f[] mirrored = mirroredBlade(animation, tick, display);
            blade = mirrored[0];
            edge = mirrored[1];
        } else {
            blade = armFrame.transform(display.transform(new Vector3f(ITEM_BLADE)));
            edge = armFrame.transform(display.transform(new Vector3f(ITEM_EDGE)));
        }

        Vec3d handWorld = neck.add(toWorld(hand, body).multiply(MODEL_SCALE));
        cached = new Pose(handWorld, toWorld(blade, body).normalize(), toWorld(edge, body).normalize());
        cachedProgress = progress;
        return cached;
    }

    /**
     * Model space (body-local, y down, x flipped) to world: redo the body bone's rotation, then the
     * renderer's flip, then the swing's yaw and pitch. The inverse of SwingPoses' model mapping, plus the pitch.
     */
    private Vec3d toWorld(Vector3f model, Quaternionf body) {
        Vector3f local = body.transform(new Vector3f(-model.x, -model.y, model.z));
        // Canonical yaw-0 frame: forward +Z, right -X, up +Y.
        double forward = -local.z;
        double right = local.x;
        double up = local.y;
        return yawFrame.forward().multiply(forward).add(yawFrame.right().multiply(right))
            .add(yawFrame.up().multiply(up));
    }

    /** Session time at which the blade reaches {@code progress}: the inverse of the strike's smoothstep. */
    private float elapsedAt(float progress) {
        int windup = swing.effectiveWindupTicks();
        float start = SwingGeometry.strikeStart(windup);
        float end = SwingGeometry.strikeEnd(windup);
        float p = MathHelper.clamp(progress, 0.0f, 1.0f);
        float u = 0.5f - (float) Math.sin(Math.asin(1.0f - 2.0f * p) / 3.0);
        return start + u * (end - start);
    }
}
