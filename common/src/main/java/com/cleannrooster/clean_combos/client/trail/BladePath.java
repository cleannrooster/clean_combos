package com.cleannrooster.clean_combos.client.trail;

import com.cleannrooster.clean_combos.client.anim.ClipBlade;
import com.cleannrooster.clean_combos.combat.Moveset.PoseFamily;
import com.cleannrooster.clean_combos.combat.SwingGeometry;
import com.cleannrooster.clean_combos.combat.SwingStep;
import net.minecraft.util.math.Vec3d;

/**
 * Where a trail is drawn, by blade progress (0..1, 0.5 on the strike tick) and fraction along the reach.
 * Normally the authored {@link SwingGeometry} arc the procedural arms follow; in clip-only animation, the
 * weapon the clip actually moves ({@link ClipBlade}).
 *
 * <p>Trails are flat sheets. The arc is planar by construction; the clip's weapon is not (the hand travels and
 * the blade rolls), so its sweep is flattened onto the plane that best fits the whole swing.
 */
interface BladePath {

    /** Where the drawn blade starts this frame: for lighting, and the cone a thrust's width is fitted to. */
    Vec3d origin();

    Vec3d point(float progress, float t, double reach);

    /** The direction the ribbon has thickness in. */
    Vec3d normal(float progress);

    /** Sideways across the blade: the width direction of a thrust. */
    Vec3d across(float progress);

    /** Sweeps follow the clip's weapon when it drives the swing; thrusts always draw the simple straight thrust. */
    static BladePath of(TrailEffect effect, float tickDelta, double reach) {
        PoseFamily family = effect.swing().profile().moveset().poseFamily;
        ClipBlade clip = SwingGeometry.sweeps(family) ? ClipBlade.of(effect.attacker(), effect.session(), tickDelta) : null;
        if (clip != null) {
            SwingGeometry.Frame frame = effect.frame(tickDelta);
            Vec3d anchor = frame.origin().add(frame.forward().multiply(reach * Clip.ANCHOR_FRACTION));
            boolean swing = family == PoseFamily.HORIZONTAL;
            float span = swing ? Clip.SWING_SPAN : 1.0f;
            return Clip.fitted(clip, effect.leadingEdge(tickDelta), reach, frame, anchor,
                swing ? Clip.SWING_TILT_KEPT : 1.0f, span, swing);
        }
        return new Arc(family, effect.swing().step(), effect.frame(tickDelta));
    }

    /** The authored arc. */
    record Arc(PoseFamily family, SwingStep step, SwingGeometry.Frame frame) implements BladePath {

        @Override
        public Vec3d origin() {
            return frame.origin();
        }

        @Override
        public Vec3d point(float progress, float t, double reach) {
            return SwingGeometry.surfacePoint(family, step, frame, progress, t, reach);
        }

        @Override
        public Vec3d normal(float progress) {
            return SwingGeometry.planeNormal(family, step, frame);
        }

        @Override
        public Vec3d across(float progress) {
            return SwingGeometry.planeAxis(family, step, frame);
        }
    }

    /**
     * The clip's weapon, out from the hand along the blade, flattened onto one plane.
     *
     * @param planePoint  a point on the fitted plane (on the hit line; see {@link #fitted})
     * @param planeNormal the fitted plane's unit normal
     * @param pivot       the point the clip's weapon path is turned about to centre it on the hit line
     * @param axis        the aimed up-axis that turn is about
     * @param angle       the turn, in radians
     */
    record Clip(ClipBlade blade, Vec3d origin, Vec3d planePoint, Vec3d planeNormal, float span, Vec3d pivot,
                Vec3d axis, double angle) implements BladePath {

        /** Progress samples the plane is fitted to, across the whole sweep. */
        private static final int FIT_SAMPLES = 16;
        /** Fractions along the reach sampled at each progress: the inner part of the sheet and the tip. */
        private static final float[] FIT_FRACTIONS = {0.35f, 1.0f};
        /** Share of a swing plane's tilt away from horizontal that is kept: swings read flatter than the clip. */
        static final float SWING_TILT_KEPT = 0.5f;
        /**
         * Share of the clip's sweep a swing's trail covers, centred on the strike: swings are narrow, so only the
         * middle of the motion is drawn — matching their narrowed hit cone.
         */
        static final float SWING_SPAN = 0.5f;
        /** How far out along the hit line, as a share of reach, the trail's plane is pinned. */
        static final float ANCHOR_FRACTION = 0.6f;

        /**
         * Fit the plane to the whole sweep rather than the part drawn so far, so it holds still as the trail
         * grows. The normal is Newell's: the summed cross products around the tip's path, which for an arc is
         * the plane its swept area faces. A sweep with no area to speak of (a pure thrust) falls back to the blade
         * and its edge at the leading progress.
         *
         * <p>The plane takes its tilt from the clip but its place from the hit: it passes through {@code anchor} on
         * the line the strike is checked along (shoulder height, the facing's yaw and pitch), not through the
         * sweep's centroid. Vanilla's hold angles the blade up from the hand, so the centroid rode well above
         * what the swing can hit and the trail read deceptively high.
         *
         * <p>It also takes its sideways placement from the hit. The clip's centre moment is when the body passes
         * neutral, and with vanilla's hold the blade isn't pointing straight ahead then, so the drawn part of the
         * sweep sat off to the starting side and covered only the beginning of the hit area. The whole path is
         * turned about the aimed up-axis (through the shoulder) so the blade at the strike points along the facing:
         * the trail spreads evenly either side of the hit line. Swings only: a chop's blade at the strike points
         * down, often past vertical toward the wielder, so its direction seen from above can face backward and
         * the turn would spin the chop half round — drawn in reverse.
         *
         * @param lead     trail progress of the leading edge this frame
         * @param frame    the swing's aimed frame: hit line, shoulder origin, aimed up
         * @param anchor   the point on the hit line the plane passes through
         * @param tiltKept share of the plane's tilt from the aimed level to keep (1 = as fitted, 0 = flat)
         * @param span     share of the clip's sweep drawn, centred on the strike (1 = all of it)
         * @param centre   turn the path onto the hit line (swings)
         */
        static Clip fitted(ClipBlade blade, float lead, double reach, SwingGeometry.Frame frame, Vec3d anchor,
                           float tiltKept, float span, boolean centre) {
            Vec3d axis = frame.up();
            double angle = centre ? centringAngle(blade.at(0.5f).blade(), frame.forward(), axis) : 0.0;
            Vec3d pivot = frame.origin();
            Vec3d[] tips = new Vec3d[FIT_SAMPLES + 1];
            Vec3d sum = Vec3d.ZERO;
            int count = 0;
            for (int i = 0; i <= FIT_SAMPLES; i++) {
                ClipBlade.Pose pose = blade.at(spanned((float) i / FIT_SAMPLES, span));
                for (float fraction : FIT_FRACTIONS) {
                    Vec3d point = turn(pose.hand().add(pose.blade().multiply(reach * fraction)), pivot, axis, angle);
                    sum = sum.add(point);
                    count++;
                    tips[i] = point;
                }
            }
            // The centroid only steadies the normal's sum; the plane itself is placed at the anchor.
            Vec3d centroid = sum.multiply(1.0 / count);
            Vec3d normal = Vec3d.ZERO;
            for (int i = 0; i < FIT_SAMPLES; i++) {
                normal = normal.add(tips[i].subtract(centroid).crossProduct(tips[i + 1].subtract(centroid)));
            }
            if (normal.lengthSquared() < 1.0e-6 * reach * reach) {
                ClipBlade.Pose pose = blade.at(0.5f);
                normal = rotate(pose.blade().crossProduct(pose.edge()), axis, angle);
            }
            Vec3d origin = turn(blade.at(spanned(lead, span)).hand(), pivot, axis, angle);
            return new Clip(blade, origin, anchor, untilt(normal.normalize(), axis, tiltKept), span, pivot, axis,
                angle);
        }

        /**
         * The turn about {@code axis} that brings {@code blade}'s direction (seen from above the aim) onto
         * {@code forward}'s; 0 when either has no sideways component to speak of.
         */
        private static double centringAngle(Vec3d blade, Vec3d forward, Vec3d axis) {
            Vec3d b = blade.subtract(axis.multiply(blade.dotProduct(axis)));
            Vec3d f = forward.subtract(axis.multiply(forward.dotProduct(axis)));
            if (b.lengthSquared() < 1.0e-6 || f.lengthSquared() < 1.0e-6) {
                return 0.0;
            }
            return Math.atan2(b.crossProduct(f).dotProduct(axis), b.dotProduct(f));
        }

        /** Rodrigues: {@code v} turned by {@code angle} about unit {@code axis}. */
        private static Vec3d rotate(Vec3d v, Vec3d axis, double angle) {
            double cos = Math.cos(angle);
            return v.multiply(cos).add(axis.crossProduct(v).multiply(Math.sin(angle)))
                .add(axis.multiply(axis.dotProduct(v) * (1.0 - cos)));
        }

        private static Vec3d turn(Vec3d point, Vec3d pivot, Vec3d axis, double angle) {
            return pivot.add(rotate(point.subtract(pivot), axis, angle));
        }

        /** The clip progress drawn at trail progress {@code progress}: squeezed toward the strike (0.5) by {@code span}. */
        static float spanned(float progress, float span) {
            return 0.5f + (progress - 0.5f) * span;
        }

        /**
         * Turn {@code normal} toward {@code aimUp} so the plane's tilt from the aimed level shrinks to {@code kept}
         * of itself. Measured against the aim, not the world, so flattening never undoes the pitch.
         */
        private static Vec3d untilt(Vec3d normal, Vec3d aimUp, float kept) {
            if (kept >= 1.0f) {
                return normal;
            }
            double along = normal.dotProduct(aimUp);
            Vec3d up = along >= 0.0 ? aimUp : aimUp.multiply(-1.0);
            double tilt = Math.acos(Math.min(1.0, Math.abs(along)));
            if (tilt < 1.0e-4) {
                return normal;
            }
            // Slerp from the vertical toward the fitted normal, stopping at the kept share of the angle.
            double sin = Math.sin(tilt);
            return up.multiply(Math.sin((1.0 - kept) * tilt) / sin).add(normal.multiply(Math.sin(kept * tilt) / sin))
                .normalize();
        }

        @Override
        public Vec3d point(float progress, float t, double reach) {
            ClipBlade.Pose pose = blade.at(spanned(progress, span));
            Vec3d point = turn(pose.hand().add(pose.blade().multiply(reach * t)), pivot, axis, angle);
            return point.subtract(planeNormal.multiply(point.subtract(planePoint).dotProduct(planeNormal)));
        }

        @Override
        public Vec3d normal(float progress) {
            return planeNormal;
        }

        /** In the plane and square to the blade, so a thrust's width lies flat too. */
        @Override
        public Vec3d across(float progress) {
            ClipBlade.Pose pose = this.blade.at(spanned(progress, span));
            Vec3d side = planeNormal.crossProduct(rotate(pose.blade(), axis, angle));
            return side.lengthSquared() < 1.0e-8 ? rotate(pose.edge(), axis, angle) : side.normalize();
        }
    }
}
