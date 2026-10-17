package com.cleannrooster.exiled_combat.client.anim;

import com.cleannrooster.exiled_combat.client.ClientCombat;
import com.cleannrooster.exiled_combat.client.anim.SwingPoses.Pose;
import com.cleannrooster.exiled_combat.combat.AttackSession;
import com.cleannrooster.exiled_combat.combat.PlannedSwing;
import com.cleannrooster.exiled_combat.combat.SwingGeometry;
import com.cleannrooster.exiled_combat.combat.SwingHand;
import com.cleannrooster.exiled_combat.combat.Moveset.PoseFamily;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * PlayerAnimator layer for a player's active {@link AttackSession}:
 * <ul>
 *   <li>arms are procedural, aimed down the {@link SwingGeometry} blade line (counter-rotated against the
 *       body bone so they stay on it), and the swinging weapon is turned through its item bone to continue
 *       that line with its edge leading the cut;</li>
 *   <li>body, head and legs come from the hand-authored {@link BodyClip} for the archetype, time-warped
 *       onto the swing's phases and mirrored for right-to-left swings;</li>
 *   <li>a snapping swing layers the lunge clip on top: extended across the push, then settling on its
 *       authored rhythm and eased to rest.</li>
 * </ul>
 * The arm left free by the grip plays the clip's {@code left_arm} keys. Clip bones are layered
 * additively over vanilla, so the head still follows the mouse. In first person the same posed arms and
 * weapon are drawn from the camera (PlayerAnimator's third-person-model mode), so the view shows the real
 * swing rather than vanilla's hand bob. Inactive without a session, so vanilla shows through.
 */
public final class ProceduralSwingAnimation implements IAnimation {

    private static final String[] CLIP_BONES = {"body", "head", "rightLeg", "leftLeg"};
    /** Arms take clip motion only when free: the lead arm and a two-handed support arm stay procedural. */
    private static final String[] ARM_BONES = {"rightArm", "leftArm"};
    /** Both arms and both held items: the support hand and an off-hand weapon belong in view too. */
    private static final FirstPersonConfiguration FIRST_PERSON = new FirstPersonConfiguration(true, true, true, true);
    /** Shortest the lunge's unwind may be squeezed to, in ticks (authored: 10). */
    private static final float LUNGE_MIN_SETTLE_TICKS = 4.0f;
    /** Share of the lunge's return after which a smooth envelope eases it the rest of the way to rest. */
    private static final float LUNGE_ENVELOPE_START = 0.6f;

    private final AbstractClientPlayerEntity player;

    // Per-frame state from setupAnim.
    private boolean posing;
    private float armWeight;
    private Pose leadPose = new Pose(0.0f, 0.0f, 0.0f);
    private @Nullable Pose supportPose;
    private float torsoYaw;
    private String leadBone = "rightArm";
    private String supportBone = "leftArm";
    private String leadItemBone = "rightItem";
    private @Nullable String freeArmBone;
    private @Nullable Vector3f bladeModel;
    private @Nullable Vector3f edgeModel;
    private @Nullable SwingPoses.ItemDisplay itemDisplay;
    /** This frame's item-bone rotation, kept so the position call can pivot it about the hilt. */
    private @Nullable Quaternionf itemRotation;
    /** Last hilt-pivot translation handed to PlayerAnimator, for the debug readout. */
    private @Nullable Vector3f lastItemOffset;
    /** The lead arm rotation actually handed to PlayerAnimator this frame (vanilla-blended). */
    private @Nullable Pose appliedLead;
    private final Map<String, Vector3f> clipRotation = new HashMap<>();
    private final Map<String, Vector3f> clipPosition = new HashMap<>();

    /** Every player's layer, so the debug command can read the local one. Weak: players come and go. */
    private static final Map<AbstractClientPlayerEntity, ProceduralSwingAnimation> LAYERS = new WeakHashMap<>();

    public ProceduralSwingAnimation(AbstractClientPlayerEntity player) {
        this.player = player;
        LAYERS.put(player, this);
    }

    public static @Nullable ProceduralSwingAnimation layerFor(AbstractClientPlayerEntity player) {
        return LAYERS.get(player);
    }

    @Override
    public boolean isActive() {
        return ClientCombat.sessionFor(player) != null;
    }

    @Override
    public void setupAnim(float tickDelta) {
        AttackSession session = ClientCombat.sessionFor(player);
        if (session == null) {
            posing = false;
            return;
        }
        PlannedSwing swing = session.swing();
        PoseFamily family = swing.profile().moveset().poseFamily;
        float elapsed = session.elapsed(tickDelta);
        float pitch = session.facingPitch(tickDelta);
        SwingGeometry.Sample sample = SwingGeometry.sample(swing, elapsed);

        boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
        boolean leadIsRight = swing.hand() == SwingHand.OFF ? !mainIsRight : mainIsRight;
        leadBone = leadIsRight ? "rightArm" : "leftArm";
        supportBone = leadIsRight ? "leftArm" : "rightArm";
        // The arm that is neither swinging nor gripping is free for the clip to animate.
        freeArmBone = swing.hand() == SwingHand.BOTH ? null : supportBone;

        float clipWeight = sampleClips(swing, elapsed, sample.weight());

        Vector3f body = clipRotation.get("body");
        leadPose = SwingPoses.leadArm(family, swing.step(), sample.progress(), pitch, body);
        leadItemBone = leadIsRight ? "rightItem" : "leftItem";
        bladeModel = SwingPoses.bladeModel(family, swing.step(), sample.progress(), pitch, body);
        edgeModel = SwingPoses.edgeModel(family, swing.step(), sample.progress(), pitch, body);
        itemDisplay = SwingPoses.itemDisplay(swing.weapon(), player, !leadIsRight);
        appliedLead = null;
        itemRotation = null;
        supportPose = swing.hand() == SwingHand.BOTH ? SwingPoses.supportArm(leadPose, leadIsRight) : null;
        // A body clip carries the twist itself; the procedural twist only fills in for clip-less swings.
        torsoYaw = SwingPoses.torsoYaw(family, swing.step(), sample.progress(), pitch, leadIsRight) * (1.0f - clipWeight);
        armWeight = sample.weight();
        posing = true;
    }

    /**
     * Fill the clip offsets for this frame.
     *
     * @return how much a clip is driving the body (0..1), for fading the procedural twist out under it
     */
    private float sampleClips(PlannedSwing swing, float elapsed, float swingWeight) {
        for (String bone : CLIP_BONES) {
            clipRotation.put(bone, new Vector3f());
            clipPosition.put(bone, new Vector3f());
        }
        for (String arm : ARM_BONES) {
            clipRotation.put(arm, new Vector3f());
            clipPosition.put(arm, new Vector3f());
        }
        boolean mirror = BodyClip.mirrored(swing);

        // The lunge is a layer on top of the attack clip, not a replacement for it. Its lean builds across the
        // push and peaks as the snap decelerates into the strike, then unwinds through the follow-through,
        // so the forward momentum carries into the cut instead of settling before it.
        float lungeWeight = 0.0f;
        int windup = swing.effectiveWindupTicks();
        if (swing.approached()) {
            int push = swing.lungeTicks();
            float total = windup + swing.effectiveRecoveryTotalTicks();
            float settleEnd = Math.min(Math.max(SwingGeometry.strikeEnd(windup), push + LUNGE_MIN_SETTLE_TICKS),
                total - 0.5f);
            lungeWeight = 1.0f - smoothstep((elapsed - MathHelper.lerp(LUNGE_ENVELOPE_START, push, settleEnd))
                / Math.max(0.5f, (1.0f - LUNGE_ENVELOPE_START) * (settleEnd - push)));
            addClip(BodyClip.LUNGE, BodyClip.LUNGE.lungeTick(elapsed, push, settleEnd), lungeWeight, mirror);
        }

        BodyClip attack = BodyClip.forMoveset(swing.profile().moveset());
        float attackWeight = 0.0f;
        if (attack != null) {
            attackWeight = swingWeight;
            addClip(attack, attack.attackTick(swing, elapsed), attackWeight, mirror);
        }
        return Math.max(attackWeight, lungeWeight);
    }

    private void addClip(BodyClip clip, float tick, float weight, boolean mirror) {
        KeyframeAnimation animation = clip.animation();
        if (animation == null || weight <= 0.0f) {
            return;
        }
        for (String bone : CLIP_BONES) {
            // Mirroring swaps sides and reflects across the body's midplane: yaw and roll flip, x moves flip.
            String source = mirror ? mirrorBone(bone) : bone;
            Vector3f rotation = ClipSampler.sample(animation, source, TransformType.ROTATION, tick);
            Vector3f position = ClipSampler.sample(animation, source, TransformType.POSITION, tick);
            if (mirror) {
                rotation.set(rotation.x, -rotation.y, -rotation.z);
                position.set(-position.x, position.y, position.z);
            }
            clipRotation.get(bone).fma(weight, rotation);
            clipPosition.get(bone).fma(weight, position);
        }
        if (freeArmBone != null) {
            // Clips are authored with the right hand leading, so their left_arm is the free arm's motion;
            // when the free arm is the right one, that motion is reflected onto it.
            Vector3f rotation = ClipSampler.sample(animation, "leftArm", TransformType.ROTATION, tick);
            Vector3f position = ClipSampler.sample(animation, "leftArm", TransformType.POSITION, tick);
            if (freeArmBone.equals("rightArm")) {
                rotation.set(rotation.x, -rotation.y, -rotation.z);
                position.set(-position.x, position.y, position.z);
            }
            clipRotation.get(freeArmBone).fma(weight, rotation);
            clipPosition.get(freeArmBone).fma(weight, position);
        }
    }

    private static float smoothstep(float value) {
        float x = MathHelper.clamp(value, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static String mirrorBone(String bone) {
        return switch (bone) {
            case "rightLeg" -> "leftLeg";
            case "leftLeg" -> "rightLeg";
            default -> bone;
        };
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
        if (!posing) {
            return value0;
        }
        if (type == TransformType.ROTATION) {
            Pose vanilla = new Pose(value0.getX(), value0.getY(), value0.getZ());
            if (modelName.equals(leadBone)) {
                appliedLead = SwingPoses.blend(vanilla, leadPose, armWeight);
                return toVec(appliedLead);
            }
            if (modelName.equals(leadItemBone)) {
                Quaternionf rotation = leadItemRotation();
                if (rotation == null) {
                    return value0;
                }
                Vector3f euler = new Matrix3f().set(rotation).getEulerAnglesZYX(new Vector3f());
                return new Vec3f(euler.x, euler.y, euler.z);
            }
            if (supportPose != null && modelName.equals(supportBone)) {
                return toVec(SwingPoses.blend(vanilla, supportPose, armWeight));
            }
            if (modelName.equals("torso")) {
                return new Vec3f(value0.getX(), value0.getY() + torsoYaw * armWeight, value0.getZ());
            }
        }
        if (type == TransformType.POSITION && modelName.equals(leadItemBone)) {
            // Rotate about the hilt, not the bone origin, so the hand stays closed on the handle.
            Quaternionf rotation = leadItemRotation();
            if (rotation == null || itemDisplay == null) {
                return value0;
            }
            Vector3f pivot = SwingPoses.gripPivotOffset(rotation, itemDisplay);
            lastItemOffset = pivot;
            return new Vec3f(value0.getX() + pivot.x, value0.getY() + pivot.y, value0.getZ() + pivot.z);
        }
        Vector3f offset = switch (type) {
            case ROTATION -> clipRotation.get(modelName);
            case POSITION -> clipPosition.get(modelName);
            default -> null;
        };
        if (offset == null) {
            return value0;
        }
        return new Vec3f(value0.getX() + offset.x, value0.getY() + offset.y, value0.getZ() + offset.z);
    }

    /**
     * The weapon's item-bone rotation: blade along the swing line, edge facing the way it travels, eased in
     * and out with the arm. Computed once per frame on first request, so the rotation and the hilt-pivot
     * translation always agree whichever PlayerAnimator asks for first. Items render after the model, so
     * the arm rotation applied this frame is already known.
     */
    private @Nullable Quaternionf leadItemRotation() {
        if (itemRotation != null) {
            return itemRotation;
        }
        if (bladeModel == null || edgeModel == null || itemDisplay == null) {
            return null;
        }
        Vector3f euler = SwingPoses.itemRotation(bladeModel, edgeModel, appliedLead != null ? appliedLead : leadPose,
            itemDisplay.rotation());
        if (euler == null) {
            return null;
        }
        // The item bone's rest rotation is zero, so easing in is a slerp from identity.
        itemRotation = new Quaternionf().slerp(new Quaternionf().rotateZYX(euler.z, euler.y, euler.x), armWeight);
        return itemRotation;
    }

    /** First person draws the posed model's arms and items, so the swing seen is the swing performed. */
    @Override
    public FirstPersonMode getFirstPersonMode(float tickDelta) {
        // Decided from the session, not from setupAnim's state: in first person the model only renders
        // (and setupAnim only runs) once this has said so.
        return ClientCombat.sessionFor(player) != null ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
    }

    @Override
    public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        return FIRST_PERSON;
    }

    /** One line for {@code /exiled_combat debug}: what the weapon bone was last given. */
    public String debugLine() {
        return String.format("item bone %s: display %s, rotation %s, hilt offset %s px",
            leadItemBone, itemDisplay != null ? "ok" : "none (built-in/empty)",
            itemRotation != null ? "set" : "unset",
            lastItemOffset != null ? String.format("(%.2f, %.2f, %.2f)", lastItemOffset.x, lastItemOffset.y,
                lastItemOffset.z) : "never applied");
    }

    private static Vec3f toVec(Pose pose) {
        return new Vec3f(pose.pitch(), pose.yaw(), pose.roll());
    }
}
