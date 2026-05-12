package com.badger124.customcompat.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * A named sequence of command steps to be executed in order by {@link MacroManager}.
 *
 * <p>Each step is a plain string in one of these formats:</p>
 * <pre>
 *   pickup nexo:crop_tomato_seed
 *   follow mymod:boss_zombie
 *   farm 50
 *   wait 5
 *   stop
 * </pre>
 * <p>Lines starting with {@code #} are treated as comments and ignored.</p>
 */
public final class MacroEntry {

    private String name;
    private List<String> steps;

    /** No-arg constructor required for Gson deserialization. */
    public MacroEntry() {
        this.name = "Unnamed";
        this.steps = new ArrayList<>();
    }

    public MacroEntry(String name) {
        this.name = name;
        this.steps = new ArrayList<>();
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public String getName() { return name; }

    public void setName(String name) { this.name = (name == null || name.isBlank()) ? "Unnamed" : name; }

    public List<String> getSteps() {
        if (steps == null) steps = new ArrayList<>();
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Returns all steps joined by newlines (comments/blank lines preserved). */
    public String getScript() {
        return String.join("\n", getSteps());
    }

    @Override
    public String toString() {
        return "MacroEntry{name='" + name + "', steps=" + (steps == null ? 0 : steps.size()) + "}";
    }
}
