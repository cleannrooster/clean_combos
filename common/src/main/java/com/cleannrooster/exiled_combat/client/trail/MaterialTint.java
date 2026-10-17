package com.cleannrooster.exiled_combat.client.trail;

import com.cleannrooster.exiled_combat.ExiledCombat;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The palette a weapon's trail is drawn in, read from what the weapon is made of (from visceral_combat).
 *
 * <p>Matching is by item id against {@link TrailStyle#MATERIAL_COLORS}: first the weapon itself, then each
 * item that repairs its tool material, so a modded sword repaired with iron comes out iron-colored with
 * no entry of its own. Unmatched weapons get the default; an empty hand gets the fist color. Every
 * result is lifted so no trail is dark. Cached per item and dropped on world change, since repair
 * ingredients are often tags that only resolve once the server has sent them.
 */
final class MaterialTint {

    private record Rule(@Nullable Identifier item, @Nullable TagKey<Item> tag, int rgb) {

        boolean matches(ItemStack stack) {
            return this.tag != null ? stack.isIn(this.tag) : Registries.ITEM.getId(stack.getItem()).equals(this.item);
        }
    }

    private static final Map<Item, TrailPalette> CACHE = new HashMap<>();
    private static @Nullable List<Rule> rules;

    private MaterialTint() {
    }

    static TrailPalette paletteFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return palette(TrailStyle.FIST_COLOR);
        }
        if (rules == null) {
            rules = parse();
        }
        return CACHE.computeIfAbsent(stack.getItem(), item -> palette(resolve(stack)));
    }

    static void invalidate() {
        CACHE.clear();
        rules = null;
    }

    private static TrailPalette palette(int rgb) {
        return TrailPalette.fromBase(rgb, TrailStyle.MIN_TRAIL_LIGHTNESS, TrailStyle.EDGE_WHITENESS);
    }

    private static int resolve(ItemStack stack) {
        List<Rule> table = rules;
        for (Rule rule : table) {
            if (rule.matches(stack)) {
                return rule.rgb();
            }
        }
        if (stack.getItem() instanceof ToolItem tool) {
            for (ItemStack repair : tool.getMaterial().getRepairIngredient().getMatchingStacks()) {
                for (Rule rule : table) {
                    if (rule.matches(repair)) {
                        return rule.rgb();
                    }
                }
            }
        }
        return TrailStyle.DEFAULT_COLOR;
    }

    private static List<Rule> parse() {
        List<Rule> parsed = new ArrayList<>();
        for (String entry : TrailStyle.MATERIAL_COLORS) {
            Rule rule = parseRule(entry);
            if (rule != null) {
                parsed.add(rule);
            } else {
                ExiledCombat.LOGGER.warn("Ignoring malformed trail material color '{}'", entry);
            }
        }
        return parsed;
    }

    private static @Nullable Rule parseRule(String entry) {
        int split = entry.indexOf('=');
        if (split <= 0) {
            return null;
        }
        String key = entry.substring(0, split).trim();
        String hex = entry.substring(split + 1).trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return null;
        }
        int rgb;
        try {
            rgb = Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
        boolean isTag = key.startsWith("#");
        Identifier id = Identifier.tryParse(isTag ? key.substring(1) : key);
        if (id == null) {
            return null;
        }
        return isTag ? new Rule(null, TagKey.of(RegistryKeys.ITEM, id), rgb) : new Rule(id, null, rgb);
    }
}
