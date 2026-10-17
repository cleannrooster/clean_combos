package com.cleannrooster.exiled_combat.client.targeting;

/** Screen-space bounds of a projected hitbox, in GUI-scaled pixels. */
public record ScreenRect(float minX, float minY, float maxX, float maxY) {
}
