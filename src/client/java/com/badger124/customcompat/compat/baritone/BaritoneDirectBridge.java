package com.badger124.customcompat.compat.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

/**
 * Thin wrapper around the real Baritone API.
 *
 * <p>This class uses direct imports from {@code baritone.api.*} and is therefore only
 * safe to load when Baritone is present on the classpath. {@link BaritoneCompat} guards
 * all calls with its {@code LOADED} flag so this class is never referenced unless Baritone
 * has been found at startup.</p>
 *
 * <p>Package-private — only {@link BaritoneCompat} may call into this class.</p>
 */
final class BaritoneDirectBridge {

    private BaritoneDirectBridge() {}

    static boolean follow(Predicate<Entity> predicate) {
        IBaritone b = primary();
        b.getFollowProcess().follow(predicate);
        return true;
    }

    static boolean pickup(Predicate<ItemStack> predicate) {
        IBaritone b = primary();
        b.getFollowProcess().pickup(predicate);
        return true;
    }

    static boolean farm(int range) {
        IBaritone b = primary();
        b.getFarmProcess().farm(range);
        return true;
    }

    static boolean stop() {
        IBaritone b = primary();
        b.getPathingBehavior().cancelEverything();
        return true;
    }

    private static IBaritone primary() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }
}
