package dev.medirian.module.impl.render;

import dev.medirian.core.Medirian;
import dev.medirian.event.Events;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.render.Gfx;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ColorSetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;

/**
 * Custom crosshair replacing the vanilla one. Drawn at real-pixel resolution so thin lines stay
 * sharp at every GUI scale. Sizes are in real pixels.
 */
public final class CrosshairModule extends Module {

    /** Crosshair shapes. */
    public enum Shape { CROSS, CROSS_DOT, DOT, T_SHAPE, CIRCLE }

    private final ModeSetting<Shape> shape;
    private final NumberSetting length;
    private final NumberSetting gap;
    private final NumberSetting thickness;
    private final ColorSetting color;
    private final BooleanSetting outline;
    private final ColorSetting outlineColor;
    private final BooleanSetting hideInThirdPerson;

    public CrosshairModule() {
        super("crosshair", "Custom Crosshair", Category.RENDER, "Replace the vanilla crosshair with your own.");
        requires(Capability.CROSSHAIR);
        shape = add(new ModeSetting<Shape>("shape", "Shape", Shape.CROSS));
        length = add(new NumberSetting("length", "Length", 8, 2, 30, 1).unit(" px"));
        gap = add(new NumberSetting("gap", "Gap", 3, 0, 20, 1).unit(" px"));
        thickness = add(new NumberSetting("thickness", "Thickness", 2, 1, 6, 1).unit(" px"));
        color = add(new ColorSetting("color", "Color", 0xFFFFFFFF));
        outline = add(new BooleanSetting("outline", "Outline", true));
        outlineColor = add(new ColorSetting("outlineColor", "Outline color", 0xB0000000).visibleWhen(outline::on));
        hideInThirdPerson = add(new BooleanSetting("hideInThirdPerson", "Hide in third person", true));
        on(Events.RenderHud.class, e -> render(e.gfx));
    }

    private void render(Gfx g) {
        if (Medirian.get().game().player() == null || (hideInThirdPerson.on() && Medirian.get().game().thirdPerson())) {
            return;
        }
        double pixels = g.pixelScale();
        int cx = (int) Math.round(g.width() * pixels / 2.0);
        int cy = (int) Math.round(g.height() * pixels / 2.0);
        g.push();
        float inv = (float) (1.0 / pixels);
        g.scale(inv, inv);
        if (outline.on()) {
            draw(g, cx, cy, 1, outlineColor.argb());
        }
        draw(g, cx, cy, 0, color.argb());
        g.pop();
    }

    /** Draws the shape centred on (cx, cy) in real pixels, grown by {@code grow} pixels for outlines. */
    private void draw(Gfx g, int cx, int cy, int grow, int argb) {
        int len = length.intValue();
        int gp = gap.intValue();
        int t = thickness.intValue();
        int half = t / 2;
        Shape s = shape.get();
        if (s == Shape.CROSS || s == Shape.CROSS_DOT || s == Shape.T_SHAPE) {
            // left, right, bottom, (top)
            rect(g, cx - gp - len, cy - half, cx - gp, cy - half + t, grow, argb);
            rect(g, cx + gp + (t % 2), cy - half, cx + gp + len + (t % 2), cy - half + t, grow, argb);
            rect(g, cx - half, cy + gp + (t % 2), cx - half + t, cy + gp + len + (t % 2), grow, argb);
            if (s != Shape.T_SHAPE) {
                rect(g, cx - half, cy - gp - len, cx - half + t, cy - gp, grow, argb);
            }
        }
        if (s == Shape.DOT || s == Shape.CROSS_DOT) {
            int dot = Math.max(2, t);
            rect(g, cx - dot / 2, cy - dot / 2, cx - dot / 2 + dot, cy - dot / 2 + dot, grow, argb);
        }
        if (s == Shape.CIRCLE) {
            ring(g, cx, cy, gp + len / 2, t, grow, argb);
        }
    }

    private static void rect(Gfx g, int x1, int y1, int x2, int y2, int grow, int argb) {
        g.fill(x1 - grow, y1 - grow, x2 + grow, y2 + grow, argb);
    }

    private static void ring(Gfx g, int cx, int cy, int radius, int thickness, int grow, int argb) {
        int outer = radius + thickness / 2 + grow;
        int inner = Math.max(0, radius - (thickness + 1) / 2 - grow);
        for (int dy = -outer; dy < outer; dy++) {
            double y = dy + 0.5;
            int outerHalf = (int) Math.round(Math.sqrt(Math.max(0, outer * outer - y * y)));
            int innerHalf = Math.abs(y) < inner ? (int) Math.round(Math.sqrt(inner * inner - y * y)) : 0;
            if (innerHalf == 0) {
                g.fill(cx - outerHalf, cy + dy, cx + outerHalf, cy + dy + 1, argb);
            } else {
                g.fill(cx - outerHalf, cy + dy, cx - innerHalf, cy + dy + 1, argb);
                g.fill(cx + innerHalf, cy + dy, cx + outerHalf, cy + dy + 1, argb);
            }
        }
    }
}
