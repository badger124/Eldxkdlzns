# Custom Content Compat — `customcompat`

A Minecraft **Fabric mod** for **1.21.4** that provides an interoperability layer
between **server resource-pack custom items / entities** and other mod APIs.

---

## Overview

Minecraft resource packs can make ordinary vanilla items and entities look completely
different — custom swords, unique mobs, special tools — but they are not real registry
entries.  From the game's perspective a "Magic Sword" is still a `diamond_sword` with a
`custom_model_data` component.

This mod bridges that gap with a **clean, stable mapping layer**:

| Concept | How it works |
|---------|-------------|
| Custom item registry | Any mod registers a `Predicate<ItemStack>` + `Identifier` pair |
| Custom entity registry | Any mod registers a `Predicate<Entity>` + `Identifier` pair |
| Detection helpers | Built-in helpers for `custom_data` NBT, `custom_model_data`, and scoreboard tags |
| Fabric events | `CustomItemDetectedCallback` and `CustomEntityDetectedCallback` |

---

## Supported Versions

| Minecraft | Fabric Loader | Fabric API |
|-----------|--------------|------------|
| 1.21.4 | ≥ 0.16.0 | ≥ 0.110.0+1.21.4 |

Java 21 required.

---

## Building

```bash
./gradlew build
# Output: build/libs/customcompat-1.0.0.jar
```

---

## Quick Integration Guide

### 1 — Add as a dependency

In your mod's `build.gradle`:
```groovy
repositories {
    // point at the local maven or a hosted repo
    maven { url 'https://maven.your-host.example/' }
}
dependencies {
    modImplementation 'com.badger124:customcompat:1.0.0'
}
```

Declare the soft dependency in `fabric.mod.json` so your mod still loads without it:
```json
"suggests": { "customcompat": "*" }
```

### 2 — Register custom items

```java
import com.badger124.customcompat.api.CustomCompatApi;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {

        // Strategy A – custom_data NBT (recommended)
        // Server command: /item replace entity @p weapon.mainhand with
        //   diamond_sword[minecraft:custom_data={customcompat:{id:"mymod:magic_sword"}}]
        CustomCompatApi.registerItem(
            Identifier.of("mymod", "magic_sword"),
            stack -> stack.isOf(Items.DIAMOND_SWORD)
                 && CustomCompatApi.readCustomDataId(stack)
                                   .filter(id -> id.equals(Identifier.of("mymod", "magic_sword")))
                                   .isPresent()
        );

        // Strategy B – custom_model_data float (legacy integer-based resource packs)
        // Server command: /give @p diamond_sword[custom_model_data={floats:[1001.0f]}]
        CustomCompatApi.registerItem(
            Identifier.of("mymod", "fire_blade"),
            stack -> stack.isOf(Items.DIAMOND_SWORD)
                 && CustomCompatApi.readCustomModelDataFloat(stack, 0)
                                   .map(f -> f == 1001f)
                                   .orElse(false)
        );
    }
}
```

### 3 — Register custom entities

```java
import net.minecraft.entity.mob.ZombieEntity;

// Strategy – scoreboard tag
// Server command: /tag @e[type=zombie,name=BossZombie] add customcompat:mymod:boss_zombie
CustomCompatApi.registerEntity(
    Identifier.of("mymod", "boss_zombie"),
    entity -> entity instanceof ZombieEntity
           && CustomCompatApi.readEntityTagId(entity, "mymod:boss_zombie").isPresent()
);
```

### 4 — Query anywhere

```java
// Check an ItemStack
Optional<Identifier> itemId = CustomCompatApi.getCustomItemId(playerStack);
itemId.ifPresent(id -> System.out.println("Custom item: " + id));

// Check an Entity
Optional<Identifier> entityId = CustomCompatApi.getCustomEntityId(targetEntity);
entityId.ifPresent(id -> System.out.println("Custom entity: " + id));
```

### 5 — Listen to events

```java
CustomItemDetectedCallback.EVENT.register((stack, id) -> {
    if (id.equals(Identifier.of("mymod", "magic_sword"))) {
        // react to magic sword being detected
    }
});

CustomEntityDetectedCallback.EVENT.register((entity, id) -> {
    // react to any custom entity detection
});
```

---

## Baritone Integration (Optional)

When [Baritone](https://github.com/cabaletta/baritone) is installed, Custom Content Compat
automatically enables an integration layer that lets Baritone **farm** and **track/follow**
custom resource-pack content.

### How it works

| Feature | Baritone API | Notes |
|---------|-------------|-------|
| Follow custom entity | `IFollowProcess.follow(Predicate<Entity>)` | Predicate resolved from your registered custom entity entries |
| Pick up custom items | `IFollowProcess.pickup(Predicate<ItemStack>)` | Predicate resolved from your registered custom item entries |
| Farm crops | `IFarmProcess.farm(int range)` | Vanilla block states unchanged — Baritone's crop logic applies as-is |

The integration uses **reflection** so the mod loads cleanly even without Baritone.

### Client commands

| Command | Effect |
|---------|--------|
| `/customcompat follow mymod:boss_zombie` | Tells Baritone to chase all entities matching that custom ID |
| `/customcompat pickup mymod:magic_sword` | Tells Baritone to pick up dropped items matching that custom ID |
| `/customcompat farm` | Starts Baritone farming (unlimited range) |
| `/customcompat farm 64` | Starts Baritone farming within 64 blocks |

### Programmatic usage

```java
import com.badger124.customcompat.compat.baritone.BaritoneCompat;

if (BaritoneCompat.isLoaded()) {
    // Follow all entities tagged as "mymod:boss_zombie"
    BaritoneCompat.followCustomEntity(Identifier.of("mymod", "boss_zombie"));

    // Pick up dropped items identified as "mymod:magic_sword"
    BaritoneCompat.pickupCustomItems(Identifier.of("mymod", "magic_sword"));

    // Start farming nearby crops
    BaritoneCompat.farm(64);
}
```

### Limitations

- Baritone's farming uses vanilla block states; resource-pack texture/model overrides do
  not change what Baritone considers a farmable crop.
- The follow/pickup predicates only match entities/items that have been explicitly
  registered in `CustomCompatApi`. Unregistered custom content is invisible to this layer.
- Baritone must be installed and initialised before any of the above calls are made
  (this is always satisfied during normal gameplay after world join).

---

## How Custom Item/Entity Mapping Works

### Items

Server resource packs override item **appearance** by setting Minecraft data components
on the `ItemStack`.  This mod reads those components to map back to a stable identifier:

| Strategy | Server-side | Mod-side helper |
|----------|------------|-----------------|
| `custom_data` NBT | `minecraft:custom_data={customcompat:{id:"ns:name"}}` | `CustomCompatApi.readCustomDataId(stack)` |
| `custom_model_data` (float) | `custom_model_data={floats:[1001.0]}` | `CustomCompatApi.readCustomModelDataFloat(stack, 0)` |
| `custom_model_data` (string) | `custom_model_data={strings:["mymod:blade"]}` | `CustomCompatApi.readCustomModelDataString(stack, 0)` |
| Custom predicate | anything | `CustomCompatApi.registerItem(id, predicate)` |

### Entities

Resource-pack "custom entities" are plain vanilla entities with a custom model applied
client-side.  The most reliable server-side marker is a **scoreboard tag** added via
commands or a datapack:

```
/tag @e[type=zombie] add customcompat:mymod:boss_zombie
```

The tag format is: `customcompat:<namespace>:<path>`

---

## Limitations & Assumptions

1. **No true new entity types.** Resource packs cannot add new entity *types* to the
   registry; they can only re-skin existing ones.  This mod maps vanilla entities to
   custom identifiers; it does not create new `EntityType` registry entries.

2. **No true new items.** Same reasoning — vanilla items re-skinned via `custom_model_data`
   or `item_model` are still the underlying vanilla item as far as game logic is concerned.

3. **Client-side rendering is unchanged.** This mod does not touch rendering; it only
   provides the lookup layer.

4. **Predicate evaluation order.** Entries are evaluated in the order they were registered.
   Place more specific predicates before less specific ones.

5. **Server must cooperate.** For the `custom_data` and scoreboard-tag strategies the
   server must explicitly mark items/entities.  On vanilla servers with no special setup
   this mod is a silent no-op.

---

## License

MIT
