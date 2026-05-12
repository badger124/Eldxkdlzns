package com.badger124.customcompat.inspector;

/**
 * Snapshot of a custom item found during a world scan.
 *
 * <ul>
 *   <li>{@link #nexoId}      – value of {@code PublicBukkitValues."nexo:id"}, or {@code null}</li>
 *   <li>{@link #baseItem}    – vanilla item id (e.g. {@code minecraft:paper})</li>
 *   <li>{@link #cmdFloat}    – first float in {@code minecraft:custom_model_data}, or {@code null}</li>
 *   <li>{@link #source}      – "inventory", "hotbar", or "ground"</li>
 * </ul>
 */
public final class ScannedItem {

    public String nexoId;
    public String baseItem;
    public Float  cmdFloat;
    public String source;

    ScannedItem() {}

    /** Returns a compact, human-readable summary line. */
    public String toDisplayLine() {
        StringBuilder sb = new StringBuilder();
        if (nexoId != null) {
            sb.append("nexo:").append(nexoId);
        } else {
            sb.append(baseItem != null ? baseItem : "?");
        }
        if (cmdFloat != null) {
            sb.append("  cmd=").append(cmdFloat.intValue());
        }
        if (source != null) {
            sb.append("  [").append(source).append("]");
        }
        return sb.toString();
    }
}
