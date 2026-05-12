package com.badger124.customcompat;

import com.badger124.customcompat.compat.baritone.BaritoneCompat;
import com.badger124.customcompat.gui.CustomCompatScreen;
import com.badger124.customcompat.gui.MacroManager;
import com.badger124.customcompat.gui.farm.CustomFarmingHandler;
import com.badger124.customcompat.gui.farm.FarmProfileManager;
import com.badger124.customcompat.inspector.DataCollector;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side initializer for Custom Content Compat.
 *
 * <p>Registers client commands and wires up the optional Baritone integration.</p>
 *
 * <h2>Client commands</h2>
 * <pre>
 *   /customcompat gui
 *       – Opens the GUI screen (item tracker + macro editor).
 *
 *   /customcompat follow &lt;customEntityId&gt;
 *       – Tells Baritone to follow entities matching the custom ID.
 *
 *   /customcompat pickup &lt;customItemId&gt;
 *       – Tells Baritone to pick up dropped items matching the custom ID.
 *
 *   /customcompat farm [range]
 *       – Starts Baritone's farming process (range defaults to 0 = unlimited).
 *
 *   /customcompat stop
 *       – Stops all active Baritone processes.
 * </pre>
 *
 * <h2>Key binding</h2>
 * <p>A key binding (unbound by default, category {@code CustomCompat}) opens the GUI.
 * It can be assigned in <em>Options → Controls</em>.</p>
 */
public final class CustomCompatClientMod implements ClientModInitializer {

    /** Key binding that opens the CustomCompat GUI screen. */
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        // Initialize the macro manager (registers its own tick listener + loads save file)
        MacroManager.getInstance().initialize(FabricLoader.getInstance().getConfigDir());

        // Initialize the farm profile manager
        FarmProfileManager.getInstance().initialize(FabricLoader.getInstance().getConfigDir());

        // Initialize the data inspector / collector
        DataCollector.getInstance().initialize(FabricLoader.getInstance().getConfigDir());

        // Register the custom farming handler tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> CustomFarmingHandler.getInstance().tick(client));

        // Register the GUI key binding (unbound by default; configurable in Options → Controls)
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.customcompat.gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "CustomCompat"
        ));

        // Poll the key binding each tick to open the screen
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (guiKey.wasPressed() && client.currentScreen == null) {
                client.setScreen(new CustomCompatScreen());
            }
        });

        registerCommands();
        CustomCompatMod.LOGGER.info("[커스텀컴팻] 클라이언트 초기화됨.");
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("customcompat")

                                // /customcompat gui
                                .then(ClientCommandManager.literal("gui")
                                        .executes(ctx -> {
                                            MinecraftClient.getInstance().setScreen(new CustomCompatScreen());
                                            return 1;
                                        }))

                                // /customcompat follow <customEntityId>
                                .then(ClientCommandManager.literal("follow")
                                        .then(ClientCommandManager.argument("id", StringArgumentType.string())
                                                .executes(ctx -> {
                                                    String raw = StringArgumentType.getString(ctx, "id");
                                                    Identifier id = Identifier.tryParse(raw);
                                                    if (id == null) {
                                                        ctx.getSource().sendError(
                                                                Text.literal(
                                                                        "[커스텀컴팻] 잘못된 ID: " + raw));
                                                        return 0;
                                                    }
                                                    if (!BaritoneCompat.isLoaded()) {
                                                        ctx.getSource().sendError(
                                                                Text.literal(
                                                                        "[커스텀컴팻] Baritone이 설치되어 있지 않습니다."));
                                                        return 0;
                                                    }
                                                    boolean started = BaritoneCompat.followCustomEntity(id);
                                                    ctx.getSource().sendFeedback(
                                                            Text.literal(started
                                                                    ? "[커스텀컴팻] 커스텀 엔티티 추적 중: " + id
                                                                    : "[커스텀컴팻] 추적 시작 실패: " + id));
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
                                                                Text.literal(
                                                                        "[커스텀컴팻] 잘못된 ID: " + raw));
                                                        return 0;
                                                    }
                                                    if (!BaritoneCompat.isLoaded()) {
                                                        ctx.getSource().sendError(
                                                                Text.literal(
                                                                        "[커스텀컴팻] Baritone이 설치되어 있지 않습니다."));
                                                        return 0;
                                                    }
                                                    boolean started = BaritoneCompat.pickupCustomItems(id);
                                                    ctx.getSource().sendFeedback(
                                                            Text.literal(started
                                                                    ? "[커스텀컴팻] 커스텀 아이템 수집 중: " + id
                                                                    : "[커스텀컴팻] 수집 시작 실패: " + id));
                                                    return started ? 1 : 0;
                                                })))

                                // /customcompat farm [range]
                                .then(ClientCommandManager.literal("farm")
                                        .executes(ctx -> {
                                            if (!BaritoneCompat.isLoaded()) {
                                                ctx.getSource().sendError(
                                                        Text.literal(
                                                                "[커스텀컴팻] Baritone이 설치되어 있지 않습니다."));
                                                return 0;
                                            }
                                            boolean started = BaritoneCompat.farm(0);
                                            ctx.getSource().sendFeedback(
                                                    Text.literal(started
                                                            ? "[커스텀컴팻] Baritone 농사 시작 (무제한 범위)."
                                                            : "[커스텀컴팻] Baritone 농사 시작 실패."));
                                            return started ? 1 : 0;
                                        })
                                        .then(ClientCommandManager.argument("range", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    if (!BaritoneCompat.isLoaded()) {
                                                        ctx.getSource().sendError(
                                                                Text.literal(
                                                                        "[커스텀컴팻] Baritone이 설치되어 있지 않습니다."));
                                                        return 0;
                                                    }
                                                    int range = IntegerArgumentType.getInteger(ctx, "range");
                                                    boolean started = BaritoneCompat.farm(range);
                                                    ctx.getSource().sendFeedback(
                                                            Text.literal(started
                                                                    ? "[커스텀컴팻] Baritone 농사 시작 (범위=" + range + ")."
                                                                    : "[커스텀컴팻] Baritone 농사 시작 실패."));
                                                    return started ? 1 : 0;
                                                })))

                                // /customcompat stop
                                .then(ClientCommandManager.literal("stop")
                                        .executes(ctx -> {
                                            if (!BaritoneCompat.isLoaded()) {
                                                ctx.getSource().sendError(
                                                        Text.literal(
                                                                "[커스텀컴팻] Baritone이 설치되어 있지 않습니다."));
                                                return 0;
                                            }
                                            boolean stopped = BaritoneCompat.stop();
                                            ctx.getSource().sendFeedback(
                                                    Text.literal(stopped
                                                            ? "[커스텀컴팻] Baritone 중단됨."
                                                            : "[커스텀컴팻] Baritone 중단 실패."));
                                            return stopped ? 1 : 0;
                                        }))

                                // /customcompat scan
                                .then(ClientCommandManager.literal("scan")
                                        .executes(ctx -> {
                                            DataCollector dc = DataCollector.getInstance();
                                            dc.scan();
                                            ctx.getSource().sendFeedback(Text.literal(
                                                    "[커스텀컴팻] 스캔: "
                                                            + dc.getLastItems().size() + " 아이템, "
                                                            + dc.getLastEntities().size() + " 엔티티 — "
                                                            + dc.getLastScanServer()
                                                            + " (config/customcompat_inspector.json 저장됨)"));
                                            return 1;
                                        }))
                )
        );
    }
}
