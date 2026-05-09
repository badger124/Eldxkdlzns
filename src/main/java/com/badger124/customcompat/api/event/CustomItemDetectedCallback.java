package com.badger124.customcompat.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Fabric event fired when an {@link ItemStack} is identified as a custom resource-pack item.
 *
 * <p>Listen to this event to react whenever
 * {@link com.badger124.customcompat.api.CustomCompatApi#getCustomItemId(ItemStack)}
 * resolves to a custom identifier.</p>
 *
 * <pre>{@code
 * CustomItemDetectedCallback.EVENT.register((stack, customId) -> {
 *     LOGGER.info("Detected custom item {} in stack {}", customId, stack);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface CustomItemDetectedCallback {

    Event<CustomItemDetectedCallback> EVENT = EventFactory.createArrayBacked(
            CustomItemDetectedCallback.class,
            listeners -> (stack, customId) -> {
                for (CustomItemDetectedCallback listener : listeners) {
                    listener.onCustomItemDetected(stack, customId);
                }
            }
    );

    /**
     * Called when a stack is matched to a registered custom item identifier.
     *
     * @param stack    The item stack that was matched.
     * @param customId The custom identifier it was matched to.
     */
    void onCustomItemDetected(ItemStack stack, Identifier customId);
}
