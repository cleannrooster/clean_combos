package com.cleannrooster.clean_combos.config;

import com.cleannrooster.clean_combos.CleanCombos;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/clean_combos-client.json}. This player's own display preferences: never sent anywhere and never
 * replaced by a server's values, unlike {@link CleanCombosConfig}.
 */
public final class CleanCombosClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "clean_combos-client.json";

    /**
     * Draw your own arms in first person during a weapon attack (the weapons are drawn either way). Fist
     * attacks always draw them, since the arms are the attack.
     */
    public boolean firstPersonArms = false;
    /**
     * For 4 seconds after an attack you hold a combat stance for your grip (third person), blending in as the
     * attack ends; walking eases you out of it until you stop.
     */
    public boolean idleStances = true;

    private static CleanCombosClientConfig instance = new CleanCombosClientConfig();

    /** Read the file, writing it back with any new fields filled in at their defaults. */
    public static void load() {
        Path path = Platform.getConfigFolder().resolve(FILE_NAME);
        CleanCombosClientConfig loaded = new CleanCombosClientConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                CleanCombosClientConfig parsed = GSON.fromJson(reader, CleanCombosClientConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (IOException | JsonParseException e) {
                CleanCombos.LOGGER.error("Could not read {}, using defaults: {}", path, e.toString());
            }
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(loaded, writer);
            }
        } catch (IOException e) {
            CleanCombos.LOGGER.error("Could not write {}: {}", path, e.toString());
        }
        instance = loaded;
    }

    public static CleanCombosClientConfig get() {
        return instance;
    }
}
