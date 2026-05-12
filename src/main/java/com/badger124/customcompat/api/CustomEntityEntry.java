package com.badger124.customcompat.api;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import java.util.function.Predicate;

/**
 * Represents a registered custom entity type.
 *
 * <p>A custom entity entry pairs a stable {@link Identifier} with a {@link Predicate}
 * that inspects an {@link Entity} to decide whether it represents this custom type.
 * Resource-pack custom "entity types" are not true registry entries; they are ordinary
 * vanilla entities distinguished by scoreboard tags, custom names, equipment, NBT, or
 * other server-controlled markers. This entry captures that mapping explicitly.</p>
 *
 * @param id        Stable identifier for this custom entity type (e.g. {@code mymod:boss_zombie}).
 * @param predicate Returns {@code true} when the given {@link Entity} represents this custom type.
 */
public record CustomEntityEntry(Identifier id, Predicate<Entity> predicate) {}
