package com.cleannrooster.exiled_combat.client.trail;

import com.cleannrooster.exiled_combat.combat.PlannedSwing;
import com.cleannrooster.exiled_combat.combat.SwingGeometry;
import com.cleannrooster.exiled_combat.combat.SwingStep;
import com.cleannrooster.exiled_combat.combat.Moveset.PoseFamily;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a swing as visceral_combat's layered crescent — wake, body, razor edge — laid onto the surface
 * {@link SwingGeometry} defines. A sweep is a crescent through its arc; a thrust or punch is a barbed
 * spearhead whose width never exceeds the profile's real hit cone.
 *
 * <p>The ribbon's leading edge is the blade progress the arm pose is sampled at, so the drawn edge and
 * the arm move as one, and both pass dead centre on the strike tick.
 */
final class TrailRenderer {

    private static final int FULL_BRIGHT = LightmapTextureManager.pack(15, 15);
    private static final int EDGE_SEGMENTS = 5;
    private static final int SWEEP_SEGMENTS = 32;
    private static final int THRUST_SEGMENTS = 8;

    private static final float SWEEP_INNER_FRACTION = 0.14f;
    private static final float SWEEP_THICKNESS = 0.08f;
    private static final float THRUST_THICKNESS = 0.06f;
    private static final float SWEEP_ALPHA = 1.0f;
    private static final float THRUST_ALPHA = 0.9f;

    /** Where each layer sits across the crescent's band: 0 at its inner edge, 1 at the reach. */
    private static final float WAKE_BAND_END = 0.7f;
    private static final float BODY_BAND_START = 0.15f;
    private static final float EDGE_BAND_START = 0.8f;

    private static final float THRUST_WIDEST = 0.55f;
    private static final float BARB_TIP = 0.15f;

    private static final float WAKE_DEPTH = 0.6f;
    private static final float BODY_DEPTH = 0.8f;
    private static final float EDGE_DEPTH = 1.0f;
    private static final float FLAT_SEPARATION = 0.004f;

    enum Pass {
        WAKE,
        BODY,
        EDGE;

        static final Pass[] ORDER = values();
    }

    private record Ribbon(Vec3d[][] points, float[][] alpha) {
    }

    private record Shade(float red, float green, float blue, float alpha, Sprite sprite, int light) {
    }

    /** The visible part of a sweep: progress range plus the crescent outline over it. */
    private record Sweep(PoseFamily family, SwingStep step, SwingGeometry.Frame frame, float tail, float lead,
                         double reach, float inner) {

        /** World position at row fraction {@code rf} (0 tail, 1 lead) and band fraction {@code b}. */
        Vec3d point(float rf, float b) {
            float s = MathHelper.lerp(rf, tail, lead);
            float t = MathHelper.lerp(b, innerAt(rf), 1.0f);
            return SwingGeometry.surfacePoint(family, step, frame, s, t, reach);
        }

        /** Crescent envelope: full band at the leading row, thinning toward the tail. */
        private float innerAt(float rowFraction) {
            float f = MathHelper.clamp(rowFraction, 0.0f, 1.0f);
            float width = MathHelper.lerp(f * f * (3.0f - 2.0f * f), TrailStyle.CRESCENT_TAIL_WIDTH, 1.0f);
            return 1.0f - (1.0f - inner) * Math.max(width, 0.02f);
        }
    }

    private TrailRenderer() {
    }

    static void render(TrailEffect effect, Pass pass, MatrixStack matrices, VertexConsumerProvider buffers,
                       float tickDelta) {
        PlannedSwing swing = effect.swing();
        PoseFamily family = swing.profile().moveset().poseFamily;
        boolean sweeps = SwingGeometry.sweeps(family);
        float alpha = (sweeps ? SWEEP_ALPHA : THRUST_ALPHA) * effect.fade(tickDelta) * passAlpha(pass);
        if (alpha <= 0.01f) {
            return;
        }
        float lead = effect.leadingEdge(tickDelta);
        if (lead <= 1.0e-4f) {
            return;
        }
        SwingGeometry.Frame frame = effect.frame(tickDelta);
        double reach = swing.profile().range() * effect.visualScale();
        Ribbon ribbon = sweeps
            ? buildSweep(effect, pass, family, swing.step(), frame, lead, reach)
            : buildThrust(effect, pass, family, swing, frame, lead, reach);
        Sprite sprite = TrailSprite.sprite();
        if (ribbon == null || sprite == null) {
            return;
        }

        RenderLayer layer = TrailStyle.TRAIL_GLOW
            ? RenderLayer.getEntityTranslucentEmissive(TrailSprite.ATLAS)
            : RenderLayer.getEntityTranslucent(TrailSprite.ATLAS);
        int light = TrailStyle.TRAIL_GLOW ? FULL_BRIGHT : sceneLight(frame.origin());
        int rgb = passColor(pass, effect.palette());
        Shade shade = new Shade((rgb >> 16 & 0xFF) / 255.0f, (rgb >> 8 & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f,
            alpha, sprite, light);
        VertexConsumer consumer = buffers.getBuffer(layer);
        MatrixStack.Entry entry = matrices.peek();

        // Two parallel sheets read as a slab; each layer sits a little further out than the last, so the
        // edge is the surface nearest the viewer on either side.
        Vec3d normal = SwingGeometry.planeNormal(family, swing.step(), frame);
        float thickness = Math.min(sweeps ? SWEEP_THICKNESS : THRUST_THICKNESS, (float) reach / 6.0f);
        float offset = thickness > 0.001f ? thickness * passDepth(pass) : FLAT_SEPARATION * pass.ordinal();
        emit(consumer, entry, offsetGrid(ribbon, normal, offset), shade, normal);
        emit(consumer, entry, offsetGrid(ribbon, normal, -offset), shade, normal.multiply(-1.0));
    }

    private static float passAlpha(Pass pass) {
        return switch (pass) {
            case WAKE -> TrailStyle.WAKE_ALPHA;
            case BODY -> TrailStyle.BODY_ALPHA;
            case EDGE -> TrailStyle.EDGE_ALPHA;
        };
    }

    private static int passColor(Pass pass, TrailPalette palette) {
        return switch (pass) {
            case WAKE -> palette.trail();
            case BODY -> palette.body();
            case EDGE -> palette.edge();
        };
    }

    private static float passDepth(Pass pass) {
        return switch (pass) {
            case WAKE -> WAKE_DEPTH;
            case BODY -> BODY_DEPTH;
            case EDGE -> EDGE_DEPTH;
        };
    }

    /** Light where the swing starts, block light raised to a floor so trails dim in the dark but stay readable. */
    private static int sceneLight(Vec3d origin) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return FULL_BRIGHT;
        }
        int packed = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(origin));
        int block = Math.max(LightmapTextureManager.getBlockLightCoordinates(packed), TrailStyle.TRAIL_MIN_LIGHT);
        return LightmapTextureManager.pack(block, LightmapTextureManager.getSkyLightCoordinates(packed));
    }

    private static @Nullable Ribbon buildSweep(TrailEffect effect, Pass pass, PoseFamily family, SwingStep step,
                                               SwingGeometry.Frame frame, float lead, double reach) {
        float inner = effect.innerFraction(SWEEP_INNER_FRACTION);
        if (inner >= 0.999f) {
            return null;
        }
        Sweep sweep = new Sweep(family, step, frame, 0.0f, lead, reach, inner);
        return switch (pass) {
            case WAKE -> buildWake(effect, sweep);
            case BODY -> buildBody(sweep);
            case EDGE -> buildEdge(sweep);
        };
    }

    /** The saturated body: serrated across both ends of the sweep, clean along the reach. */
    private static Ribbon buildBody(Sweep sweep) {
        int rows = SWEEP_SEGMENTS;
        int cols = TrailStyle.SERRATION_TEETH * 2;
        float depth = MathHelper.clamp(TrailStyle.SERRATION_DEPTH, 0.0f, 0.45f);
        Vec3d[][] points = new Vec3d[rows + 1][cols + 1];
        float[][] alpha = new float[rows + 1][cols + 1];
        for (int c = 0; c <= cols; c++) {
            float notch = (c & 1) == 1 ? depth : 0.0f;
            float b = MathHelper.lerp((float) c / cols, BODY_BAND_START, 1.0f);
            float across = smoothstep((b - BODY_BAND_START) / 0.45f);
            for (int r = 0; r <= rows; r++) {
                float rf = MathHelper.lerp((float) r / rows, notch, 1.0f - notch);
                points[r][c] = sweep.point(rf, b);
                alpha[r][c] = smoothstep(rf) * across;
            }
        }
        return new Ribbon(points, alpha);
    }

    /** The near-white edge: a thin strip against the reach, ending at full opacity on its last row. */
    private static Ribbon buildEdge(Sweep sweep) {
        int rows = SWEEP_SEGMENTS;
        int cols = EDGE_SEGMENTS;
        Vec3d[][] points = new Vec3d[rows + 1][cols + 1];
        float[][] alpha = new float[rows + 1][cols + 1];
        for (int c = 0; c <= cols; c++) {
            float x = 1.0f - (float) c / cols;
            float across = 1.0f - x * x;
            float b = MathHelper.lerp(across, EDGE_BAND_START, 1.0f);
            float edgeAlpha = across * across * across;
            for (int r = 0; r <= rows; r++) {
                float rf = (float) r / rows;
                points[r][c] = sweep.point(rf, b);
                alpha[r][c] = edgeAlpha * (0.15f + 0.85f * rf * (float) Math.sqrt(rf));
            }
        }
        return new Ribbon(points, alpha);
    }

    /** The wake: deepest color, under the body, spiked along its tail end and inner edge. */
    private static Ribbon buildWake(TrailEffect effect, Sweep sweep) {
        int spikes = TrailStyle.SPIKE_COUNT;
        float maxLength = MathHelper.clamp(TrailStyle.SPIKE_MAX_LENGTH, 0.0f, 0.6f);
        int rows = spikes * 4;
        int cols = spikes * 2;
        Random random = Random.create(effect.seed());

        float[] valley = new float[rows + 1];
        for (int k = 0; k < spikes; k++) {
            float length = maxLength * (0.4f + 0.6f * random.nextFloat());
            valley[k * 4 + 1] = length * 0.5f;
            valley[k * 4 + 2] = length;
            valley[k * 4 + 3] = length * 0.5f;
        }
        float[] notch = new float[cols + 1];
        for (int c = 1; c < cols; c += 2) {
            notch[c] = maxLength * (0.4f + 0.6f * random.nextFloat());
        }

        Vec3d[][] points = new Vec3d[rows + 1][cols + 1];
        float[][] alpha = new float[rows + 1][cols + 1];
        for (int r = 0; r <= rows; r++) {
            float rowFraction = (float) r / rows;
            for (int c = 0; c <= cols; c++) {
                float rf = MathHelper.lerp(rowFraction, notch[c], 1.0f);
                float b = MathHelper.lerp((float) c / cols, valley[r] * WAKE_BAND_END, WAKE_BAND_END);
                float inward = 0.35f + 0.65f * smoothstep(b / (WAKE_BAND_END * 0.5f));
                float outward = 1.0f - smoothstep((b - WAKE_BAND_END * 0.7f) / (WAKE_BAND_END * 0.3f));
                points[r][c] = sweep.point(rf, b);
                alpha[r][c] = (float) Math.pow(rf, 0.6) * inward * outward;
            }
        }
        return new Ribbon(points, alpha);
    }

    /**
     * A thrust: a barbed spearhead down the blade line, as far as the strike has extended. Its width is
     * capped by the profile's hit cone at each distance, so the drawn point fits what can be hit.
     */
    private static Ribbon buildThrust(TrailEffect effect, Pass pass, PoseFamily family, PlannedSwing swing,
                                      SwingGeometry.Frame frame, float lead, double reach) {
        float widthShare = switch (pass) {
            case WAKE -> 1.0f;
            case BODY -> 0.7f;
            case EDGE -> 0.28f;
        };
        boolean core = pass == Pass.EDGE;
        int barbs = switch (pass) {
            case WAKE -> TrailStyle.SPIKE_COUNT;
            case BODY -> TrailStyle.SERRATION_TEETH;
            case EDGE -> 0;
        };
        float barbDepth = switch (pass) {
            case WAKE -> MathHelper.clamp(TrailStyle.SPIKE_MAX_LENGTH * 2.0f, 0.0f, 0.85f);
            case BODY -> MathHelper.clamp(TrailStyle.SERRATION_DEPTH * 3.0f, 0.0f, 0.6f);
            case EDGE -> 0.0f;
        };
        Random random = Random.create(effect.seed() ^ pass.ordinal());

        int rows = barbs > 0 ? barbs * 4 : THRUST_SEGMENTS;
        float[] along = new float[rows + 1];
        float[] barb = new float[rows + 1];
        if (barbs > 0) {
            float[] pattern = {0.0f, BARB_TIP, 0.5f, 0.75f};
            for (int k = 0; k < barbs; k++) {
                float depth = barbDepth * (pass == Pass.WAKE ? 0.5f + 0.5f * random.nextFloat() : 1.0f);
                for (int i = 0; i < 4; i++) {
                    int r = k * 4 + i;
                    along[r] = (k + pattern[i]) / barbs;
                    float fromTip = i == 0 ? 1.0f : (pattern[i] - BARB_TIP) / (1.0f - BARB_TIP);
                    barb[r] = 1.0f - depth * fromTip;
                }
            }
            along[rows] = 1.0f;
            barb[rows] = 1.0f;
        } else {
            for (int r = 0; r <= rows; r++) {
                along[r] = (float) r / rows;
                barb[r] = 1.0f;
            }
        }

        int cols = pass == Pass.WAKE ? 6 : 4;
        float[] notch = new float[cols + 1];
        if (pass == Pass.WAKE) {
            float maxNotch = MathHelper.clamp(TrailStyle.SPIKE_MAX_LENGTH, 0.0f, 0.5f);
            for (int c = 1; c < cols; c += 2) {
                notch[c] = maxNotch * (0.4f + 0.6f * random.nextFloat());
            }
        }

        Vec3d across = SwingGeometry.planeAxis(family, swing.step(), frame);
        double coneTan = Math.tan(Math.toRadians(swing.profile().coneHalfAngleDeg()));
        double laneHalfWidth = swing.profile().range() * 0.25;
        float inner = effect.innerFraction(0.0f);
        Vec3d[][] points = new Vec3d[rows + 1][cols + 1];
        float[][] alpha = new float[rows + 1][cols + 1];
        for (int r = 0; r <= rows; r++) {
            for (int c = 0; c <= cols; c++) {
                float u = MathHelper.lerp(along[r], notch[c], 1.0f);
                float t = MathHelper.lerp(u, inner, 1.0f);
                Vec3d centre = SwingGeometry.surfacePoint(family, swing.step(), frame, lead, t, reach);
                double allowed = Math.min(laneHalfWidth, centre.distanceTo(frame.origin()) * coneTan);
                double width = allowed * thrustOutline(u) * barb[r] * widthShare;
                float x = (float) c / cols * 2.0f - 1.0f;
                points[r][c] = centre.add(across.multiply(width * x));
                float side = Math.abs(x);
                float acrossAlpha = core ? (float) Math.pow(1.0f - side, 1.5f) : 1.0f - 0.35f * side * side;
                float alongAlpha = core ? 0.2f + 0.8f * u * (float) Math.sqrt(u) : 0.25f + 0.75f * smoothstep(u);
                alpha[r][c] = acrossAlpha * alongAlpha;
            }
        }
        return new Ribbon(points, alpha);
    }

    private static float thrustOutline(float u) {
        if (u <= THRUST_WIDEST) {
            return 0.3f + 0.7f * smoothstep(u / THRUST_WIDEST);
        }
        return MathHelper.clamp((1.0f - u) / (1.0f - THRUST_WIDEST), 0.0f, 1.0f);
    }

    private static Ribbon offsetGrid(Ribbon ribbon, Vec3d normal, float distance) {
        Vec3d[][] source = ribbon.points();
        Vec3d[][] shifted = new Vec3d[source.length][];
        Vec3d offset = normal.multiply(distance);
        for (int r = 0; r < source.length; r++) {
            shifted[r] = new Vec3d[source[r].length];
            for (int c = 0; c < source[r].length; c++) {
                shifted[r][c] = source[r][c].add(offset);
            }
        }
        return new Ribbon(shifted, ribbon.alpha());
    }

    private static void emit(VertexConsumer consumer, MatrixStack.Entry entry, Ribbon ribbon, Shade shade,
                             Vec3d normal) {
        Vec3d[][] points = ribbon.points();
        float[][] alpha = ribbon.alpha();
        int rows = points.length - 1;
        int cols = points[0].length - 1;
        float[] window = TrailSprite.WINDOW;
        Sprite sprite = shade.sprite();
        Vec3d back = normal.multiply(-1.0);

        for (int r = 0; r < rows; r++) {
            float u0 = sprite.getFrameU(MathHelper.lerp((float) r / rows, window[0], window[2]));
            float u1 = sprite.getFrameU(MathHelper.lerp((float) (r + 1) / rows, window[0], window[2]));
            for (int c = 0; c < cols; c++) {
                float v0 = sprite.getFrameV(MathHelper.lerp((float) c / cols, window[1], window[3]));
                float v1 = sprite.getFrameV(MathHelper.lerp((float) (c + 1) / cols, window[1], window[3]));
                float a00 = shade.alpha() * alpha[r][c];
                float a01 = shade.alpha() * alpha[r][c + 1];
                float a11 = shade.alpha() * alpha[r + 1][c + 1];
                float a10 = shade.alpha() * alpha[r + 1][c];
                if (a00 + a01 + a11 + a10 <= 0.004f) {
                    continue;
                }
                Vec3d p00 = points[r][c];
                Vec3d p10 = points[r + 1][c];
                Vec3d p11 = points[r + 1][c + 1];
                Vec3d p01 = points[r][c + 1];
                quad(consumer, entry, shade, normal, p00, u0, v0, a00, p01, u0, v1, a01, p11, u1, v1, a11, p10, u1, v0, a10);
                // Reverse winding: visible from whichever side the viewer is on.
                quad(consumer, entry, shade, back, p10, u1, v0, a10, p11, u1, v1, a11, p01, u0, v1, a01, p00, u0, v0, a00);
            }
        }
    }

    private static void quad(VertexConsumer consumer, MatrixStack.Entry entry, Shade shade, Vec3d normal,
                             Vec3d a, float au, float av, float aa, Vec3d b, float bu, float bv, float ba,
                             Vec3d c, float cu, float cv, float ca, Vec3d d, float du, float dv, float da) {
        vertex(consumer, entry, shade, normal, a, au, av, aa);
        vertex(consumer, entry, shade, normal, b, bu, bv, ba);
        vertex(consumer, entry, shade, normal, c, cu, cv, ca);
        vertex(consumer, entry, shade, normal, d, du, dv, da);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, Shade shade, Vec3d normal,
                               Vec3d position, float u, float v, float alpha) {
        consumer.vertex(entry, (float) position.x, (float) position.y, (float) position.z)
            .color(shade.red(), shade.green(), shade.blue(), MathHelper.clamp(alpha, 0.0f, 1.0f))
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(shade.light())
            .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static float smoothstep(float value) {
        float x = MathHelper.clamp(value, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }
}
