package com.badger124.customcompat.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

/**
 * Fabric event fired when an {@link Entity} is identified as a custom resource-pack entity.
 *
 * <p>Listen to this event to react whenever
 * {@link com.badger124.customcompat.api.CustomCompatApi#getCustomEntityId(Entity)}
 * resolves to a custom identifier.</p>
 *
 * <pre>{@code
 * CustomEntityDetectedCallback.EVENT.register((entity, customId) -> {
 *     LOGGER.info("Detected custom entity {} for {}", customId, entity);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface CustomEntityDetectedCallback {

    Event<CustomEntityDetectedCallback> EVENT = EventFactory.createArrayBacked(
            CustomEntityDetectedCallback.class,
            listeners -> (entity, customId) -> {
                for (CustomEntityDetectedCallback listener : listeners) {
                    listener.onCustomEntityDetected(entity, customId);
                }
            }
    );

    /**
     * Called when an entity is matched to a registered custom entity identifier.
     *
     * @param entity   The entity that was matched.
     * @param customId The custom identifier it was matched to.
     */
    void onCustomEntityDetected(Entity entity, Identifier customId);
}
