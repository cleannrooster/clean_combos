package com.cleannrooster.exiled_combat.client.trail;

import java.util.List;

/**
 * Trail look, as constants for now. These are visceral_combat's client-config defaults; when exiled_combat
 * gets a config screen they become its fields.
 */
final class TrailStyle {

    static final boolean VISUAL_REACH_ENABLED = true;
    static final double VISUAL_REACH_KNEE = 3.5;
    static final double VISUAL_REACH_SOFTNESS = 2.0;

    static final List<String> MATERIAL_COLORS = List.of(
        "minecraft:trident=#70E8D0",
        "minecraft:mace=#C8D4E8",
        "#minecraft:planks=#E8B070",
        "#minecraft:stone_tool_materials=#B8C4D8",
        "minecraft:iron_ingot=#D8E8FF",
        "minecraft:gold_ingot=#FFE060",
        "minecraft:diamond=#70F0F0",
        "minecraft:netherite_ingot=#B890E8");
    static final int DEFAULT_COLOR = 0xC8DCFF;
    /** Unarmed and claw strikes: a pale, faintly warm streak rather than a material color. */
    static final int FIST_COLOR = 0xF0E0D0;
    static final float MIN_TRAIL_LIGHTNESS = 0.55f;
    static final float EDGE_WHITENESS = 0.55f;

    static final float CRESCENT_TAIL_WIDTH = 0.15f;
    static final boolean TRAIL_GLOW = false;
    static final int TRAIL_MIN_LIGHT = 7;

    static final float EDGE_ALPHA = 0.8f;
    static final float BODY_ALPHA = 0.65f;
    static final float WAKE_ALPHA = 0.45f;

    static final int SERRATION_TEETH = 5;
    static final float SERRATION_DEPTH = 0.08f;
    static final int SPIKE_COUNT = 7;
    static final float SPIKE_MAX_LENGTH = 0.3f;

    private TrailStyle() {
    }
}
