package com.badger124.customcompat.api;

import com.badger124.customcompat.api.event.CustomEntityDetectedCallback;
import com.badger124.customcompat.api.event.CustomItemDetectedCallback;
import com.badger124.customcompat.impl.CustomCompatRegistryImpl;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Primary public API for the Custom Content Compat mod.
 *
 * <h2>Purpose</h2>
 * <p>Minecraft resource-pack custom items and entities are not true registry objects;
 * they are vanilla items/entities whose appearance is overridden client-side via a
 * resource pack. This API provides a stable mapping layer so that other mods can:</p>
 * <ul>
 *   <li>Register their own custom item/entity definitions backed by arbitrary predicates.</li>
 *   <li>Check whether an {@link ItemStack} or {@link Entity} corresponds to a known custom type.</li>
 *   <li>Retrieve the stable {@link Identifier} for that custom type.</li>
 *   <li>React to detections via Fabric events.</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // 1. Register during your mod's onInitialize()
 * CustomCompatApi.registerItem(
 *     Identifier.of("mymod", "magic_sword"),
 *     stack -> stack.isOf(Items.DIAMOND_SWORD)
 *          && CustomCompatApi.readCustomDataId(stack)
 *                            .map(id -> id.equals(Identifier.of("mymod", "magic_sword")))
 *                            .orElse(false)
 * );
 *
 * // 2. Query anywhere
 * Optional<Identifier> id = CustomCompatApi.getCustomItemId(playerStack);
 * id.ifPresent(i -> System.out.println("Custom item: " + i));
 * }</pre>
 *
 * <h2>Supported detection strategies</h2>
 * <ul>
 *   <li><b>{@code minecraft:custom_data} NBT (customcompat)</b> – recommended; server stores
 *       {@code {customcompat:{id:"namespace:path"}}} in the stack's custom data.
 *       Use {@link #readCustomDataId(ItemStack)}.</li>
 *   <li><b>{@code minecraft:custom_data} NBT (Nexo/Oraxen)</b> – for Nexo-based servers;
 *       items have {@code {PublicBukkitValues:{"nexo:id":"item_name"}}} in custom_data.
 *       Use {@link #readNexoId(ItemStack)} or just run
 *       {@code /customcompat pickup nexo:item_name}.</li>
 *   <li><b>{@code minecraft:custom_model_data}</b> – legacy integer-based approach.
 *       Use {@link #readCustomModelDataFloat(ItemStack, int)} to read a float value.</li>
 *   <li><b>Scoreboard tags</b> – for entities, servers can add a tag like
 *       {@code "customcompat:mymod:boss_zombie"} that is read via
 *       {@link #readEntityTagId(Entity, String)}.</li>
 *   <li><b>Arbitrary predicates</b> – register any {@link Predicate} for full control.</li>
 * </ul>
 */
public final class CustomCompatApi {

    /** NBT key written by the server into {@code minecraft:custom_data} to store the custom ID. */
    public static final String CUSTOM_DATA_NAMESPACE = "customcompat";
    /** Inner NBT key for the identifier string (e.g. {@code "mymod:magic_sword"}). */
    public static final String CUSTOM_DATA_ID_KEY = "id";
    /**
     * Scoreboard-tag prefix that servers add to entities to mark them as custom types.
     * Full tag format: {@code "customcompat:<namespace>:<path>"}.
     */
    public static final String ENTITY_TAG_PREFIX = "customcompat:";

    private CustomCompatApi() {}

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a custom item type.
     *
     * <p>Call this during your mod's {@code ModInitializer.onInitialize()}.</p>
     *
     * @param id        Unique identifier for this custom item type.
     * @param predicate Predicate that returns {@code true} when an {@link ItemStack}
     *                  matches this custom type. Should be fast and side-effect free.
     * @throws NullPointerException if {@code id} or {@code predicate} is {@code null}.
     */
    public static void registerItem(Identifier id, Predicate<ItemStack> predicate) {
        CustomCompatRegistryImpl.registerItem(new CustomItemEntry(id, predicate));
    }

    /**
     * Registers a custom entity type.
     *
     * <p>Call this during your mod's {@code ModInitializer.onInitialize()}.</p>
     *
     * @param id        Unique identifier for this custom entity type.
     * @param predicate Predicate that returns {@code true} when an {@link Entity}
     *                  represents this custom type. Should be fast and side-effect free.
     * @throws NullPointerException if {@code id} or {@code predicate} is {@code null}.
     */
    public static void registerEntity(Identifier id, Predicate<Entity> predicate) {
        CustomCompatRegistryImpl.registerEntity(new CustomEntityEntry(id, predicate));
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Returns the custom {@link Identifier} for the given stack, if it matches any registered entry.
     *
     * <p>Entries are evaluated in registration order; the first match wins.
     * When a match is found, {@link CustomItemDetectedCallback#EVENT} is fired.</p>
     *
     * @param stack The item stack to check. May be empty.
     * @return The matching custom identifier, or {@link Optional#empty()} if none matched.
     */
    public static Optional<Identifier> getCustomItemId(ItemStack stack) {
        Optional<Identifier> result = CustomCompatRegistryImpl.resolveItem(stack);
        result.ifPresent(id -> CustomItemDetectedCallback.EVENT.invoker().onCustomItemDetected(stack, id));
        return result;
    }

    /**
     * Returns the custom {@link Identifier} for the given entity, if it matches any registered entry.
     *
     * <p>Entries are evaluated in registration order; the first match wins.
     * When a match is found, {@link CustomEntityDetectedCallback#EVENT} is fired.</p>
     *
     * @param entity The entity to check. Must not be {@code null}.
     * @return The matching custom identifier, or {@link Optional#empty()} if none matched.
     */
    public static Optional<Identifier> getCustomEntityId(Entity entity) {
        Optional<Identifier> result = CustomCompatRegistryImpl.resolveEntity(entity);
        result.ifPresent(id -> CustomEntityDetectedCallback.EVENT.invoker().onCustomEntityDetected(entity, id));
        return result;
    }

    /**
     * Returns a read-only view of all registered custom item entries.
     *
     * <p>Useful for debugging, tooling, or iterating all known custom types.</p>
     */
    public static List<CustomItemEntry> getRegisteredItems() {
        return CustomCompatRegistryImpl.getItemEntries();
    }

    /**
     * Returns a read-only view of all registered custom entity entries.
     *
     * <p>Useful for debugging, tooling, or iterating all known custom types.</p>
     */
    public static List<CustomEntityEntry> getRegisteredEntities() {
        return CustomCompatRegistryImpl.getEntityEntries();
    }

    // =========================================================================
    // Helper: custom_data NBT strategy
    // =========================================================================

    /**
     * Reads a custom identifier from the stack's {@code minecraft:custom_data} component.
     *
     * <p>The server is expected to write the following NBT structure into the item's
     * {@code custom_data} component:</p>
     * <pre>{@code
     * minecraft:custom_data={customcompat:{id:"mymod:magic_sword"}}
     * }</pre>
     *
     * <p>This is the <b>recommended server-side strategy</b> because it survives resource-pack
     * reloads and is unambiguous regardless of model overrides.</p>
     *
     * @param stack The stack to inspect.
     * @return The parsed {@link Identifier}, or {@link Optional#empty()} if the NBT is absent
     *         or malformed.
     */
    public static Optional<Identifier> readCustomDataId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        NbtComponent nbtComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComponent == null) return Optional.empty();

        NbtCompound root = nbtComponent.copyNbt();
        if (!root.contains(CUSTOM_DATA_NAMESPACE)) return Optional.empty();

        NbtCompound inner = root.getCompound(CUSTOM_DATA_NAMESPACE);
        if (!inner.contains(CUSTOM_DATA_ID_KEY)) return Optional.empty();

        String rawId = inner.getString(CUSTOM_DATA_ID_KEY);
        return Optional.ofNullable(Identifier.tryParse(rawId));
    }

    // =========================================================================
    // Helper: Nexo plugin custom_data strategy
    // =========================================================================

    /**
     * Reads a Nexo item identifier from the stack's {@code minecraft:custom_data} component.
     *
     * <p>Nexo (and its predecessor Oraxen) stores item IDs in
     * {@code PublicBukkitValues} inside {@code minecraft:custom_data}:</p>
     * <pre>{@code
     * minecraft:custom_data={PublicBukkitValues:{"nexo:id":"crop_tomato_seed"}}
     * }</pre>
     *
     * <p>This method constructs an {@link Identifier} with namespace {@code "nexo"} and
     * the value from {@code "nexo:id"} as the path, e.g.
     * {@code nexo:crop_tomato_seed}. The command to track such an item would be:</p>
     * <pre>{@code
     * /customcompat pickup nexo:crop_tomato_seed
     * }</pre>
     *
     * @param stack The stack to inspect.
     * @return The parsed {@link Identifier} (e.g. {@code nexo:crop_tomato_seed}),
     *         or {@link Optional#empty()} if the NBT is absent or malformed.
     */
    public static Optional<Identifier> readNexoId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        NbtComponent nbtComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComponent == null) return Optional.empty();

        NbtCompound root = nbtComponent.copyNbt();
        if (!root.contains("PublicBukkitValues")) return Optional.empty();

        NbtCompound publicValues = root.getCompound("PublicBukkitValues");
        if (!publicValues.contains("nexo:id")) return Optional.empty();

        String nexoItemId = publicValues.getString("nexo:id");
        if (nexoItemId.isEmpty()) return Optional.empty();
        // Construct "nexo:<item_id>" as the canonical Identifier
        return Optional.ofNullable(Identifier.tryParse("nexo:" + nexoItemId));
    }

    // =========================================================================
    // Helper: custom_model_data strategy
    // =========================================================================

    /**
     * Reads a float value from the {@code minecraft:custom_model_data} component.
     *
     * <p>In Minecraft 1.21.4, {@code custom_model_data} supports lists of floats, flags,
     * strings, and colors. The legacy single-integer approach maps to
     * {@code floats[0]} (cast to float). Use this helper to access the value at a given index.</p>
     *
     * <pre>{@code
     * // Register a custom item recognised by custom_model_data floats[0] == 1001
     * CustomCompatApi.registerItem(
     *     Identifier.of("mymod", "magic_sword"),
     *     stack -> stack.isOf(Items.DIAMOND_SWORD)
     *          && CustomCompatApi.readCustomModelDataFloat(stack, 0)
     *                            .map(f -> f == 1001f)
     *                            .orElse(false)
     * );
     * }</pre>
     *
     * @param stack The stack to inspect.
     * @param index Index into the float list (0-based).
     * @return The float at the given index, or {@link Optional#empty()} if absent.
     */
    public static Optional<Float> readCustomModelDataFloat(ItemStack stack, int index) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        var cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmd == null) return Optional.empty();
        List<Float> floats = cmd.floats();
        if (index < 0 || index >= floats.size()) return Optional.empty();
        return Optional.of(floats.get(index));
    }

    /**
     * Reads a string value from the {@code minecraft:custom_model_data} component.
     *
     * @param stack The stack to inspect.
     * @param index Index into the string list (0-based).
     * @return The string at the given index, or {@link Optional#empty()} if absent.
     */
    public static Optional<String> readCustomModelDataString(ItemStack stack, int index) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        var cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmd == null) return Optional.empty();
        List<String> strings = cmd.strings();
        if (index < 0 || index >= strings.size()) return Optional.empty();
        return Optional.of(strings.get(index));
    }

    // =========================================================================
    // Helper: entity scoreboard-tag strategy
    // =========================================================================

    /**
     * Reads a custom entity identifier from the entity's scoreboard tags.
     *
     * <p>The server is expected to add a scoreboard tag in the format
     * {@code "customcompat:<namespace>:<path>"} (e.g. {@code "customcompat:mymod:boss_zombie"})
     * to the entity via a command or datapack.</p>
     *
     * <pre>{@code
     * // In a server command / datapack:
     * //   /tag @e[type=zombie,name=BossZombie] add customcompat:mymod:boss_zombie
     *
     * CustomCompatApi.registerEntity(
     *     Identifier.of("mymod", "boss_zombie"),
     *     entity -> CustomCompatApi.readEntityTagId(entity, "mymod:boss_zombie").isPresent()
     * );
     * }</pre>
     *
     * @param entity        The entity to inspect.
     * @param expectedPath  The {@code "<namespace>:<path>"} portion of the tag
     *                      (without the {@code "customcompat:"} prefix).
     * @return The parsed {@link Identifier}, or {@link Optional#empty()} if the tag is absent.
     */
    public static Optional<Identifier> readEntityTagId(Entity entity, String expectedPath) {
        if (entity == null || expectedPath == null) return Optional.empty();
        String expectedTag = ENTITY_TAG_PREFIX + expectedPath;
        if (entity.getCommandTags().contains(expectedTag)) {
            return Optional.ofNullable(Identifier.tryParse(expectedPath));
        }
        return Optional.empty();
    }

    /**
     * Returns the first custom entity identifier found in any matching scoreboard tag
     * (any tag starting with {@link #ENTITY_TAG_PREFIX}).
     *
     * @param entity The entity to inspect.
     * @return The first matching custom identifier, or {@link Optional#empty()} if none found.
     */
    public static Optional<Identifier> readFirstEntityTagId(Entity entity) {
        if (entity == null) return Optional.empty();
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(ENTITY_TAG_PREFIX)) {
                String rawId = tag.substring(ENTITY_TAG_PREFIX.length());
                Optional<Identifier> parsed = Optional.ofNullable(Identifier.tryParse(rawId));
                if (parsed.isPresent()) return parsed;
            }
        }
        return Optional.empty();
    }
}
