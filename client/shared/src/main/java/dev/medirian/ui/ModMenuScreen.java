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
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.ActionSetting;
import dev.medirian.setting.Setting;
import dev.medirian.ui.widget.Button;
import dev.medirian.ui.widget.KeybindButton;
import dev.medirian.ui.widget.ScrollState;
import dev.medirian.ui.widget.Switch;
import dev.medirian.ui.widget.TextField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Medirian mod menu: Categories | Modules | Settings, with search.
 * Opened with Right Shift by default.
 */
public final class ModMenuScreen extends MedirianScreen {

    private static final float SIDEBAR_W = 124;
    private static final float CARD_H = 30;
    private static final float CARD_GAP = 4;

    // remembered between openings
    private static Category lastCategory;
    private static String lastModuleId;

    private float px;
    /** The menu key closes the menu only after the key that opened it was released (no key-repeat close). */
    private boolean closeKeyArmed;
    private float py;
    private float pw;
    private float ph;
    private float listX;
    private float listW;
    private float detailX;
    private float detailW;

    private Category category = lastCategory;
    private Module selected;
    private String query = "";
    private List<Module> visibleModules;
    private final Map<Module, Switch> switches = new HashMap<Module, Switch>();
    private final Map<Object, Anim> hoverAnims = new HashMap<Object, Anim>();
    private final ScrollState listScroll = new ScrollState();
    private final Anim open = new Anim(0f, 16f);

    private TextField search;
    private SettingsList details;
    private Switch enabledSwitch;
    private KeybindButton keybindButton;
    private Button hudButton;
    private Button resetButton;
    private Button hudEditorButton;
    private Button settingsButton;
    /** Only in versions that render cosmetics. */
    private Button cosmeticsButton;

    public ModMenuScreen(MedirianScreen parent) {
        super(parent);
        if (lastModuleId != null) {
            selected = Medirian.get().modules().get(lastModuleId);
        }
    }

    @Override
    protected void init() {
        pw = Math.min(660, width - 32);
        ph = Math.min(390, height - 32);
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;
        listX = px + SIDEBAR_W + 10;
        listW = Math.min(230, (pw - SIDEBAR_W) * 0.44f);
        detailX = listX + listW + 12;
        detailW = px + pw - detailX - 12;

        if (search == null) {
            search = new TextField(this, query, 32)
                    .placeholder(I18n.tr("ui.modmenu.search", "Search modules..."))
                    .onChange(text -> {
                        query = text;
                        refreshList();
                    });
        }
        search.bounds(listX, py + 12, listW, 16);
        hudEditorButton = new Button(I18n.tr("ui.hudEditor", "Edit HUD"), Button.Style.PRIMARY,
                () -> Medirian.get().platform().openScreen(new HudEditorScreen(this)))
                .bounds(px + 10, py + ph - 46, SIDEBAR_W - 20, 16);
        settingsButton = new Button(I18n.tr("ui.settings", "Settings"), Button.Style.SECONDARY,
                () -> Medirian.get().platform().openScreen(new SettingsScreen(this)))
                .bounds(px + 10, py + ph - 26, SIDEBAR_W - 20, 16);
        cosmeticsButton = Medirian.get().cosmetics().available()
                ? new Button(I18n.tr("ui.cosmetics", "Cosmetics"), Button.Style.SECONDARY,
                        () -> Medirian.get().platform().openScreen(new CosmeticsScreen(this)))
                        .bounds(px + 10, py + ph - 66, SIDEBAR_W - 20, 16)
                : null;
        refreshList();
        if (selected == null && !visibleModules.isEmpty()) {
            select(visibleModules.get(0));
        } else if (selected != null) {
            select(selected);
        }
    }

    private void refreshList() {
        visibleModules = Medirian.get().modules().search(query, query.isEmpty() ? category : null);
        listScroll.reset();
    }

    private void select(Module module) {
        selected = module;
        lastModuleId = module == null ? null : module.id();
        details = new SettingsList(this);
        if (module == null) {
            return;
        }
        enabledSwitch = new Switch(module::isEnabled, module::setEnabled);
        keybindButton = new KeybindButton(this, module.keybind());
        hudButton = module.hasHud()
                ? new Button(I18n.tr("ui.modmenu.position", "Position"), Button.Style.SECONDARY,
                        () -> Medirian.get().platform().openScreen(new HudEditorScreen(this, module)))
                : null;
        resetButton = new Button(I18n.tr("ui.modmenu.reset", "Reset"), Button.Style.GHOST, () -> {
            for (Setting<?> setting : module.settings()) {
                if (!(setting instanceof ActionSetting)) {
                    setting.reset();
                }
            }
            module.keybind().reset();
        });
        if (module.isLocked()) {
            details.add(new SettingsList.InfoRow(I18n.tr("ui.modmenu.enabled", "Enabled"), module::lockReason));
        } else {
            details.add(new SettingsList.ControlRow(I18n.tr("ui.modmenu.enabled", "Enabled"), null, enabledSwitch, 22));
        }
        details.add(new SettingsList.ControlRow(
                module.keybindWorksWhenDisabled() ? I18n.tr("ui.modmenu.keybind", "Keybind") : I18n.tr("ui.modmenu.keybindHold", "Activation key"),
                null, keybindButton, 72));
        if (hudButton != null) {
            details.add(new SettingsList.ControlRow(I18n.tr("ui.modmenu.hud", "HUD element"),
                    I18n.tr("ui.modmenu.hud.desc", "Move and scale it in the HUD editor"), hudButton, 72));
        }
        details.addSettings(module.settings());
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void render(Gfx g, float mx, float my, float delta) {
        if (!closeKeyArmed && !Medirian.get().platform().input().isKeyDown(Medirian.get().settings().modMenuKey.key())) {
            closeKeyArmed = true;
        }
        Theme theme = Theme.current();
        renderBackdrop(g);
        float appear = open.target(1f).get();
        g.push();
        float offset = (1f - appear) * 8f;
        g.translate(0, offset);

        UiDraw.shadow(g, px, py, pw, ph, 8, 4);
        UiDraw.roundRectBordered(g, px, py, pw, ph, 8, theme.panel, theme.border);
        // sidebar
        UiDraw.roundRect(g, px + 1, py + 1, SIDEBAR_W, ph - 2, 7, theme.surface);
        g.fill((int) (px + SIDEBAR_W), (int) py + 1, (int) (px + SIDEBAR_W) + 1, (int) (py + ph) - 1, theme.border);

        renderBrand(g, theme);
        renderCategories(g, theme, mx, my - offset);
        hudEditorButton.render(g, mx, my - offset);
        settingsButton.render(g, mx, my - offset);
        if (cosmeticsButton != null) {
            cosmeticsButton.render(g, mx, my - offset);
        }

        search.render(g, mx, my - offset);
        renderModuleList(g, theme, mx, my - offset);
        g.fill((int) (detailX - 6), (int) py + 12, (int) (detailX - 5), (int) (py + ph) - 12, theme.border);
        renderDetails(g, theme, mx, my - offset);
        g.pop();
    }

    private void renderBrand(Gfx g, Theme theme) {
        float bx = px + 12;
        float by = py + 13;
        g.texture("gui/mark.png", (int) bx, (int) by, 24, 13, 0xFFFFFFFF);
        g.text("§lMEDIRIAN", bx + 30, by - 1, theme.text, false);
        g.push();
        g.translate(bx + 30, by + 9);
        g.scale(0.7f, 0.7f);
        g.text("CLIENT", 0, 0, theme.textDim, false);
        g.pop();
        if (theme.decorations) {
            UiDraw.moon(g, px + SIDEBAR_W - 13, by + 5, 3.5f, Colors.withAlpha(0xFFE9E2F5, 0xB0), theme.surface);
        }
    }

    private void renderCategories(Gfx g, Theme theme, float mx, float my) {
        float y = py + 40;
        y = categoryItem(g, theme, null, I18n.tr("category.all", "All"), y, mx, my);
        for (Category c : Category.values()) {
            if (!Medirian.get().modules().byCategory(c).isEmpty()) {
                y = categoryItem(g, theme, c, c.displayName(), y, mx, my);
            }
        }
        // footer: version and target
        String footer = "v" + BuildInfo.VERSION + " · " + Medirian.get().platform().minecraftVersion();
        g.push();
        g.translate(px + 12, py + ph - (cosmeticsButton != null ? 78 : 58));
        g.scale(0.75f, 0.75f);
        g.text(footer, 0, 0, theme.textMuted, false);
        g.pop();
    }

    private float categoryItem(Gfx g, Theme theme, Category c, String label, float y, float mx, float my) {
        float x = px + 8;
        float w = SIDEBAR_W - 16;
        float h = 17;
        boolean active = query.isEmpty() && category == c;
        boolean hovered = mx >= x && my >= y && mx < x + w && my < y + h;
        float t = anim(c == null ? "all" : c, hovered || active);
        if (active) {
            UiDraw.roundRect(g, x, y, w, h, 3, theme.accentSoft);
            UiDraw.roundRect(g, x, y + 4, 2, h - 8, 1, theme.accent);
        } else if (t > 0.01f) {
            UiDraw.roundRect(g, x, y, w, h, 3, Colors.fade(theme.elevated, t));
        }
        int color = active ? theme.text : Colors.lerp(theme.textDim, theme.text, t);
        g.text(label, x + 9, y + 5, color, false);
        int count = c == null ? Medirian.get().modules().all().size() : Medirian.get().modules().byCategory(c).size();
        String countText = String.valueOf(count);
        g.text(countText, x + w - 6 - g.textWidth(countText), y + 5, theme.textMuted, false);
        return y + h + 2;
    }

    private void renderModuleList(Gfx g, Theme theme, float mx, float my) {
        float top = py + 34;
        float bottom = py + ph - 10;
        float viewH = bottom - top;
        listScroll.setBounds(visibleModules.size() * (CARD_H + CARD_GAP), viewH);
        float offset = listScroll.offset();
        if (visibleModules.isEmpty()) {
            String empty = I18n.tr("ui.modmenu.empty", "No modules match your search");
            g.text(empty, listX + (listW - g.textWidth(empty)) / 2f, top + 20, theme.textMuted, false);
            return;
        }
        boolean inside = mx >= listX && mx < listX + listW && my >= top && my < bottom;
        g.enableScissor((int) listX, (int) top, (int) (listX + listW), (int) bottom);
        float y = top - offset;
        for (Module module : visibleModules) {
            if (y + CARD_H >= top && y <= bottom) {
                renderCard(g, theme, module, y, inside ? mx : -1, inside ? my : -1);
            }
            y += CARD_H + CARD_GAP;
        }
        g.disableScissor();
        listScroll.renderBar(g, listX + listW + 3, top);
    }

    private void renderCard(Gfx g, Theme theme, Module module, float y, float mx, float my) {
        boolean active = module == selected;
        boolean hovered = mx >= listX && my >= y && mx < listX + listW && my < y + CARD_H;
        float t = anim(module, hovered);
        int fill = active ? theme.elevated : Colors.lerp(theme.surface, theme.elevated, t * 0.7f);
        UiDraw.roundRectBordered(g, listX, y, listW, CARD_H, 4, fill, active ? Colors.withAlpha(theme.accent, 0xB0) : theme.border);
        if (module.isEnabled()) {
            UiDraw.roundRect(g, listX + 4, y + 9, 2, CARD_H - 18, 1, theme.accent);
        }
        if (module.isLocked()) {
            String locked = I18n.tr("ui.modmenu.locked", "Locked");
            g.push();
            g.translate(listX + listW - 8 - g.textWidth(locked) * 0.8f, y + (CARD_H - 7) / 2f);
            g.scale(0.8f, 0.8f);
            g.text(locked, 0, 0, theme.textMuted, false);
            g.pop();
        } else {
            Switch toggle = switches.get(module);
            if (toggle == null) {
                toggle = new Switch(module::isEnabled, module::setEnabled);
                switches.put(module, toggle);
            }
            toggle.bounds(listX + listW - 30, y + (CARD_H - 12) / 2f, 22, 12);
            toggle.render(g, mx, my);
        }
        float textW = listW - 50;
        g.text(UiDraw.ellipsize(g, module.displayName(), (int) textW), listX + 11, y + 6, theme.text, false);
        g.push();
        g.translate(listX + 11, y + 17);
        g.scale(0.8f, 0.8f);
        g.text(UiDraw.ellipsize(g, module.displayDescription(), (int) (textW / 0.8f)), 0, 0, theme.textMuted, false);
        g.pop();
    }

    private void renderDetails(Gfx g, Theme theme, float mx, float my) {
        if (selected == null) {
            return;
        }
        float y = py + 12;
        g.text("§l" + selected.displayName(), detailX, y + 1, theme.text, false);
        String chip = selected.category().displayName().toUpperCase();
        float chipW = g.textWidth(chip) * 0.7f + 8;
        float chipX = detailX + detailW - chipW;
        UiDraw.roundRect(g, chipX, y, chipW, 11, 3, theme.accentSoft);
        g.push();
        g.translate(chipX + 4, y + 3);
        g.scale(0.7f, 0.7f);
        g.text(chip, 0, 0, theme.accentHover, false);
        g.pop();
        y += 14;
        List<String> lines = UiDraw.wrap(g, selected.displayDescription(), (int) detailW);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(lines.get(i), detailX, y, theme.textDim, false);
            y += 10;
        }
        y += 4;
        g.fill((int) detailX, (int) y, (int) (detailX + detailW), (int) y + 1, theme.border);
        y += 4;
        float listBottom = py + ph - 30;
        details.bounds(detailX, y, detailW + 6, listBottom - y);
        details.render(g, mx, my);
        // wide enough for longer translations ("Zurücksetzen", "Restablecer")
        float resetW = Math.max(60, g.textWidth(I18n.tr("ui.modmenu.reset", "Reset")) + 14);
        resetButton.bounds(detailX + detailW - resetW, py + ph - 24, resetW, 14);
        resetButton.render(g, mx, my);
    }

    private float anim(Object key, boolean on) {
        Anim anim = hoverAnims.get(key);
        if (anim == null) {
            anim = new Anim(0f, 18f);
            hoverAnims.put(key, anim);
        }
        return anim.target(on ? 1f : 0f).get();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(float mx, float my, int button) {
        if (search.mouseClicked(mx, my, button) || hudEditorButton.mouseClicked(mx, my, button)
                || settingsButton.mouseClicked(mx, my, button)
                || (cosmeticsButton != null && cosmeticsButton.mouseClicked(mx, my, button))) {
            return true;
        }
        // categories
        float y = py + 40;
        if (mx >= px + 8 && mx < px + SIDEBAR_W - 8) {
            if (my >= y && my < y + 17) {
                setCategory(null);
                return true;
            }
            y += 19;
            for (Category c : Category.values()) {
                if (Medirian.get().modules().byCategory(c).isEmpty()) {
                    continue;
                }
                if (my >= y && my < y + 17) {
                    setCategory(c);
                    return true;
                }
                y += 19;
            }
        }
        // module cards
        float top = py + 34;
        float bottom = py + ph - 10;
        if (mx >= listX && mx < listX + listW && my >= top && my < bottom) {
            float cardY = top - listScroll.offset();
            for (Module module : visibleModules) {
                if (my >= cardY && my < cardY + CARD_H) {
                    Switch toggle = switches.get(module);
                    if (toggle != null && !module.isLocked() && toggle.mouseClicked(mx, my, button)) {
                        return true;
                    }
                    if (button == 1) {
                        module.toggle();
                    } else {
                        select(module);
                    }
                    return true;
                }
                cardY += CARD_H + CARD_GAP;
            }
        }
        if (details != null && details.mouseClicked(mx, my, button)) {
            return true;
        }
        return resetButton != null && resetButton.mouseClicked(mx, my, button);
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
        if (mx >= listX && mx < listX + listW) {
            listScroll.scroll(amount);
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
