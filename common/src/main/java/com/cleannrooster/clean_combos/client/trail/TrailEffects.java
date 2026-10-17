package com.cleannrooster.clean_combos.client.trail;

import com.cleannrooster.clean_combos.combat.AttackSession;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Client-side pool of live trails, one per swing, drawn from each loader's world-render hook. */
public final class TrailEffects {

    private static final List<TrailEffect> ACTIVE = new ArrayList<>();
    /** Trails get their own immediate buffers so their passes flush in order, independent of the entity batch. */
    private static final VertexConsumerProvider.Immediate BUFFERS =
        VertexConsumerProvider.immediate(new BufferAllocator(256 * 1024));
    private static final double RENDER_DISTANCE = 96.0;

    private TrailEffects() {
    }

    /** Start a trail for a session that just began; the palette comes from the swinging hand's weapon. */
    public static void spawn(LivingEntity attacker, AttackSession session) {
        ACTIVE.add(new TrailEffect(session, attacker, MaterialTint.paletteFor(session.swing().weapon()),
            visualScale(session.swing().profile().range()), ThreadLocalRandom.current().nextInt()));
    }

    public static void tick() {
        for (Iterator<TrailEffect> it = ACTIVE.iterator(); it.hasNext(); ) {
            TrailEffect effect = it.next();
            effect.tick();
            if (effect.isExpired()) {
                it.remove();
            }
        }
    }

    /** One pass at a time across every trail, each flushed before the next, so wake < body < edge holds. */
    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Vec3d cameraPos = camera.getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        List<TrailEffect> visible = new ArrayList<>(ACTIVE.size());
        for (TrailEffect effect : ACTIVE) {
            if (effect.attacker().getPos().squaredDistanceTo(cameraPos) <= RENDER_DISTANCE * RENDER_DISTANCE) {
                visible.add(effect);
            }
        }
        for (TrailRenderer.Pass pass : TrailRenderer.Pass.ORDER) {
            for (TrailEffect effect : visible) {
                TrailRenderer.render(effect, pass, matrices, BUFFERS, tickDelta);
            }
            BUFFERS.draw();
        }
        matrices.pop();
    }

    public static void clear() {
        ACTIVE.clear();
        MaterialTint.invalidate();
    }

    /**
     * Drawn reach over real reach (visceral_combat's VisualReach): identity up to a knee, logarithmic past
     * it, so long weapons still draw longer but never paint the whole screen, and never past the real reach.
     */
    private static float visualScale(double realRange) {
        double knee = TrailStyle.VISUAL_REACH_KNEE;
        if (!TrailStyle.VISUAL_REACH_ENABLED || realRange <= knee || realRange <= 1.0e-4) {
            return 1.0f;
        }
        double softness = TrailStyle.VISUAL_REACH_SOFTNESS;
        double visual = knee + softness * Math.log1p((realRange - knee) / softness);
        return (float) Math.min(1.0, visual / realRange);
    }
}
