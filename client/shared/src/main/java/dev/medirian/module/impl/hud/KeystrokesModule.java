package dev.medirian.module.impl.hud;

import dev.medirian.core.Medirian;
import dev.medirian.hud.HudSurface;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.HudElement;
import dev.medirian.hud.HudStyle;
import dev.medirian.i18n.I18n;
import dev.medirian.input.ClickTracker;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.InputView;
import dev.medirian.platform.InputView.GameKey;
import dev.medirian.render.Anim;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ColorSetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;

/**
 * Keystrokes: W / A S D / LMB RMB / Space (+ optional Sneak/Sprint row) with press animation and
 * per-button CPS. Key labels follow the player's actual Minecraft key bindings.
 */
public final class KeystrokesModule extends Module {

    /** Visual style of the key boxes. */
    public enum Style { FILLED, OUTLINED, MINIMAL }

    private static final String LAYOUT = "Layout";
    private static final String COLORS = "Colors";

    private final ModeSetting<Style> style;
    private final NumberSetting keySize;
    private final NumberSetting gap;
    private final BooleanSetting showMouse;
    private final BooleanSetting showCps;
    private final BooleanSetting showSpace;
    private final BooleanSetting showModifiers;
    private final BooleanSetting rounded;
    private final BooleanSetting animate;
    private final ColorSetting background;
    private final ColorSetting pressedBackground;
    private final ColorSetting textColor;
    private final ColorSetting pressedText;
    private final BooleanSetting shadow;

    private final Anim[] press = new Anim[11];

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", Category.HUD, "Shows movement keys and mouse buttons with CPS.");
        style = add(new ModeSetting<Style>("style", "Style", Style.FILLED).group(LAYOUT));
        keySize = add(new NumberSetting("keySize", "Key size", 22, 14, 36, 1).group(LAYOUT));
        gap = add(new NumberSetting("gap", "Spacing", 2, 0, 8, 1).group(LAYOUT));
        showMouse = add(new BooleanSetting("showMouse", "Show mouse buttons", true).group(LAYOUT));
        showCps = add(new BooleanSetting("showCps", "Show CPS on mouse buttons", true).group(LAYOUT).visibleWhen(this::mouseShown));
        showSpace = add(new BooleanSetting("showSpace", "Show space bar", true).group(LAYOUT));
        showModifiers = add(new BooleanSetting("showModifiers", "Show sneak / sprint", false).group(LAYOUT));
        rounded = add(new BooleanSetting("rounded", "Rounded corners", true).group(LAYOUT));
        animate = add(new BooleanSetting("animate", "Press animation", true).group(LAYOUT));
        background = add(new ColorSetting("background", "Background", HudStyle.DEFAULT_BACKGROUND).group(COLORS));
        pressedBackground = add(new ColorSetting("pressedBackground", "Pressed background", 0xD99B55D6).group(COLORS));
        textColor = add(new ColorSetting("textColor", "Text", HudStyle.DEFAULT_TEXT).group(COLORS));
        pressedText = add(new ColorSetting("pressedText", "Pressed text", 0xFFFFFFFF).group(COLORS));
        shadow = add(new BooleanSetting("shadow", "Text shadow", true).group(COLORS));
        for (int i = 0; i < press.length; i++) {
            press[i] = new Anim(0f, 22f);
        }
        hud(new Element());
    }

    private boolean mouseShown() {
        return showMouse.on();
    }

    private final class Element extends HudElement {

        Element() {
            super(KeystrokesModule.this, Anchor.TOP_RIGHT, -4, 4);
        }

        @Override
        public void render(Gfx g, boolean editor) {
            InputView input = Medirian.get().platform().input();
            int k = keySize.intValue();
            int sp = gap.intValue();
            int full = k * 3 + sp * 2;
            int y = 0;

            // row 1: forward
            key(g, 0, input, GameKey.FORWARD, k + sp, y, k, k, input.label(GameKey.FORWARD), null);
            y += k + sp;
            // row 2: left / back / right
            key(g, 1, input, GameKey.LEFT, 0, y, k, k, input.label(GameKey.LEFT), null);
            key(g, 2, input, GameKey.BACK, k + sp, y, k, k, input.label(GameKey.BACK), null);
            key(g, 3, input, GameKey.RIGHT, (k + sp) * 2, y, k, k, input.label(GameKey.RIGHT), null);
            y += k + sp;
            // row 3: mouse buttons
            if (showMouse.on()) {
                int half = (full - sp) / 2;
                int mouseHeight = showCps.on() ? Math.max(k, 22) : k;
                long now = System.currentTimeMillis();
                ClickTracker left = Medirian.get().inputStats().left();
                ClickTracker right = Medirian.get().inputStats().right();
                key(g, 4, input, GameKey.ATTACK, 0, y, half, mouseHeight, "LMB",
                        showCps.on() ? Math.round(left.cps(now, 1000)) + " CPS" : null);
                key(g, 5, input, GameKey.USE, half + sp, y, full - half - sp, mouseHeight, "RMB",
                        showCps.on() ? Math.round(right.cps(now, 1000)) + " CPS" : null);
                y += mouseHeight + sp;
            }
            // row 4: space bar
            if (showSpace.on()) {
                int spaceHeight = Math.max(8, k * 11 / 20);
                key(g, 6, input, GameKey.JUMP, 0, y, full, spaceHeight, null, null);
                y += spaceHeight + sp;
            }
            // row 5: sneak / sprint
            if (showModifiers.on()) {
                int half = (full - sp) / 2;
                int modHeight = Math.max(12, k * 3 / 4);
                key(g, 7, input, GameKey.SNEAK, 0, y, half, modHeight, I18n.tr("hud.keystrokes.sneak", "Sneak"), null);
                key(g, 8, input, GameKey.SPRINT, half + sp, y, full - half - sp, modHeight, I18n.tr("hud.keystrokes.sprint", "Sprint"), null);
                y += modHeight + sp;
            }
            width = full;
            height = y - sp;
        }

        private void key(Gfx g, int index, InputView input, GameKey gameKey, int x, int y, int w, int h,
                         String label, String subLabel) {
            boolean down = input.isDown(gameKey);
            Anim anim = press[index].target(down ? 1f : 0f);
            float t = animate.on() ? anim.get() : (down ? 1f : 0f);
            Style mode = style.get();
            int bg = Colors.lerp(background.argb(), pressedBackground.argb(), t);
            int fg = Colors.lerp(textColor.argb(), pressedText.argb(), t);
            float radius = rounded.on() ? 3f : 0f;

            if (mode == Style.FILLED) {
                if (HudSurface.glass()) {
                    // every key is its own pane of glass; a press lights it up
                    HudSurface.panel(g, x, y, w, h, bg);
                    if (t > 0.01f) {
                        UiDraw.roundRect(g, x, y, w, h, HudSurface.radius(), Colors.fade(pressedBackground.argb(), t * 0.8f));
                    }
                } else {
                    UiDraw.roundRect(g, x, y, w, h, radius, bg);
                }
            } else if (mode == Style.OUTLINED) {
                UiDraw.roundRect(g, x, y, w, h, radius, Colors.fade(bg, t));
                UiDraw.hairline(g, x, y, w, h, Colors.lerp(Colors.withAlpha(textColor.argb(), 110), pressedBackground.argb(), t));
            } else if (t > 0.01f) {
                UiDraw.roundRect(g, x, y, w, h, radius, Colors.fade(pressedBackground.argb(), t * 0.6f));
            }

            int fontHeight = g.fontHeight();
            if (label == null) {
                // space bar: a short horizontal line
                int lineWidth = Math.max(8, w / 4);
                g.fill(x + (w - lineWidth) / 2, y + h / 2 - 1, x + (w + lineWidth) / 2, y + h / 2, fg);
                return;
            }
            if (subLabel == null) {
                g.text(label, x + (w - g.textWidth(label)) / 2f, y + (h - fontHeight) / 2f + 1, fg, shadow.on());
                return;
            }
            float labelY = y + h / 2f - fontHeight + 1;
            g.text(label, x + (w - g.textWidth(label)) / 2f, labelY, fg, shadow.on());
            g.push();
            float small = 0.75f;
            float subWidth = g.textWidth(subLabel) * small;
            g.translate(x + (w - subWidth) / 2f, y + h / 2f + 1.5f);
            g.scale(small, small);
            g.text(subLabel, 0, 0, Colors.fade(fg, 0.8f), shadow.on());
            g.pop();
        }
    }
}
