package com.cleannrooster.clean_combos.combat;

import java.util.Locale;

/**
 * The attack a weapon gets. Every melee item in the game falls into one of these, decided automatically
 * (see {@link Movesets#detect}); a moveset behaves the same whatever the weapon and whatever the grip.
 */
public enum Moveset {
    /** The default for any melee item: a horizontal cut. */
    SWING(PoseFamily.HORIZONTAL),
    /** Axes and maces: an overhead cut. Stays one-handed with a free off hand. */
    CHOP(PoseFamily.OVERHEAD),
    /** Spears and tridents: a straight thrust. */
    THRUST(PoseFamily.THRUST),
    /** An empty hand, or anything that isn't a weapon: punches. */
    FIST(PoseFamily.PUNCH),
    /**
     * Not a weapon class: the shape every non-thrust attack takes when started in the air — a steep
     * downward chop. Never detected from an item; the planner substitutes it for airborne swings.
     */
    AERIAL(PoseFamily.OVERHEAD);

    public enum PoseFamily {
        HORIZONTAL,
        OVERHEAD,
        THRUST,
        PUNCH
    }

    public final PoseFamily poseFamily;

    Moveset(PoseFamily poseFamily) {
        this.poseFamily = poseFamily;
    }

    public boolean isFist() {
        return this == FIST;
    }

    /** Whether a free off hand turns this into a two-handed grip. Fists dual-punch instead; chops stay one-handed. */
    public boolean twoHandsWhenFree() {
        return this == SWING || this == THRUST;
    }

    public static Moveset parse(String name) {
        return valueOf(name.trim().toUpperCase(Locale.ROOT));
    }
}
