package com.cleannrooster.exiled_combat.client.trail;

import com.cleannrooster.exiled_combat.combat.AttackSession;
import com.cleannrooster.exiled_combat.combat.PlannedSwing;
import com.cleannrooster.exiled_combat.combat.SwingGeometry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/**
 * One trail playing on the client. It holds no geometry: every frame the ribbon is regenerated from
 * {@link SwingGeometry} with the session's swing and facing, the same sample the arm pose uses — so the
 * leading edge of the trail is where the arm is pointing, and it crosses centre on the strike tick.
 *
 * <p>It keeps its own clock (started with the session, ticked alongside it) so it can finish fading
 * after the session it belongs to has been replaced by the next swing.
 */
final class TrailEffect {

    private static final float FIRST_PERSON_CLEARANCE = 1.25f;
    static final int FADE_OUT_TICKS = 4;

    private final AttackSession session;
    private final LivingEntity attacker;
    private final TrailPalette palette;
    private final float visualScale;
    private final int seed;
    private final int lifetime;
    private int age;

    TrailEffect(AttackSession session, LivingEntity attacker, TrailPalette palette, float visualScale, int seed) {
        this.session = session;
        this.attacker = attacker;
        this.palette = palette;
        this.visualScale = visualScale;
        this.seed = seed;
        this.lifetime = (int) Math.ceil(SwingGeometry.strikeEnd(session.swing().effectiveWindupTicks())) + FADE_OUT_TICKS;
    }

    PlannedSwing swing() {
        return session.swing();
    }

    LivingEntity attacker() {
        return attacker;
    }

    TrailPalette palette() {
        return palette;
    }

    float visualScale() {
        return visualScale;
    }

    int seed() {
        return seed;
    }

    /** Held still while the swing is frozen in hitstop, like the pose. */
    float elapsed(float tickDelta) {
        return age + (session.isInHitstop() ? 0.0f : tickDelta);
    }

    SwingGeometry.Frame frame(float tickDelta) {
        return SwingGeometry.Frame.of(attacker, session.facingYaw(tickDelta), session.facingPitch(tickDelta), tickDelta);
    }

    /** Blade progress now — the ribbon's leading edge. Zero (nothing drawn) until the fast part starts. */
    float leadingEdge(float tickDelta) {
        return SwingGeometry.sample(session.swing(), elapsed(tickDelta)).progress();
    }

    /** Fade once the fast part has finished, so the arc dissipates rather than vanishing. */
    float fade(float tickDelta) {
        float end = SwingGeometry.strikeEnd(session.swing().effectiveWindupTicks());
        float since = elapsed(tickDelta) - end;
        return since <= 0.0f ? 1.0f : MathHelper.clamp(1.0f - since / FADE_OUT_TICKS, 0.0f, 1.0f);
    }

    /** Where the ribbon starts along the blade; pushed out in first person so it doesn't fill the screen. */
    float innerFraction(float base) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (attacker == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()) {
            return Math.max(base, FIRST_PERSON_CLEARANCE / (float) (session.swing().profile().range() * visualScale));
        }
        return base;
    }

    /** Ticks with the session's clock: a tick the session spent frozen in hitstop doesn't age the trail. */
    void tick() {
        if (!session.wasPausedLastTick()) {
            age++;
        }
    }

    boolean isExpired() {
        return age >= lifetime || attacker.isRemoved();
    }
}
