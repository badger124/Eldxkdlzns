package com.example.client.farm;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChestRegistry {
    private static final int MAX_CHESTS = 10;
    private static final Path CHEST_SAVE_PATH = Path.of("config", "farm_chests.txt");

    private final List<BlockPos> registeredChests = new ArrayList<>();
    private final Set<BlockPos> fullChests = new HashSet<>();

    public void loadFromFile() {
        // 시작 시점마다 메모리 목록을 파일 기준으로 재구성한다.
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

    public void saveToFile() {
        // 등록 순서를 그대로 저장해 이후 가장 가까운 상자 탐색 기준을 유지한다.
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

    public void deleteSaveFile() {
        try {
            Files.deleteIfExists(CHEST_SAVE_PATH);
            System.out.println("[FarmTest] Deleted chest save file");
        } catch (Exception e) {
            System.out.println("[FarmTest] Failed to delete chest save file");
            e.printStackTrace();
        }
    }

    // 마을에서 바라보는 저장 블록을 등록하고 즉시 파일로 영속화한다.
    public void registerLookingChest(Minecraft client, ServerDetector serverDetector) {
        ServerType currentServer = serverDetector.getCurrentServerType(client);

        if (currentServer != ServerType.TOWN) {
            client.player.displayClientMessage(
                    Component.literal(
                            "§c[상자 등록] 마을 서버에서만 등록할 수 있습니다. 현재 서버: "
                                    + serverDetector.getServerColor(currentServer)
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
        saveToFile();

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

    public void printRegisteredChests(Minecraft client, ServerDetector serverDetector) {
        ServerType currentServer = serverDetector.getCurrentServerType(client);

        client.player.displayClientMessage(
                Component.literal(
                        "§b[상자 목록] §f"
                                + registeredChests.size()
                                + "/"
                                + MAX_CHESTS
                                + "개 §7/ 현재 서버: "
                                + serverDetector.getServerColor(currentServer)
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

    public void clearAllAndDeleteFile() {
        registeredChests.clear();
        fullChests.clear();
        deleteSaveFile();
    }

    public void clearFullChests() {
        fullChests.clear();
    }

    public void markChestFull(BlockPos pos) {
        if (pos != null) {
            fullChests.add(pos.immutable());
        }
    }

    public BlockPos findNearestUsableChest(BlockPos playerPos) {
        // 현재 FULL로 표시된 상자를 제외한 뒤 거리 기준으로 1개를 선택한다.
        return registeredChests.stream()
                .filter(pos -> !fullChests.contains(pos))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)))
                .orElse(null);
    }

    public int getRegisteredCount() {
        return registeredChests.size();
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
}
