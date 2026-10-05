package dev.medirian.module.impl.movement;

import dev.medirian.input.Key;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.render.Anim;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;

/** Optifine-style zoom: hold (or toggle) the key to narrow the FOV; scroll to adjust while zoomed. */
public final class ZoomModule extends Module {

    /** Whether the key must be held or toggles zoom. */
    public enum Mode { HOLD, TOGGLE }

    private final ModeSetting<Mode> mode;
    private final NumberSetting factor;
    private final BooleanSetting smooth;
    private final BooleanSetting scroll;

    private final Anim multiplier = new Anim(1f, 14f);
    private boolean zooming;
    private double scrollFactor;

    public ZoomModule() {
        super("zoom", "Zoom", Category.MOVEMENT, "Zoom in while holding the key.");
        requires(Capability.ZOOM);
        enableByDefault();
        defaultKeybind(Key.C);
        mode = add(new ModeSetting<Mode>("mode", "Key mode", Mode.HOLD));
        factor = add(new NumberSetting("factor", "Zoom level", 4, 1.5, 12, 0.5).unit("x"));
        smooth = add(new BooleanSetting("smooth", "Smooth zoom", true));
        scroll = add(new BooleanSetting("scroll", "Scroll to adjust", true));
    }

    @Override
    public boolean keybindWorksWhenDisabled() {
        return false;
    }

    @Override
    protected void onKeybindPressed() {
        if (mode.is(Mode.TOGGLE)) {
            setZooming(!zooming);
        } else {
            setZooming(true);
        }
    }

    @Override
    protected void onKeybindReleased() {
        if (mode.is(Mode.HOLD)) {
            setZooming(false);
        }
    }

    @Override
    protected void onDisable() {
        setZooming(false);
        multiplier.snap(1f);
    }

    private void setZooming(boolean on) {
        zooming = on;
        if (on) {
            scrollFactor = factor.doubleValue();
        }
    }

    public boolean zooming() {
        return zooming;
    }

    /** FOV multiplier for this frame (1 = no zoom). */
    public double fovMultiplier() {
        if (!isEnabled()) {
            return 1.0;
        }
        float target = zooming ? (float) (1.0 / Math.max(1.0, scrollFactor)) : 1f;
        if (!smooth.on()) {
            multiplier.snap(target);
            return target;
        }
        return multiplier.target(target).get();
    }

    /** Adjusts zoom with the mouse wheel; returns true when the scroll was consumed. */
    public boolean onScroll(double amount) {
        if (!isEnabled() || !zooming || !scroll.on() || amount == 0) {
            return false;
        }
        scrollFactor = Math.max(1.5, Math.min(50, scrollFactor * (amount > 0 ? 1.25 : 0.8)));
        return true;
    }
}
