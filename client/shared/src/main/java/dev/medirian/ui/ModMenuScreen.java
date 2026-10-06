package dev.medirian.ui;

import dev.medirian.core.BuildInfo;
import dev.medirian.core.Medirian;
import dev.medirian.i18n.I18n;
import dev.medirian.input.Key;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.render.Anim;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Pixel;
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.ActionSetting;
import dev.medirian.setting.Setting;
import dev.medirian.ui.widget.Button;
import dev.medirian.ui.widget.KeybindButton;
import dev.medirian.ui.widget.ScrollState;
import dev.medirian.ui.widget.TextField;

import java.util.ArrayList;
import java.util.List;

/**
 * The Medirian mod menu: a window with category tabs on its left edge (like the creative
 * inventory), a grid of module tiles (icon, name and an ON/OFF lamp), the selected module's page
 * on the right and a hotbar of quick actions at the bottom. Opened with Right Shift by default.
 *
 * <p>Clicking a tile opens the module's page; clicking its lamp (or right-clicking the tile)
 * switches the module on or off. Typing anywhere searches.
 */
public final class ModMenuScreen extends MedirianScreen {

    private static final int TAB_W = 26;
    private static final int TAB_H = 24;
    private static final int TILE_W = 66;
    private static final int TILE_H = 74;
    private static final int TILE_GAP = 6;
    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 28;

    // remembered between openings
    private static Category lastCategory;
    private static String lastModuleId;

    /** The menu key closes the menu only after the key that opened it was released (no key-repeat close). */
    private boolean closeKeyArmed;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int columns;
    private int detailX;
    private int detailW;

    private Category category = lastCategory;
    private Module selected;
    private String query = "";
    private List<Module> visibleModules = new ArrayList<Module>();
    private final List<Category> tabs = new ArrayList<Category>();
    private final ScrollState gridScroll = new ScrollState();
    private final Anim open = new Anim(0f, 14f);

    private TextField search;
    private SettingsList details;
    private Button powerButton;
    private KeybindButton keybindButton;
    private Button hudButton;
    private Button resetButton;
    private final List<Button> hotbar = new ArrayList<Button>();
    private Button closeButton;

    public ModMenuScreen(MedirianScreen parent) {
        super(parent);
        if (lastModuleId != null) {
            selected = Medirian.get().modules().get(lastModuleId);
        }
    }

    @Override
    protected void init() {
        pw = Math.min(width - TAB_W - 24, 640);
        ph = Math.min(height - 20, 380);
        px = (width - pw + TAB_W) / 2;
        py = (height - ph) / 2;
        detailW = Math.max(176, Math.min(232, Math.round(pw * 0.36f)));
        detailX = px + pw - 8 - detailW;
        gridX = px + 8;
        gridY = py + HEADER_H + 16;
        gridW = detailX - 8 - gridX;
        gridH = py + ph - FOOTER_H - 6 - gridY;
        columns = Math.max(1, (gridW + TILE_GAP) / (TILE_W + TILE_GAP));

        tabs.clear();
        tabs.add(null);
        for (Category c : Category.values()) {
            if (!Medirian.get().modules().byCategory(c).isEmpty()) {
                tabs.add(c);
            }
        }

        if (search == null) {
            search = new TextField(this, query, 32)
                    .placeholder(I18n.tr("ui.modmenu.search", "Search modules..."))
                    .onChange(text -> {
                        query = text;
                        refreshList();
                    });
        }
        int searchW = Math.min(170, pw / 3);
        search.bounds(px + pw - 8 - 18 - 6 - searchW, py + 7, searchW, 16);
        closeButton = new Button("", Button.Style.SECONDARY, this::close).icon("close").bounds(px + pw - 8 - 18, py + 7, 18, 16);

        hotbar.clear();
        hotbar.add(new Button(I18n.tr("ui.hudEditor", "Edit HUD"), Button.Style.SECONDARY,
                () -> Medirian.get().platform().openScreen(new HudEditorScreen(this))).icon("hudedit"));
        if (Medirian.get().cosmetics().available()) {
            hotbar.add(new Button(I18n.tr("ui.cosmetics", "Cosmetics"), Button.Style.SECONDARY,
                    () -> Medirian.get().platform().openScreen(new CosmeticsScreen(this))).icon("cosmetics"));
        }
        hotbar.add(new Button(I18n.tr("ui.waypoints", "Waypoints"), Button.Style.SECONDARY,
                () -> Medirian.get().platform().openScreen(new WaypointsScreen(this))).icon("waypoints"));
        hotbar.add(new Button(I18n.tr("ui.settings", "Settings"), Button.Style.SECONDARY,
                () -> Medirian.get().platform().openScreen(new SettingsScreen(this))).icon("settings"));

        refreshList();
        select(selected);
    }

    private void refreshList() {
        visibleModules = Medirian.get().modules().search(query, query.isEmpty() ? category : null);
        gridScroll.reset();
    }

    private void select(Module module) {
        selected = module;
        lastModuleId = module == null ? null : module.id();
        details = new SettingsList(this);
        if (module == null) {
            return;
        }
        powerButton = new Button(() -> module.isEnabled() ? I18n.tr("ui.modmenu.on", "Enabled") : I18n.tr("ui.modmenu.off", "Disabled"),
                Button.Style.SECONDARY, module::toggle);
        keybindButton = new KeybindButton(this, module.keybind());
        hudButton = module.hasHud()
                ? new Button(I18n.tr("ui.modmenu.position", "Position"), Button.Style.SECONDARY,
                        () -> Medirian.get().platform().openScreen(new HudEditorScreen(this, module))).icon("hudedit")
                : null;
        resetButton = new Button(I18n.tr("ui.modmenu.reset", "Reset"), Button.Style.GHOST, () -> {
            for (Setting<?> setting : module.settings()) {
                if (!(setting instanceof ActionSetting)) {
                    setting.reset();
                }
            }
            module.keybind().reset();
        });
        details.addSettings(module.settings());
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void render(Gfx g, float mx, float my, float delta) {
        if (!closeKeyArmed && !Medirian.get().platform().input().isKeyDown(Medirian.get().settings().modMenuKey.key())) {
            closeKeyArmed = true;
        }
        Theme t = Theme.current();
        renderBackdrop(g);
        float appear = open.target(1f).get();
        int offset = Math.round((1f - appear) * 10f);
        g.push();
        g.translate(0, offset);
        float my2 = my - offset;

        // shadow, tabs behind the window, the window, then the selected tab in front of it
        g.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x50000000);
        renderTabs(g, t, mx, my2, false);
        Pixel.panel(g, px, py, pw, ph);
        renderTabs(g, t, mx, my2, true);

        renderHeader(g, t, mx, my2);
        renderGrid(g, t, mx, my2);
        renderDetails(g, t, mx, my2);
        renderFooter(g, t, mx, my2);
        renderTabTooltip(g, t, mx, my2);
        g.pop();
    }

    private void renderHeader(Gfx g, Theme t, float mx, float my) {
        g.texture("gui/mark.png", px + 8, py + 6, 32, 18, 0xFFFFFFFF);
        Pixel.text(g, "MEDIRIAN CLIENT", px + 46, py + 7, t.text);
        String section = query.isEmpty()
                ? (category == null ? I18n.tr("category.all", "All") : category.displayName())
                : I18n.tr("ui.modmenu.results", "Search results");
        g.text(section, px + 46, py + 17, t.pumpkinLight, false);
        search.render(g, mx, my);
        Pixel.icon(g, "search", search.x + search.w - 17, search.y, 1, 0xFFFFFFFF);
        closeButton.render(g, mx, my);
        Pixel.groove(g, px + 4, py + HEADER_H, pw - 8);
    }

    private int tabY(int index) {
        return py + HEADER_H + 4 + index * (TAB_H + 3);
    }

    private void renderTabs(Gfx g, Theme t, float mx, float my, boolean selectedOnly) {
        for (int i = 0; i < tabs.size(); i++) {
            Category c = tabs.get(i);
            boolean active = query.isEmpty() && category == c;
            if (active != selectedOnly) {
                continue;
            }
            int y = tabY(i);
            int x = px - TAB_W + (active ? 0 : 3);
            boolean hovered = mx >= x && mx < px && my >= y && my < y + TAB_H;
            if (active) {
                // merges into the window: no outline on its right side
                Pixel.frame(g, x, y, TAB_W + 3, TAB_H, t.panel, t.panelLight, t.panelDark);
                g.fill(px, y + 1, px + 2, y + TAB_H - 1, t.panel);
                g.fill(px - 2, y + TAB_H - 2, px + 2, y + TAB_H - 1, t.panelDark);
            } else {
                Pixel.frame(g, x, y, TAB_W, TAB_H, hovered ? t.surfaceLight : t.surfaceDark, hovered ? 0xFF4C3870 : t.surface, t.panelDark);
            }
            String icon = c == null ? "cat-all" : "cat-" + c.name().toLowerCase();
            Pixel.icon(g, icon, x + (active ? 6 : 5), y + 4, 1, active || hovered ? 0xFFFFFFFF : 0xB0FFFFFF);
        }
    }

    private void renderTabTooltip(Gfx g, Theme t, float mx, float my) {
        for (int i = 0; i < tabs.size(); i++) {
            int y = tabY(i);
            if (mx >= px - TAB_W && mx < px && my >= y && my < y + TAB_H) {
                Category c = tabs.get(i);
                String label = c == null ? I18n.tr("category.all", "All") : c.displayName();
                int count = c == null ? Medirian.get().modules().all().size() : Medirian.get().modules().byCategory(c).size();
                tooltip(g, t, label + "  §7" + count, Math.round(mx) + 8, Math.round(my) - 4);
                return;
            }
        }
    }

    private static void tooltip(Gfx g, Theme t, String text, int x, int y) {
        int w = g.textWidth(text) + 8;
        Pixel.frame(g, x, y, w, 14, t.panelDark, t.accentDark, t.panelDark);
        g.text(text, x + 4, y + 3, t.text, false);
    }

    private void renderGrid(Gfx g, Theme t, float mx, float my) {
        int enabled = 0;
        for (Module module : visibleModules) {
            if (module.isEnabled()) {
                enabled++;
            }
        }
        String count = I18n.tr("ui.modmenu.count", "{0} modules", visibleModules.size());
        g.text(count, gridX + 1, py + HEADER_H + 5, t.textMuted, false);
        String on = I18n.tr("ui.modmenu.enabledCount", "{0} on", enabled);
        int onW = g.textWidth(on);
        g.fill(gridX + gridW - onW - 7, py + HEADER_H + 7, gridX + gridW - onW - 3, py + HEADER_H + 11, t.pumpkin);
        g.text(on, gridX + gridW - onW, py + HEADER_H + 5, t.textDim, false);

        if (visibleModules.isEmpty()) {
            String empty = I18n.tr("ui.modmenu.empty", "No modules match your search");
            g.text(empty, gridX + (gridW - g.textWidth(empty)) / 2f, gridY + 30, t.textMuted, false);
            Pixel.icon(g, "cat-misc", gridX + gridW / 2f - 16, gridY + 44, 2, 0x90FFFFFF);
            return;
        }
        int rows = (visibleModules.size() + columns - 1) / columns;
        gridScroll.setBounds(rows * (TILE_H + TILE_GAP) - TILE_GAP + 4, gridH);
        int scroll = Math.round(gridScroll.offset());
        int used = columns * TILE_W + (columns - 1) * TILE_GAP;
        int left = gridX + (gridW - used) / 2;
        boolean inside = mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH;
        g.enableScissor(gridX - 2, gridY - 2, gridX + gridW + 2, gridY + gridH);
        for (int i = 0; i < visibleModules.size(); i++) {
            int x = left + (i % columns) * (TILE_W + TILE_GAP);
            int y = gridY + 2 + (i / columns) * (TILE_H + TILE_GAP) - scroll;
            if (y + TILE_H < gridY || y > gridY + gridH) {
                continue;
            }
            renderTile(g, t, visibleModules.get(i), x, y, inside ? mx : -1, inside ? my : -1);
        }
        g.disableScissor();
        gridScroll.renderBar(g, gridX + gridW + 2, gridY);
    }

    private void renderTile(Gfx g, Theme t, Module module, int x, int y, float mx, float my) {
        boolean on = module.isEnabled();
        boolean hovered = mx >= x && my >= y && mx < x + TILE_W && my < y + TILE_H;
        boolean active = module == selected;
        int fill = hovered ? t.surfaceLight : on ? 0xFF2E1F3C : t.surface;
        Pixel.frame(g, x, y, TILE_W, TILE_H, fill, hovered ? 0xFF4C3870 : t.surfaceLight, t.surfaceDark);
        if (on) {
            // warm candlelight behind the icon
            g.fill(x + 15, y + 6, x + TILE_W - 15, y + 36, Colors.withAlpha(t.pumpkin, 0x18));
            g.fill(x + 19, y + 4, x + TILE_W - 19, y + 38, Colors.withAlpha(t.pumpkin, 0x14));
        }
        Pixel.icon(g, Pixel.moduleIcon(module.id()), x + (TILE_W - 32) / 2, y + 4, 2, on ? 0xFFFFFFFF : 0x8CFFFFFF);
        // the name on one or two lines
        List<String> lines = UiDraw.wrap(g, module.displayName(), TILE_W - 6);
        int color = on ? t.text : t.textDim;
        if (lines.size() == 1) {
            String name = UiDraw.ellipsize(g, lines.get(0), TILE_W - 6);
            g.text(name, x + (TILE_W - g.textWidth(name)) / 2f, y + 43, color, false);
        } else {
            String first = UiDraw.ellipsize(g, lines.get(0), TILE_W - 6);
            StringBuilder rest = new StringBuilder(lines.get(1));
            for (int i = 2; i < lines.size(); i++) {
                rest.append(' ').append(lines.get(i));
            }
            String second = UiDraw.ellipsize(g, rest.toString(), TILE_W - 6);
            g.text(first, x + (TILE_W - g.textWidth(first)) / 2f, y + 38, color, false);
            g.text(second, x + (TILE_W - g.textWidth(second)) / 2f, y + 48, color, false);
        }
        renderLamp(g, t, module, x + 4, y + TILE_H - 14, TILE_W - 8, 11, mx, my);
        if (active) {
            Pixel.highlight(g, x, y, TILE_W, TILE_H, t.pumpkinLight);
        } else if (hovered) {
            Pixel.highlight(g, x, y, TILE_W, TILE_H, t.accentHover);
        }
    }

    /** The ON/OFF lamp of a tile: a lit pumpkin bar or a dark well. */
    private void renderLamp(Gfx g, Theme t, Module module, int x, int y, int w, int h, float mx, float my) {
        boolean hovered = mx >= x && my >= y && mx < x + w && my < y + h;
        String label;
        int color;
        if (module.isLocked()) {
            Pixel.inset(g, x, y, w, h, t.inset);
            label = I18n.tr("ui.modmenu.locked", "Locked");
            color = t.textMuted;
        } else if (module.isEnabled()) {
            Pixel.frame(g, x, y, w, h, hovered ? t.pumpkinLight : t.pumpkin, t.ember, t.pumpkinDark);
            label = I18n.tr("ui.modmenu.lampOn", "ON");
            color = t.onPumpkin;
        } else {
            Pixel.inset(g, x, y, w, h, hovered ? t.surfaceDark : t.inset);
            label = I18n.tr("ui.modmenu.lampOff", "OFF");
            color = hovered ? t.textDim : t.textMuted;
        }
        label = UiDraw.ellipsize(g, label, w - 4);
        g.text(label, x + (w - g.textWidth(label)) / 2f, y + 2, color, false);
    }

    private void renderDetails(Gfx g, Theme t, float mx, float my) {
        int top = py + HEADER_H + 6;
        int bottom = py + ph - FOOTER_H - 4;
        Pixel.inset(g, detailX, top, detailW, bottom - top, t.panelDark);
        int x = detailX + 6;
        int w = detailW - 12;
        if (selected == null) {
            renderOverview(g, t, x, top + 8, w);
            return;
        }
        int y = top + 6;
        // icon slot, name, category
        Pixel.inset(g, x, y, 38, 38, t.inset);
        Pixel.icon(g, Pixel.moduleIcon(selected.id()), x + 3, y + 3, 2, 0xFFFFFFFF);
        int textX = x + 44;
        Pixel.text(g, UiDraw.ellipsize(g, selected.displayName(), w - 44), textX, y + 4, t.text);
        String cat = selected.category().displayName();
        Pixel.icon(g, "cat-" + selected.category().name().toLowerCase(), textX - 1, y + 15, 1, 0xFFFFFFFF);
        g.text(cat, textX + 17, y + 19, t.textMuted, false);
        y += 44;
        List<String> lines = UiDraw.wrap(g, selected.displayDescription(), w);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.text(lines.get(i), x, y, t.textDim, false);
            y += 10;
        }
        y += 4;
        if (selected.isLocked()) {
            Pixel.inset(g, x, y, w, 16, t.inset);
            g.text(UiDraw.ellipsize(g, selected.lockReason(), w - 8), x + 4, y + 4, t.warning, false);
        } else {
            powerButton.style(selected.isEnabled() ? Button.Style.PRIMARY : Button.Style.SECONDARY);
            powerButton.bounds(x, y, w - 76, 16);
            powerButton.render(g, mx, my);
            keybindButton.bounds(x + w - 72, y, 72, 16);
            keybindButton.render(g, mx, my);
        }
        y += 20;
        if (hudButton != null) {
            hudButton.bounds(x, y, (w - 4) / 2f, 16);
            hudButton.render(g, mx, my);
        }
        float resetW = Math.max(56, g.textWidth(I18n.tr("ui.modmenu.reset", "Reset")) + 14);
        resetButton.bounds(x + w - resetW, y, resetW, 16);
        resetButton.render(g, mx, my);
        y += 22;
        Pixel.groove(g, x, y, w);
        y += 4;
        details.bounds(x, y, w + 4, bottom - 4 - y);
        if (details.isEmpty()) {
            String none = I18n.tr("ui.modmenu.noSettings", "Nothing more to set up here.");
            g.text(UiDraw.ellipsize(g, none, w), x, y + 6, t.textMuted, false);
        } else {
            details.render(g, mx, my);
        }
    }

    private void renderOverview(Gfx g, Theme t, int x, int y, int w) {
        g.texture("gui/mark.png", x + (w - 64) / 2, y + 4, 64, 36, 0xFFFFFFFF);
        y += 48;
        String title = I18n.tr("ui.modmenu.welcome", "Welcome to Medirian Client");
        for (String line : UiDraw.wrap(g, title, w)) {
            Pixel.text(g, line, x + (w - g.textWidth(line)) / 2f, y, t.text);
            y += 11;
        }
        y += 4;
        String hint = I18n.tr("ui.modmenu.hint", "Pick a module to see its settings. Click its lamp to switch it on or off.");
        for (String line : UiDraw.wrap(g, hint, w)) {
            g.text(line, x + (w - g.textWidth(line)) / 2f, y, t.textDim, false);
            y += 10;
        }
    }

    private void renderFooter(Gfx g, Theme t, float mx, float my) {
        int y = py + ph - FOOTER_H;
        Pixel.groove(g, px + 4, y - 2, pw - 8);
        int x = px + 8;
        for (Button button : hotbar) {
            float bw = button.preferredWidth(g, 60);
            button.bounds(x, y + 4, bw, 18);
            button.render(g, mx, my);
            x += Math.round(bw) + 4;
        }
        String version = "v" + BuildInfo.VERSION + " · " + Medirian.get().platform().minecraftVersion();
        int vw = g.textWidth(version);
        if (px + pw - 8 - vw > x + 4) {
            g.text(version, px + pw - 8 - vw, y + 9, t.textMuted, false);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(float mx, float my, int button) {
        float y0 = my;
        if (search.mouseClicked(mx, y0, button) || closeButton.mouseClicked(mx, y0, button)) {
            return true;
        }
        for (Button b : hotbar) {
            if (b.mouseClicked(mx, y0, button)) {
                return true;
            }
        }
        // category tabs
        if (mx >= px - TAB_W && mx < px) {
            for (int i = 0; i < tabs.size(); i++) {
                int y = tabY(i);
                if (my >= y && my < y + TAB_H) {
                    setCategory(tabs.get(i));
                    return true;
                }
            }
        }
        // tiles
        if (mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH && !visibleModules.isEmpty()) {
            int used = columns * TILE_W + (columns - 1) * TILE_GAP;
            int left = gridX + (gridW - used) / 2;
            int scroll = Math.round(gridScroll.offset());
            for (int i = 0; i < visibleModules.size(); i++) {
                int x = left + (i % columns) * (TILE_W + TILE_GAP);
                int y = gridY + 2 + (i / columns) * (TILE_H + TILE_GAP) - scroll;
                if (mx < x || my < y || mx >= x + TILE_W || my >= y + TILE_H) {
                    continue;
                }
                Module module = visibleModules.get(i);
                boolean onLamp = my >= y + TILE_H - 14;
                if (button == 1 || (button == 0 && onLamp)) {
                    if (!module.isLocked()) {
                        module.toggle();
                    }
                } else if (button == 0) {
                    select(module);
                }
                return true;
            }
        }
        if (selected != null) {
            if (!selected.isLocked() && (powerButton.mouseClicked(mx, my, button) || keybindButton.mouseClicked(mx, my, button))) {
                return true;
            }
            if ((hudButton != null && hudButton.mouseClicked(mx, my, button)) || resetButton.mouseClicked(mx, my, button)) {
                return true;
            }
            return details.mouseClicked(mx, my, button);
        }
        return false;
    }

    private void setCategory(Category c) {
        category = c;
        lastCategory = c;
        query = "";
        search.setText("");
        refreshList();
    }

    @Override
    protected boolean mouseReleased(float mx, float my, int button) {
        if (details != null) {
            details.mouseReleased(mx, my, button);
        }
        return true;
    }

    @Override
    protected boolean mouseDragged(float mx, float my, int button) {
        if (details != null) {
            details.mouseDragged(mx, my);
        }
        return true;
    }

    @Override
    protected boolean mouseScrolled(float mx, float my, double amount) {
        if (mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH) {
            gridScroll.scroll(amount);
            return true;
        }
        return details != null && details.mouseScrolled(mx, my, amount);
    }

    @Override
    protected boolean charTyped(char c) {
        // start typing anywhere to search
        if (c > 32 && c != 127) {
            search.focus();
            return search.charTyped(c);
        }
        return false;
    }

    @Override
    protected boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        if (details != null && details.keyPressed(key, ctrl, shift, mouseX(), mouseY())) {
            return true;
        }
        if (ctrl && key == Key.F) {
            search.focus();
            return true;
        }
        if (closeKeyArmed && key == Medirian.get().settings().modMenuKey.key() && focused() == null) {
            close();
            return true;
        }
        return false;
    }
}
