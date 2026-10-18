package com.cleannrooster.clean_combos.config;

import com.cleannrooster.clean_combos.CleanCombos;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.architectury.platform.Platform;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config/clean_combos.json}. Gameplay values the server decides; the server's whole copy is sent to
 * clients on join, so a client always plays by the server it is on.
 */
public final class CleanCombosConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "clean_combos.json";

    /** Fraction of normal movement speed while an attack is locked in (the snap itself stays fully locked). */
    public float attackMoveSpeed = 0.4f;
    /**
     * The combo's one-two beat: 1 keeps each moveset's rhythm (slow first swing, quick second), 0 gives both
     * swings the same time, values in between blend. A combo's total time is the same either way.
     */
    public float comboRhythm = 1.0f;
    /**
     * Multiplies vanilla's attack time ({@code 20 / attackSpeed} ticks) to give the average swing's time:
     * below 1 is snappier than vanilla's cooldown, above 1 slower.
     */
    public float attackPace = 0.8f;
    /**
     * Flat bonus to every attack's speed, in percent, on top of the weapon and grip. The default 25 makes a
     * two-handed sword (1.6 × 0.8) swing at the sword's own 1.6, so two-handing doesn't feel slow; 0 turns it off.
     */
    public float attackSpeedBonusPercent = 25.0f;
    /**
     * Anti-scaling: attack speed above this (after the weapon, grip and bonus above) has diminishing returns,
     * so stacked attack speed can't run away. Normal weapons sit below it and are untouched. 0 turns it off.
     */
    public float attackSpeedSoftCap = 2.0f;
    /** The share of each point of attack speed above the soft cap that still counts (0.5 = half). */
    public float attackSpeedOverCap = 0.5f;
    /** Ticks a connecting swing freezes for (chops and jump attacks one more); 0 turns hitstop off. */
    public int hitstopTicks = 3;
    /** Extra entity-interaction range, in blocks, while a spear (thrust weapon) is in the main hand. */
    public float spearReachBonus = 1.0f;
    /** Two hands on one weapon (empty off hand): damage and attack-speed multipliers. */
    public float twoHandedDamage = 1.25f;
    public float twoHandedSpeed = 0.8f;
    /** A weapon in each hand: damage and attack-speed multipliers. */
    public float dualWieldDamage = 0.8f;
    public float dualWieldSpeed = 1.25f;
    /**
     * Play the attack clips whole — arms included, the weapon in vanilla's hold — with trails following the
     * weapon the clip moves (fists and thrusts keep their straight trail). Off: the older style, with
     * procedural arms and a turned weapon on the authored swing arc under the clips' body motion.
     */
    public boolean clipOnlyAnimation = true;
    /**
     * Fixes for the rare misdetected weapon: item id or {@code #tag} to {@code swing}, {@code chop},
     * {@code thrust} or {@code fist}. Checked before automatic detection.
     */
    public Map<String, String> movesetOverrides = new LinkedHashMap<>();

    private static CleanCombosConfig local = new CleanCombosConfig();
    private static CleanCombosConfig active = local;

    /** Read the file, writing it back with any new fields filled in at their defaults. */
    public static void load() {
        Path path = Platform.getConfigFolder().resolve(FILE_NAME);
        CleanCombosConfig loaded = new CleanCombosConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                CleanCombosConfig parsed = GSON.fromJson(reader, CleanCombosConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (IOException | JsonParseException e) {
                CleanCombos.LOGGER.error("Could not read {}, using defaults: {}", path, e.toString());
            }
        }
        loaded.sanitise();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(loaded, writer);
            }
        } catch (IOException e) {
            CleanCombos.LOGGER.error("Could not write {}: {}", path, e.toString());
        }
        local = loaded;
        active = loaded;
    }

    private void sanitise() {
        attackMoveSpeed = MathHelper.clamp(attackMoveSpeed, 0.0f, 1.0f);
        comboRhythm = MathHelper.clamp(comboRhythm, 0.0f, 1.0f);
        attackPace = MathHelper.clamp(attackPace, 0.2f, 3.0f);
        attackSpeedBonusPercent = MathHelper.clamp(attackSpeedBonusPercent, -90.0f, 400.0f);
        attackSpeedSoftCap = MathHelper.clamp(attackSpeedSoftCap, 0.0f, 20.0f);
        attackSpeedOverCap = MathHelper.clamp(attackSpeedOverCap, 0.0f, 1.0f);
        hitstopTicks = MathHelper.clamp(hitstopTicks, 0, 10);
        spearReachBonus = MathHelper.clamp(spearReachBonus, 0.0f, 5.0f);
        twoHandedDamage = MathHelper.clamp(twoHandedDamage, 0.1f, 10.0f);
        twoHandedSpeed = MathHelper.clamp(twoHandedSpeed, 0.1f, 10.0f);
        dualWieldDamage = MathHelper.clamp(dualWieldDamage, 0.1f, 10.0f);
        dualWieldSpeed = MathHelper.clamp(dualWieldSpeed, 0.1f, 10.0f);
        if (movesetOverrides == null) {
            movesetOverrides = new LinkedHashMap<>();
        }
    }

    /** The values in force: the server's while connected, otherwise this install's own file. */
    public static CleanCombosConfig get() {
        return active;
    }

    public static CleanCombosConfig local() {
        return local;
    }

    /** This install's config as JSON, for the server to send to clients. */
    public static String localAsJson() {
        return GSON.toJson(local);
    }

    /** Client: adopt the server's values for this connection. */
    public static void applyServerJson(String json) {
        try {
            CleanCombosConfig synced = GSON.fromJson(json, CleanCombosConfig.class);
            if (synced != null) {
                synced.sanitise();
                active = synced;
            }
        } catch (JsonParseException e) {
            CleanCombos.LOGGER.error("Ignoring malformed server config: {}", e.toString());
        }
    }

    /** Client: back to this install's own file after disconnecting. */
    public static void clearServerValues() {
        active = local;
    }
}
