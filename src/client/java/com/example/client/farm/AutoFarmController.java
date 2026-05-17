package com.example.client.farm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class AutoFarmController {
    private final ServerDetector serverDetector;
    private final ChestRegistry chestRegistry;

    private AutoState autoState = AutoState.IDLE;
    private BlockPos targetChest = null;
    private int openWaitTicks = 0;
    private int depositWaitTicks = 0;
    private int farmRestartTicks = 0;

    public AutoFarmController(ServerDetector serverDetector, ChestRegistry chestRegistry) {
        this.serverDetector = serverDetector;
        this.chestRegistry = chestRegistry;
    }

    public void startAutoDeposit(Minecraft client) {
        // 자동화는 서버별 규칙이 다르므로 먼저 현재 서버 타입을 판별한다.
        ServerType currentServer = serverDetector.getCurrentServerType(client);

        if (currentServer != ServerType.TOWN) {
            client.player.displayClientMessage(
                    Component.literal("§c[자동 농사] 마을 서버에서만 실행합니다. 현재 서버: " + currentServer.name()),
                    false
            );
            return;
        }

        if (chestRegistry.getRegisteredCount() == 0) {
            client.player.displayClientMessage(
                    Component.literal("§c[자동 농사] 등록된 상자가 없습니다."),
                    false
            );
            return;
        }

        chestRegistry.clearFullChests();

        boolean ok = BaritoneBridge.startFarm(64);

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

    public void stopAutoDeposit(Minecraft client) {
        autoState = AutoState.IDLE;
        targetChest = null;
        openWaitTicks = 0;
        BaritoneBridge.cancel();

        client.player.displayClientMessage(
                Component.literal("§c[자동 투입] 중지했습니다."),
                false
        );
    }

    // 자동 농사 전체 흐름(FARMING→이동→열기→투입→재시작)을 상태머신으로 유지한다.
    public void tickAutoDeposit(Minecraft client) {
        if (autoState == AutoState.IDLE) return;

        if (autoState == AutoState.FARMING) {
            // 일정 주기로 Baritone farm 재호출해서 탐색이 멈춘 케이스를 복구한다.
            farmRestartTicks++;

            if (farmRestartTicks >= 200) {
                farmRestartTicks = 0;

                BaritoneBridge.startFarm(64);

                client.player.displayClientMessage(
                        Component.literal("§7[자동 농사] farm 재탐색 시도"),
                        false
                );
            }

            // 인벤이 가득 차면 상자 투입 단계로 전환한다.
            if (CropHelper.isMainInventoryFull(client)) {
                BaritoneBridge.cancel();

                targetChest = chestRegistry.findNearestUsableChest(client.player.blockPosition());

                if (targetChest == null) {
                    client.player.displayClientMessage(
                            Component.literal("§c[자동 농사] 인벤이 찼지만 사용 가능한 상자가 없습니다."),
                            false
                    );

                    autoState = AutoState.IDLE;
                    return;
                }

                boolean ok = BaritoneBridge.startGoalNear(targetChest, 2);

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
            BaritoneBridge.cancel();
            return;
        }

        if (autoState == AutoState.GOING_TO_CHEST) {
            double distance = client.player.blockPosition().distSqr(targetChest);

            if (distance <= 12.25) {
                BaritoneBridge.cancel();
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

            if (CropHelper.hasDepositableCropItem(client)) {
                if (targetChest != null) {
                    chestRegistry.markChestFull(targetChest);
                }

                if (client.screen != null) {
                    client.player.closeContainer();
                }

                client.player.displayClientMessage(
                        Component.literal("§e[자동 농사] 아직 작물이 남아있습니다. 다음 상자로 이동합니다."),
                        false
                );

                targetChest = chestRegistry.findNearestUsableChest(client.player.blockPosition());

                if (targetChest == null) {
                    client.player.displayClientMessage(
                            Component.literal("§c[자동 농사] 남은 작물이 있지만 사용 가능한 상자가 없습니다."),
                            false
                    );

                    autoState = AutoState.IDLE;
                    openWaitTicks = 0;
                    BaritoneBridge.cancel();
                    return;
                }

                boolean ok = BaritoneBridge.startGoalNear(targetChest, 2);

                if (!ok) {
                    client.player.displayClientMessage(
                            Component.literal("§c[자동 농사] 다음 상자로 이동 시작 실패."),
                            false
                    );

                    autoState = AutoState.IDLE;
                    openWaitTicks = 0;
                    BaritoneBridge.cancel();
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

            chestRegistry.clearFullChests();

            boolean ok = BaritoneBridge.startFarm(64);

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
        }
    }

    private void markCurrentChestFullOrBadAndMoveNext(Minecraft client) {
        // 실패한 상자는 임시 FULL 집합에 넣어 같은 상자를 반복 시도하지 않게 한다.
        if (targetChest != null) {
            chestRegistry.markChestFull(targetChest);
        }

        if (client.screen != null) {
            client.player.closeContainer();
        }

        targetChest = chestRegistry.findNearestUsableChest(client.player.blockPosition());

        if (targetChest == null) {
            client.player.displayClientMessage(
                    Component.literal("§c[자동 투입] 사용 가능한 다음 상자가 없습니다."),
                    false
            );

            autoState = AutoState.IDLE;
            openWaitTicks = 0;
            BaritoneBridge.cancel();
            return;
        }

        boolean ok = BaritoneBridge.startGoalNear(targetChest, 2);

        if (!ok) {
            client.player.displayClientMessage(
                    Component.literal("§c[자동 투입] 다음 상자로 이동 시작 실패."),
                    false
            );

            autoState = AutoState.IDLE;
            openWaitTicks = 0;
            BaritoneBridge.cancel();
            return;
        }

        autoState = AutoState.GOING_TO_CHEST;
        openWaitTicks = 0;

        client.player.displayClientMessage(
                Component.literal("§a[자동 투입] 다음 상자로 이동 → " + formatPos(targetChest)),
                false
        );
    }

    public void runDepositDryRun(Minecraft client) {
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

    private DepositResult depositCropsToOpenContainer(Minecraft client, boolean dryRun) {
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

            String itemId = CropHelper.getItemId(stack);

            if (!CropHelper.isCropItem(itemId, stack)) continue;

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

            String itemId = CropHelper.getItemId(stack);

            if (!CropHelper.isCropItem(itemId, stack)) continue;

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
                // QUICK_MOVE를 사용해 플레이어 인벤토리에서 상자 영역으로 즉시 이동시킨다.
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

    private boolean hasContainerSpaceForStack(AbstractContainerMenu menu, int containerSlots, ItemStack stack) {
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

    private boolean openChestAt(Minecraft client, BlockPos pos) {
        if (client.gameMode == null || client.player == null) return false;

        // 블록 중심 좌표를 히트 포인트로 사용해 서버 인식 편차를 줄인다.
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

    public void runServerDetectTest(Minecraft client) {
        ServerType serverType = serverDetector.getCurrentServerType(client);
        String rawTabText = serverDetector.getRawTabText(client);
        String matchedLine = serverDetector.findMyChannelLine(rawTabText, client.player.getGameProfile().getName());

        client.player.displayClientMessage(Component.literal("§b[서버 타입 인식 테스트]"), false);
        client.player.displayClientMessage(Component.literal("§7탐지된 라인: §f" + (matchedLine.isBlank() ? "없음" : matchedLine)), false);
        client.player.displayClientMessage(Component.literal("§7결과: " + serverDetector.getServerColor(serverType) + serverType.name()), false);
        client.player.displayClientMessage(Component.literal("§7설명: §f" + serverDetector.getServerDescription(serverType)), false);
    }

    public void runInventoryTest(Minecraft client) {
        int emptySlots = 0;
        int cropSlots = 0;
        int totalCropCount = 0;

        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);

            if (stack.isEmpty()) {
                emptySlots++;
                continue;
            }

            String itemId = CropHelper.getItemId(stack);

            if (CropHelper.isCropItem(itemId, stack)) {
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

    public void runContainerTest(Minecraft client) {
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

    public void printRawTabText(Minecraft client) {
        String raw = serverDetector.getRawTabText(client);

        client.player.displayClientMessage(
                Component.literal("§b[RAW TAB TEXT] 콘솔에 출력했습니다."),
                false
        );

        System.out.println("========== RAW TAB ==========");
        System.out.println(raw);
        System.out.println("=============================");
    }

    public void registerLookingChest(Minecraft client) {
        chestRegistry.registerLookingChest(client, serverDetector);
    }

    public void printRegisteredChests(Minecraft client) {
        chestRegistry.printRegisteredChests(client, serverDetector);
    }

    public void clearRegisteredChests(Minecraft client) {
        chestRegistry.clearAllAndDeleteFile();
        client.player.displayClientMessage(
                Component.literal("§c[상자 초기화] 등록된 상자를 모두 삭제했습니다."),
                false
        );
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) return "(null)";

        return "("
                + pos.getX() + ", "
                + pos.getY() + ", "
                + pos.getZ() + ")";
    }
}
