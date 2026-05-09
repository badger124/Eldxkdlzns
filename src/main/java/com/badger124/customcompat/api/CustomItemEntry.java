package com.badger124.customcompat.api;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.function.Predicate;

/**
 * Represents a registered custom item type.
 *
 * <p>A custom item entry pairs a stable {@link Identifier} with a {@link Predicate}
 * that inspects an {@link ItemStack} to decide whether it matches this custom type.
 * The predicate is evaluated in order against registered entries; the first match wins.</p>
 *
 * @param id        Stable identifier for this custom item type (e.g. {@code mymod:magic_sword}).
 * @param predicate Returns {@code true} when the given {@link ItemStack} is this custom item.
 */
public record CustomItemEntry(Identifier id, Predicate<ItemStack> predicate) {}
