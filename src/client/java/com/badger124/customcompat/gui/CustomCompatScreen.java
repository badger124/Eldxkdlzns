package com.badger124.customcompat.gui;

import com.badger124.customcompat.compat.baritone.BaritoneCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Main GUI for the Custom Content Compat mod.
 *
 * <h2>Items tab</h2>
 * <p>Enter a Nexo/custom item ID (e.g. {@code nexo:crop_tomato_seed}) and click
 * <em>Pickup</em> or <em>Follow</em> to start the corresponding Baritone action.
 * Recent IDs are kept in the scrollable list below; right-click a row to remove it.</p>
 *
 * <h2>Macros tab</h2>
 * <p>Create named macros containing a sequence of steps.  Each step is one of:</p>
 * <pre>
 *   pickup nexo:crop_tomato_seed
 *   follow mymod:boss_zombie
 *   farm [range]
 *   wait &lt;seconds&gt;
 *   stop
 *   # comment
 * </pre>
 * <p>Click <em>▶ Run</em> to execute the selected macro; click <em>■ Stop</em> to abort.</p>
 *
 * <h2>Opening the screen</h2>
 * <pre>
 *   /customcompat gui
 * </pre>
 */
public final class CustomCompatScreen extends Screen {

    // ── Tab constants ─────────────────────────────────────────────────────────
    private static final int TAB_ITEMS  = 0;
    private static final int TAB_MACROS = 1;

    // ── State ─────────────────────────────────────────────────────────────────
    private int activeTab = TAB_ITEMS;

    // Items tab
    private final List<String> recentIds = new ArrayList<>();
    private int itemScrollOffset = 0;
    private TextFieldWidget idField;

    // Macros tab
    private MacroEntry selectedMacro = null;
    private TextFieldWidget macroNameField;
    private TextFieldWidget stepField;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int COL_BG        = 0xC0000000;
    private static final int COL_ROW       = 0x30FFFFFF;
    private static final int COL_ROW_SEL   = 0x70FFFFFF;
    private static final int COL_ROW_HOVER = 0x50FFFFFF;
    private static final int COL_DIVIDER   = 0x80FFFFFF;
    private static final int COL_WHITE     = 0xFFFFFF;
    private static final int COL_GREY      = 0xAAAAAA;
    private static final int COL_DARK      = 0x666666;
    private static final int COL_GREEN     = 0x55FF55;

    // ── Geometry helpers ──────────────────────────────────────────────────────
    /** Y of the top of the scrollable items list. */
    private static final int ITEM_LIST_TOP_OFFSET = 92;
    /** Height of one row in the items list. */
    private static final int ITEM_ROW_H = 18;

    public CustomCompatScreen() {
        super(Text.literal("CustomCompat Manager"));
    }

    // =========================================================================
    // Screen lifecycle
    // =========================================================================

    @Override
    protected void init() {
        rebuildWidgets();
    }

    /**
     * Clears and re-creates all interactive widgets for the current tab.
     * Called on init and whenever a tab switch or list mutation occurs.
     */
    private void rebuildWidgets() {
        clearChildren();

        int cx = width / 2;

        // ── Common: tab buttons + close ──────────────────────────────────────
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Items"), b -> switchTab(TAB_ITEMS))
                        .dimensions(cx - 105, 13, 100, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Macros"), b -> switchTab(TAB_MACROS))
                        .dimensions(cx + 5, 13, 100, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("✕"), b -> close())
                        .dimensions(width - 24, 4, 20, 20).build());

        // ── Tab-specific ──────────────────────────────────────────────────────
        if (activeTab == TAB_ITEMS) {
            initItemsTab();
        } else {
            initMacrosTab();
        }
    }

    private void switchTab(int tab) {
        activeTab = tab;
        rebuildWidgets();
    }

    // =========================================================================
    // Items tab — widget init
    // =========================================================================

    private void initItemsTab() {
        int cx = width / 2;
        int y  = 40;

        // ID input field
        idField = new TextFieldWidget(textRenderer, cx - 150, y, 240, 20, Text.empty());
        idField.setMaxLength(128);
        idField.setPlaceholderText(Text.literal("nexo:crop_tomato_seed  or  mymod:item"));
        addDrawableChild(idField);

        // Action buttons — right side of the field
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Pickup"), b -> doPickup())
                        .dimensions(cx + 96, y, 58, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Follow"), b -> doFollow())
                        .dimensions(cx + 96, y + 22, 58, 20).build());

        // Farm / Stop — left of field
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Farm"), b -> BaritoneCompat.farm(0))
                        .dimensions(cx - 150, y + 22, 55, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Stop"), b -> BaritoneCompat.stop())
                        .dimensions(cx - 91, y + 22, 55, 20).build());

        // Scroll arrows for the recent-IDs list
        int listTop = ITEM_LIST_TOP_OFFSET;
        addDrawableChild(
                ButtonWidget.builder(Text.literal("▲"), b -> scrollItems(-1))
                        .dimensions(width - 22, listTop, 18, 14).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("▼"), b -> scrollItems(1))
                        .dimensions(width - 22, height - 26, 18, 14).build());
    }

    private void doPickup() {
        Identifier id = parseId();
        if (id == null) return;
        BaritoneCompat.pickupCustomItems(id);
        addRecent(id.toString());
        idField.setText("");
    }

    private void doFollow() {
        Identifier id = parseId();
        if (id == null) return;
        BaritoneCompat.followCustomEntity(id);
        addRecent(id.toString());
        idField.setText("");
    }

    private Identifier parseId() {
        if (idField == null) return null;
        String raw = idField.getText().trim();
        if (raw.isEmpty()) return null;
        return Identifier.tryParse(raw);
    }

    private void addRecent(String id) {
        recentIds.remove(id);
        recentIds.add(0, id);
        if (recentIds.size() > 64) recentIds.remove(recentIds.size() - 1);
    }

    private void scrollItems(int delta) {
        int max = Math.max(0, recentIds.size() - visibleItemRows());
        itemScrollOffset = Math.max(0, Math.min(max, itemScrollOffset + delta));
    }

    private int visibleItemRows() {
        return (height - ITEM_LIST_TOP_OFFSET - 30) / ITEM_ROW_H;
    }

    // =========================================================================
    // Macros tab — widget init
    // =========================================================================

    private void initMacrosTab() {
        MacroManager mgr = MacroManager.getInstance();
        int topY = 40;

        // Left-panel controls
        addDrawableChild(
                ButtonWidget.builder(Text.literal("+ New"), b -> {
                    MacroEntry e = new MacroEntry("Macro " + (mgr.getMacros().size() + 1));
                    mgr.addMacro(e);
                    selectedMacro = e;
                    rebuildWidgets();
                }).dimensions(10, topY, 70, 20).build());

        addDrawableChild(
                ButtonWidget.builder(Text.literal("Delete"), b -> {
                    if (selectedMacro != null) {
                        mgr.removeMacro(selectedMacro);
                        List<MacroEntry> list = mgr.getMacros();
                        selectedMacro = list.isEmpty() ? null : list.get(0);
                        rebuildWidgets();
                    }
                }).dimensions(84, topY, 60, 20).build());

        boolean running = mgr.isRunning();
        addDrawableChild(
                ButtonWidget.builder(Text.literal(running ? "■ Stop" : "▶ Run"), b -> {
                    if (mgr.isRunning()) {
                        mgr.stopMacro();
                    } else if (selectedMacro != null) {
                        mgr.startMacro(selectedMacro);
                    }
                    rebuildWidgets();
                }).dimensions(148, topY, 70, 20).build());

        // Right-panel editor (only when a macro is selected)
        if (selectedMacro != null) {
            int rx = width / 2 + 10;

            // Name field + save
            macroNameField = new TextFieldWidget(textRenderer, rx, topY, 155, 20, Text.empty());
            macroNameField.setMaxLength(64);
            macroNameField.setText(selectedMacro.getName());
            addDrawableChild(macroNameField);

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("Save"), b -> {
                        if (macroNameField != null) {
                            selectedMacro.setName(macroNameField.getText());
                            mgr.updateMacro(selectedMacro);
                            rebuildWidgets();
                        }
                    }).dimensions(rx + 159, topY, 50, 20).build());

            // Add-step field + buttons near the bottom
            int ay = height - 50;
            stepField = new TextFieldWidget(textRenderer, rx, ay, 175, 20, Text.empty());
            stepField.setMaxLength(128);
            stepField.setPlaceholderText(Text.literal("pickup nexo:<id>   wait 5   farm   stop"));
            addDrawableChild(stepField);

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("Add"), b -> {
                        if (stepField != null && !stepField.getText().isBlank()) {
                            selectedMacro.getSteps().add(stepField.getText().trim());
                            mgr.updateMacro(selectedMacro);
                            stepField.setText("");
                            // no full rebuild needed – just redraw
                        }
                    }).dimensions(rx + 179, ay, 40, 20).build());

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("Del Last"), b -> {
                        List<String> steps = selectedMacro.getSteps();
                        if (!steps.isEmpty()) {
                            steps.remove(steps.size() - 1);
                            mgr.updateMacro(selectedMacro);
                        }
                    }).dimensions(rx + 223, ay, 58, 20).build());
        }
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 3, COL_WHITE);

        // Active-tab underline
        int tabX = (activeTab == TAB_ITEMS ? width / 2 - 105 : width / 2 + 5);
        ctx.fill(tabX, 34, tabX + 100, 35, 0xFFFFFFFF);

        if (activeTab == TAB_ITEMS) {
            renderItemsTab(ctx, mouseX, mouseY);
        } else {
            renderMacrosTab(ctx, mouseX, mouseY);
        }
    }

    // ── Items tab rendering ───────────────────────────────────────────────────

    private void renderItemsTab(DrawContext ctx, int mouseX, int mouseY) {
        int lt = ITEM_LIST_TOP_OFFSET;

        // Section header
        ctx.drawHorizontalLine(10, width - 28, lt - 6, COL_DIVIDER);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Recent IDs  (right-click to remove):"),
                12, lt - 14, COL_GREY);

        if (recentIds.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Enter a Nexo/custom item ID above and press Pickup or Follow."),
                    width / 2, lt + 16, COL_DARK);
            return;
        }

        int visible = visibleItemRows();
        for (int i = itemScrollOffset; i < Math.min(recentIds.size(), itemScrollOffset + visible); i++) {
            String id = recentIds.get(i);
            int y = lt + (i - itemScrollOffset) * ITEM_ROW_H;
            boolean hover = mouseX >= 12 && mouseX < width - 28 && mouseY >= y && mouseY < y + ITEM_ROW_H;
            ctx.fill(12, y, width - 28, y + ITEM_ROW_H - 1, hover ? COL_ROW_HOVER : COL_ROW);
            ctx.drawTextWithShadow(textRenderer, Text.literal(id), 16, y + 4, COL_WHITE);
        }

        // Scroll indicator
        if (recentIds.size() > visible) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal((itemScrollOffset + 1) + "-"
                            + Math.min(recentIds.size(), itemScrollOffset + visible)
                            + " / " + recentIds.size()),
                    width - 90, height - 22, COL_DARK);
        }
    }

    // ── Macros tab rendering ──────────────────────────────────────────────────

    private void renderMacrosTab(DrawContext ctx, int mouseX, int mouseY) {
        MacroManager mgr = MacroManager.getInstance();
        List<MacroEntry> macros = mgr.getMacros();

        // Left panel background
        int lx = 10, ly = 66, lw = width / 2 - 15, lh = height - ly - 10;
        ctx.fill(lx, ly, lx + lw, ly + lh, COL_BG);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Macros:"), lx + 4, ly + 3, COL_GREY);

        int rowH = 20, innerY = ly + 16;
        for (int i = 0; i < macros.size(); i++) {
            MacroEntry m = macros.get(i);
            int y = innerY + i * rowH;
            if (y + rowH > ly + lh) break;
            boolean sel   = m == selectedMacro;
            boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= y && mouseY < y + rowH;
            ctx.fill(lx, y, lx + lw, y + rowH - 1,
                    sel ? COL_ROW_SEL : (hover ? COL_ROW_HOVER : COL_ROW));
            ctx.drawTextWithShadow(textRenderer, Text.literal(m.getName()),
                    lx + 6, y + 5, sel ? 0x000000 : COL_WHITE);
        }

        if (macros.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Click '+ New' to create a macro."),
                    lx + 6, innerY + 5, COL_DARK);
        }

        // Right panel
        int rx = width / 2 + 10, ry = 66, rw = width - rx - 10;
        ctx.fill(rx, ry, rx + rw, ry + lh, COL_BG);

        if (selectedMacro == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Select a macro on the left."),
                    rx + rw / 2, ry + lh / 2, COL_DARK);
        } else {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Steps:"), rx + 4, ry + 3, COL_GREY);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("pickup <id>  |  follow <id>  |  farm [n]  |  wait <s>  |  stop"),
                    rx + 4, ry + 13, COL_DARK);

            // Step list
            int sy = ry + 26;
            List<String> steps = selectedMacro.getSteps();
            int activeStep = (mgr.isRunning() && mgr.getActiveMacro() == selectedMacro)
                    ? mgr.getStepIndex() - 1 : -1;
            for (int i = 0; i < steps.size(); i++) {
                if (sy + 12 > ry + lh - 55) {
                    ctx.drawTextWithShadow(textRenderer,
                            Text.literal("… " + (steps.size() - i) + " more"),
                            rx + 4, sy, COL_DARK);
                    break;
                }
                int col = (i == activeStep) ? COL_GREEN : COL_WHITE;
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal((i + 1) + ". " + steps.get(i)), rx + 4, sy, col);
                sy += 12;
            }

            if (steps.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("No steps yet. Add one below."),
                        rx + 4, ry + 26, COL_DARK);
            }

            // Add-step label
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Add step:"), rx + 4, height - 65, COL_GREY);
        }

        // Running status bar
        if (mgr.isRunning() && mgr.getActiveMacro() != null) {
            MacroEntry act = mgr.getActiveMacro();
            String status = "▶ " + act.getName()
                    + "  step " + mgr.getStepIndex() + " / " + act.getSteps().size();
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status),
                    width / 2, height - 8, COL_GREEN);
        }
    }

    // =========================================================================
    // Input handling
    // =========================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Macro list: left-click to select
        if (activeTab == TAB_MACROS) {
            MacroManager mgr = MacroManager.getInstance();
            List<MacroEntry> macros = mgr.getMacros();
            int lx = 10, ly = 66 + 16, lw = width / 2 - 15, rowH = 20;
            for (int i = 0; i < macros.size(); i++) {
                int y = ly + i * rowH;
                if (y + rowH > height - 10) break;
                if (mx >= lx && mx < lx + lw && my >= y && my < y + rowH) {
                    selectedMacro = macros.get(i);
                    rebuildWidgets();
                    return true;
                }
            }
        }

        // Recent ID list: right-click to remove
        if (activeTab == TAB_ITEMS && button == 1) {
            int lt = ITEM_LIST_TOP_OFFSET;
            int visible = visibleItemRows();
            for (int i = itemScrollOffset; i < Math.min(recentIds.size(), itemScrollOffset + visible); i++) {
                int y = lt + (i - itemScrollOffset) * ITEM_ROW_H;
                if (mx >= 12 && mx < width - 28 && my >= y && my < y + ITEM_ROW_H) {
                    recentIds.remove(i);
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (activeTab == TAB_ITEMS) {
            scrollItems(vAmt < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean shouldPause() {
        // Keep the game running while the screen is open (world is still visible).
        return false;
    }
}
