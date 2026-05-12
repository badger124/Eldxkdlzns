package com.badger124.customcompat.gui;

import com.badger124.customcompat.compat.baritone.BaritoneCompat;
import com.badger124.customcompat.gui.farm.CustomFarmingHandler;
import com.badger124.customcompat.gui.farm.FarmProfile;
import com.badger124.customcompat.gui.farm.FarmProfileManager;
import com.badger124.customcompat.inspector.DataCollector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Main GUI for the Custom Content Compat mod.
 *
 * <h2>Tabs</h2>
 * <dl>
 *   <dt>Items</dt>
 *   <dd>Enter a Nexo/custom item ID and click Pickup / Follow / Farm / Stop to start the
 *       corresponding Baritone action. Recent IDs are kept in a scrollable list.</dd>
 *
 *   <dt>Macros</dt>
 *   <dd>Create named macros with sequential steps ({@code pickup}, {@code follow},
 *       {@code farm}, {@code farmcustom}, {@code wait}, {@code stop}).</dd>
 *
 *   <dt>Farm</dt>
 *   <dd>Configure custom note-block farmland profiles. Each profile maps note-pitch ranges
 *       to wet/dry farmland and specifies crop block IDs, mature ages, and seed Nexo IDs
 *       for automatic harvest-and-replant.</dd>
 * </dl>
 */
public final class CustomCompatScreen extends Screen {

    // ── Tab constants ─────────────────────────────────────────────────────────
    private static final int TAB_ITEMS     = 0;
    private static final int TAB_MACROS    = 1;
    private static final int TAB_FARM      = 2;
    private static final int TAB_INSPECTOR = 3;

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

    // Farm tab
    private FarmProfile selectedProfile = null;
    private TextFieldWidget farmNameField;
    private TextFieldWidget farmRangeField;
    private TextFieldWidget farmWetMinField;
    private TextFieldWidget farmWetMaxField;
    private TextFieldWidget cropLineField;

    // Inspector tab
    private int     inspectorScrollOffset = 0;
    private boolean inspectorShowEntities = false;
    private String  inspectorSelectedServer = "";

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
    private static final int COL_YELLOW    = 0xFFFF55;

    // ── Geometry helpers ──────────────────────────────────────────────────────
    private static final int ITEM_LIST_TOP_OFFSET = 92;
    private static final int ITEM_ROW_H = 18;

    public CustomCompatScreen() {
        super(Text.literal("커스텀컴팻 관리자"));
    }

    // =========================================================================
    // Screen lifecycle
    // =========================================================================

    @Override
    protected void init() {
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearChildren();

        int cx = width / 2;

        // ── Common: tab buttons + close ──────────────────────────────────────
        addDrawableChild(
                ButtonWidget.builder(Text.literal("아이템"), b -> switchTab(TAB_ITEMS))
                        .dimensions(cx - 160, 13, 72, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("매크로"), b -> switchTab(TAB_MACROS))
                        .dimensions(cx - 84, 13, 72, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("농사"), b -> switchTab(TAB_FARM))
                        .dimensions(cx - 8, 13, 72, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("분석기"), b -> switchTab(TAB_INSPECTOR))
                        .dimensions(cx + 68, 13, 82, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("✕"), b -> close())
                        .dimensions(width - 24, 4, 20, 20).build());

        switch (activeTab) {
            case TAB_ITEMS     -> initItemsTab();
            case TAB_MACROS    -> initMacrosTab();
            case TAB_FARM      -> initFarmTab();
            case TAB_INSPECTOR -> initInspectorTab();
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

        idField = new TextFieldWidget(textRenderer, cx - 150, y, 240, 20, Text.empty());
        idField.setMaxLength(128);
        idField.setPlaceholderText(Text.literal("nexo:crop_tomato_seed  또는  mymod:item"));
        addDrawableChild(idField);

        addDrawableChild(
                ButtonWidget.builder(Text.literal("수집"), b -> doPickup())
                        .dimensions(cx + 96, y, 58, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("추적"), b -> doFollow())
                        .dimensions(cx + 96, y + 22, 58, 20).build());

        addDrawableChild(
                ButtonWidget.builder(Text.literal("농사"), b -> BaritoneCompat.farm(0))
                        .dimensions(cx - 150, y + 22, 55, 20).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("중단"), b -> BaritoneCompat.stop())
                        .dimensions(cx - 91, y + 22, 55, 20).build());

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

        addDrawableChild(
                ButtonWidget.builder(Text.literal("+ 새로 만들기"), b -> {
                    MacroEntry e = new MacroEntry("매크로 " + (mgr.getMacros().size() + 1));
                    mgr.addMacro(e);
                    selectedMacro = e;
                    rebuildWidgets();
                }).dimensions(10, topY, 70, 20).build());

        addDrawableChild(
                ButtonWidget.builder(Text.literal("삭제"), b -> {
                    if (selectedMacro != null) {
                        mgr.removeMacro(selectedMacro);
                        List<MacroEntry> list = mgr.getMacros();
                        selectedMacro = list.isEmpty() ? null : list.get(0);
                        rebuildWidgets();
                    }
                }).dimensions(84, topY, 60, 20).build());

        boolean running = mgr.isRunning();
        addDrawableChild(
                ButtonWidget.builder(Text.literal(running ? "■ 중단" : "▶ 실행"), b -> {
                    if (mgr.isRunning()) {
                        mgr.stopMacro();
                    } else if (selectedMacro != null) {
                        mgr.startMacro(selectedMacro);
                    }
                    rebuildWidgets();
                }).dimensions(148, topY, 70, 20).build());

        if (selectedMacro != null) {
            int rx = width / 2 + 10;

            macroNameField = new TextFieldWidget(textRenderer, rx, topY, 155, 20, Text.empty());
            macroNameField.setMaxLength(64);
            macroNameField.setText(selectedMacro.getName());
            addDrawableChild(macroNameField);

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("저장"), b -> {
                        if (macroNameField != null) {
                            selectedMacro.setName(macroNameField.getText());
                            mgr.updateMacro(selectedMacro);
                            rebuildWidgets();
                        }
                    }).dimensions(rx + 159, topY, 50, 20).build());

            int ay = height - 50;
            stepField = new TextFieldWidget(textRenderer, rx, ay, 175, 20, Text.empty());
            stepField.setMaxLength(128);
            stepField.setPlaceholderText(Text.literal("pickup nexo:<id>   farmcustom <이름>   wait 5"));
            addDrawableChild(stepField);

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("추가"), b -> {
                        if (stepField != null && !stepField.getText().isBlank()) {
                            selectedMacro.getSteps().add(stepField.getText().trim());
                            mgr.updateMacro(selectedMacro);
                            stepField.setText("");
                        }
                    }).dimensions(rx + 179, ay, 40, 20).build());

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("마지막 삭제"), b -> {
                        List<String> steps = selectedMacro.getSteps();
                        if (!steps.isEmpty()) {
                            steps.remove(steps.size() - 1);
                            mgr.updateMacro(selectedMacro);
                        }
                    }).dimensions(rx + 223, ay, 58, 20).build());
        }
    }

    // =========================================================================
    // Farm tab — widget init
    // =========================================================================

    private void initFarmTab() {
        FarmProfileManager mgr = FarmProfileManager.getInstance();
        CustomFarmingHandler handler = CustomFarmingHandler.getInstance();
        int topY = 40;

        // Left-panel controls
        addDrawableChild(
                ButtonWidget.builder(Text.literal("+ 새로 만들기"), b -> {
                    FarmProfile p = new FarmProfile("농사 " + (mgr.getProfiles().size() + 1));
                    mgr.addProfile(p);
                    selectedProfile = p;
                    rebuildWidgets();
                }).dimensions(10, topY, 70, 20).build());

        addDrawableChild(
                ButtonWidget.builder(Text.literal("삭제"), b -> {
                    if (selectedProfile != null) {
                        if (handler.isActive() && handler.getActiveProfile() == selectedProfile) {
                            handler.stop();
                        }
                        mgr.removeProfile(selectedProfile);
                        List<FarmProfile> list = mgr.getProfiles();
                        selectedProfile = list.isEmpty() ? null : list.get(0);
                        rebuildWidgets();
                    }
                }).dimensions(84, topY, 60, 20).build());

        boolean farming = handler.isActive();
        addDrawableChild(
                ButtonWidget.builder(Text.literal(farming ? "■ 중단" : "▶ 시작"), b -> {
                    if (handler.isActive()) {
                        handler.stop();
                    } else if (selectedProfile != null) {
                        handler.start(selectedProfile);
                    }
                    rebuildWidgets();
                }).dimensions(148, topY, 80, 20).build());

        // Right-panel editor (only when a profile is selected)
        if (selectedProfile != null) {
            int rx = width / 2 + 10;

            // Name + Save
            farmNameField = new TextFieldWidget(textRenderer, rx, topY, 120, 20, Text.empty());
            farmNameField.setMaxLength(64);
            farmNameField.setText(selectedProfile.getName());
            addDrawableChild(farmNameField);

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("저장"), b -> saveFarmProfile()).dimensions(rx + 124, topY, 44, 20).build());

            // Range field
            farmRangeField = new TextFieldWidget(textRenderer, rx + 10, topY + 26, 40, 18, Text.empty());
            farmRangeField.setMaxLength(3);
            farmRangeField.setText(String.valueOf(selectedProfile.getRange()));
            addDrawableChild(farmRangeField);

            // Wet pitch min / max
            farmWetMinField = new TextFieldWidget(textRenderer, rx + 10, topY + 50, 30, 18, Text.empty());
            farmWetMinField.setMaxLength(2);
            farmWetMinField.setText(String.valueOf(selectedProfile.getWetPitchMin()));
            addDrawableChild(farmWetMinField);

            farmWetMaxField = new TextFieldWidget(textRenderer, rx + 50, topY + 50, 30, 18, Text.empty());
            farmWetMaxField.setMaxLength(2);
            farmWetMaxField.setText(String.valueOf(selectedProfile.getWetPitchMax()));
            addDrawableChild(farmWetMaxField);

            // Skip dry toggle
            addDrawableChild(
                    ButtonWidget.builder(
                            Text.literal("건조 건너뜀: " + (selectedProfile.isSkipDry() ? "ON" : "OFF")),
                            b -> {
                                selectedProfile.setSkipDry(!selectedProfile.isSkipDry());
                                mgr.update();
                                rebuildWidgets();
                            }).dimensions(rx + 90, topY + 48, 80, 20).build());

            // Crop line editor near bottom
            int ay = height - 50;
            cropLineField = new TextFieldWidget(textRenderer, rx, ay, 180, 20, Text.empty());
            cropLineField.setMaxLength(200);
            cropLineField.setPlaceholderText(Text.literal("<블록ID> <성숙나이> <nexo:씨앗ID>"));
            addDrawableChild(cropLineField);

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("추가"), b -> {
                        if (cropLineField != null && !cropLineField.getText().isBlank()) {
                            selectedProfile.getCropLines().add(cropLineField.getText().trim());
                            mgr.update();
                            cropLineField.setText("");
                        }
                    }).dimensions(rx + 184, ay, 38, 20).build());

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("삭제"), b -> {
                        List<String> lines = selectedProfile.getCropLines();
                        if (!lines.isEmpty()) {
                            lines.remove(lines.size() - 1);
                            mgr.update();
                        }
                    }).dimensions(rx + 226, ay, 38, 20).build());
        }
    }

    private void saveFarmProfile() {
        if (selectedProfile == null) return;
        FarmProfileManager mgr = FarmProfileManager.getInstance();
        if (farmNameField  != null) selectedProfile.setName(farmNameField.getText());
        if (farmRangeField != null) {
            try { selectedProfile.setRange(Integer.parseInt(farmRangeField.getText().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (farmWetMinField != null) {
            try { selectedProfile.setWetPitchMin(Integer.parseInt(farmWetMinField.getText().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (farmWetMaxField != null) {
            try { selectedProfile.setWetPitchMax(Integer.parseInt(farmWetMaxField.getText().trim())); }
            catch (NumberFormatException ignored) {}
        }
        mgr.update();
        rebuildWidgets();
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 3, COL_WHITE);

        // Active-tab underline
        int tabX = switch (activeTab) {
            case TAB_ITEMS     -> width / 2 - 160;
            case TAB_MACROS    -> width / 2 - 84;
            case TAB_FARM      -> width / 2 - 8;
            default            -> width / 2 + 68;
        };
        ctx.fill(tabX, 34, tabX + (activeTab == TAB_INSPECTOR ? 82 : 72), 35, 0xFFFFFFFF);

        switch (activeTab) {
            case TAB_ITEMS     -> renderItemsTab(ctx, mouseX, mouseY);
            case TAB_MACROS    -> renderMacrosTab(ctx, mouseX, mouseY);
            case TAB_FARM      -> renderFarmTab(ctx, mouseX, mouseY);
            case TAB_INSPECTOR -> renderInspectorTab(ctx, mouseX, mouseY);
        }
    }

    // ── Items tab rendering ───────────────────────────────────────────────────

    private void renderItemsTab(DrawContext ctx, int mouseX, int mouseY) {
        int lt = ITEM_LIST_TOP_OFFSET;

        ctx.drawHorizontalLine(10, width - 28, lt - 6, COL_DIVIDER);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("최근 ID  (우클릭으로 삭제):"),
                12, lt - 14, COL_GREY);

        if (recentIds.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("위에 Nexo/커스텀 아이템 ID를 입력하고 수집 또는 추적을 누르세요."),
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

        int lx = 10, ly = 66, lw = width / 2 - 15, lh = height - ly - 10;
        ctx.fill(lx, ly, lx + lw, ly + lh, COL_BG);
        ctx.drawTextWithShadow(textRenderer, Text.literal("매크로:"), lx + 4, ly + 3, COL_GREY);

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
                    Text.literal("'+ 새로 만들기'를 눌러 매크로를 생성하세요."),
                    lx + 6, innerY + 5, COL_DARK);
        }

        int rx = width / 2 + 10, ry = 66, rw = width - rx - 10;
        ctx.fill(rx, ry, rx + rw, ry + lh, COL_BG);

        if (selectedMacro == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("왼쪽에서 매크로를 선택하세요."),
                    rx + rw / 2, ry + lh / 2, COL_DARK);
        } else {
            ctx.drawTextWithShadow(textRenderer, Text.literal("스텝:"), rx + 4, ry + 3, COL_GREY);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("pickup <id>  follow <id>  farm [n]  farmcustom <이름>  wait <초>  stop"),
                    rx + 4, ry + 13, COL_DARK);

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
                        Text.literal("스텝이 없습니다. 아래에 추가하세요."),
                        rx + 4, ry + 26, COL_DARK);
            }

            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("스텝 추가:"), rx + 4, height - 65, COL_GREY);
        }

        if (mgr.isRunning() && mgr.getActiveMacro() != null) {
            MacroEntry act = mgr.getActiveMacro();
            String status = "▶ " + act.getName()
                    + "  스텝 " + mgr.getStepIndex() + " / " + act.getSteps().size();
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status),
                    width / 2, height - 8, COL_GREEN);
        }
    }

    // ── Farm tab rendering ────────────────────────────────────────────────────

    private void renderFarmTab(DrawContext ctx, int mouseX, int mouseY) {
        FarmProfileManager mgr = FarmProfileManager.getInstance();
        CustomFarmingHandler handler = CustomFarmingHandler.getInstance();
        List<FarmProfile> profiles = mgr.getProfiles();

        int lx = 10, ly = 66, lw = width / 2 - 15, lh = height - ly - 10;
        ctx.fill(lx, ly, lx + lw, ly + lh, COL_BG);
        ctx.drawTextWithShadow(textRenderer, Text.literal("농사 프로파일:"), lx + 4, ly + 3, COL_GREY);

        int rowH = 20, innerY = ly + 16;
        for (int i = 0; i < profiles.size(); i++) {
            FarmProfile p = profiles.get(i);
            int y = innerY + i * rowH;
            if (y + rowH > ly + lh) break;
            boolean sel   = p == selectedProfile;
            boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= y && mouseY < y + rowH;
            boolean active = handler.isActive() && handler.getActiveProfile() == p;
            ctx.fill(lx, y, lx + lw, y + rowH - 1,
                    sel ? COL_ROW_SEL : (hover ? COL_ROW_HOVER : COL_ROW));
            String label = (active ? "▶ " : "") + p.getName();
            ctx.drawTextWithShadow(textRenderer, Text.literal(label),
                    lx + 6, y + 5, active ? COL_GREEN : (sel ? 0x000000 : COL_WHITE));
        }

        if (profiles.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("'+ 새로 만들기'를 눌러 농사 프로파일을 생성하세요."),
                    lx + 6, innerY + 5, COL_DARK);
        }

        // Right panel
        int rx = width / 2 + 10, ry = 66, rw = width - rx - 10;
        ctx.fill(rx, ry, rx + rw, ry + lh, COL_BG);

        if (selectedProfile == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("왼쪽에서 프로파일을 선택하세요."),
                    rx + rw / 2, ry + lh / 2, COL_DARK);
        } else {
            int topY = 40;
            // Labels for fields
            ctx.drawTextWithShadow(textRenderer, Text.literal("범위:"), rx, topY + 29, COL_GREY);
            ctx.drawTextWithShadow(textRenderer, Text.literal("젖은 경작지 음높이:"), rx, topY + 53, COL_GREY);
            ctx.drawTextWithShadow(textRenderer, Text.literal("–"), rx + 44, topY + 53, COL_GREY);

            // Crop mappings header
            ctx.drawHorizontalLine(rx, rx + rw - 2, ry + 80, COL_DIVIDER);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("작물  (블록ID  성숙나이  nexo:씨앗ID):"),
                    rx + 2, ry + 83, COL_GREY);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("성숙나이 -1은 모든 나이에서 수확. # = 주석."),
                    rx + 2, ry + 93, COL_DARK);

            // Crop lines list
            List<String> cropLines = selectedProfile.getCropLines();
            int sy = ry + 104;
            for (int i = 0; i < cropLines.size(); i++) {
                if (sy + 12 > height - 58) {
                    ctx.drawTextWithShadow(textRenderer,
                            Text.literal("… " + (cropLines.size() - i) + " more"),
                            rx + 2, sy, COL_DARK);
                    break;
                }
                ctx.drawTextWithShadow(textRenderer, Text.literal((i + 1) + ". " + cropLines.get(i)),
                        rx + 2, sy, COL_WHITE);
                sy += 12;
            }
            if (cropLines.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("작물 매핑이 없습니다. 아래에 추가하세요."),
                        rx + 2, ry + 104, COL_DARK);
            }

            // Add-line label
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("작물 매핑 추가:"), rx + 2, height - 65, COL_GREY);
        }

        // Status bar
        if (handler.isActive() && handler.getActiveProfile() != null) {
            String status = "▶ 농사 중: " + handler.getActiveProfile().getName()
                    + "  (음 범위 " + handler.getActiveProfile().getWetPitchMin()
                    + "-" + handler.getActiveProfile().getWetPitchMax() + " = 젖은 경작지)";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status),
                    width / 2, height - 8, COL_GREEN);
        }
    }

    // =========================================================================
    // Inspector tab — widget init
    // =========================================================================

    private void initInspectorTab() {
        DataCollector dc = DataCollector.getInstance();
        int topY = 40;

        // Scan button
        addDrawableChild(
                ButtonWidget.builder(Text.literal("⟳ 지금 스캔"), b -> {
                    dc.scan();
                    inspectorSelectedServer = dc.getLastScanServer();
                    inspectorScrollOffset = 0;
                    rebuildWidgets();
                }).dimensions(10, topY, 90, 20).build());

        // Toggle items/entities view
        addDrawableChild(
                ButtonWidget.builder(
                        Text.literal(inspectorShowEntities ? "표시: 엔티티" : "표시: 아이템"),
                        b -> {
                            inspectorShowEntities = !inspectorShowEntities;
                            inspectorScrollOffset = 0;
                            rebuildWidgets();
                        }).dimensions(104, topY, 110, 20).build());

        // Clear button
        addDrawableChild(
                ButtonWidget.builder(Text.literal("전체 삭제"), b -> {
                    dc.clearAll();
                    inspectorScrollOffset = 0;
                    rebuildWidgets();
                }).dimensions(218, topY, 75, 20).build());

        // Scroll arrows
        addDrawableChild(
                ButtonWidget.builder(Text.literal("▲"), b -> scrollInspector(-1))
                        .dimensions(width - 22, 68, 18, 14).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("▼"), b -> scrollInspector(1))
                        .dimensions(width - 22, height - 26, 18, 14).build());

        // Server selector buttons (left panel)
        Set<String> servers = dc.getKnownServers();
        int sy = 68;
        for (String s : servers) {
            final String key = s;
            String label = s.length() > 18 ? s.substring(0, 15) + "…" : s;
            addDrawableChild(
                    ButtonWidget.builder(Text.literal(label), b -> {
                        inspectorSelectedServer = key;
                        inspectorScrollOffset = 0;
                        rebuildWidgets();
                    }).dimensions(10, sy, 120, 16).build());
            sy += 18;
            if (sy + 18 > height - 10) break;
        }
    }

    private void scrollInspector(int delta) {
        DataCollector dc = DataCollector.getInstance();
        List<String> lines = getInspectorLines(dc);
        int max = Math.max(0, lines.size() - inspectorVisibleRows());
        inspectorScrollOffset = Math.max(0, Math.min(max, inspectorScrollOffset + delta));
    }

    private int inspectorVisibleRows() {
        return (height - 68 - 30) / 12;
    }

    private List<String> getInspectorLines(DataCollector dc) {
        if (inspectorSelectedServer.isEmpty()) return Collections.emptyList();
        return inspectorShowEntities
                ? dc.getEntitiesForServer(inspectorSelectedServer)
                : dc.getItemsForServer(inspectorSelectedServer);
    }

    // =========================================================================
    // Inspector tab — rendering
    // =========================================================================

    private void renderInspectorTab(DrawContext ctx, int mouseX, int mouseY) {
        DataCollector dc = DataCollector.getInstance();
        int lx = 10, ly = 66, lw = 134, lh = height - ly - 10;
        ctx.fill(lx, ly, lx + lw, ly + lh, COL_BG);
        ctx.drawTextWithShadow(textRenderer, Text.literal("서버:"), lx + 4, ly + 3, COL_GREY);

        Set<String> servers = dc.getKnownServers();
        if (servers.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("데이터 없음 — 지금 스캔을 누르세요."),
                    lx + 4, ly + 16, COL_DARK);
        } else {
            int sy = ly + 16;
            for (String s : servers) {
                boolean sel = s.equals(inspectorSelectedServer);
                ctx.fill(lx, sy, lx + lw, sy + 15, sel ? COL_ROW_SEL : COL_ROW);
                String label = s.length() > 16 ? s.substring(0, 13) + "…" : s;
                ctx.drawTextWithShadow(textRenderer, Text.literal(label),
                        lx + 4, sy + 3, sel ? 0x000000 : COL_WHITE);
                sy += 17;
                if (sy + 17 > ly + lh) break;
            }
        }

        // Right panel — list of items or entities
        int rx = lx + lw + 10, ry = 66, rw = width - rx - 10;
        ctx.fill(rx, ry, rx + rw, ry + (height - ry - 10), COL_BG);

        if (inspectorSelectedServer.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("왼쪽에서 서버를 선택하거나 먼저 스캔하세요."),
                    rx + rw / 2, ry + (height - ry - 10) / 2, COL_DARK);
        } else {
            String kind = inspectorShowEntities ? "엔티티" : "아이템";
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(kind + "  (" + inspectorSelectedServer + "):"),
                    rx + 4, ry + 3, COL_GREY);

            List<String> lines = getInspectorLines(dc);
            if (lines.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("이 서버에 대한 " + kind + " 데이터가 없습니다."),
                        rx + 4, ry + 16, COL_DARK);
            } else {
                int visible = inspectorVisibleRows();
                int ty = ry + 16;
                for (int i = inspectorScrollOffset;
                     i < Math.min(lines.size(), inspectorScrollOffset + visible); i++) {
                    ctx.drawTextWithShadow(textRenderer, Text.literal(lines.get(i)), rx + 4, ty, COL_WHITE);
                    ty += 12;
                }
                if (lines.size() > visible) {
                    ctx.drawTextWithShadow(textRenderer,
                            Text.literal((inspectorScrollOffset + 1)
                                    + "-" + Math.min(lines.size(), inspectorScrollOffset + visible)
                                    + " / " + lines.size()),
                            width - 90, height - 22, COL_DARK);
                }
            }
        }

        // Last-scan status
        String lastServer = dc.getLastScanServer();
        if (!lastServer.isEmpty()) {
            int itemCount   = dc.getLastItems().size();
            int entityCount = dc.getLastEntities().size();
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("마지막 스캔: " + itemCount + " 아이템, " + entityCount
                            + " 엔티티  →  config/customcompat_inspector.json"),
                    10, height - 8, COL_DARK);
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

        // Farm profile list: left-click to select
        if (activeTab == TAB_FARM) {
            FarmProfileManager mgr = FarmProfileManager.getInstance();
            List<FarmProfile> profiles = mgr.getProfiles();
            int lx = 10, ly = 66 + 16, lw = width / 2 - 15, rowH = 20;
            for (int i = 0; i < profiles.size(); i++) {
                int y = ly + i * rowH;
                if (y + rowH > height - 10) break;
                if (mx >= lx && mx < lx + lw && my >= y && my < y + rowH) {
                    selectedProfile = profiles.get(i);
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
        if (activeTab == TAB_INSPECTOR) {
            scrollInspector(vAmt < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
