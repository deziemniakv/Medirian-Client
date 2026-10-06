package dev.medirian.ui;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.HudElement;
import dev.medirian.hud.HudManager;
import dev.medirian.i18n.I18n;
import dev.medirian.input.Key;
import dev.medirian.module.Module;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Pixel;
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;
import dev.medirian.ui.widget.Button;
import dev.medirian.ui.widget.Slider;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD editor: drag elements, snap to edges/centre/other elements (with guide lines), scale with
 * the corner handle or mouse wheel, edit properties in the side panel, add/remove elements and
 * reset the layout. Changes are saved to the active configuration profile.
 */
public final class HudEditorScreen extends MedirianScreen {

    private static final float SNAP = 4f;
    private static final float MARGIN = 3f;
    private static final float HANDLE = 5f;
    private static final float PANEL_W = 184f;

    private static boolean snapping = true;

    private final HudManager hud = Medirian.get().hud();
    private HudElement selected;
    private HudElement hovered;
    private boolean dragging;
    private boolean resizing;
    private float grabX;
    private float grabY;
    private float resizeStartScale;
    private float resizeStartWidth;
    private float guideX = Float.NaN;
    private float guideY = Float.NaN;
    private boolean addMenuOpen;
    private boolean confirmReset;
    private long confirmResetUntil;

    private final List<Button> toolbar = new ArrayList<Button>();
    private SettingsList properties;
    private Slider scaleSlider;
    private Button resetElementButton;
    private Button removeElementButton;
    private float panelX;

    public HudEditorScreen(MedirianScreen parent) {
        super(parent);
    }

    /** Opens the editor with {@code module}'s element selected (enabling the module if needed). */
    public HudEditorScreen(MedirianScreen parent, Module module) {
        super(parent);
        if (module.hasHud()) {
            module.setEnabled(true);
            select(module.hud());
        }
    }

    /** Centres the toolbar; with {@code g}, every button is as wide as its label needs. */
    private void layoutToolbar(Gfx g) {
        float total = -4;
        float[] widths = new float[toolbar.size()];
        for (int i = 0; i < toolbar.size(); i++) {
            widths[i] = g == null ? 86 : toolbar.get(i).preferredWidth(g, 70);
            total += widths[i] + 4;
        }
        float x = (width - total) / 2f;
        for (int i = 0; i < toolbar.size(); i++) {
            toolbar.get(i).bounds(x, height - 26, widths[i], 16);
            x += widths[i] + 4;
        }
    }

    @Override
    protected void init() {
        toolbar.clear();
        toolbar.add(new Button(I18n.tr("ui.hudeditor.add", "Add element"), Button.Style.PRIMARY,
                () -> addMenuOpen = !addMenuOpen));
        toolbar.add(new Button(() -> confirmReset && System.currentTimeMillis() < confirmResetUntil
                ? I18n.tr("ui.hudeditor.resetConfirm", "Click to confirm")
                : I18n.tr("ui.hudeditor.reset", "Reset layout"), Button.Style.SECONDARY, this::resetLayout));
        toolbar.add(new Button(() -> I18n.tr("ui.hudeditor.snapping", "Snapping") + ": "
                + (snapping ? I18n.tr("ui.on", "On") : I18n.tr("ui.off", "Off")), Button.Style.SECONDARY,
                () -> snapping = !snapping));
        toolbar.add(new Button(I18n.tr("ui.done", "Done"), Button.Style.SECONDARY, this::close));
        layoutToolbar(null);
        if (selected != null) {
            buildProperties();
        }
    }

    private void resetLayout() {
        long now = System.currentTimeMillis();
        if (confirmReset && now < confirmResetUntil) {
            Medirian.get().config().resetHudLayout();
            confirmReset = false;
        } else {
            confirmReset = true;
            confirmResetUntil = now + 3000;
        }
    }

    private void select(HudElement element) {
        selected = element;
        if (element != null && width > 0) {
            buildProperties();
        }
    }

    private void buildProperties() {
        final HudElement element = selected;
        properties = new SettingsList(this);
        scaleSlider = new Slider(new Slider.Model() {
            @Override
            public double progress() {
                return (element.scale() - 0.5) / 2.5;
            }

            @Override
            public void setProgress(double progress) {
                setScaleKeepingCorner(element, (float) (0.5 + progress * 2.5));
            }

            @Override
            public String label() {
                return Math.round(element.scale() * 100) + "%";
            }

            @Override
            public void nudge(int steps) {
                setScaleKeepingCorner(element, element.scale() + steps * 0.05f);
            }
        });
        properties.add(new SettingsList.ControlRow(I18n.tr("ui.hudeditor.scale", "Scale"), null, scaleSlider, 100));
        resetElementButton = new Button(I18n.tr("ui.hudeditor.resetElement", "Reset"), Button.Style.SECONDARY, () -> {
            element.resetLayout();
            Medirian.get().config().markDirty();
        });
        properties.add(new SettingsList.ControlRow(I18n.tr("ui.hudeditor.position", "Position"), null, resetElementButton, 60));
        removeElementButton = new Button(I18n.tr("ui.hudeditor.remove", "Remove"), Button.Style.DANGER, () -> {
            element.module().setEnabled(false);
            select(null);
        });
        properties.add(new SettingsList.ControlRow(I18n.tr("ui.hudeditor.element", "Element"), null, removeElementButton, 60));
        properties.addSettings(element.module().settings());
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void render(Gfx g, float mx, float my, float delta) {
        Theme theme = Theme.current();
        float spaceW = exactWidth();
        float spaceH = exactHeight();
        if (Medirian.get().game().inWorld()) {
            g.fill(0, 0, width, height, 0x38000000);
        } else {
            renderBackdrop(g); // main menu: nothing is rendered behind the editor
        }
        if (dragging || resizing) {
            renderGrid(g, spaceW, spaceH);
        }
        hud.renderInSpace(g, spaceW, spaceH, true);

        hovered = null;
        if (!dragging && !resizing && !overUi(mx, my)) {
            hovered = elementAt(mx, my);
        }
        for (HudElement element : hud.elements()) {
            if (!element.module().isEnabled()) {
                continue;
            }
            if (element == selected) {
                UiDraw.hairline(g, element.screenX() - 1, element.screenY() - 1, element.screenWidth() + 2, element.screenHeight() + 2, theme.pumpkinLight);
                float hx = element.screenX() + element.screenWidth() - HANDLE / 2f;
                float hy = element.screenY() + element.screenHeight() - HANDLE / 2f;
                Pixel.frame(g, hx, hy, HANDLE, HANDLE, theme.pumpkin, theme.ember, theme.pumpkinDark);
                renderTag(g, theme, element);
            } else if (element == hovered) {
                UiDraw.hairline(g, element.screenX() - 1, element.screenY() - 1, element.screenWidth() + 2, element.screenHeight() + 2,
                        Colors.withAlpha(theme.text, 0x90));
            } else {
                UiDraw.hairline(g, element.screenX() - 1, element.screenY() - 1, element.screenWidth() + 2, element.screenHeight() + 2,
                        Colors.withAlpha(theme.text, 0x28));
            }
        }
        if (!Float.isNaN(guideX)) {
            g.fill(Math.round(guideX), 0, Math.round(guideX) + 1, height, Colors.withAlpha(theme.pumpkin, 0xC0));
        }
        if (!Float.isNaN(guideY)) {
            g.fill(0, Math.round(guideY), width, Math.round(guideY) + 1, Colors.withAlpha(theme.pumpkin, 0xC0));
        }

        if (selected != null && selected.module().isEnabled() && properties != null && !dragging && !resizing) {
            renderPanel(g, theme, mx, my);
        }
        renderToolbar(g, theme, mx, my);
        if (addMenuOpen) {
            renderAddMenu(g, theme, mx, my);
        }
        if (hud.elements().isEmpty()) {
            UiDraw.centeredText(g, I18n.tr("ui.hudeditor.noElements", "No HUD elements available"), width / 2f, height / 2f, theme.textDim, true);
        }
    }

    private void renderGrid(Gfx g, float w, float h) {
        for (int x = 0; x < w; x += 20) {
            g.fill(x, 0, x + 1, (int) h, 0x0CFFFFFF);
        }
        for (int y = 0; y < h; y += 20) {
            g.fill(0, y, (int) w, y + 1, 0x0CFFFFFF);
        }
        g.fill((int) (w / 2), 0, (int) (w / 2) + 1, (int) h, 0x18FFFFFF);
        g.fill(0, (int) (h / 2), (int) w, (int) (h / 2) + 1, 0x18FFFFFF);
    }

    private void renderTag(Gfx g, Theme theme, HudElement element) {
        String tag = element.displayName() + "  " + Math.round(element.scale() * 100) + "%";
        float tagW = g.textWidth(tag) + 8;
        float tagX = Math.round(Math.max(2, Math.min(width - tagW - 2, element.screenX())));
        float tagY = Math.round(element.screenY() - 15 < 2 ? element.screenY() + element.screenHeight() + 3 : element.screenY() - 15);
        Pixel.frame(g, tagX, tagY, tagW, 13, theme.pumpkin, theme.ember, theme.pumpkinDark);
        g.text(tag, tagX + 4, tagY + 3, theme.onPumpkin, false);
    }

    private void renderPanel(Gfx g, Theme theme, float mx, float my) {
        boolean right = selected.screenX() + selected.screenWidth() / 2f < width / 2f;
        panelX = right ? width - PANEL_W - 8 : 8;
        float py = 8;
        float ph = height - 44;
        Pixel.panel(g, panelX, py, PANEL_W, ph);
        Pixel.icon(g, Pixel.moduleIcon(selected.module().id()), panelX + 8, py + 5, 1, 0xFFFFFFFF);
        Pixel.text(g, UiDraw.ellipsize(g, selected.displayName(), (int) PANEL_W - 40), panelX + 28, py + 9, theme.text);
        Pixel.groove(g, panelX + 6, py + 24, PANEL_W - 12);
        properties.bounds(panelX + 10, py + 30, PANEL_W - 14, ph - 36);
        properties.render(g, mx, my);
    }

    private void renderToolbar(Gfx g, Theme theme, float mx, float my) {
        layoutToolbar(g);
        Button first = toolbar.get(0);
        Button last = toolbar.get(toolbar.size() - 1);
        float x = first.x - 6;
        float w = last.x + last.w - first.x + 12;
        Pixel.panel(g, x, height - 32, w, 28);
        for (Button button : toolbar) {
            button.render(g, mx, my);
        }
    }

    private List<Module> addableModules() {
        List<Module> result = new ArrayList<Module>();
        for (HudElement element : hud.elements()) {
            if (!element.module().isEnabled()) {
                result.add(element.module());
            }
        }
        return result;
    }

    private void renderAddMenu(Gfx g, Theme theme, float mx, float my) {
        List<Module> modules = addableModules();
        Button add = toolbar.get(0);
        float itemH = 14;
        float menuW = 150;
        float menuH = Math.max(1, modules.size()) * itemH + 8;
        float x = add.x;
        float y = add.y - 8 - menuH;
        Pixel.panel(g, x, y, menuW, menuH);
        if (modules.isEmpty()) {
            g.text(I18n.tr("ui.hudeditor.allAdded", "All elements are on screen"), x + 6, y + 7, theme.textMuted, false);
            return;
        }
        float iy = y + 4;
        for (Module module : modules) {
            boolean hover = mx >= x && mx < x + menuW && my >= iy && my < iy + itemH;
            if (hover) {
                g.fill(Math.round(x) + 3, Math.round(iy), Math.round(x + menuW) - 3, Math.round(iy + itemH), theme.surfaceLight);
            }
            g.text(module.displayName(), x + 8, iy + 3, hover ? theme.text : theme.textDim, false);
            g.text("+", x + menuW - 14, iy + 3, theme.pumpkinLight, false);
            iy += itemH;
        }
    }

    // ------------------------------------------------------------------ geometry helpers

    private HudElement elementAt(float mx, float my) {
        List<HudElement> elements = hud.elements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            if (element.module().isEnabled() && element.contains(mx, my)) {
                return element;
            }
        }
        return null;
    }

    private boolean overHandle(HudElement element, float mx, float my) {
        float hx = element.screenX() + element.screenWidth();
        float hy = element.screenY() + element.screenHeight();
        return Math.abs(mx - hx) <= HANDLE && Math.abs(my - hy) <= HANDLE;
    }

    private boolean overUi(float mx, float my) {
        if (my >= height - 34) {
            return true;
        }
        return selected != null && properties != null && mx >= panelX && mx < panelX + PANEL_W && my < height - 36;
    }

    /** Moves an element so its top-left is at (x, y) and re-derives its anchor. */
    private void place(HudElement element, float x, float y) {
        float w = element.screenWidth();
        float h = element.screenHeight();
        float spaceW = exactWidth();
        float spaceH = exactHeight();
        x = Math.max(0, Math.min(spaceW - w, x));
        y = Math.max(0, Math.min(spaceH - h, y));
        Anchor anchor = Anchor.fromPosition(x, y, w, h, spaceW, spaceH);
        element.setLayout(anchor, anchor.offsetX(x, w, spaceW), anchor.offsetY(y, h, spaceH));
    }

    private void setScaleKeepingCorner(HudElement element, float scale) {
        float x = element.screenX();
        float y = element.screenY();
        element.setScale(scale);
        float total = element.scale() * hud.globalScale();
        float w = element.width() * total;
        float h = element.height() * total;
        float spaceW = exactWidth();
        float spaceH = exactHeight();
        Anchor anchor = element.anchor();
        element.setLayout(anchor, anchor.offsetX(x, w, spaceW), anchor.offsetY(y, h, spaceH));
    }

    private float[] snap(HudElement element, float x, float y) {
        guideX = Float.NaN;
        guideY = Float.NaN;
        if (!snapping) {
            return new float[] {x, y};
        }
        float w = element.screenWidth();
        float h = element.screenHeight();
        float spaceW = exactWidth();
        float spaceH = exactHeight();
        List<Float> targetsX = new ArrayList<Float>();
        List<Float> targetsY = new ArrayList<Float>();
        targetsX.add(MARGIN);
        targetsX.add(spaceW / 2f);
        targetsX.add(spaceW - MARGIN);
        targetsY.add(MARGIN);
        targetsY.add(spaceH / 2f);
        targetsY.add(spaceH - MARGIN);
        for (HudElement other : hud.elements()) {
            if (other == element || !other.module().isEnabled()) {
                continue;
            }
            targetsX.add(other.screenX());
            targetsX.add(other.screenX() + other.screenWidth());
            targetsX.add(other.screenX() + other.screenWidth() / 2f);
            targetsY.add(other.screenY());
            targetsY.add(other.screenY() + other.screenHeight());
            targetsY.add(other.screenY() + other.screenHeight() / 2f);
        }
        float bestDx = SNAP + 1;
        float snappedX = x;
        for (float target : targetsX) {
            float[] edges = {x, x + w / 2f, x + w};
            for (int i = 0; i < 3; i++) {
                float d = target - edges[i];
                if (Math.abs(d) < Math.abs(bestDx) && Math.abs(d) <= SNAP) {
                    bestDx = d;
                    snappedX = x + d;
                    guideX = target;
                }
            }
        }
        float bestDy = SNAP + 1;
        float snappedY = y;
        for (float target : targetsY) {
            float[] edges = {y, y + h / 2f, y + h};
            for (int i = 0; i < 3; i++) {
                float d = target - edges[i];
                if (Math.abs(d) < Math.abs(bestDy) && Math.abs(d) <= SNAP) {
                    bestDy = d;
                    snappedY = y + d;
                    guideY = target;
                }
            }
        }
        return new float[] {snappedX, snappedY};
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(float mx, float my, int button) {
        if (addMenuOpen) {
            if (clickAddMenu(mx, my)) {
                return true;
            }
            addMenuOpen = false;
        }
        for (Button b : toolbar) {
            if (b.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        if (selected != null && properties != null && mx >= panelX && mx < panelX + PANEL_W && my < height - 36) {
            properties.mouseClicked(mx, my, button);
            return true;
        }
        if (selected != null && overHandle(selected, mx, my)) {
            resizing = true;
            resizeStartScale = selected.scale();
            resizeStartWidth = Math.max(1, selected.screenWidth());
            grabX = mx;
            return true;
        }
        HudElement element = elementAt(mx, my);
        if (element != null) {
            if (button == 1) {
                select(element);
                return true;
            }
            select(element);
            dragging = true;
            grabX = mx - element.screenX();
            grabY = my - element.screenY();
            return true;
        }
        select(null);
        return true;
    }

    private boolean clickAddMenu(float mx, float my) {
        List<Module> modules = addableModules();
        Button add = toolbar.get(0);
        float itemH = 14;
        float menuW = 150;
        float menuH = Math.max(1, modules.size()) * itemH + 8;
        float x = add.x;
        float y = add.y - 8 - menuH;
        if (mx < x || mx >= x + menuW || my < y || my >= y + menuH) {
            return false;
        }
        int index = (int) ((my - y - 4) / itemH);
        if (index >= 0 && index < modules.size()) {
            Module module = modules.get(index);
            module.setEnabled(true);
            select(module.hud());
            addMenuOpen = false;
        }
        return true;
    }

    @Override
    protected boolean mouseDragged(float mx, float my, int button) {
        if (resizing && selected != null) {
            float factor = (resizeStartWidth + (mx - grabX)) / resizeStartWidth;
            setScaleKeepingCorner(selected, resizeStartScale * factor);
            return true;
        }
        if (dragging && selected != null) {
            float[] snapped = snap(selected, mx - grabX, my - grabY);
            place(selected, snapped[0], snapped[1]);
            return true;
        }
        if (properties != null) {
            properties.mouseDragged(mx, my);
        }
        return true;
    }

    @Override
    protected boolean mouseReleased(float mx, float my, int button) {
        if (dragging || resizing) {
            Medirian.get().config().markDirty();
        }
        dragging = false;
        resizing = false;
        guideX = Float.NaN;
        guideY = Float.NaN;
        if (properties != null) {
            properties.mouseReleased(mx, my, button);
        }
        return true;
    }

    @Override
    protected boolean mouseScrolled(float mx, float my, double amount) {
        if (selected != null && properties != null && mx >= panelX && mx < panelX + PANEL_W) {
            return properties.mouseScrolled(mx, my, amount);
        }
        HudElement element = elementAt(mx, my);
        if (element != null) {
            setScaleKeepingCorner(element, element.scale() + (amount > 0 ? 0.05f : -0.05f));
            Medirian.get().config().markDirty();
            return true;
        }
        return false;
    }

    @Override
    protected boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        if (selected == null) {
            return false;
        }
        float step = shift ? 5 : 1;
        switch (key) {
            case LEFT:
                place(selected, selected.screenX() - step, selected.screenY());
                return true;
            case RIGHT:
                place(selected, selected.screenX() + step, selected.screenY());
                return true;
            case UP:
                place(selected, selected.screenX(), selected.screenY() - step);
                return true;
            case DOWN:
                place(selected, selected.screenX(), selected.screenY() + step);
                return true;
            case DELETE:
                selected.module().setEnabled(false);
                select(null);
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void removed() {
        Medirian.get().config().markDirty();
    }
}
