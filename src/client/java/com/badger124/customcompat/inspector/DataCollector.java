package com.badger124.customcompat.inspector;

import com.badger124.customcompat.CustomCompatMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scans the client world on demand and accumulates custom-item / custom-entity
 * data, keyed by server address.  Results are persisted to
 * {@code config/customcompat_inspector.json} so they survive restarts and can
 * be read as a reference while writing mod code.
 *
 * <h2>What gets collected</h2>
 * <ul>
 *   <li>All {@link ItemStack}s in the player's inventory (hotbar + main)
 *       that carry either a {@code nexo:id} or a {@code custom_model_data}
 *       component.</li>
 *   <li>Dropped {@link ItemEntity}s within {@code scanRadius} blocks that
 *       carry the same markers.</li>
 *   <li>Living / non-item {@link Entity}s within {@code scanRadius} blocks
 *       that have at least one scoreboard tag.</li>
 * </ul>
 *
 * <h2>Server key</h2>
 * <ul>
 *   <li>Multiplayer: the server IP / hostname from
 *       {@code getCurrentServerEntry()}.</li>
 *   <li>Singleplayer / LAN-host: {@code "singleplayer"} or
 *       {@code "lan:<level-name>"}.</li>
 * </ul>
 */
public final class DataCollector {

    // ── Singleton ──────────────────────────────────────────────────────────────
    private static final DataCollector INSTANCE = new DataCollector();
    public static DataCollector getInstance() { return INSTANCE; }

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final String SAVE_FILE    = "customcompat_inspector.json";
    private static final int    SCAN_RADIUS  = 64;
    private static final Gson   GSON         = new GsonBuilder().setPrettyPrinting().create();

    // ── State ──────────────────────────────────────────────────────────────────
    /** serverKey → list of unique item display lines */
    private final Map<String, List<String>> itemsByServer   = new LinkedHashMap<>();
    /** serverKey → list of unique entity display lines */
    private final Map<String, List<String>> entitiesByServer = new LinkedHashMap<>();

    // In-memory results for current session (reset each scan)
    private final List<ScannedItem>   lastItems    = new ArrayList<>();
    private final List<ScannedEntity> lastEntities = new ArrayList<>();
    private String lastScanServer = "";

    private Path saveFile;

    private DataCollector() {}

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public void initialize(Path configDir) {
        saveFile = configDir.resolve(SAVE_FILE);
        load();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Performs a full scan of the client world and accumulates results.
     * Call from a client tick or a command handler (client thread only).
     */
    public void scan() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return;

        lastItems.clear();
        lastEntities.clear();

        // ── Determine server key ─────────────────────────────────────────────
        String serverKey;
        if (mc.isIntegratedServerRunning()) {
            // Singleplayer or hosted LAN
            var server = mc.getServer();
            String levelName = (server != null) ? server.getSaveProperties().getLevelName() : "unknown";
            serverKey = mc.isConnectedToLocalServer()
                    ? "singleplayer:" + levelName
                    : "lan:" + levelName;
        } else {
            var entry = mc.getCurrentServerEntry();
            serverKey = (entry != null) ? entry.address : "unknown-server";
        }
        lastScanServer = serverKey;

        // ── Scan player inventory ────────────────────────────────────────────
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            ScannedItem si = tryExtractItem(stack, i < 9 ? "hotbar" : "inventory");
            if (si != null) lastItems.add(si);
        }

        // ── Scan nearby dropped items ────────────────────────────────────────
        Box scanBox = player.getBoundingBox().expand(SCAN_RADIUS);
        for (Entity e : world.getEntitiesByClass(ItemEntity.class, scanBox, x -> true)) {
            ScannedItem si = tryExtractItem(((ItemEntity) e).getStack(), "ground");
            if (si != null) lastItems.add(si);
        }

        // ── Scan nearby living entities / armor stands ───────────────────────
        for (Entity e : world.getNonSpectatingEntities(Entity.class, scanBox)) {
            if (e instanceof ItemEntity) continue;
            if (e == player) continue;

            Set<String> tags = e.getCommandTags();
            if (tags.isEmpty()) continue;

            ScannedEntity se = new ScannedEntity();
            se.entityType = Registries.ENTITY_TYPE.getId(e.getType()).toString();
            se.allTags.addAll(tags);
            for (String t : tags) {
                if (t.startsWith("customcompat:")) {
                    se.customTags.add(t.substring("customcompat:".length()));
                }
            }
            Text nameText = e.getCustomName();
            if (nameText != null) se.customName = nameText.getString();
            lastEntities.add(se);
        }

        // ── Deduplicate & merge into per-server store ────────────────────────
        List<String> serverItems    = itemsByServer.computeIfAbsent(serverKey, k -> new ArrayList<>());
        List<String> serverEntities = entitiesByServer.computeIfAbsent(serverKey, k -> new ArrayList<>());

        for (ScannedItem si : lastItems) {
            String line = si.toDisplayLine();
            if (!serverItems.contains(line)) serverItems.add(line);
        }
        for (ScannedEntity se : lastEntities) {
            String line = se.toDisplayLine();
            if (!serverEntities.contains(line)) serverEntities.add(line);
        }

        save();

        CustomCompatMod.LOGGER.info("[CustomCompat Inspector] Scan complete for '{}': {} items, {} entities",
                serverKey, lastItems.size(), lastEntities.size());
    }

    /** Clears accumulated data for the given server key. */
    public void clearServer(String serverKey) {
        itemsByServer.remove(serverKey);
        entitiesByServer.remove(serverKey);
        save();
    }

    /** Clears all accumulated data. */
    public void clearAll() {
        itemsByServer.clear();
        entitiesByServer.clear();
        save();
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public List<ScannedItem>   getLastItems()    { return Collections.unmodifiableList(lastItems); }
    public List<ScannedEntity> getLastEntities() { return Collections.unmodifiableList(lastEntities); }
    public String              getLastScanServer() { return lastScanServer; }

    /** All server keys that have accumulated data. */
    public Set<String> getKnownServers() {
        Set<String> keys = new LinkedHashSet<>(itemsByServer.keySet());
        keys.addAll(entitiesByServer.keySet());
        return keys;
    }

    public List<String> getItemsForServer(String serverKey) {
        return itemsByServer.getOrDefault(serverKey, Collections.emptyList());
    }

    public List<String> getEntitiesForServer(String serverKey) {
        return entitiesByServer.getOrDefault(serverKey, Collections.emptyList());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private ScannedItem tryExtractItem(ItemStack stack, String source) {
        if (stack == null || stack.isEmpty()) return null;

        ScannedItem si = new ScannedItem();
        si.source   = source;
        si.baseItem = Registries.ITEM.getId(stack.getItem()).toString();

        // Try nexo:id from custom_data → PublicBukkitValues
        var customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound root = customData.copyNbt();
            NbtElement pbv = root.get("PublicBukkitValues");
            if (pbv instanceof NbtCompound pbvCompound) {
                NbtElement nexo = pbvCompound.get("nexo:id");
                if (nexo != null) si.nexoId = nexo.asString();
            }
        }

        // Try custom_model_data float[0]
        var cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (cmd != null && !cmd.floats().isEmpty()) {
            si.cmdFloat = cmd.floats().get(0);
        }

        // Only keep if there's something custom
        if (si.nexoId == null && si.cmdFloat == null) return null;
        return si;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void load() {
        if (saveFile == null || !Files.exists(saveFile)) return;
        try (Reader r = Files.newBufferedReader(saveFile)) {
            Map<String, Object> root = GSON.fromJson(r, Map.class);
            if (root == null) return;

            Object itemsObj = root.get("items");
            if (itemsObj instanceof Map<?,?> m) {
                for (Map.Entry<?,?> e : m.entrySet()) {
                    if (e.getValue() instanceof List<?> list) {
                        List<String> lines = new ArrayList<>();
                        for (Object o : list) if (o instanceof String s) lines.add(s);
                        itemsByServer.put(e.getKey().toString(), lines);
                    }
                }
            }
            Object entitiesObj = root.get("entities");
            if (entitiesObj instanceof Map<?,?> m) {
                for (Map.Entry<?,?> e : m.entrySet()) {
                    if (e.getValue() instanceof List<?> list) {
                        List<String> lines = new ArrayList<>();
                        for (Object o : list) if (o instanceof String s) lines.add(s);
                        entitiesByServer.put(e.getKey().toString(), lines);
                    }
                }
            }
        } catch (IOException ex) {
            CustomCompatMod.LOGGER.warn("[CustomCompat Inspector] Failed to load data: {}", ex.getMessage());
        }
    }

    private void save() {
        if (saveFile == null) return;
        try (Writer w = Files.newBufferedWriter(saveFile)) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("items",    itemsByServer);
            root.put("entities", entitiesByServer);
            GSON.toJson(root, w);
        } catch (IOException ex) {
            CustomCompatMod.LOGGER.warn("[CustomCompat Inspector] Failed to save data: {}", ex.getMessage());
        }
    }
}
