package com.badger124.customcompat;

import com.badger124.customcompat.compat.baritone.BaritoneCompat;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.util.Identifier;

/**
 * Client-side initializer for Custom Content Compat.
 *
 * <p>Registers client commands and wires up the optional Baritone integration.</p>
 *
 * <h2>Client commands</h2>
 * <pre>
 *   /customcompat follow &lt;customEntityId&gt;
 *       – Tells Baritone to follow entities matching the custom ID.
 *
 *   /customcompat pickup &lt;customItemId&gt;
 *       – Tells Baritone to pick up dropped items matching the custom ID.
 *
 *   /customcompat farm [range]
 *       – Starts Baritone's farming process (range defaults to 0 = unlimited).
 * </pre>
 */
public final class CustomCompatClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        registerCommands();
        CustomCompatMod.LOGGER.info("[CustomCompat] Client initialized.");
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("customcompat")

                                // /customcompat follow <customEntityId>
                                .then(ClientCommandManager.literal("follow")
                                        .then(ClientCommandManager.argument("id", StringArgumentType.string())
                                                .executes(ctx -> {
                                                    String raw = StringArgumentType.getString(ctx, "id");
                                                    Identifier id = Identifier.tryParse(raw);
                                                    if (id == null) {
                                                        ctx.getSource().sendError(
                                                                net.minecraft.text.Text.literal(
                                                                        "[CustomCompat] Invalid identifier: " + raw));
                                                        return 0;
                                                    }
                                                    if (!BaritoneCompat.isLoaded()) {
                                                        ctx.getSource().sendError(
                                                                net.minecraft.text.Text.literal(
                                                                        "[CustomCompat] Baritone is not installed."));
                                                        return 0;
                                                    }
                                                    boolean started = BaritoneCompat.followCustomEntity(id);
                                                    ctx.getSource().sendFeedback(
                                                            net.minecraft.text.Text.literal(started
                                                                    ? "[CustomCompat] Following custom entity: " + id
                                                                    : "[CustomCompat] Failed to start follow for: " + id));
                                                    return started ? 1 : 0;
                                                })))

                                // /customcompat pickup <customItemId>
                                .then(ClientCommandManager.literal("pickup")
                                        .then(ClientCommandManager.argument("id", StringArgumentType.string())
                                                .executes(ctx -> {
                                                    String raw = StringArgumentType.getString(ctx, "id");
                                                    Identifier id = Identifier.tryParse(raw);
                                                    if (id == null) {
                                                        ctx.getSource().sendError(
                                                                net.minecraft.text.Text.literal(
                                                                        "[CustomCompat] Invalid identifier: " + raw));
                                                        return 0;
                                                    }
                                                    if (!BaritoneCompat.isLoaded()) {
                                                        ctx.getSource().sendError(
                                                                net.minecraft.text.Text.literal(
                                                                        "[CustomCompat] Baritone is not installed."));
                                                        return 0;
                                                    }
                                                    boolean started = BaritoneCompat.pickupCustomItems(id);
                                                    ctx.getSource().sendFeedback(
                                                            net.minecraft.text.Text.literal(started
                                                                    ? "[CustomCompat] Picking up custom items: " + id
                                                                    : "[CustomCompat] Failed to start pickup for: " + id));
                                                    return started ? 1 : 0;
                                                })))

                                // /customcompat farm [range]
                                .then(ClientCommandManager.literal("farm")
                                        .executes(ctx -> {
                                            if (!BaritoneCompat.isLoaded()) {
                                                ctx.getSource().sendError(
                                                        net.minecraft.text.Text.literal(
                                                                "[CustomCompat] Baritone is not installed."));
                                                return 0;
                                            }
                                            boolean started = BaritoneCompat.farm(0);
                                            ctx.getSource().sendFeedback(
                                                    net.minecraft.text.Text.literal(started
                                                            ? "[CustomCompat] Baritone farming started (unlimited range)."
                                                            : "[CustomCompat] Failed to start Baritone farming."));
                                            return started ? 1 : 0;
                                        })
                                        .then(ClientCommandManager.argument("range", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    if (!BaritoneCompat.isLoaded()) {
                                                        ctx.getSource().sendError(
                                                                net.minecraft.text.Text.literal(
                                                                        "[CustomCompat] Baritone is not installed."));
                                                        return 0;
                                                    }
                                                    int range = IntegerArgumentType.getInteger(ctx, "range");
                                                    boolean started = BaritoneCompat.farm(range);
                                                    ctx.getSource().sendFeedback(
                                                            net.minecraft.text.Text.literal(started
                                                                    ? "[CustomCompat] Baritone farming started (range=" + range + ")."
                                                                    : "[CustomCompat] Failed to start Baritone farming."));
                                                    return started ? 1 : 0;
                                                })))
                )
        );
    }
}
