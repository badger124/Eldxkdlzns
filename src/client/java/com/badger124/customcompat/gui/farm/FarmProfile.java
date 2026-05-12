package com.badger124.customcompat.gui.farm;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for one custom farming profile.
 *
 * <h2>Farmland detection</h2>
 * <p>The server uses <b>note blocks</b> as custom farmland. The note block's {@code note}
 * property (0–24) determines moisture:</p>
 * <ul>
 *   <li>{@code wetPitchMin}–{@code wetPitchMax}: wet farmland — crops can grow and
 *       the handler will harvest &amp; replant here.</li>
 *   <li>Any other pitch: dry farmland — skipped when {@code skipDry} is {@code true}.</li>
 * </ul>
 *
 * <h2>Crop detection (crop mappings)</h2>
 * <p>Each crop mapping line has the form:</p>
 * <pre>  &lt;blockId&gt;  &lt;matureAge&gt;  &lt;seedNexoId&gt;</pre>
 * <p>Example:</p>
 * <pre>  minecraft:wheat  7  nexo:tomato_seed</pre>
 * <p>Set {@code matureAge} to {@code -1} to harvest regardless of age (block ID match only).</p>
 *
 * <h2>Trap-state context</h2>
 * <p>The server may also use tripwire-hook states adjacent to the note block to encode
 * additional metadata (growth rate, crop variety). Future versions of this profile will
 * expose trap-state configuration; for now the crop type is identified purely from the
 * block above the note block.</p>
 */
public final class FarmProfile {

    private String name = "New Profile";
    /** Horizontal scan radius in blocks (capped at 64 for performance). */
    private int range = 32;
    /** Inclusive lower bound of the "wet" note-pitch range (0–24). */
    private int wetPitchMin = 0;
    /** Inclusive upper bound of the "wet" note-pitch range (0–24). */
    private int wetPitchMax = 12;
    /** When {@code true}, note blocks outside the wet range are skipped. */
    private boolean skipDry = true;
    /**
     * Crop mapping lines.  Each non-empty, non-comment line has the format:
     * {@code <blockId> <matureAge> <seedNexoId>}
     */
    private List<String> cropLines = new ArrayList<>();

    /** No-arg constructor required for Gson deserialization. */
    public FarmProfile() {}

    public FarmProfile(String name) {
        this.name = name;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public String getName() { return name; }
    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Unnamed" : name;
    }

    public int getRange() { return range; }
    public void setRange(int range) { this.range = Math.max(1, Math.min(64, range)); }

    public int getWetPitchMin() { return wetPitchMin; }
    public void setWetPitchMin(int v) { this.wetPitchMin = Math.max(0, Math.min(24, v)); }

    public int getWetPitchMax() { return wetPitchMax; }
    public void setWetPitchMax(int v) { this.wetPitchMax = Math.max(0, Math.min(24, v)); }

    public boolean isSkipDry() { return skipDry; }
    public void setSkipDry(boolean skipDry) { this.skipDry = skipDry; }

    public List<String> getCropLines() {
        if (cropLines == null) cropLines = new ArrayList<>();
        return cropLines;
    }
    public void setCropLines(List<String> lines) {
        this.cropLines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Returns {@code true} if the given note-block pitch indicates wet farmland. */
    public boolean isWetPitch(int note) {
        return note >= wetPitchMin && note <= wetPitchMax;
    }

    /**
     * Parses all non-empty, non-comment crop lines and returns the resulting mappings.
     * Malformed lines are silently skipped.
     */
    public List<CropMapping> parsedCrops() {
        List<CropMapping> result = new ArrayList<>();
        for (String line : getCropLines()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 3) continue;
            try {
                String blockId = parts[0];
                int matureAge = Integer.parseInt(parts[1]);
                String seedNexoId = parts[2];
                result.add(new CropMapping(blockId, matureAge, seedNexoId));
            } catch (NumberFormatException ignored) {
                // skip malformed line
            }
        }
        return result;
    }

    // =========================================================================
    // CropMapping
    // =========================================================================

    /** Parsed representation of one crop mapping line. */
    public record CropMapping(String blockId, int matureAge, String seedNexoId) {}

    @Override
    public String toString() {
        return "FarmProfile{name='" + name + "', crops=" + getCropLines().size() + "}";
    }
}
