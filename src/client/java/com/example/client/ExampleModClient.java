package com.example.client;

import com.example.client.farm.AutoFarmController;
import com.example.client.farm.ChestRegistry;
import com.example.client.farm.ServerDetector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ExampleModClient implements ClientModInitializer {

    private KeyMapping autoDepositKey;
    private KeyMapping stopAutoKey;
    private KeyMapping dryRunKey;
    private KeyMapping serverDetectKey;
    private KeyMapping rawTabKey;
    private KeyMapping registerChestKey;
    private KeyMapping listChestKey;
    private KeyMapping clearChestKey;
    private KeyMapping inventoryTestKey;
    private KeyMapping containerTestKey;

    private final ServerDetector serverDetector = new ServerDetector();
    private final ChestRegistry chestRegistry = new ChestRegistry();
    private final AutoFarmController autoFarmController = new AutoFarmController(serverDetector, chestRegistry);

    @Override
    public void onInitializeClient() {
        chestRegistry.loadFromFile();

        registerKeys();
        registerMessageCapture();
        registerTickLoop();
    }

    // 기존 기능을 유지하기 위해 단축키 정의는 동일한 키/문구로 고정한다.
    private void registerKeys() {
        autoDepositKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: 상자로 이동 후 투입",
                GLFW.GLFW_KEY_F1,
                "FarmTest"
        ));

        stopAutoKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: 자동 투입 중지",
                GLFW.GLFW_KEY_F2,
                "FarmTest"
        ));

        dryRunKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: 열린 상자 dry-run",
                GLFW.GLFW_KEY_F3,
                "FarmTest"
        ));

        serverDetectKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: 서버 타입 인식",
                GLFW.GLFW_KEY_F6,
                "FarmTest"
        ));

        rawTabKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: RAW 탭 출력",
                GLFW.GLFW_KEY_F7,
                "FarmTest"
        ));

        registerChestKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: 마을 상자 등록",
                GLFW.GLFW_KEY_F8,
                "FarmTest"
        ));

        listChestKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: 등록 상자 목록",
                GLFW.GLFW_KEY_F9,
                "FarmTest"
        ));

        clearChestKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: 등록 상자 초기화",
                GLFW.GLFW_KEY_F10,
                "FarmTest"
        ));

        inventoryTestKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: 인벤토리 검사",
                GLFW.GLFW_KEY_F11,
                "FarmTest"
        ));

        containerTestKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "테스트: 열린 상자 검사",
                GLFW.GLFW_KEY_F12,
                "FarmTest"
        ));
    }

    private void registerMessageCapture() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (message == null) return;

            String text = message.getString();
            if (!serverDetector.shouldCaptureText(text)) return;

            serverDetector.addRecentText(text);
        });
    }

    private void registerTickLoop() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) return;

            serverDetector.removeOldTexts();
            consumeKeys(client);
            autoFarmController.tickAutoDeposit(client);
        });
    }

    // 입력 처리와 상태머신 실행을 분리해 유지보수 시 변경 지점을 명확히 한다.
    private void consumeKeys(Minecraft client) {
        if (autoDepositKey.consumeClick()) {
            autoFarmController.startAutoDeposit(client);
        }

        if (stopAutoKey.consumeClick()) {
            autoFarmController.stopAutoDeposit(client);
        }

        if (dryRunKey.consumeClick()) {
            autoFarmController.runDepositDryRun(client);
        }

        if (serverDetectKey.consumeClick()) {
            autoFarmController.runServerDetectTest(client);
        }

        if (rawTabKey.consumeClick()) {
            autoFarmController.printRawTabText(client);
        }

        if (registerChestKey.consumeClick()) {
            autoFarmController.registerLookingChest(client);
        }

        if (listChestKey.consumeClick()) {
            autoFarmController.printRegisteredChests(client);
        }

        if (clearChestKey.consumeClick()) {
            autoFarmController.clearRegisteredChests(client);
        }

        if (inventoryTestKey.consumeClick()) {
            autoFarmController.runInventoryTest(client);
        }

        if (containerTestKey.consumeClick()) {
            autoFarmController.runContainerTest(client);
        }
    }
}
