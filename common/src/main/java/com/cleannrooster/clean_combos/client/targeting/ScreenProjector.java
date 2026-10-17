package com.cleannrooster.clean_combos.client.targeting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Optional;

/**
 * World -> screen projection with the last rendered frame's matrices (captured by the loader render
 * hooks). Targeting runs on the tick, so it is always one frame behind — invisible at these tolerances.
 *
 * <p>The cursor is the screen centre while the mouse is grabbed, so first person and an isometric camera
 * with a free cursor go through the same code.
 */
public final class ScreenProjector {

    public record Ray(Vec3d origin, Vec3d direction) {
    }

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3d cameraPos = Vec3d.ZERO;
    private static boolean captured;

    private ScreenProjector() {
    }

    public static void capture(Matrix4f view, Matrix4f projection, Vec3d camera) {
        VIEW.set(view);
        PROJECTION.set(projection);
        cameraPos = camera;
        captured = true;
    }

    public static boolean hasFrame() {
        return captured;
    }

    public static void reset() {
        captured = false;
    }

    /** Screen-space bounds of the box's projected corners, in window pixels. Empty if fully behind the camera. */
    public static Optional<ScreenRect> project(Box box) {
        Window window = MinecraftClient.getInstance().getWindow();
        float width = window.getWidth();
        float height = window.getHeight();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        boolean any = false;
        Vector4f v = new Vector4f();
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? box.minX : box.maxX;
            double y = (i & 2) == 0 ? box.minY : box.maxY;
            double z = (i & 4) == 0 ? box.minZ : box.maxZ;
            v.set((float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z), 1.0f);
            VIEW.transform(v);
            PROJECTION.transform(v);
            if (v.w <= 0.05f) {
                continue;
            }
            float sx = (v.x / v.w * 0.5f + 0.5f) * width;
            float sy = (1.0f - (v.y / v.w * 0.5f + 0.5f)) * height;
            minX = Math.min(minX, sx);
            minY = Math.min(minY, sy);
            maxX = Math.max(maxX, sx);
            maxY = Math.max(maxY, sy);
            any = true;
        }
        return any ? Optional.of(new ScreenRect(minX, minY, maxX, maxY)) : Optional.empty();
    }

    public static Vec2f cursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        if (client.mouse.isCursorLocked()) {
            return new Vec2f(window.getWidth() * 0.5f, window.getHeight() * 0.5f);
        }
        return new Vec2f((float) client.mouse.getX(), (float) client.mouse.getY());
    }

    /** Cursor distance to the rect (0 inside it), as a fraction of screen height. */
    public static double offset(ScreenRect rect) {
        Vec2f c = cursor();
        double dx = Math.max(0.0, Math.max(rect.minX() - c.x, c.x - rect.maxX()));
        double dy = Math.max(0.0, Math.max(rect.minY() - c.y, c.y - rect.maxY()));
        return Math.sqrt(dx * dx + dy * dy) / Math.max(1, MinecraftClient.getInstance().getWindow().getHeight());
    }

    /** The world-space ray under the cursor, or null before the first frame. */
    public static @Nullable Ray cursorRay() {
        if (!captured) {
            return null;
        }
        Window window = MinecraftClient.getInstance().getWindow();
        Vec2f c = cursor();
        float ndcX = 2.0f * c.x / window.getWidth() - 1.0f;
        float ndcY = 1.0f - 2.0f * c.y / window.getHeight();
        Matrix4f inverse = new Matrix4f(PROJECTION).mul(VIEW).invert();
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, -1.0f, 1.0f));
        Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, 1.0f, 1.0f));
        near.div(near.w);
        far.div(far.w);
        Vec3d origin = cameraPos.add(near.x, near.y, near.z);
        Vec3d direction = new Vec3d(far.x - near.x, far.y - near.y, far.z - near.z).normalize();
        return new Ray(origin, direction);
    }
}
