package com.badger124.customcompat.gui.farm;

import com.badger124.customcompat.CustomCompatMod;
import com.badger124.customcompat.api.CustomCompatApi;
import net.minecraft.block.NoteBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;

/**
 * Tick-based handler for custom note-block farmland harvesting and replanting.
 *
 * <h2>System overview</h2>
 * <p>The server represents custom farmland as <b>note blocks</b>.  The note block's
 * {@code note} property (0–24) encodes moisture:</p>
 * <ul>
 *   <li>Wet pitches ({@link FarmProfile#getWetPitchMin()}–{@link FarmProfile#getWetPitchMax()}):
 *       crops above can be harvested and replanted.</li>
 *   <li>Dry pitches: skipped (configurable via {@link FarmProfile#isSkipDry()}).</li>
 * </ul>
 *
 * <p>Trap (tripwire-hook) states adjacent to the note block may further differentiate crop
 * varieties and growth rates.  The current implementation identifies harvestable crops by the
 * block ID and age of the block directly above the note block, as configured per
 * {@link FarmProfile.CropMapping}.</p>
 *
 * <h2>Harvest and replant flow</h2>
 * <ol>
 *   <li>Every {@value #SCAN_INTERVAL} ticks, scan a sphere of radius {@link FarmProfile#getRange()}
 *       blocks around the player for wet note blocks.</li>
 *   <li>For each wet note block, inspect the block above.  If it matches a configured
 *       {@link FarmProfile.CropMapping} and its age is ≥ {@code matureAge}, add it to the
 *       harvest queue.</li>
 *   <li>Pick the nearest harvestable block that is within reach ({@value #REACH} blocks).</li>
 *   <li>Break the crop with {@code ClientPlayerInteractionManager#attackBlock}.</li>
 *   <li>Wait {@value #REPLANT_DELAY_TICKS} ticks, then equip the matching seed from the
 *       hotbar and right-click the note block to replant.</li>
 * </ol>
 *
 * <p>The player (or Baritone) is responsible for movement; this handler only interacts with
 * blocks that are already within reach.</p>
 */
public final class CustomFarmingHandler {

    private static final CustomFarmingHandler INSTANCE = new CustomFarmingHandler();
    public static CustomFarmingHandler getInstance() { return INSTANCE; }

    /** Maximum block-interaction reach in blocks. */
    private static final double REACH = 4.5;
    private static final double REACH_SQ = REACH * REACH;

    /** How often (in ticks) to rescan the surrounding area. */
    private static final int SCAN_INTERVAL = 10;

    /** Ticks to wait after harvesting before replanting. */
    private static final int REPLANT_DELAY_TICKS = 3;

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean active = false;
    private FarmProfile profile = null;

    /** Note-block position for the pending replant (null when no replant is queued). */
    private BlockPos pendingReplantNotePos = null;
    /**
     * Which crop mapping was used for the last harvest (used to select the correct seed).
     * Null when no replant is pending.
     */
    private FarmProfile.CropMapping pendingReplantCrop = null;
    private int replantCountdown = 0;
    private int scanCountdown = 0;

    private CustomFarmingHandler() {}

    // =========================================================================
    // Public control
    // =========================================================================

    public boolean isActive() { return active; }
    public FarmProfile getActiveProfile() { return profile; }

    public void start(FarmProfile p) {
        this.active = true;
        this.profile = p;
        this.pendingReplantNotePos = null;
        this.pendingReplantCrop = null;
        this.replantCountdown = 0;
        this.scanCountdown = 0;
        CustomCompatMod.LOGGER.info("[CustomCompat] Custom farming started: '{}'", p.getName());
    }

    public void stop() {
        this.active = false;
        this.profile = null;
        this.pendingReplantNotePos = null;
        this.pendingReplantCrop = null;
        CustomCompatMod.LOGGER.info("[CustomCompat] Custom farming stopped.");
    }

    // =========================================================================
    // Tick
    // =========================================================================

    public void tick(MinecraftClient client) {
        if (!active || profile == null) return;

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        // ── Replant phase ──────────────────────────────────────────────────────
        if (pendingReplantNotePos != null) {
            if (--replantCountdown <= 0) {
                doReplant(client, player);
                pendingReplantNotePos = null;
                pendingReplantCrop = null;
            }
            return;
        }

        // ── Scan phase ────────────────────────────────────────────────────────
        if (--scanCountdown > 0) return;
        scanCountdown = SCAN_INTERVAL;

        BlockPos playerPos = player.getBlockPos();
        int range = Math.min(profile.getRange(), 64);
        List<FarmProfile.CropMapping> crops = profile.parsedCrops();
        if (crops.isEmpty()) return;

        Vec3d eyePos = player.getEyePos();
        BlockPos bestCropPos = null;
        BlockPos bestNotePos = null;
        FarmProfile.CropMapping bestCrop = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                for (int y = -4; y <= 4; y++) {
                    BlockPos notePos = playerPos.add(x, y, z);
                    var noteState = client.world.getBlockState(notePos);

                    if (!(noteState.getBlock() instanceof NoteBlock)) continue;

                    int note = noteState.get(NoteBlock.NOTE);
                    if (profile.isSkipDry() && !profile.isWetPitch(note)) continue;

                    BlockPos cropPos = notePos.up();
                    var cropState = client.world.getBlockState(cropPos);

                    FarmProfile.CropMapping matched = matchCrop(cropState, crops);
                    if (matched == null) continue;

                    Vec3d cropCenter = Vec3d.ofCenter(cropPos);
                    double distSq = eyePos.squaredDistanceTo(cropCenter);
                    if (distSq <= REACH_SQ && distSq < bestDistSq) {
                        bestCropPos = cropPos;
                        bestNotePos = notePos;
                        bestCrop = matched;
                        bestDistSq = distSq;
                    }
                }
            }
        }

        if (bestCropPos != null) {
            harvest(client, bestCropPos, bestNotePos, bestCrop);
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private FarmProfile.CropMapping matchCrop(
            net.minecraft.block.BlockState state,
            List<FarmProfile.CropMapping> crops) {
        if (state.isAir()) return null;
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        for (FarmProfile.CropMapping crop : crops) {
            if (!blockId.equals(crop.blockId())) continue;
            int matureAge = crop.matureAge();
            if (matureAge < 0) return crop; // any age
            // Check common age properties
            if (state.contains(Properties.AGE_7)  && state.get(Properties.AGE_7)  >= matureAge) return crop;
            if (state.contains(Properties.AGE_3)  && state.get(Properties.AGE_3)  >= matureAge) return crop;
            if (state.contains(Properties.AGE_5)  && state.get(Properties.AGE_5)  >= matureAge) return crop;
            if (state.contains(Properties.AGE_25) && state.get(Properties.AGE_25) >= matureAge) return crop;
            if (state.contains(Properties.AGE_15) && state.get(Properties.AGE_15) >= matureAge) return crop;
            if (matureAge == 0) return crop; // block ID match, no age check needed
        }
        return null;
    }

    private void harvest(MinecraftClient client, BlockPos cropPos,
                         BlockPos notePos, FarmProfile.CropMapping crop) {
        client.interactionManager.attackBlock(cropPos, Direction.DOWN);
        this.pendingReplantNotePos = notePos;
        this.pendingReplantCrop = crop;
        this.replantCountdown = REPLANT_DELAY_TICKS;
        CustomCompatMod.LOGGER.debug("[CustomCompat] Harvesting {} at {}", crop.blockId(), cropPos);
    }

    private void doReplant(MinecraftClient client, ClientPlayerEntity player) {
        if (pendingReplantCrop == null) return;
        String seedNexoId = pendingReplantCrop.seedNexoId();
        if (seedNexoId == null || seedNexoId.isBlank()) return;

        int slot = findSeedSlot(player, seedNexoId);
        if (slot < 0) {
            CustomCompatMod.LOGGER.debug("[CustomCompat] Seed '{}' not found in hotbar.", seedNexoId);
            return;
        }

        int prevSlot = player.getInventory().selectedSlot;
        player.getInventory().selectedSlot = slot;

        // Right-click the note block (farmland) to plant
        Vec3d hitVec = Vec3d.ofCenter(pendingReplantNotePos).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pendingReplantNotePos, false);
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);

        player.getInventory().selectedSlot = prevSlot;
        CustomCompatMod.LOGGER.debug("[CustomCompat] Replanting '{}' at {}", seedNexoId, pendingReplantNotePos);
    }

    /**
     * Scans hotbar slots 0–8 for the seed identified by {@code seedNexoId}.
     *
     * <p>Accepts both the canonical form {@code "nexo:crop_tomato_seed"} and the raw path
     * {@code "crop_tomato_seed"} to accommodate different ways of entering the seed ID.</p>
     *
     * @return Hotbar slot index (0–8) or {@code -1} if not found.
     */
    private static int findSeedSlot(ClientPlayerEntity player, String seedNexoId) {
        String normalized = seedNexoId.startsWith("nexo:") ? seedNexoId : "nexo:" + seedNexoId;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Optional<Identifier> nexoId = CustomCompatApi.readNexoId(stack);
            if (nexoId.isPresent() && nexoId.get().toString().equals(normalized)) return i;
        }
        return -1;
    }
}
