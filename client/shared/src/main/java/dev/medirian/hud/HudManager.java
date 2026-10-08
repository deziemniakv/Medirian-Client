package dev.medirian.hud;

import dev.medirian.config.ProfileSettings;
import dev.medirian.module.Module;
import dev.medirian.module.ModuleManager;
import dev.medirian.render.Gfx;
import dev.medirian.ui.UiScale;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders all visible HUD elements in a single pass in Medirian's virtual HUD space
 * ({@link UiScale}), so the layout looks the same at every resolution and GUI scale.
 */
public final class HudManager {

    private final ModuleManager modules;
    private final ProfileSettings profileSettings;
    private final List<HudElement> elements = new ArrayList<HudElement>();
    private float spaceWidth = 480;
    private float spaceHeight = 270;
    private float factor = 1f;

    public HudManager(ModuleManager modules, ProfileSettings profileSettings) {
        this.modules = modules;
        this.profileSettings = profileSettings;
        HudSurface.bind(profileSettings);
    }

    /** Collects HUD elements from registered modules; call after all modules are registered. */
    public void refreshElements() {
        elements.clear();
        for (Module module : modules.all()) {
            if (module.hasHud()) {
                elements.add(module.hud());
            }
        }
    }

    /** All HUD elements, including those of disabled modules (for the HUD editor). */
    public List<HudElement> elements() {
        return elements;
    }

    public void tick() {
        for (int i = 0; i < elements.size(); i++) {
            HudElement element = elements.get(i);
            if (element.module().isEnabled()) {
                element.tick();
            }
        }
    }

    /**
     * Draws the HUD. Called with the game's GUI-space {@link Gfx}.
     *
     * @param editor true when drawn by the HUD editor (shows elements without live data)
     */
    public void render(Gfx g, boolean editor) {
        factor = UiScale.factor(g.guiScale(), g.height());
        g.push();
        g.scale(factor, factor);
        renderInSpace(g, g.width() / factor, g.height() / factor, editor);
        g.pop();
    }

    /** Draws the HUD into a {@link Gfx} already transformed into HUD space of the given size. */
    public void renderInSpace(Gfx g, float width, float height, boolean editor) {
        spaceWidth = width;
        spaceHeight = height;
        float globalScale = profileSettings.hudScale.floatValue();
        boolean prepared = false;
        int blur = HudSurface.blurStrength();
        for (int i = 0; i < elements.size(); i++) {
            HudElement element = elements.get(i);
            if (!element.module().isEnabled() || (!editor && !element.hasContent())) {
                continue;
            }
            // the frosted backdrop of Liquid Glass: one capture and blur per frame, only when needed
            if (!prepared && blur > 0) {
                g.prepareBackdrop(blur);
                prepared = true;
            }
            draw(g, element, globalScale, editor);
        }
    }

    private void draw(Gfx g, HudElement element, float globalScale, boolean editor) {
        float scale = element.scale() * globalScale;
        element.drawnScale = scale;
        float w = element.width() * scale;
        float h = element.height() * scale;
        float x = element.anchor().resolveX(element.offsetX(), w, spaceWidth);
        float y = element.anchor().resolveY(element.offsetY(), h, spaceHeight);
        // keep elements on screen after resolution changes
        x = Math.max(0, Math.min(spaceWidth - w, x));
        y = Math.max(0, Math.min(spaceHeight - h, y));
        // snap to whole real pixels so text stays crisp
        double pixels = g.pixelScale();
        x = (float) (Math.round(x * pixels) / pixels);
        y = (float) (Math.round(y * pixels) / pixels);
        element.screenX = x;
        element.screenY = y;
        g.push();
        g.translate(x, y);
        g.scale(scale, scale);
        element.render(g, editor);
        g.pop();
    }

    /** Width of the HUD space in the last frame. */
    public float spaceWidth() {
        return spaceWidth;
    }

    public float spaceHeight() {
        return spaceHeight;
    }

    /** Factor between GUI units and HUD space units in the last frame. */
    public float factor() {
        return factor;
    }

    public float globalScale() {
        return profileSettings.hudScale.floatValue();
    }
}
