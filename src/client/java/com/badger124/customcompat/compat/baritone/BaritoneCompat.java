package com.badger124.customcompat.compat.baritone;

import com.badger124.customcompat.CustomCompatMod;
import com.badger124.customcompat.api.CustomCompatApi;
import com.badger124.customcompat.impl.CustomCompatRegistryImpl;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Optional soft-dependency integration with the <a href="https://github.com/cabaletta/baritone">Baritone</a>
 * pathfinding mod.
 *
 * <h2>Design</h2>
 * <p>All Baritone calls go through reflection so this mod loads cleanly even when Baritone
 * is not installed. Client-side only (Baritone is a client mod).</p>
 *
 * <h2>Supported operations</h2>
 * <ul>
 *   <li><b>Follow / track</b> – tells Baritone to follow entities whose custom id matches
 *       a registered {@link com.badger124.customcompat.api.CustomEntityEntry} via
 *       {@code IFollowProcess.follow(Predicate)}.</li>
 *   <li><b>Pick up</b> – tells Baritone to pick up dropped item entities whose custom id
 *       matches a registered {@link com.badger124.customcompat.api.CustomItemEntry} via
 *       {@code IFollowProcess.pickup(Predicate)}.</li>
 *   <li><b>Farm</b> – starts Baritone's built-in farming process via
 *       {@code IFarmProcess.farm(int)}. Because resource-pack custom crops are still vanilla
 *       blocks under the hood, Baritone's normal crop-harvesting logic applies unchanged.</li>
 * </ul>
 *
 * <h2>Usage (from another mod)</h2>
 * <pre>{@code
 * if (BaritoneCompat.isLoaded()) {
 *     BaritoneCompat.followCustomEntity(Identifier.of("mymod", "boss_zombie"));
 * }
 * }</pre>
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

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns {@code true} if Baritone is present on the classpath at runtime.
     */
    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * Tells Baritone to follow all entities matching the given custom entity identifier.
     *
     * <p>Checks registered {@link CustomCompatRegistryImpl} entries first. If no entry is
     * registered for {@code customEntityId}, falls back to scoreboard-tag detection:
     * any entity that has the tag {@code "customcompat:<namespace>:<path>"} will match.
     * This means you can use the command without a separate mod calling
     * {@link com.badger124.customcompat.api.CustomCompatApi#registerEntity} — just tag the
     * entity on the server with
     * {@code /tag <selector> add customcompat:<namespace>:<path>}.</p>
     *
     * @param customEntityId Custom entity identifier (e.g. {@code mymod:boss_zombie}).
     * @return {@code true} if the follow command was issued successfully.
     */
    public static boolean followCustomEntity(Identifier customEntityId) {
        if (!LOADED) return false;
        try {
            // Check registered entries first; fall back to scoreboard-tag detection so
            // the command works without a separate mod calling registerEntity().
            // Tag format the server should add: /tag @e add customcompat:<namespace>:<path>
            String expectedTag = CustomCompatApi.ENTITY_TAG_PREFIX + customEntityId;
            Predicate<Entity> predicate = entity -> {
                // 1. Registered predicate match
                if (CustomCompatRegistryImpl.resolveEntity(entity)
                        .filter(customEntityId::equals)
                        .isPresent()) {
                    return true;
                }
                // 2. Scoreboard-tag fallback (no pre-registration needed)
                return entity.getCommandTags().contains(expectedTag);
            };
            return invokeFollow(predicate);
        } catch (Exception e) {
            CustomCompatMod.LOGGER.error("[CustomCompat] Baritone follow failed for {}", customEntityId, e);
            return false;
        }
    }

    /**
     * Tells Baritone to pick up all dropped item entities matching the given custom item identifier.
     *
     * <p>Checks registered {@link CustomCompatRegistryImpl} entries first. If no entry is
     * registered for {@code customItemId}, falls back to reading the
     * {@code minecraft:custom_data} NBT component written by the server
     * ({@code {customcompat:{id:"namespace:path"}}}).</p>
     *
     * @param customItemId Custom item identifier (registered via
     *                     {@link com.badger124.customcompat.api.CustomCompatApi#registerItem},
     *                     or identified via custom_data NBT without pre-registration).
     * @return {@code true} if the pickup command was issued successfully.
     */
    public static boolean pickupCustomItems(Identifier customItemId) {
        if (!LOADED) return false;
        try {
            // Check registered entries first; fall back to custom_data NBT detection so
            // the command works without a separate mod calling registerItem().
            Predicate<ItemStack> predicate = stack -> {
                // 1. Registered predicate match
                if (CustomCompatRegistryImpl.resolveItem(stack)
                        .filter(customItemId::equals)
                        .isPresent()) {
                    return true;
                }
                // 2. customcompat custom_data NBT fallback (no pre-registration needed)
                if (CustomCompatApi.readCustomDataId(stack)
                        .filter(customItemId::equals)
                        .isPresent()) {
                    return true;
                }
                // 3. Nexo plugin PublicBukkitValues NBT fallback
                //    Item has: custom_data={PublicBukkitValues:{"nexo:id":"crop_tomato_seed"}}
                //    Command:  /customcompat pickup nexo:crop_tomato_seed
                return CustomCompatApi.readNexoId(stack)
                        .filter(customItemId::equals)
                        .isPresent();
            };
            return invokePickup(predicate);
        } catch (Exception e) {
            CustomCompatMod.LOGGER.error("[CustomCompat] Baritone pickup failed for {}", customItemId, e);
            return false;
        }
    }

    /**
     * Cancels all active Baritone processes (follow, farm, pathing, etc.).
     *
     * <p>Calls {@code IPathingBehavior.cancelEverything()} via reflection.
     * Safe to call even when Baritone is not installed.</p>
     *
     * @return {@code true} if the stop command was issued successfully.
     */
    public static boolean stop() {
        if (!LOADED) return false;
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            pathingBehavior.getClass().getMethod("cancelEverything").invoke(pathingBehavior);
            CustomCompatMod.LOGGER.info("[CustomCompat] Baritone stopped.");
            return true;
        } catch (Exception e) {
            CustomCompatMod.LOGGER.error("[CustomCompat] Baritone stop failed", e);
            return false;
        }
    }

    /**
     * Starts Baritone's farming process within the given block radius.
     *
     * <p>Baritone's farming logic targets block states (wheat, carrots, potatoes, etc.).
     * Resource-pack custom crops are still the same underlying vanilla blocks, so they are
     * harvested correctly without any special handling.</p>
     *
     * @param range Search radius in blocks. {@code 0} means unlimited.
     * @return {@code true} if the farm command was issued successfully.
     */
    public static boolean farm(int range) {
        if (!LOADED) return false;
        try {
            return invokeFarm(range);
        } catch (Exception e) {
            CustomCompatMod.LOGGER.error("[CustomCompat] Baritone farm failed", e);
            return false;
        }
    }

    // =========================================================================
    // Reflection helpers
    // =========================================================================

    private static boolean invokeFollow(Predicate<Entity> predicate) throws Exception {
        Object followProcess = getProcess("getFollowProcess");
        Method m = findMethod(followProcess, "follow", 1);
        if (m == null) return false;
        m.invoke(followProcess, predicate);
        return true;
    }

    private static boolean invokePickup(Predicate<ItemStack> predicate) throws Exception {
        Object followProcess = getProcess("getFollowProcess");
        Method m = findMethod(followProcess, "pickup", 1);
        if (m == null) return false;
        m.invoke(followProcess, predicate);
        return true;
    }

    private static boolean invokeFarm(int range) throws Exception {
        Object farmProcess = getProcess("getFarmProcess");
        // Prefer farm(int, BlockPos) — pass null for pos to use player's position
        Method twoArg = findMethod(farmProcess, "farm", 2);
        if (twoArg != null) {
            twoArg.invoke(farmProcess, range, null);
            return true;
        }
        // Fallback to farm(int)
        Method oneArg = findMethod(farmProcess, "farm", 1);
        if (oneArg != null) {
            oneArg.invoke(farmProcess, range);
            return true;
        }
        // Last resort: no-arg farm()
        Method noArg = findMethod(farmProcess, "farm", 0);
        if (noArg != null) {
            noArg.invoke(farmProcess);
            return true;
        }
        return false;
    }

    /** Obtains the IBaritone process with the given getter name via reflection. */
    private static Object getProcess(String getterName) throws Exception {
        Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
        Object provider = apiClass.getMethod("getProvider").invoke(null);
        Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
        return baritone.getClass().getMethod(getterName).invoke(baritone);
    }

    /**
     * Finds the first public method with the given name and parameter count.
     * Uses {@link Class#getMethods()} so interface default methods are also found.
     */
    private static Method findMethod(Object target, String name, int paramCount) {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }
}
