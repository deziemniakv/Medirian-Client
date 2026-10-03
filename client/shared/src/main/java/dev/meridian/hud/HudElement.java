package dev.meridian.hud;

import com.google.gson.JsonObject;
import dev.meridian.module.Module;
import dev.meridian.render.Gfx;

/**
 * A draggable element of the in-game HUD, owned by a {@link Module}. The element is visible
 * while its module is enabled.
 *
 * <p>Subclasses draw their content at (0, 0) in {@link #render} and keep {@link #width} /
 * {@link #height} up to date. Position and scale are owned by the HUD system and persisted
 * per configuration profile.
 */
public abstract class HudElement {

    protected final Module module;
    private final Anchor defaultAnchor;
    private final float defaultX;
    private final float defaultY;

    private Anchor anchor;
    private float offsetX;
    private float offsetY;
    private float scale = 1f;

    /** Unscaled content size, maintained by the subclass. */
    protected int width = 40;
    protected int height = 16;

    // Resolved each frame by the HudManager (HUD space, scaled size).
    float screenX;
    float screenY;
    float drawnScale = 1f;

    protected HudElement(Module module, Anchor anchor, float x, float y) {
        this.module = module;
        this.defaultAnchor = anchor;
        this.defaultX = x;
        this.defaultY = y;
        resetLayout();
    }

    public Module module() {
        return module;
    }

    public String id() {
        return module.id();
    }

    public String displayName() {
        return module.displayName();
    }

    /** Called 20 times per second while visible. Do expensive work here, not in {@link #render}. */
    public void tick() {
    }

    /**
     * Draws the element at (0, 0).
     *
     * @param editor true inside the HUD editor; elements without live data should draw a preview
     */
    public abstract void render(Gfx g, boolean editor);

    /** False hides the element in game (e.g. Ping in singleplayer). The editor always shows it. */
    public boolean hasContent() {
        return true;
    }

    // ----- layout -----

    public final int width() {
        return width;
    }

    public final int height() {
        return height;
    }

    public final Anchor anchor() {
        return anchor;
    }

    public final float offsetX() {
        return offsetX;
    }

    public final float offsetY() {
        return offsetY;
    }

    public final float scale() {
        return scale;
    }

    public final void setScale(float newScale) {
        this.scale = Math.max(0.5f, Math.min(3f, Math.round(newScale * 20f) / 20f));
    }

    public final void setLayout(Anchor newAnchor, float x, float y) {
        this.anchor = newAnchor;
        this.offsetX = x;
        this.offsetY = y;
    }

    public final void resetLayout() {
        anchor = defaultAnchor;
        offsetX = defaultX;
        offsetY = defaultY;
        scale = 1f;
    }

    /** Last drawn top-left X in HUD space. */
    public final float screenX() {
        return screenX;
    }

    public final float screenY() {
        return screenY;
    }

    /** Last drawn size in HUD space (content size × element scale × global HUD scale). */
    public final float screenWidth() {
        return width * drawnScale;
    }

    public final float screenHeight() {
        return height * drawnScale;
    }

    public final boolean contains(float x, float y) {
        return x >= screenX && y >= screenY && x < screenX + screenWidth() && y < screenY + screenHeight();
    }

    // ----- persistence -----

    public JsonObject layoutToJson() {
        JsonObject json = new JsonObject();
        json.addProperty("anchor", anchor.name());
        json.addProperty("x", Math.round(offsetX * 10f) / 10f);
        json.addProperty("y", Math.round(offsetY * 10f) / 10f);
        json.addProperty("scale", scale);
        return json;
    }

    public void layoutFromJson(JsonObject json) {
        try {
            Anchor parsed = Anchor.valueOf(json.get("anchor").getAsString());
            setLayout(parsed, json.get("x").getAsFloat(), json.get("y").getAsFloat());
            if (json.has("scale")) {
                setScale(json.get("scale").getAsFloat());
            }
        } catch (RuntimeException e) {
            resetLayout();
        }
    }
}
