# Custom Content Compat — API Reference

> Minecraft 1.21.4 · Fabric Loader ≥ 0.16.0

---

## Package structure

```
com.badger124.customcompat
├── CustomCompatMod              – Fabric ModInitializer (common, internal)
├── CustomCompatClientMod        – Fabric ClientModInitializer (client, registers commands)
├── api/
│   ├── CustomCompatApi          – Static public API entry point
│   ├── CustomItemEntry          – Record: (Identifier, Predicate<ItemStack>)
│   ├── CustomEntityEntry        – Record: (Identifier, Predicate<Entity>)
│   └── event/
│       ├── CustomItemDetectedCallback   – Fabric event
│       └── CustomEntityDetectedCallback – Fabric event
├── compat/
│   └── baritone/
│       └── BaritoneCompat       – Optional Baritone follow/farm bridge (client)
└── impl/
    └── CustomCompatRegistryImpl  – Internal registry (not public API)
```

---

## `CustomCompatApi`

All methods are `static`. Thread-safety: registration must happen on the main thread
(during `ModInitializer.onInitialize()`); lookup methods are safe to call from any
thread after registration is complete.

### Registration

```java
CustomCompatApi.registerItem(Identifier id, Predicate<ItemStack> predicate)
CustomCompatApi.registerEntity(Identifier id, Predicate<Entity> predicate)
```

### Lookup

```java
Optional<Identifier> CustomCompatApi.getCustomItemId(ItemStack stack)
Optional<Identifier> CustomCompatApi.getCustomEntityId(Entity entity)
List<CustomItemEntry>   CustomCompatApi.getRegisteredItems()
List<CustomEntityEntry> CustomCompatApi.getRegisteredEntities()
```

`getCustomItemId` / `getCustomEntityId` also fire the corresponding Fabric event on
each successful match.

### Helpers — `custom_data` NBT

```java
Optional<Identifier> CustomCompatApi.readCustomDataId(ItemStack stack)
```

Reads `{customcompat:{id:"namespace:path"}}` from `minecraft:custom_data`.

### Helpers — `custom_model_data`

```java
Optional<Float>  CustomCompatApi.readCustomModelDataFloat(ItemStack stack, int index)
Optional<String> CustomCompatApi.readCustomModelDataString(ItemStack stack, int index)
```

### Helpers — scoreboard tags

```java
Optional<Identifier> CustomCompatApi.readEntityTagId(Entity entity, String expectedPath)
Optional<Identifier> CustomCompatApi.readFirstEntityTagId(Entity entity)
```

Tags must follow the format `customcompat:<namespace>:<path>`.

---

## `CustomItemDetectedCallback`

```java
CustomItemDetectedCallback.EVENT.register((ItemStack stack, Identifier customId) -> {
    // react to detection
});
```

Fired by `CustomCompatApi.getCustomItemId()` on a successful match.

## `CustomEntityDetectedCallback`

```java
CustomEntityDetectedCallback.EVENT.register((Entity entity, Identifier customId) -> {
    // react to detection
});
```

Fired by `CustomCompatApi.getCustomEntityId()` on a successful match.

---

## Constants

| Constant | Value | Meaning |
|----------|-------|---------|
| `CustomCompatApi.CUSTOM_DATA_NAMESPACE` | `"customcompat"` | Root NBT key in `minecraft:custom_data` |
| `CustomCompatApi.CUSTOM_DATA_ID_KEY` | `"id"` | Key inside the namespace compound |
| `CustomCompatApi.ENTITY_TAG_PREFIX` | `"customcompat:"` | Prefix for entity scoreboard tags |

---

## Complete example mod

```java
package com.example.mymod;

import com.badger124.customcompat.api.CustomCompatApi;
import com.badger124.customcompat.api.event.CustomItemDetectedCallback;
import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

public class MyMod implements ModInitializer {

    private static final Identifier MAGIC_SWORD = Identifier.of("mymod", "magic_sword");
    private static final Identifier BOSS_ZOMBIE  = Identifier.of("mymod", "boss_zombie");

    @Override
    public void onInitialize() {

        // --- Items ---

        // Recommended: server writes custom_data NBT
        CustomCompatApi.registerItem(
            MAGIC_SWORD,
            stack -> stack.isOf(Items.DIAMOND_SWORD)
                  && CustomCompatApi.readCustomDataId(stack)
                                    .filter(MAGIC_SWORD::equals)
                                    .isPresent()
        );

        // Legacy: custom_model_data integer (stored as floats[0] in 1.21.4)
        CustomCompatApi.registerItem(
            Identifier.of("mymod", "fire_blade"),
            stack -> stack.isOf(Items.DIAMOND_SWORD)
                  && CustomCompatApi.readCustomModelDataFloat(stack, 0)
                                    .map(f -> f == 1001f)
                                    .orElse(false)
        );

        // --- Entities ---

        // Server adds scoreboard tag: /tag @e[type=zombie] add customcompat:mymod:boss_zombie
        CustomCompatApi.registerEntity(
            BOSS_ZOMBIE,
            entity -> entity instanceof ZombieEntity
                   && CustomCompatApi.readEntityTagId(entity, "mymod:boss_zombie").isPresent()
        );

        // --- Events ---

        CustomItemDetectedCallback.EVENT.register((stack, id) -> {
            if (MAGIC_SWORD.equals(id)) {
                System.out.println("Player is holding the magic sword!");
            }
        });
    }
}
```

---

## Interoperability with other APIs

Because `CustomCompatApi.registerItem` / `registerEntity` accept arbitrary predicates,
you can compose with any other mod's detection logic:

```java
// Compose with a hypothetical CoolItemsAPI
CustomCompatApi.registerItem(
    Identifier.of("coolitemsmod", "frost_axe"),
    stack -> CoolItemsApi.isCoolItem(stack)
          && CoolItemsApi.getCoolItemId(stack).equals("frost_axe")
);
```

After registration, any other code that calls `CustomCompatApi.getCustomItemId(stack)`
will receive `coolitemsmod:frost_axe` back — bridging the two APIs transparently.

---

## `BaritoneCompat` — Baritone Integration (Optional, Client-only)

`com.badger124.customcompat.compat.baritone.BaritoneCompat`

Provides a soft bridge to the [Baritone](https://github.com/cabaletta/baritone) pathfinding
mod. All calls are via reflection; the class is safe to reference even when Baritone is absent.

### Methods

```java
boolean BaritoneCompat.isLoaded()
boolean BaritoneCompat.followCustomEntity(Identifier customEntityId)
boolean BaritoneCompat.pickupCustomItems(Identifier customItemId)
boolean BaritoneCompat.farm(int range)
```

### `followCustomEntity(Identifier)`

Builds a `Predicate<Entity>` from the registry (for `customEntityId`) and passes it to
`IFollowProcess.follow(Predicate)`. Baritone will then navigate toward all matching entities.

### `pickupCustomItems(Identifier)`

Builds a `Predicate<ItemStack>` from the registry (for `customItemId`) and passes it to
`IFollowProcess.pickup(Predicate)`. Baritone will navigate to and pick up matching item drops.

### `farm(int range)`

Calls `IFarmProcess.farm(int, BlockPos)` with `null` position (= player's current position).
Baritone's farming is block-state based; resource-pack skins are irrelevant — vanilla crops
are harvested correctly.

### Client commands

Registered via Fabric's `ClientCommandRegistrationCallback`:

| Command | Effect |
|---------|--------|
| `/customcompat follow <id>` | `followCustomEntity(id)` |
| `/customcompat pickup <id>` | `pickupCustomItems(id)` |
| `/customcompat farm [range]` | `farm(range)` (default 0 = unlimited) |

### Example

```java
// In onInitializeClient() or any client-side code after Baritone initialises:
if (BaritoneCompat.isLoaded()) {
    // Follow custom boss zombies with Baritone
    BaritoneCompat.followCustomEntity(Identifier.of("mymod", "boss_zombie"));
    
    // Have Baritone pick up custom magic swords that dropped on the ground
    BaritoneCompat.pickupCustomItems(Identifier.of("mymod", "magic_sword"));
    
    // Have Baritone farm within 128 blocks
    BaritoneCompat.farm(128);
}
```

### Runtime compatibility note

Our mod compiles against **Yarn** mappings; Baritone compiles against **Mojang** mappings.
At runtime both are remapped to **Intermediary** by the Fabric toolchain, so the entity and
item-stack classes are identical at the bytecode level. The `Predicate` lambdas created by
`BaritoneCompat` receive Intermediary-named MC classes, which our Yarn-compiled predicates
handle correctly.

