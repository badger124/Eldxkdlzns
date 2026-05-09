package com.badger124.customcompat.impl;

import com.badger124.customcompat.api.CustomEntityEntry;
import com.badger124.customcompat.api.CustomItemEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Internal registry that stores all registered custom item and entity entries.
 * Not part of the public API – use {@link com.badger124.customcompat.api.CustomCompatApi} instead.
 */
public final class CustomCompatRegistryImpl {

    private static final List<CustomItemEntry> ITEM_ENTRIES = new ArrayList<>();
    private static final List<CustomEntityEntry> ENTITY_ENTRIES = new ArrayList<>();

    private CustomCompatRegistryImpl() {}

    // -----------------------------------------------------------------------
    // Registration

    public static void registerItem(CustomItemEntry entry) {
        if (entry == null) throw new NullPointerException("entry must not be null");
        if (entry.id() == null) throw new NullPointerException("entry id must not be null");
        if (entry.predicate() == null) throw new NullPointerException("entry predicate must not be null");
        ITEM_ENTRIES.add(entry);
    }

    public static void registerEntity(CustomEntityEntry entry) {
        if (entry == null) throw new NullPointerException("entry must not be null");
        if (entry.id() == null) throw new NullPointerException("entry id must not be null");
        if (entry.predicate() == null) throw new NullPointerException("entry predicate must not be null");
        ENTITY_ENTRIES.add(entry);
    }

    // -----------------------------------------------------------------------
    // Lookup

    public static Optional<Identifier> resolveItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        for (CustomItemEntry entry : ITEM_ENTRIES) {
            if (entry.predicate().test(stack)) {
                return Optional.of(entry.id());
            }
        }
        return Optional.empty();
    }

    public static Optional<Identifier> resolveEntity(Entity entity) {
        if (entity == null) return Optional.empty();
        for (CustomEntityEntry entry : ENTITY_ENTRIES) {
            if (entry.predicate().test(entity)) {
                return Optional.of(entry.id());
            }
        }
        return Optional.empty();
    }

    // -----------------------------------------------------------------------
    // Read-only views (for debugging / tooling)

    public static List<CustomItemEntry> getItemEntries() {
        return Collections.unmodifiableList(ITEM_ENTRIES);
    }

    public static List<CustomEntityEntry> getEntityEntries() {
        return Collections.unmodifiableList(ENTITY_ENTRIES);
    }
}
