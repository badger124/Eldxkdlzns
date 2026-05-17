package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExampleModClient implements ClientModInitializer {

    private static KeyMapping autoDepositKey;
    private static KeyMapping stopAutoKey;
    private static KeyMapping dryRunKey;
    private static int farmRestartTicks = 0;

    private static KeyMapping serverDetectKey;
    private static KeyMapping rawTabKey;
    private static KeyMapping registerChestKey;
    private static KeyMapping listChestKey;
    private static KeyMapping clearChestKey;
    private static KeyMapping inventoryTestKey;
    private static KeyMapping containerTestKey;

    private static final Deque<TimedText> recentTexts = new ArrayDeque<>();
    private static final List<BlockPos> registeredChests = new ArrayList<>();
    private static final Set<BlockPos> fullChests = new HashSet<>();

    private static final int MAX_RECENT_TEXTS = 50;
    private static final long TEXT_KEEP_MS = 5000;
    private static final int MAX_CHESTS = 10;

    private static final Path CHEST_SAVE_PATH = Path.of("config", "farm_chests.txt");
    private static int depositWaitTicks = 0;
    private static AutoState autoState = AutoState.IDLE;
    private static BlockPos targetChest = null;
    private static int openWaitTicks = 0;

    @Override
    public void onInitializeClient() {
        loadChestsFromFile();

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

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (message == null) return;

            String text = message.getString();
            if (text == null || text.isBlank()) return;

            if (text.contains("[서버 타입 인식 테스트]")) return;
            if (text.contains("[RAW TAB TEXT]")) return;
            if (text.contains("[상자 등록]")) return;
            if (text.contains("[상자 목록]")) return;
            if (text.contains("[상자 초기화]")) return;
            if (text.contains("[인벤토리 검사]")) return;
            if (text.contains("[상자 화면 검사]")) return;
            if (text.contains("[자동 투입]")) return;
            if (text.contains("[DryRun]")) return;
            if (text.contains("결과:")) return;
            if (text.contains("설명:")) return;
            if (text.contains("탐지된 라인:")) return;

            addRecentText(text);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) return;

            removeOldTexts();

            if (autoDepositKey.consumeClick()) {
                startAutoDeposit(client);
            }

            if (stopAutoKey.consumeClick()) {
                stopAutoDeposit(client);
            }

            if (dryRunKey.consumeClick()) {
                runDepositDryRun(client);
            }

            if (serverDetectKey.consumeClick()) {
                runServerDetectTest(client);
            }

            if (rawTabKey.consumeClick()) {
                printRawTabText(client);
            }

            if (registerChestKey.consumeClick()) {
                registerLookingChest(client);
            }

            if (listChestKey.consumeClick()) {
                printRegisteredChests(client);
            }

            if (clearChestKey.consumeClick()) {
                registeredChests.clear();
                fullChests.clear();
                deleteChestSaveFile();

                client.player.displayClientMessage(
                        Component.literal("§c[상자 초기화] 등록된 상자를 모두 삭제했습니다."),
                        false
                );
            }

            if (inventoryTestKey.consumeClick()) {
                runInventoryTest(client);
            }

            if (containerTestKey.consumeClick()) {
                runContainerTest(client);
            }

            tickAutoDeposit(client);
        });
    }

    private static void startAutoDeposit(Minecraft client) {
        ServerType currentServer = getCurrentServerType(client);

        if (currentServer != ServerType.TOWN) {
            client.player.displayClientMessage(
                    Component.literal("§c[자동 농사] 마을 서버에서만 실행합니다. 현재 서버: " + currentServer.name()),
                    false
            );
            return;
        }

        if (registeredChests.isEmpty()) {
            client.player.displayClientMessage(
                    Component.literal("§c[자동 농사] 등록된 상자가 없습니다."),
                    false
            );
            return;
        }

        fullChests.clear();

        boolean ok = startBaritoneFarm(64);

        if (!ok) {
            client.player.displayClientMessage(
                    Component.literal("§c[자동 농사] Baritone 농사 API 시작 실패."),
                    false
            );
            return;
        }

        autoState = AutoState.FARMING;
        targetChest = null;
        openWaitTicks = 0;

        client.player.displayClientMessage(
                Component.literal("§a[자동 농사] 시작했습니다. 인벤이 차면 상자로 이동합니다."),
                false
        );
    }

    private static void stopAutoDeposit(Minecraft client) {
        autoState = AutoState.IDLE;
        targetChest = null;
        openWaitTicks = 0;
        cancelBaritone();

        client.player.displayClientMessage(
                Component.literal("§c[자동 투입] 중지했습니다."),
                false
        );
    }

    private static void tickAutoDeposit(Minecraft client) {
        if (autoState == AutoState.IDLE) return;

        if (autoState == AutoState.FARMING) {

            // 10초마다 farm 재시작 시도
            farmRestartTicks++;

            if (farmRestartTicks >= 200) {
                farmRestartTicks = 0;

                startBaritoneFarm(64);

                client.player.displayClientMessage(
                        Component.literal("§7[자동 농사] farm 재탐색 시도"),
                        false
                );
            }

            // 인벤 가득 차면 상자 이동
            if (isMainInventoryFull(client)) {
                cancelBaritone();

                targetChest = findNearestUsableChest(client);

                if (targetChest == null) {
                    client.player.displayClientMessage(
                            Component.literal("§c[자동 농사] 인벤이 찼지만 사용 가능한 상자가 없습니다."),
                            false
                    );

                    autoState = AutoState.IDLE;
                    return;
                }

                boolean ok = startBaritoneGoalNear(targetChest, 2);

                if (!ok) {
                    client.player.displayClientMessage(
                            Component.literal("§c[자동 농사] 상자 이동 시작 실패."),
                            false
                    );

                    autoState = AutoState.IDLE;
                    return;
                }

                autoState = AutoState.GOING_TO_CHEST;
                openWaitTicks = 0;

                client.player.displayClientMessage(
                        Component.literal("§e[자동 농사] 인벤 가득 참 → 상자로 이동 " + formatPos(targetChest)),
                        false
                );
            }

            return;
        }

        if (targetChest == null) {
            autoState = AutoState.IDLE;
            cancelBaritone();
            return;
        }

        if (autoState == AutoState.GOING_TO_CHEST) {
            double distance = client.player.blockPosition().distSqr(targetChest);

            if (distance <= 12.25) {
                cancelBaritone();
                autoState = AutoState.OPENING_CHEST;
                openWaitTicks = 0;

                client.player.displayClientMessage(
                        Component.literal("§e[자동 농사] 상자 근처 도착. 여는 중..."),
                        false
                );
            }

            return;
        }

        if (autoState == AutoState.OPENING_CHEST) {
            openWaitTicks++;

            if (client.screen instanceof AbstractContainerScreen<?>) {
                autoState = AutoState.DEPOSITING;
                return;
            }

            if (openWaitTicks == 5 || openWaitTicks == 15 || openWaitTicks == 25) {
                openChestAt(client, targetChest);
            }

            if (openWaitTicks > 60) {
                client.player.displayClientMessage(
                        Component.literal("§c[자동 농사] 상자 열기 실패. 다음 상자로 이동합니다."),
                        false
                );

                markCurrentChestFullOrBadAndMoveNext(client);
            }

            return;
        }

        if (autoState == AutoState.DEPOSITING) {
            DepositResult result = depositCropsToOpenContainer(client, false);

            if (result.noSpace) {
                client.player.displayClientMessage(
                        Component.literal("§e[자동 농사] 현재 상자에 공간이 없습니다. 다음 상자로 이동합니다."),
                        false
                );

                markCurrentChestFullOrBadAndMoveNext(client);
                return;
            }

            client.player.displayClientMessage(
                    Component.literal(
                            "§a[자동 농사] 투입 클릭 완료. 반영 대기 중... 클릭 슬롯 수: §f"
                                    + result.clickedSlots
                    ),
                    false
            );

            depositWaitTicks = 0;
            autoState = AutoState.WAIT_AFTER_DEPOSIT;
            return;
        }
        if (autoState == AutoState.WAIT_AFTER_DEPOSIT) {
            depositWaitTicks++;

            if (depositWaitTicks < 10) {
                return;
            }

            depositWaitTicks = 0;

            if (hasDepositableCropItem(client)) {
                if (targetChest != null) {
                    fullChests.add(targetChest.immutable());
                }

                if (client.screen != null) {
                    client.player.closeContainer();
                }

                client.player.displayClientMessage(
                        Component.literal("§e[자동 농사] 아직 작물이 남아있습니다. 다음 상자로 이동합니다."),
                        false
                );

                targetChest = findNearestUsableChest(client);

                if (targetChest == null) {
                    client.player.displayClientMessage(
                            Component.literal("§c[자동 농사] 남은 작물이 있지만 사용 가능한 상자가 없습니다."),
                            false
                    );

                    autoState = AutoState.IDLE;
                    openWaitTicks = 0;
                    cancelBaritone();
                    return;
                }

                boolean ok = startBaritoneGoalNear(targetChest, 2);

                if (!ok) {
                    client.player.displayClientMessage(
                            Component.literal("§c[자동 농사] 다음 상자로 이동 시작 실패."),
                            false
                    );

                    autoState = AutoState.IDLE;
                    openWaitTicks = 0;
                    cancelBaritone();
                    return;
                }

                autoState = AutoState.GOING_TO_CHEST;
                openWaitTicks = 0;

                client.player.displayClientMessage(
                        Component.literal("§a[자동 농사] 다음 상자로 이동 → " + formatPos(targetChest)),
                        false
                );

                return;
            }

            if (client.screen != null) {
                client.player.closeContainer();
            }

            fullChests.clear();

            boolean ok = startBaritoneFarm(64);

            if (!ok) {
                client.player.displayClientMessage(
                        Component.literal("§c[자동 농사] 투입 후 농사 재시작 실패."),
                        false
                );

                autoState = AutoState.IDLE;
                return;
            }

            autoState = AutoState.FARMING;
            targetChest = null;
            openWaitTicks = 0;

            client.player.displayClientMessage(
                    Component.literal("§a[자동 농사] 모든 작물 투입 완료 → 농사 재시작"),
                    false
            );

            return;
        }
    }

    private static void markCurrentChestFullOrBadAndMoveNext(Minecraft client) {
        if (targetChest != null) {
            fullChests.add(targetChest.immutable());
        }

        if (client.screen != null) {
            client.player.closeContainer();
        }

        targetChest = findNearestUsableChest(client);

        if (targetChest == null) {
            client.player.displayClientMessage(
                    Component.literal("§c[자동 투입] 사용 가능한 다음 상자가 없습니다."),
                    false
            );

            autoState = AutoState.IDLE;
            openWaitTicks = 0;
            cancelBaritone();
            return;
        }

        boolean ok = startBaritoneGoalNear(targetChest, 2);

        if (!ok) {
            client.player.displayClientMessage(
                    Component.literal("§c[자동 투입] 다음 상자로 이동 시작 실패."),
                    false
            );

            autoState = AutoState.IDLE;
            openWaitTicks = 0;
            cancelBaritone();
            return;
        }

        autoState = AutoState.GOING_TO_CHEST;
        openWaitTicks = 0;

        client.player.displayClientMessage(
                Component.literal("§a[자동 투입] 다음 상자로 이동 → " + formatPos(targetChest)),
                false
        );
    }

    private static void runDepositDryRun(Minecraft client) {
        DepositResult result = depositCropsToOpenContainer(client, true);

        client.player.displayClientMessage(
                Component.literal(
                        "§b[DryRun] 후보 슬롯: §f"
                                + result.cropCandidateSlots
                                + " §7/ 공간 여부: "
                                + (result.noSpace ? "§c없음" : "§a있음")
                ),
                false
        );
    }

    private static DepositResult depositCropsToOpenContainer(Minecraft client, boolean dryRun) {
        DepositResult result = new DepositResult();

        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            client.player.displayClientMessage(
                    Component.literal("§c[DryRun] 상자 화면이 열려있지 않습니다."),
                    false
            );
            result.noSpace = true;
            return result;
        }

        AbstractContainerMenu menu = screen.getMenu();

        int totalSlots = menu.slots.size();
        int playerInventorySlots = 36;
        int containerSlots = Math.max(0, totalSlots - playerInventorySlots);

        if (containerSlots < 9) {
            client.player.displayClientMessage(
                    Component.literal("§c[DryRun] 컨테이너 슬롯 수가 이상합니다: " + containerSlots),
                    false
            );
            result.noSpace = true;
            return result;
        }

        List<Integer> cropSlots = new ArrayList<>();

        for (int slotIndex = containerSlots; slotIndex < totalSlots; slotIndex++) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();

            if (stack.isEmpty()) continue;

            String itemId = getItemId(stack);

            if (!isCropItem(itemId, stack)) continue;

            cropSlots.add(slotIndex);
        }

        result.cropCandidateSlots = cropSlots.size();

        if (cropSlots.isEmpty()) {
            result.noSpace = false;
            return result;
        }

        boolean hasSpace = false;

        for (int slotIndex : cropSlots) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();

            if (hasContainerSpaceForStack(menu, containerSlots, stack)) {
                hasSpace = true;
                break;
            }
        }

        if (!hasSpace) {
            result.noSpace = true;

            if (dryRun) {
                client.player.displayClientMessage(
                        Component.literal("§e[DryRun] 현재 상자에는 농작물을 넣을 공간이 없습니다."),
                        false
                );
            }

            return result;
        }

        for (int slotIndex : cropSlots) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();

            if (stack.isEmpty()) continue;

            String itemId = getItemId(stack);

            if (!isCropItem(itemId, stack)) continue;

            if (!hasContainerSpaceForStack(menu, containerSlots, stack)) {
                continue;
            }

            if (dryRun) {
                client.player.displayClientMessage(
                        Component.literal(
                                "§7[DryRun] 슬롯 "
                                        + slotIndex
                                        + " → 상자 투입 가능: §f"
                                        + stack.getHoverName().getString()
                                        + " x"
                                        + stack.getCount()
                                        + " §8("
                                        + itemId
                                        + ")"
                        ),
                        false
                );
            } else {
                client.gameMode.handleInventoryMouseClick(
                        menu.containerId,
                        slotIndex,
                        0,
                        ClickType.QUICK_MOVE,
                        client.player
                );

                result.clickedSlots++;
            }
        }

        return result;
    }

    private static boolean hasContainerSpaceForStack(AbstractContainerMenu menu, int containerSlots, ItemStack stack) {
        if (stack.isEmpty()) return false;

        for (int i = 0; i < containerSlots; i++) {
            ItemStack chestStack = menu.slots.get(i).getItem();

            if (chestStack.isEmpty()) {
                return true;
            }

            if (ItemStack.isSameItemSameComponents(chestStack, stack)
                    && chestStack.getCount() < chestStack.getMaxStackSize()) {
                return true;
            }
        }

        return false;
    }

    private static boolean openChestAt(Minecraft client, BlockPos pos) {
        if (client.gameMode == null || client.player == null) return false;

        Vec3 hitVec = Vec3.atCenterOf(pos);

        BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                Direction.UP,
                pos,
                false
        );

        client.gameMode.useItemOn(
                client.player,
                InteractionHand.MAIN_HAND,
                hitResult
        );

        return true;
    }

    private static BlockPos findNearestUsableChest(Minecraft client) {
        BlockPos playerPos = client.player.blockPosition();

        return registeredChests.stream()
                .filter(pos -> !fullChests.contains(pos))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)))
                .orElse(null);
    }

    private static boolean hasDepositableCropItem(Minecraft client) {
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);

            if (stack.isEmpty()) continue;

            String itemId = getItemId(stack);
            String name = stack.getHoverName().getString();

            // 씨앗은 농사용으로 남겨둘 가능성이 높으므로 제외
            if (itemId.contains("seed") || itemId.contains("seeds")) continue;
            if (name.contains("씨앗")) continue;

            if (isCropItem(itemId, stack)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasCropItem(Minecraft client) {
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);

            if (stack.isEmpty()) continue;

            if (isCropItem(getItemId(stack), stack)) {
                return true;
            }
        }

        return false;
    }

    private static boolean startBaritoneGoalNear(BlockPos pos, int range) {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object goalProcess = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);

            Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
            Class<?> goalNearClass = Class.forName("baritone.api.pathing.goals.GoalNear");

            Object goal;

            try {
                Constructor<?> c = goalNearClass.getConstructor(BlockPos.class, int.class);
                goal = c.newInstance(pos, range);
            } catch (NoSuchMethodException e) {
                Constructor<?> c = goalNearClass.getConstructor(int.class, int.class, int.class, int.class);
                goal = c.newInstance(pos.getX(), pos.getY(), pos.getZ(), range);
            }

            Method setGoalAndPath = goalProcess.getClass().getMethod("setGoalAndPath", goalClass);
            setGoalAndPath.invoke(goalProcess, goal);

            return true;

        } catch (Exception e) {
            System.out.println("[FarmTest] Baritone start failed");
            e.printStackTrace();
            return false;
        }
    }

    private static void cancelBaritone() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);

            pathingBehavior.getClass().getMethod("cancelEverything").invoke(pathingBehavior);

        } catch (Exception e) {
            System.out.println("[FarmTest] Baritone cancel failed");
            e.printStackTrace();
        }
    }

    private static void runServerDetectTest(Minecraft client) {
        ServerType serverType = getCurrentServerType(client);
        String rawTabText = getRawTabText(client);
        String matchedLine = findMyChannelLine(rawTabText, client.player.getGameProfile().getName());

        client.player.displayClientMessage(Component.literal("§b[서버 타입 인식 테스트]"), false);
        client.player.displayClientMessage(Component.literal("§7탐지된 라인: §f" + (matchedLine.isBlank() ? "없음" : matchedLine)), false);
        client.player.displayClientMessage(Component.literal("§7결과: " + getServerColor(serverType) + serverType.name()), false);
        client.player.displayClientMessage(Component.literal("§7설명: §f" + getServerDescription(serverType)), false);
    }

    private static void registerLookingChest(Minecraft client) {
        ServerType currentServer = getCurrentServerType(client);

        if (currentServer != ServerType.TOWN) {
            client.player.displayClientMessage(
                    Component.literal(
                            "§c[상자 등록] 마을 서버에서만 등록할 수 있습니다. 현재 서버: "
                                    + getServerColor(currentServer)
                                    + currentServer.name()
                    ),
                    false
            );
            return;
        }

        if (registeredChests.size() >= MAX_CHESTS) {
            client.player.displayClientMessage(
                    Component.literal("§c[상자 등록] 최대 " + MAX_CHESTS + "개까지만 등록할 수 있습니다."),
                    false
            );
            return;
        }

        HitResult hit = client.hitResult;

        if (!(hit instanceof BlockHitResult blockHit)) {
            client.player.displayClientMessage(
                    Component.literal("§c[상자 등록] 바라보는 블록이 없습니다."),
                    false
            );
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);

        if (!isStorageBlock(state)) {
            client.player.displayClientMessage(
                    Component.literal("§c[상자 등록] 바라보는 블록이 상자/덫 상자/배럴이 아닙니다."),
                    false
            );
            return;
        }

        BlockPos immutablePos = pos.immutable();

        if (registeredChests.contains(immutablePos)) {
            client.player.displayClientMessage(
                    Component.literal(
                            "§e[상자 등록] 이미 등록된 상자입니다. §7("
                                    + pos.getX() + ", "
                                    + pos.getY() + ", "
                                    + pos.getZ() + ")"
                    ),
                    false
            );
            return;
        }

        registeredChests.add(immutablePos);
        saveChestsToFile();

        client.player.displayClientMessage(
                Component.literal(
                        "§a[상자 등록] §f"
                                + getStorageName(state)
                                + " §7("
                                + pos.getX() + ", "
                                + pos.getY() + ", "
                                + pos.getZ()
                                + ") §8["
                                + registeredChests.size()
                                + "/"
                                + MAX_CHESTS
                                + "] §7저장 완료"
                ),
                false
        );
    }

    private static void printRegisteredChests(Minecraft client) {
        ServerType currentServer = getCurrentServerType(client);

        client.player.displayClientMessage(
                Component.literal(
                        "§b[상자 목록] §f"
                                + registeredChests.size()
                                + "/"
                                + MAX_CHESTS
                                + "개 §7/ 현재 서버: "
                                + getServerColor(currentServer)
                                + currentServer.name()
                                + " §7/ 가득 찬 상자: "
                                + fullChests.size()
                ),
                false
        );

        if (registeredChests.isEmpty()) {
            client.player.displayClientMessage(
                    Component.literal("§7등록된 상자가 없습니다."),
                    false
            );
            return;
        }

        for (int i = 0; i < registeredChests.size(); i++) {
            BlockPos pos = registeredChests.get(i);

            String status = fullChests.contains(pos) ? " §c[FULL]" : "";

            client.player.displayClientMessage(
                    Component.literal(
                            "§7" + (i + 1) + ". §f("
                                    + pos.getX() + ", "
                                    + pos.getY() + ", "
                                    + pos.getZ() + ")"
                                    + status
                    ),
                    false
            );
        }
    }

    private static void runInventoryTest(Minecraft client) {
        int emptySlots = 0;
        int cropSlots = 0;
        int totalCropCount = 0;

        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);

            if (stack.isEmpty()) {
                emptySlots++;
                continue;
            }

            String itemId = getItemId(stack);

            if (isCropItem(itemId, stack)) {
                cropSlots++;
                totalCropCount += stack.getCount();
            }
        }

        boolean inventoryFull = emptySlots == 0;

        client.player.displayClientMessage(Component.literal("§b[인벤토리 검사]"), false);
        client.player.displayClientMessage(Component.literal("§7빈 슬롯: §f" + emptySlots + "개"), false);
        client.player.displayClientMessage(Component.literal("§7인벤 가득 참: " + (inventoryFull ? "§cYES" : "§aNO")), false);
        client.player.displayClientMessage(Component.literal("§7농작물 후보 슬롯: §f" + cropSlots + "개"), false);
        client.player.displayClientMessage(Component.literal("§7농작물 후보 총 개수: §f" + totalCropCount + "개"), false);
    }

    private static void runContainerTest(Minecraft client) {
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            client.player.displayClientMessage(
                    Component.literal("§c[상자 화면 검사] 현재 컨테이너 화면이 열려있지 않습니다."),
                    false
            );
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();

        int totalSlots = menu.slots.size();
        int playerInventorySlots = 36;
        int containerSlots = Math.max(0, totalSlots - playerInventorySlots);

        client.player.displayClientMessage(Component.literal("§b[상자 화면 검사]"), false);
        client.player.displayClientMessage(Component.literal("§7화면 제목: §f" + screen.getTitle().getString()), false);
        client.player.displayClientMessage(Component.literal("§7전체 슬롯 수: §f" + totalSlots), false);
        client.player.displayClientMessage(Component.literal("§7추정 상자 슬롯 수: §f" + containerSlots), false);
    }

    private static boolean isCropItem(String itemId, ItemStack stack) {
        String name = stack.getHoverName().getString();

        if (itemId.contains("wheat")) return true;
        if (itemId.contains("carrot")) return true;
        if (itemId.contains("potato")) return true;
        if (itemId.contains("beetroot")) return true;
        if (itemId.contains("melon")) return true;
        if (itemId.contains("pumpkin")) return true;
        if (itemId.contains("sugar_cane")) return true;
        if (itemId.contains("cactus")) return true;
        if (itemId.contains("nether_wart")) return true;
        if (itemId.contains("bamboo")) return true;
        if (itemId.contains("cocoa")) return true;
        if (itemId.contains("seed")) return true;
        if (itemId.contains("seeds")) return true;
        if (itemId.contains("crop")) return true;

        if (name.contains("밀")) return true;
        if (name.contains("당근")) return true;
        if (name.contains("감자")) return true;
        if (name.contains("비트")) return true;
        if (name.contains("수박")) return true;
        if (name.contains("호박")) return true;
        if (name.contains("사탕수수")) return true;
        if (name.contains("선인장")) return true;
        if (name.contains("네더 사마귀")) return true;
        if (name.contains("대나무")) return true;
        if (name.contains("코코아")) return true;
        if (name.contains("씨앗")) return true;
        if (name.contains("작물")) return true;

        return false;
    }

    private static String getItemId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "unknown" : id.toString();
    }

    private static void saveChestsToFile() {
        try {
            Files.createDirectories(CHEST_SAVE_PATH.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(CHEST_SAVE_PATH)) {
                for (BlockPos pos : registeredChests) {
                    writer.write(pos.getX() + "," + pos.getY() + "," + pos.getZ());
                    writer.newLine();
                }
            }

            System.out.println("[FarmTest] Saved chests: " + registeredChests.size());

        } catch (Exception e) {
            System.out.println("[FarmTest] Failed to save chests");
            e.printStackTrace();
        }
    }

    private static void loadChestsFromFile() {
        registeredChests.clear();

        if (!Files.exists(CHEST_SAVE_PATH)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(CHEST_SAVE_PATH)) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split(",");
                if (parts.length != 3) continue;

                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());

                BlockPos pos = new BlockPos(x, y, z);

                if (!registeredChests.contains(pos) && registeredChests.size() < MAX_CHESTS) {
                    registeredChests.add(pos);
                }
            }

            System.out.println("[FarmTest] Loaded chests: " + registeredChests.size());

        } catch (Exception e) {
            System.out.println("[FarmTest] Failed to load chests");
            e.printStackTrace();
        }
    }

    private static void deleteChestSaveFile() {
        try {
            Files.deleteIfExists(CHEST_SAVE_PATH);
            System.out.println("[FarmTest] Deleted chest save file");
        } catch (Exception e) {
            System.out.println("[FarmTest] Failed to delete chest save file");
            e.printStackTrace();
        }
    }

    private static ServerType getCurrentServerType(Minecraft client) {
        String rawTabText = getRawTabText(client);
        String matchedLine = findMyChannelLine(rawTabText, client.player.getGameProfile().getName());
        String recentText = getRecentTextJoined();

        return detectServerType(matchedLine, recentText);
    }

    private static String getRawTabText(Minecraft client) {
        if (client.getConnection() == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
            if (info.getTabListDisplayName() != null) {
                builder.append(info.getTabListDisplayName().getString()).append("\n");
            }

            if (info.getProfile() != null) {
                builder.append(info.getProfile().getName()).append("\n");
            }
        }

        return builder.toString();
    }

    private static String findMyChannelLine(String rawText, String myName) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String[] lines = rawText.split("\n");

        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            if (!line.contains(myName)) continue;
            if (!line.contains("채널")) continue;

            return line.trim();
        }

        return "";
    }

    private static ServerType detectServerType(String matchedLine, String recentText) {
        ServerType lineResult = detectFromChannelLine(matchedLine);

        if (lineResult != ServerType.UNKNOWN) {
            return lineResult;
        }

        ServerType recentResult = detectFromRecentText(recentText);

        if (recentResult != ServerType.UNKNOWN) {
            return recentResult;
        }

        return ServerType.UNKNOWN;
    }

    private static ServerType detectFromChannelLine(String line) {
        if (line == null || line.isBlank()) {
            return ServerType.UNKNOWN;
        }

        String lower = line.toLowerCase();

        if (line.contains("마을") || lower.contains("town")) return ServerType.TOWN;
        if (line.contains("스폰") || lower.contains("spawn")) return ServerType.SPAWN;
        if (line.contains("아일랜드") || lower.contains("island")) return ServerType.ISLAND;
        if (line.contains("야생") || lower.contains("wild")) return ServerType.WILD;

        return ServerType.UNKNOWN;
    }

    private static ServerType detectFromRecentText(String text) {
        if (text == null || text.isBlank()) {
            return ServerType.UNKNOWN;
        }

        if (text.contains("잠수 포인트")) {
            return ServerType.AFK;
        }

        if (text.contains("확률형 아이템")) {
            return ServerType.LOBBY;
        }

        return ServerType.UNKNOWN;
    }

    private static boolean isStorageBlock(BlockState state) {
        return state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.BARREL);
    }

    private static String getStorageName(BlockState state) {
        if (state.is(Blocks.CHEST)) return "상자";
        if (state.is(Blocks.TRAPPED_CHEST)) return "덫 상자";
        if (state.is(Blocks.BARREL)) return "배럴";
        return "저장 블록";
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) return "(null)";

        return "("
                + pos.getX() + ", "
                + pos.getY() + ", "
                + pos.getZ() + ")";
    }

    private static void printRawTabText(Minecraft client) {
        String raw = getRawTabText(client);

        client.player.displayClientMessage(
                Component.literal("§b[RAW TAB TEXT] 콘솔에 출력했습니다."),
                false
        );

        System.out.println("========== RAW TAB ==========");
        System.out.println(raw);
        System.out.println("=============================");
    }

    private static void addRecentText(String text) {
        recentTexts.addLast(new TimedText(text, System.currentTimeMillis()));

        while (recentTexts.size() > MAX_RECENT_TEXTS) {
            recentTexts.removeFirst();
        }

        System.out.println("[SERVER TEXT] " + text);
    }

    private static void removeOldTexts() {
        long now = System.currentTimeMillis();

        while (!recentTexts.isEmpty()) {
            TimedText first = recentTexts.peekFirst();

            if (now - first.timeMs <= TEXT_KEEP_MS) {
                break;
            }

            recentTexts.removeFirst();
        }
    }

    private static String getRecentTextJoined() {
        removeOldTexts();

        StringBuilder builder = new StringBuilder();

        for (TimedText timedText : recentTexts) {
            builder.append(timedText.text).append("\n");
        }

        return builder.toString();
    }

    private static String getServerColor(ServerType type) {
        return switch (type) {
            case TOWN -> "§a";
            case SPAWN -> "§e";
            case ISLAND -> "§b";
            case WILD -> "§2";
            case AFK -> "§d";
            case LOBBY -> "§6";
            case UNKNOWN -> "§c";
        };
    }

    private static String getServerDescription(ServerType type) {
        return switch (type) {
            case TOWN -> "마을 서버";
            case SPAWN -> "스폰 서버";
            case ISLAND -> "아일랜드 서버";
            case WILD -> "야생 서버";
            case AFK -> "잠수 서버";
            case LOBBY -> "로비 서버";
            case UNKNOWN -> "알 수 없음";
        };
    }

    private enum AutoState {
        IDLE,
        FARMING,
        GOING_TO_CHEST,
        OPENING_CHEST,
        DEPOSITING,
        WAIT_AFTER_DEPOSIT
    }

    private enum ServerType {
        TOWN,
        SPAWN,
        ISLAND,
        WILD,
        AFK,
        LOBBY,
        UNKNOWN
    }

    private static class DepositResult {
        int cropCandidateSlots = 0;
        int clickedSlots = 0;
        boolean noSpace = false;
    }

    private static class TimedText {
        String text;
        long timeMs;

        TimedText(String text, long timeMs) {
            this.text = text;
            this.timeMs = timeMs;
        }
    }
    private static boolean isMainInventoryFull(Minecraft client) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);

            if (stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }
    private static boolean startBaritoneFarm(int range) {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object farmProcess = baritone.getClass().getMethod("getFarmProcess").invoke(baritone);

            for (Method method : farmProcess.getClass().getMethods()) {
                if (!method.getName().equals("farm")) continue;

                Class<?>[] params = method.getParameterTypes();

                if (params.length == 1 && params[0] == int.class) {
                    method.invoke(farmProcess, range);
                    return true;
                }

                if (params.length == 2 && params[0] == int.class) {
                    method.invoke(farmProcess, range, null);
                    return true;
                }
            }

            System.out.println("[FarmTest] No suitable farm(int...) method found");
            return false;

        } catch (Exception e) {
            System.out.println("[FarmTest] Baritone farm start failed");
            e.printStackTrace();
            return false;
        }
    }
}