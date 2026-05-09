package com.badger124.customcompat.compat.baritone;

import com.badger124.customcompat.CustomCompatMod;
import com.badger124.customcompat.api.CustomCompatApi;
import com.badger124.customcompat.impl.CustomCompatRegistryImpl;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.function.Predicate;

/**
 * Optional soft-dependency integration with the
 * <a href="https://github.com/cabaletta/baritone">Baritone</a> pathfinding mod.
 *
 * <h2>Design</h2>
 * <p>All real Baritone calls are delegated to {@link BaritoneDirectBridge} which uses the
 * Baritone API directly (no reflection). The bridge class is only loaded by the JVM when
 * Baritone is present, because every public method in this class returns early when
 * {@code !LOADED}. This keeps the mod functional even when Baritone is not installed.</p>
 *
 * <h2>Runtime requirement</h2>
 * <p>The {@code baritone-api-fabric-*.jar} is declared as {@code modCompileOnly} — it is
 * used at compile time but not bundled into the output jar. At runtime the player must
 * provide a compatible Baritone jar in their {@code mods/} folder.</p>
 */
public final class BaritoneCompat {

    private static final boolean LOADED;

    static {
        boolean loaded = false;
        try {
            Class.forName("baritone.api.BaritoneAPI");
            loaded = true;
        } catch (ClassNotFoundException ignored) {
            // Baritone is not installed — integration silently disabled
        }
        LOADED = loaded;
        if (LOADED) {
            CustomCompatMod.LOGGER.info("[CustomCompat] Baritone detected – follow/farm integration active.");
        } else {
            CustomCompatMod.LOGGER.debug("[CustomCompat] Baritone not found – integration disabled.");
        }
    }

    private BaritoneCompat() {}

    /** Returns {@code true} if Baritone is present on the classpath at runtime. */
    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * Tells Baritone to follow all entities matching the given custom entity identifier.
     */
    public static boolean followCustomEntity(Identifier customEntityId) {
        if (!LOADED) return false;
        try {
            String expectedTag = CustomCompatApi.ENTITY_TAG_PREFIX + customEntityId;
            Predicate<Entity> predicate = entity -> {
                if (CustomCompatRegistryImpl.resolveEntity(entity)
                        .filter(customEntityId::equals)
                        .isPresent()) {
                    return true;
                }
                return entity.getCommandTags().contains(expectedTag);
            };
            return BaritoneDirectBridge.follow(predicate);
        } catch (Exception e) {
            CustomCompatMod.LOGGER.error("[CustomCompat] Baritone follow failed for {}", customEntityId, e);
            return false;
        }
    }

    /**
     * Tells Baritone to pick up all dropped item entities matching the given custom item identifier.
     */
    public static boolean pickupCustomItems(Identifier customItemId) {
        if (!LOADED) return false;
        try {
            Predicate<ItemStack> predicate = stack -> {
                if (CustomCompatRegistryImpl.resolveItem(stack)
                        .filter(customItemId::equals)
                        .isPresent()) {
                    return true;
                }
                if (CustomCompatApi.readCustomDataId(stack)
                        .filter(customItemId::equals)
                        .isPresent()) {
                    return true;
                }
                return CustomCompatApi.readNexoId(stack)
                        .filter(customItemId::equals)
                        .isPresent();
            };
            return BaritoneDirectBridge.pickup(predicate);
        } catch (Exception e) {
            CustomCompatMod.LOGGER.error("[CustomCompat] Baritone pickup failed for {}", customItemId, e);
            return false;
        }
    }

    /**
     * Starts Baritone's farming process within the given block radius.
     *
     * @param range Search radius in blocks. {@code 0} means unlimited.
     */
    public static boolean farm(int range) {
        if (!LOADED) return false;
        try {
            return BaritoneDirectBridge.farm(range);
        } catch (Exception e) {
            CustomCompatMod.LOGGER.error("[CustomCompat] Baritone farm failed", e);
            return false;
        }
    }

    /**
     * Cancels all active Baritone processes.
     */
    public static boolean stop() {
        if (!LOADED) return false;
        try {
            return BaritoneDirectBridge.stop();
        } catch (Exception e) {
            CustomCompatMod.LOGGER.error("[CustomCompat] Baritone stop failed", e);
            return false;
        }
    }
}
