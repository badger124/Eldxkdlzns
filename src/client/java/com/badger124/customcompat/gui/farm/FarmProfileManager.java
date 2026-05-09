package com.badger124.customcompat.gui.farm;

import com.badger124.customcompat.CustomCompatMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton that manages {@link FarmProfile farm profiles} and persists them to
 * {@code config/customcompat_farm_profiles.json}.
 *
 * <p>Call {@link #initialize(Path)} once from the client initializer before any profile
 * operations.</p>
 */
public final class FarmProfileManager {

    private static final FarmProfileManager INSTANCE = new FarmProfileManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<FarmProfile> profiles = new ArrayList<>();
    private Path savePath;

    private FarmProfileManager() {}

    public static FarmProfileManager getInstance() { return INSTANCE; }

    // =========================================================================
    // Initialization
    // =========================================================================

    public void initialize(Path configDir) {
        this.savePath = configDir.resolve("customcompat_farm_profiles.json");
        load();
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    public void load() {
        if (savePath == null || !Files.exists(savePath)) return;
        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(savePath), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<FarmProfile>>() {}.getType();
            List<FarmProfile> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                profiles.clear();
                profiles.addAll(loaded);
            }
        } catch (IOException | com.google.gson.JsonParseException e) {
            CustomCompatMod.LOGGER.warn("[CustomCompat] Could not load farm profiles: {}", e.getMessage());
        }
    }

    public void save() {
        if (savePath == null) return;
        try {
            Files.createDirectories(savePath.getParent());
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    Files.newOutputStream(savePath), StandardCharsets.UTF_8)) {
                GSON.toJson(profiles, writer);
            }
        } catch (IOException e) {
            CustomCompatMod.LOGGER.warn("[CustomCompat] Could not save farm profiles: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Profile list management
    // =========================================================================

    public List<FarmProfile> getProfiles() { return profiles; }

    public void addProfile(FarmProfile p) {
        profiles.add(p);
        save();
    }

    public void removeProfile(FarmProfile p) {
        profiles.remove(p);
        save();
    }

    public void update() { save(); }
}
