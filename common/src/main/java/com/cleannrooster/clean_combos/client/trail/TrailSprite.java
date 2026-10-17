package com.cleannrooster.clean_combos.client.trail;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The surface grain of a trail, borrowed from the particle atlas (from visceral_combat). Nothing here is
 * a texture this mod ships — the shape is all geometry — so trails follow the resource pack. Looked up
 * on every use because a resource reload replaces the atlas.
 */
final class TrailSprite {

    @SuppressWarnings("deprecation")
    static final Identifier ATLAS = SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE;
    private static final Identifier ID = Identifier.ofVanilla("sweep_3");
    /** The solid band through the middle of the vanilla sweep_3 frame: u0, v0, u1, v1. */
    static final float[] WINDOW = {0.19f, 0.41f, 0.81f, 0.46f};

    private TrailSprite() {
    }

    static @Nullable Sprite sprite() {
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(ATLAS);
        return texture instanceof SpriteAtlasTexture atlas ? atlas.getSprite(ID) : null;
    }
}
