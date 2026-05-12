package com.badger124.customcompat.inspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of a custom entity found during a world scan.
 *
 * <ul>
 *   <li>{@link #entityType} – Minecraft entity type id (e.g. {@code minecraft:zombie})</li>
 *   <li>{@link #customTags} – scoreboard tags that start with {@code "customcompat:"}</li>
 *   <li>{@link #allTags}    – all scoreboard tags on the entity</li>
 *   <li>{@link #customName} – display name, if set</li>
 * </ul>
 */
public final class ScannedEntity {

    public String       entityType;
    public List<String> customTags = new ArrayList<>();
    public List<String> allTags    = new ArrayList<>();
    public String       customName;

    ScannedEntity() {}

    /** Returns a compact, human-readable summary line. */
    public String toDisplayLine() {
        StringBuilder sb = new StringBuilder(entityType != null ? entityType : "?");
        if (customName != null) {
            sb.append("  \"").append(customName).append("\"");
        }
        if (!customTags.isEmpty()) {
            sb.append("  ⇒ ").append(String.join(", ", customTags));
        } else if (!allTags.isEmpty()) {
            sb.append("  tags=").append(String.join(", ", allTags));
        }
        return sb.toString();
    }
}
