package com.badger124.customcompat;

import com.badger124.customcompat.api.CustomCompatApi;
import com.badger124.customcompat.api.event.CustomEntityDetectedCallback;
import com.badger124.customcompat.api.event.CustomItemDetectedCallback;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric mod initializer for Custom Content Compat.
 *
 * <p>This class wires up any built-in defaults and debug logging.
 * All real work happens through {@link CustomCompatApi}.</p>
 */
public final class CustomCompatMod implements ModInitializer {

    public static final String MOD_ID = "customcompat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[CustomCompat] Initialized. Ready for custom item/entity registrations.");

        // Debug listeners – only active when assertions are enabled (-ea JVM flag).
        // Remove or guard these behind a config option for production builds.
        assert registerDebugListeners();
    }

    /**
     * Registers lightweight debug listeners that log each detection.
     * Returns {@code true} so it can be used in an {@code assert} statement
     * to make it a no-op in production.
     */
    private static boolean registerDebugListeners() {
        CustomItemDetectedCallback.EVENT.register((stack, id) ->
                LOGGER.debug("[CustomCompat] Custom item detected: {} -> {}", stack, id));
        CustomEntityDetectedCallback.EVENT.register((entity, id) ->
                LOGGER.debug("[CustomCompat] Custom entity detected: {} -> {}", entity, id));
        return true;
    }
}
