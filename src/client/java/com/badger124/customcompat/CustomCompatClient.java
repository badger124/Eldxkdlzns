package com.badger124.customcompat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class CustomCompatClient implements ClientModInitializer {
    private static final double RANGE = 10.0D;
    private static final double RANGE_SQUARED = RANGE * RANGE;
    private static final String KEY_CATEGORY = "category.customcompat";

    private KeyBinding scanKey;

    @Override
    public void onInitializeClient() {
        scanKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.customcompat.scan_interactions",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (scanKey.wasPressed()) {
                scanNearbyInteractions(client);
            }
        });
    }

    private void scanNearbyInteractions(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        List<Entity> interactions = client.world.getEntities().stream()
                .filter(entity -> entity.getType() == EntityType.INTERACTION)
                .filter(entity -> client.player.squaredDistanceTo(entity) <= RANGE_SQUARED)
                .toList();

        if (interactions.isEmpty()) {
            client.player.sendMessage(Text.translatable("message.customcompat.scan.none"), false);
            return;
        }

        client.player.sendMessage(Text.translatable("message.customcompat.scan.count", interactions.size()), false);

        for (Entity entity : interactions) {
            String line = String.format(
                    "UUID=%s | getName()=%s | getCustomName()=%s | getDisplayName()=%s | getScoreboardTags()=%s | width=%.2f | height=%.2f | blockPos=%s",
                    entity.getUuid(),
                    entity.getName().getString(),
                    entity.getCustomName() == null ? "null" : entity.getCustomName().getString(),
                    entity.getDisplayName().getString(),
                    entity.getScoreboardTags(),
                    entity.getWidth(),
                    entity.getHeight(),
                    entity.getBlockPos()
            );
            client.player.sendMessage(Text.literal(line), false);
        }
    }
}
