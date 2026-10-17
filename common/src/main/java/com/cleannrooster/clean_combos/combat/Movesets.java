package com.cleannrooster.clean_combos.combat;

import com.cleannrooster.clean_combos.CleanCombos;
import com.cleannrooster.clean_combos.config.CleanCombosConfig;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The built-in movesets and how items are sorted into them. No data files: every item in the game gets a
 * moveset the moment it exists, which is what makes the mod drag-and-drop.
 *
 * <p>Detection order: a config override ({@code movesetOverrides}: item id or {@code #tag} to a moveset or
 * {@code vanilla}), then items left to vanilla by default (the vanilla mace), then thrust (tridents, spear tags,
 * spear-like names), then chop (axes, maces, mace-like names), then swing for anything that deals melee damage;
 * everything else punches.
 */
public final class Movesets {

    /** How much further the snap may carry an attacker than they can reach, in blocks. */
    private static final double SNAP_EXTRA_RANGE = 7.0;
    /** Reach when the attacker has no interaction-range attribute (non-player wielders). */
    private static final double DEFAULT_REACH = 3.0;
    private static final String[] THRUST_NAME_HINTS = {"spear", "lance", "pike", "javelin"};
    private static final List<TagKey<Item>> THRUST_TAGS = List.of(
        tag("c", "tools/spear"), tag("c", "tools/spears"), tag("c", "spears"));
    private static final String[] CHOP_NAME_HINTS = {"mace"};
    /**
     * Items left entirely to vanilla by default — no moveset, vanilla's own attack. The vanilla mace keeps its
     * smash attack this way; modded maces are still picked up as chops by name.
     */
    private static final Set<Identifier> VANILLA_HANDLED = Set.of(Identifier.ofVanilla("mace"));
    private static final List<TagKey<Item>> CHOP_TAGS = List.of(
        ItemTags.AXES, tag("c", "tools/axe"), tag("c", "tools/axes"), tag("c", "tools/mace"), tag("c", "tools/maces"));

    private record Spec(SwingStep first, SwingStep second, MovesetTiming timing, double reachBonus,
                        float coneHalfAngleDeg, int maxTargets, int approachPenaltyTicks, int chainGraceTicks,
                        TrackingBudget tracking) {
    }

    private static final Map<Moveset, Spec> SPECS = new EnumMap<>(Moveset.class);

    static {
        SPECS.put(Moveset.SWING, new Spec(
            step(SwingSide.LEFT, 70, -60, 15), step(SwingSide.RIGHT, 70, -60, 15),
            new MovesetTiming(0.24f, 0.68f, 0.5f, 5.0f),
            0.0, 22.0f, 2, 4, 8, new TrackingBudget(30.0f, 9.0f, 1.5f)));
        SPECS.put(Moveset.CHOP, new Spec(
            step(SwingSide.LEFT, 150, -50, 20), step(SwingSide.RIGHT, 140, -50, 20),
            new MovesetTiming(0.23f, 0.69f, 0.5f, 6.0f),
            0.0, 18.0f, 2, 5, 8, new TrackingBudget(26.0f, 8.0f, 1.5f)));
        SPECS.put(Moveset.THRUST, new Spec(
            step(SwingSide.LEFT, 8, -4, 0), step(SwingSide.RIGHT, 8, -4, 0),
            new MovesetTiming(0.25f, 0.7f, 0.5f, 5.0f),
            0.0, 12.0f, 2, 4, 8, new TrackingBudget(22.0f, 9.0f, 1.5f)));
        SPECS.put(Moveset.AERIAL, new Spec(
            step(SwingSide.LEFT, 165, -75, 8), step(SwingSide.RIGHT, 165, -75, 8),
            new MovesetTiming(0.23f, 0.69f, 0.5f, 6.0f),
            0.0, 20.0f, 2, 5, 8, new TrackingBudget(26.0f, 8.0f, 1.5f)));
        SPECS.put(Moveset.FIST, new Spec(
            step(SwingSide.LEFT, 10, -5, 0), step(SwingSide.RIGHT, 10, -5, 0),
            new MovesetTiming(0.14f, 0.62f, 0.5f, 6.0f),
            -0.5, 20.0f, 1, 3, 7, new TrackingBudget(30.0f, 12.0f, 1.5f)));
    }

    private Movesets() {
    }

    /** The moveset {@code wielder} uses {@code moveset} with, reach included. */
    public static WeaponProfile profile(Moveset moveset, LivingEntity wielder) {
        Spec spec = SPECS.get(moveset);
        // The wielder's own reach without the spear modifier, plus whatever this moveset grants: right for a
        // held spear, an off-hand one, or one only hovered in a tooltip, and never counted twice.
        double reach = wielder.getAttributes().hasAttribute(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE)
            ? wielder.getAttributeValue(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE) - SpearReach.appliedBonus(wielder)
            : DEFAULT_REACH;
        double range = Math.max(1.0, reach + SpearReach.bonusFor(moveset) + spec.reachBonus());
        return new WeaponProfile(moveset, spec.first(), spec.second(), spec.timing(), range, range + SNAP_EXTRA_RANGE,
            spec.coneHalfAngleDeg(), spec.maxTargets(), spec.approachPenaltyTicks(), spec.chainGraceTicks(),
            spec.tracking());
    }

    /**
     * Which attack {@code stack} gets, or null to leave it to vanilla entirely. An empty hand, or anything
     * that isn't a weapon, punches.
     */
    public static @Nullable Moveset detect(ItemStack stack) {
        if (stack.isEmpty()) {
            return Moveset.FIST;
        }
        Overrides.Rule override = Overrides.lookup(stack);
        if (override != null) {
            return override.moveset();
        }
        Item item = stack.getItem();
        if (VANILLA_HANDLED.contains(Registries.ITEM.getId(item))) {
            return null;
        }
        if (item instanceof TridentItem || isInAny(stack, THRUST_TAGS) || nameHints(item, THRUST_NAME_HINTS)) {
            return Moveset.THRUST;
        }
        if (item instanceof AxeItem || item instanceof MaceItem || isInAny(stack, CHOP_TAGS)
                || nameHints(item, CHOP_NAME_HINTS)) {
            return Moveset.CHOP;
        }
        return dealsMeleeDamage(stack) ? Moveset.SWING : Moveset.FIST;
    }

    /** True when holding the item adds attack damage: weapons and tools, not blocks or food. */
    public static boolean dealsMeleeDamage(ItemStack stack) {
        double[] added = {0.0};
        stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                    && modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                added[0] += modifier.value();
            }
        });
        return added[0] > 0.0;
    }

    private static boolean isInAny(ItemStack stack, List<TagKey<Item>> tags) {
        for (TagKey<Item> tag : tags) {
            if (stack.isIn(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nameHints(Item item, String[] hints) {
        String path = Registries.ITEM.getId(item).getPath().toLowerCase(Locale.ROOT);
        for (String hint : hints) {
            if (path.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static SwingStep step(SwingSide side, float start, float end, float tilt) {
        return new SwingStep(side, new SwingArc(start, end, tilt));
    }

    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.of(RegistryKeys.ITEM, Identifier.of(namespace, path));
    }

    /** The config's {@code movesetOverrides}, parsed once per config instance. */
    private static final class Overrides {
        /** @param moveset the moveset to use, or null for {@code "vanilla"}: leave the item to vanilla */
        private record Rule(@Nullable Identifier item, @Nullable TagKey<Item> tag, @Nullable Moveset moveset) {
        }

        private static @Nullable CleanCombosConfig parsedFor;
        private static List<Rule> rules = List.of();

        static @Nullable Rule lookup(ItemStack stack) {
            CleanCombosConfig config = CleanCombosConfig.get();
            if (config != parsedFor) {
                rules = parse(config.movesetOverrides);
                parsedFor = config;
            }
            Identifier id = Registries.ITEM.getId(stack.getItem());
            for (Rule rule : rules) {
                if (rule.item != null ? rule.item.equals(id) : stack.isIn(rule.tag)) {
                    return rule;
                }
            }
            return null;
        }

        private static List<Rule> parse(@Nullable Map<String, String> entries) {
            List<Rule> parsed = new ArrayList<>();
            if (entries == null) {
                return parsed;
            }
            entries.forEach((key, value) -> {
                try {
                    Moveset moveset = "vanilla".equalsIgnoreCase(value.trim()) ? null : Moveset.parse(value);
                    boolean isTag = key.startsWith("#");
                    Identifier id = Identifier.of(isTag ? key.substring(1) : key);
                    parsed.add(isTag ? new Rule(null, TagKey.of(RegistryKeys.ITEM, id), moveset)
                        : new Rule(id, null, moveset));
                } catch (RuntimeException e) {
                    CleanCombos.LOGGER.warn("Ignoring moveset override '{}' -> '{}': {}", key, value, e.toString());
                }
            });
            return parsed;
        }
    }
}
