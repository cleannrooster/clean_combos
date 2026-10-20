package com.cleannrooster.clean_combos.client.anim;

import com.cleannrooster.clean_combos.CleanCombos;
import com.cleannrooster.clean_combos.client.ClientCombat;
import com.cleannrooster.clean_combos.combat.AttackSession;
import com.cleannrooster.clean_combos.combat.Grip;
import com.cleannrooster.clean_combos.combat.SwingPlanner;
import com.cleannrooster.clean_combos.config.CleanCombosClientConfig;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The in-combat stance: for {@link #COMBAT_TICKS} after an attack, a player standing with something Clean Combos
 * attacks with holds the authored ready pose for the grip. It blends in the moment an attack's recovery hands
 * off, eases out when they walk (and back in when they stop, while still in combat), and eases out for good once
 * the combat window runs out.
 *
 * <ul>
 *   <li>{@code idle_2h} — two-handed: both hands raised on the weapon.</li>
 *   <li>{@code idle_1h} — one-handed (a shield, torch or nothing in the off hand): weapon arm raised.</li>
 *   <li>{@code idle_dualwield} — dual wield: both arms relaxed, mirror-symmetric.</li>
 *   <li>{@code idle_unarmed} — fist weapons (claws, gauntlets...): fists up. A bare empty hand gets no stance.</li>
 * </ul>
 *
 * Poses are authored right-handed; a left-handed player gets the mirror image. It sits under the attack layer and
 * clears out fast when an attack starts, since the attack's clip is layered over whatever pose the body is in.
 * In first person the posed weapons are drawn from the camera, as during attacks — with the arms when
 * {@code firstPersonArms} is on, and always for fist weapons. Client config {@code idleStances}.
 */
public final class IdleStanceAnimation implements IAnimation {

    private static final String[] BONES = {"body", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "torso"};
    private static final FirstPersonConfiguration ARMS_AND_ITEMS = new FirstPersonConfiguration(true, true, true, true);
    private static final FirstPersonConfiguration ITEMS_ONLY = new FirstPersonConfiguration(false, false, true, true);
    /** Ticks to ease in, and out when moving or doing something else. */
    private static final float BLEND_TICKS = 6.0f;
    /** Ticks to clear out when an attack starts. */
    private static final float ATTACK_CLEAR_TICKS = 2.0f;
    /** How long after the last attack the player counts as in combat: 4 seconds. */
    private static final int COMBAT_TICKS = 80;
    /**
     * Walk-animation speed below which the player counts as standing still. The same signal vanilla swings the
     * legs by: it rises the moment walking starts and is right for other players too. (Position minus last
     * tick's position isn't: layers tick before the player moves, so it read zero and walking never ended it.)
     */
    private static final float STILL_LIMB_SPEED = 0.05f;

    private enum Stance {
        TWO_HANDED("idle_2h"),
        ONE_HANDED("idle_1h"),
        DUAL_WIELD("idle_dualwield"),
        UNARMED("idle_unarmed");

        final Identifier id;

        Stance(String name) {
            this.id = Identifier.of(CleanCombos.MOD_ID, name);
        }
    }

    private final AbstractClientPlayerEntity player;
    private final Map<String, Vector3f> rotation = new HashMap<>();
    private final Map<String, Vector3f> position = new HashMap<>();
    private @Nullable Stance shown;
    private float weight;
    private float prevWeight;
    private float frameWeight;
    private int lastAttackAge = Integer.MIN_VALUE / 2;
    private int lastEvaluatedAge = Integer.MIN_VALUE;
    private @Nullable Stance wanted;
    /** In combat with something to fight with: first person stays in the posed-model view, stance or not. */
    private @Nullable Stance combat;
    private boolean attacking;

    public IdleStanceAnimation(AbstractClientPlayerEntity player) {
        this.player = player;
    }

    /** Also where the stance is decided, once per tick: an inactive layer is never ticked, so it must wake itself. */
    @Override
    public boolean isActive() {
        evaluate();
        return weight > 0.0f || prevWeight > 0.0f || wanted != null || combat != null;
    }

    @Override
    public void tick() {
        evaluate();
        prevWeight = weight;
        if (wanted != null && wanted == shown) {
            weight = Math.min(1.0f, weight + 1.0f / BLEND_TICKS);
        } else {
            // Out first; a different stance takes over once the old one has gone.
            weight = Math.max(0.0f, weight - 1.0f / (attacking ? ATTACK_CLEAR_TICKS : BLEND_TICKS));
            if (weight <= 0.0f) {
                shown = wanted;
            }
        }
    }

    private void evaluate() {
        if (player.age == lastEvaluatedAge) {
            return;
        }
        lastEvaluatedAge = player.age;
        AttackSession session = ClientCombat.sessionFor(player);
        if (session != null) {
            lastAttackAge = player.age;
        }
        // Only the windup clears the stance. From the hit on, the stance blends in under the attack's recovery,
        // which fades out over it: one crossfade, instead of dropping to the neutral pose and rising again.
        attacking = session != null && !session.hasStruck();
        boolean inCombat = player.age - lastAttackAge <= COMBAT_TICKS;
        boolean still = player.limbAnimator.getSpeed() < STILL_LIMB_SPEED && player.isOnGround() && !player.isSneaking()
            && !player.isUsingItem() && !player.hasVehicle() && !player.isSwimming() && !player.isFallFlying()
            && !player.isSleeping() && !player.isSpectator() && !attacking;
        combat = CleanCombosClientConfig.get().idleStances && inCombat ? stanceFor() : null;
        wanted = still ? combat : null;
    }

    private @Nullable Stance stanceFor() {
        SwingPlanner.Hands hands = SwingPlanner.hands(player);
        // Nothing in hand: just standing there, no fighting stance.
        if (hands == null || player.getMainHandStack().isEmpty()) {
            return null;
        }
        if (hands.main().isFist()) {
            return Stance.UNARMED;
        }
        return hands.grip() == Grip.TWO_HANDED ? Stance.TWO_HANDED
            : hands.grip() == Grip.DUAL_WIELD ? Stance.DUAL_WIELD : Stance.ONE_HANDED;
    }

    @Override
    public void setupAnim(float tickDelta) {
        float w = MathHelper.lerp(tickDelta, prevWeight, weight);
        frameWeight = w * w * (3.0f - 2.0f * w);
        rotation.clear();
        position.clear();
        Stance stance = shown;
        KeyframeAnimation animation = stance != null && frameWeight > 0.0f
            && PlayerAnimationRegistry.getAnimation(stance.id) instanceof KeyframeAnimation keyframes ? keyframes : null;
        if (animation == null) {
            frameWeight = 0.0f;
            return;
        }
        boolean mirror = player.getMainArm() == Arm.LEFT;
        for (String bone : BONES) {
            String source = mirror ? mirrorBone(bone) : bone;
            Vector3f rot = ClipSampler.sample(animation, source, TransformType.ROTATION, 0.0f);
            Vector3f pos = ClipSampler.sample(animation, source, TransformType.POSITION, 0.0f);
            if (mirror) {
                rot.set(rot.x, -rot.y, -rot.z);
                pos.set(-pos.x, pos.y, pos.z);
            }
            rotation.put(bone, rot.mul(frameWeight));
            position.put(bone, pos.mul(frameWeight));
        }
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
        if (frameWeight <= 0.0f) {
            return value0;
        }
        Vector3f offset = switch (type) {
            case ROTATION -> rotation.get(modelName);
            case POSITION -> position.get(modelName);
            default -> null;
        };
        if (offset == null) {
            return value0;
        }
        return new Vec3f(value0.getX() + offset.x, value0.getY() + offset.y, value0.getZ() + offset.z);
    }

    /**
     * First person draws the posed model's weapons (and arms, per config) for the whole combat window — walking
     * between attacks included — so the view doesn't flick back to vanilla's hand between swings.
     */
    @Override
    public FirstPersonMode getFirstPersonMode(float tickDelta) {
        return combat != null || (shown != null && (weight > 0.0f || prevWeight > 0.0f))
            ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
    }

    @Override
    public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        return shown == Stance.UNARMED || combat == Stance.UNARMED || CleanCombosClientConfig.get().firstPersonArms
            ? ARMS_AND_ITEMS : ITEMS_ONLY;
    }

    private static String mirrorBone(String bone) {
        return switch (bone) {
            case "rightArm" -> "leftArm";
            case "leftArm" -> "rightArm";
            case "rightLeg" -> "leftLeg";
            case "leftLeg" -> "rightLeg";
            default -> bone;
        };
    }
}
