package com.cleannrooster.exiled_combat.config;

import com.cleannrooster.exiled_combat.ExiledCombat;
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
 * {@code config/exiled_combat.json}. Gameplay values the server decides; the server's whole copy is sent to
 * clients on join, so a client always plays by the server it is on.
 */
public final class ExiledCombatConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "exiled_combat.json";

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
    /** Ticks a connecting swing freezes for (chops and jump attacks one more); 0 turns hitstop off. */
    public int hitstopTicks = 3;
    /** Two hands on one weapon (empty off hand): damage and attack-speed multipliers. */
    public float twoHandedDamage = 1.25f;
    public float twoHandedSpeed = 0.8f;
    /** A weapon in each hand: damage and attack-speed multipliers. */
    public float dualWieldDamage = 0.8f;
    public float dualWieldSpeed = 1.25f;
    /**
     * Fixes for the rare misdetected weapon: item id or {@code #tag} to {@code swing}, {@code chop},
     * {@code thrust} or {@code fist}. Checked before automatic detection.
     */
    public Map<String, String> movesetOverrides = new LinkedHashMap<>();

    private static ExiledCombatConfig local = new ExiledCombatConfig();
    private static ExiledCombatConfig active = local;

    /** Read the file, writing it back with any new fields filled in at their defaults. */
    public static void load() {
        Path path = Platform.getConfigFolder().resolve(FILE_NAME);
        ExiledCombatConfig loaded = new ExiledCombatConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ExiledCombatConfig parsed = GSON.fromJson(reader, ExiledCombatConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (IOException | JsonParseException e) {
                ExiledCombat.LOGGER.error("Could not read {}, using defaults: {}", path, e.toString());
            }
        }
        loaded.sanitise();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(loaded, writer);
            }
        } catch (IOException e) {
            ExiledCombat.LOGGER.error("Could not write {}: {}", path, e.toString());
        }
        local = loaded;
        active = loaded;
    }

    private void sanitise() {
        attackMoveSpeed = MathHelper.clamp(attackMoveSpeed, 0.0f, 1.0f);
        comboRhythm = MathHelper.clamp(comboRhythm, 0.0f, 1.0f);
        attackPace = MathHelper.clamp(attackPace, 0.2f, 3.0f);
        hitstopTicks = MathHelper.clamp(hitstopTicks, 0, 10);
        twoHandedDamage = MathHelper.clamp(twoHandedDamage, 0.1f, 10.0f);
        twoHandedSpeed = MathHelper.clamp(twoHandedSpeed, 0.1f, 10.0f);
        dualWieldDamage = MathHelper.clamp(dualWieldDamage, 0.1f, 10.0f);
        dualWieldSpeed = MathHelper.clamp(dualWieldSpeed, 0.1f, 10.0f);
        if (movesetOverrides == null) {
            movesetOverrides = new LinkedHashMap<>();
        }
    }

    /** The values in force: the server's while connected, otherwise this install's own file. */
    public static ExiledCombatConfig get() {
        return active;
    }

    public static ExiledCombatConfig local() {
        return local;
    }

    /** This install's config as JSON, for the server to send to clients. */
    public static String localAsJson() {
        return GSON.toJson(local);
    }

    /** Client: adopt the server's values for this connection. */
    public static void applyServerJson(String json) {
        try {
            ExiledCombatConfig synced = GSON.fromJson(json, ExiledCombatConfig.class);
            if (synced != null) {
                synced.sanitise();
                active = synced;
            }
        } catch (JsonParseException e) {
            ExiledCombat.LOGGER.error("Ignoring malformed server config: {}", e.toString());
        }
    }

    /** Client: back to this install's own file after disconnecting. */
    public static void clearServerValues() {
        active = local;
    }
}
