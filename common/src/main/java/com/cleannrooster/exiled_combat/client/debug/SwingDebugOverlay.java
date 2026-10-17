package com.cleannrooster.exiled_combat.client.debug;

import com.cleannrooster.exiled_combat.client.ClientCombat;
import com.cleannrooster.exiled_combat.combat.PlannedSwing;
import com.cleannrooster.exiled_combat.combat.SwingGeometry;
import com.cleannrooster.exiled_combat.combat.Moveset.PoseFamily;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * {@code /exiled_combat debug}: draws, for every active swing, the strike cone the server will test
 * (yellow, red on the strike tick), its centre line (white) and the live blade line the pose and trail
 * are sampled from (cyan). In sync means the cyan line lies on the white one exactly while the cone is red.
 */
public final class SwingDebugOverlay {

    private static final VertexConsumerProvider.Immediate BUFFERS =
        VertexConsumerProvider.immediate(new BufferAllocator(16 * 1024));
    private static boolean enabled;

    private SwingDebugOverlay() {
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!enabled) {
            return;
        }
        Vec3d cameraPos = camera.getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        VertexConsumer lines = BUFFERS.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = matrices.peek();
        ClientCombat.forEachSession((attacker, session) -> {
            PlannedSwing swing = session.swing();
            float elapsed = session.elapsed(tickDelta);
            SwingGeometry.Frame frame = SwingGeometry.Frame.of(attacker, session.facingYaw(tickDelta),
                session.facingPitch(tickDelta), tickDelta);
            double range = swing.profile().range();
            boolean striking = Math.abs(elapsed - swing.effectiveWindupTicks()) <= 0.5f;

            int cone = striking ? 0xFFFF3030 : 0xFFFFD040;
            double half = Math.toRadians(swing.profile().coneHalfAngleDeg());
            for (int side = -1; side <= 1; side += 2) {
                Vec3d dir = frame.forward().multiply(Math.cos(half)).add(frame.right().multiply(Math.sin(half) * side));
                line(lines, entry, frame.origin(), frame.origin().add(dir.multiply(range)), cone);
            }
            line(lines, entry, frame.origin(), frame.origin().add(frame.forward().multiply(range)), 0xFFFFFFFF);

            PoseFamily family = swing.profile().moveset().poseFamily;
            float progress = SwingGeometry.sample(swing, elapsed).progress();
            Vec3d tip = SwingGeometry.surfacePoint(family, swing.step(), frame, progress, 1.0f, range);
            line(lines, entry, frame.origin(), tip, 0xFF40E0FF);
        });
        BUFFERS.draw();
        matrices.pop();
    }

    private static void line(VertexConsumer lines, MatrixStack.Entry entry, Vec3d from, Vec3d to, int argb) {
        Vec3d n = to.subtract(from).normalize();
        lines.vertex(entry, (float) from.x, (float) from.y, (float) from.z).color(argb)
            .normal(entry, (float) n.x, (float) n.y, (float) n.z);
        lines.vertex(entry, (float) to.x, (float) to.y, (float) to.z).color(argb)
            .normal(entry, (float) n.x, (float) n.y, (float) n.z);
    }
}
