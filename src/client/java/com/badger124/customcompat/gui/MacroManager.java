package com.badger124.customcompat.gui;

import com.badger124.customcompat.CustomCompatMod;
import com.badger124.customcompat.compat.baritone.BaritoneCompat;
import com.badger124.customcompat.gui.farm.CustomFarmingHandler;
import com.badger124.customcompat.gui.farm.FarmProfile;
import com.badger124.customcompat.gui.farm.FarmProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
 * Singleton that manages saved {@link MacroEntry macros} and executes them step-by-step
 * on the client tick.
 *
 * <h2>Supported step commands</h2>
 * <pre>
 *   pickup nexo:crop_tomato_seed   – tells Baritone to pick up matching dropped items
 *   follow mymod:boss_zombie       – tells Baritone to follow matching entities
 *   farm [range]                   – starts Baritone farming (range defaults to 0 = unlimited)
 *   farmcustom &lt;profileName&gt;      – starts custom note-block farm handler for the named profile
 *   wait  &lt;seconds&gt;               – pauses the macro for the given number of seconds
 *   stop                           – stops Baritone and the custom farm handler
 *   # comment                      – ignored
 * </pre>
 *
 * <h2>Lifecycle</h2>
 * <p>Call {@link #initialize(Path)} once from the client initializer. This registers the
 * tick event listener and loads saved macros from disk.</p>
 */
public final class MacroManager {

    private static final MacroManager INSTANCE = new MacroManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<MacroEntry> macros = new ArrayList<>();
    private Path savePath;

    // ── Execution state ──────────────────────────────────────────────────────
    private MacroEntry activeMacro = null;
    private int stepIndex = 0;
    private int waitTicksRemaining = 0;
    private boolean running = false;

    private MacroManager() {}

    public static MacroManager getInstance() { return INSTANCE; }

    // =========================================================================
    // Initialization
    // =========================================================================

    /**
     * Registers the tick listener and loads macros from disk.
     * Must be called once during client initialization.
     *
     * @param configDir The directory in which to save {@code customcompat_macros.json}.
     */
    public void initialize(Path configDir) {
        this.savePath = configDir.resolve("customcompat_macros.json");
        loadMacros();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    public void loadMacros() {
        if (savePath == null || !Files.exists(savePath)) return;
        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(savePath), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<MacroEntry>>() {}.getType();
            List<MacroEntry> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                macros.clear();
                macros.addAll(loaded);
            }
        } catch (IOException | com.google.gson.JsonParseException e) {
            CustomCompatMod.LOGGER.warn("[CustomCompat] Could not load macros: {}", e.getMessage());
        }
    }

    public void saveMacros() {
        if (savePath == null) return;
        try {
            Files.createDirectories(savePath.getParent());
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    Files.newOutputStream(savePath), StandardCharsets.UTF_8)) {
                GSON.toJson(macros, writer);
            }
        } catch (IOException e) {
            CustomCompatMod.LOGGER.warn("[CustomCompat] Could not save macros: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Macro list management
    // =========================================================================

    public List<MacroEntry> getMacros() { return macros; }

    public void addMacro(MacroEntry entry) {
        macros.add(entry);
        saveMacros();
    }

    public void removeMacro(MacroEntry entry) {
        if (activeMacro == entry) stopMacro();
        macros.remove(entry);
        saveMacros();
    }

    /** Call after mutating a MacroEntry to persist changes. */
    public void updateMacro(MacroEntry entry) {
        saveMacros();
    }

    // =========================================================================
    // Execution control
    // =========================================================================

    public boolean isRunning() { return running; }

    public MacroEntry getActiveMacro() { return activeMacro; }

    /** Returns the 1-based index of the step that is currently being executed (or about to be). */
    public int getStepIndex() { return stepIndex; }

    public void startMacro(MacroEntry macro) {
        this.activeMacro = macro;
        this.stepIndex = 0;
        this.waitTicksRemaining = 0;
        this.running = true;
        CustomCompatMod.LOGGER.info("[CustomCompat] Macro '{}' started.", macro.getName());
    }

    public void stopMacro() {
        running = false;
        activeMacro = null;
        stepIndex = 0;
        waitTicksRemaining = 0;
        CustomCompatMod.LOGGER.info("[CustomCompat] Macro stopped.");
    }

    // =========================================================================
    // Tick handler
    // =========================================================================

    private void onTick(MinecraftClient client) {
        if (!running || activeMacro == null) return;

        // During a wait, just count down ticks
        if (waitTicksRemaining > 0) {
            waitTicksRemaining--;
            return;
        }

        List<String> steps = activeMacro.getSteps();

        // Skip blank/comment lines
        while (stepIndex < steps.size()) {
            String line = steps.get(stepIndex).trim();
            if (!line.isEmpty() && !line.startsWith("#")) break;
            stepIndex++;
        }

        if (stepIndex >= steps.size()) {
            stopMacro();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[CustomCompat] Macro finished."), true);
            }
            return;
        }

        String step = steps.get(stepIndex);
        stepIndex++;
        executeStep(client, step.trim());
    }

    private void executeStep(MinecraftClient client, String step) {
        if (step.isEmpty() || step.startsWith("#")) return;

        String[] parts = step.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "pickup" -> {
                Identifier id = Identifier.tryParse(arg);
                if (id != null) {
                    boolean ok = BaritoneCompat.pickupCustomItems(id);
                    feedback(client, ok
                            ? "[CustomCompat] Macro: pickup " + id
                            : "[CustomCompat] Macro: pickup failed (" + arg + ")");
                } else {
                    feedback(client, "[CustomCompat] Macro: invalid id '" + arg + "'");
                }
            }
            case "follow" -> {
                Identifier id = Identifier.tryParse(arg);
                if (id != null) {
                    boolean ok = BaritoneCompat.followCustomEntity(id);
                    feedback(client, ok
                            ? "[CustomCompat] Macro: follow " + id
                            : "[CustomCompat] Macro: follow failed (" + arg + ")");
                } else {
                    feedback(client, "[CustomCompat] Macro: invalid id '" + arg + "'");
                }
            }
            case "farm" -> {
                int range = 0;
                if (!arg.isEmpty()) {
                    try {
                        range = Integer.parseInt(arg);
                    } catch (NumberFormatException e) {
                        CustomCompatMod.LOGGER.warn("[CustomCompat] Macro: invalid farm range '{}', defaulting to 0 (unlimited).", arg);
                    }
                }
                boolean ok = BaritoneCompat.farm(range);
                feedback(client, ok
                        ? "[CustomCompat] Macro: farm started (range=" + range + ")"
                        : "[CustomCompat] Macro: farm failed");
            }
            case "farmcustom" -> {
                // arg = profile name
                if (arg.isBlank()) {
                    feedback(client, "[CustomCompat] Macro: farmcustom requires a profile name.");
                    break;
                }
                FarmProfile target = FarmProfileManager.getInstance().getProfiles().stream()
                        .filter(p -> p.getName().equalsIgnoreCase(arg))
                        .findFirst()
                        .orElse(null);
                if (target == null) {
                    feedback(client, "[CustomCompat] Macro: farm profile '" + arg + "' not found.");
                } else {
                    CustomFarmingHandler.getInstance().start(target);
                    feedback(client, "[CustomCompat] Macro: custom farming started (" + target.getName() + ")");
                }
            }
            case "wait" -> {
                int seconds = 5;
                if (!arg.isEmpty()) {
                    try {
                        seconds = Math.max(0, Integer.parseInt(arg));
                    } catch (NumberFormatException e) {
                        CustomCompatMod.LOGGER.warn("[CustomCompat] Macro: invalid wait duration '{}', defaulting to 5 seconds.", arg);
                    }
                }
                waitTicksRemaining = seconds * 20; // 20 ticks = 1 second
            }
            case "stop" -> {
                stopMacro();
                CustomFarmingHandler.getInstance().stop();
                BaritoneCompat.stop();
                feedback(client, "[CustomCompat] Macro: stopped by 'stop' step.");
            }
            default -> CustomCompatMod.LOGGER.warn("[CustomCompat] Unknown macro step: '{}'", step);
        }
    }

    private static void feedback(MinecraftClient client, String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(msg), true);
        }
    }
}
