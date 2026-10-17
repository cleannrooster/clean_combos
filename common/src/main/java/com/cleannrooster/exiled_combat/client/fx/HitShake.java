package com.cleannrooster.exiled_combat.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The victim's half of hitstop: a short, decaying jitter on struck entities' models, timed to the attacker's
 * freeze. Purely visual; nothing about the entity moves.
 */
public final class HitShake {

    private static final float AMPLITUDE = 0.05f;

    private record Shake(long start, int ticks) {
    }

    private static final Map<Integer, Shake> ACTIVE = new HashMap<>();

    private HitShake() {
    }

    public static void start(int entityId, int ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ticks > 0 && client.world != null) {
            ACTIVE.put(entityId, new Shake(client.world.getTime(), ticks));
        }
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ACTIVE.isEmpty() || client.world == null) {
            return;
        }
        long now = client.world.getTime();
        for (Iterator<Shake> it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Shake shake = it.next();
            if (now - shake.start() > shake.ticks()) {
                it.remove();
            }
        }
    }

    /** Called before a living entity's model renders: nudge it if it is shaking. */
    public static void apply(Entity entity, MatrixStack matrices, float tickDelta) {
        Shake shake = ACTIVE.get(entity.getId());
        if (shake == null) {
            return;
        }
        float age = entity.getWorld().getTime() - shake.start() + tickDelta;
        float fade = 1.0f - MathHelper.clamp(age / Math.max(1, shake.ticks()), 0.0f, 1.0f);
        if (fade <= 0.0f) {
            return;
        }
        float phase = age * 9.0f + entity.getId();
        matrices.translate(MathHelper.sin(phase) * AMPLITUDE * fade, 0.0f,
            MathHelper.cos(phase * 1.3f) * AMPLITUDE * fade);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
