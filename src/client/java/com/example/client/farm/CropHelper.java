package com.example.client.farm;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class CropHelper {
    private CropHelper() {
    }

    public static boolean isCropItem(String itemId, ItemStack stack) {
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

    public static String getItemId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "unknown" : id.toString();
    }

    public static boolean hasDepositableCropItem(Minecraft client) {
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);

            if (stack.isEmpty()) continue;

            String itemId = getItemId(stack);
            String name = stack.getHoverName().getString();

            // 재심기용 씨앗은 우선 보존하고 수확물 위주로 투입한다.
            if (itemId.contains("seed") || itemId.contains("seeds")) continue;
            if (name.contains("씨앗")) continue;

            if (isCropItem(itemId, stack)) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasCropItem(Minecraft client) {
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);

            if (stack.isEmpty()) continue;

            if (isCropItem(getItemId(stack), stack)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isMainInventoryFull(Minecraft client) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);

            if (stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }
}
