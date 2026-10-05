package dev.medirian.module.impl.world;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.HudElement;
import dev.medirian.hud.HudStyle;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.PlayerView;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.ColorSetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;
import dev.medirian.util.Format;

/** Facing direction as text or as a scrolling compass strip. */
public final class DirectionModule extends Module {

    /** Display style. */
    public enum Style { TEXT, COMPASS }

    private static final String[] CARDINALS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] NAMES = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};
    private static final String[] AXES = {"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};

    private final ModeSetting<Style> style;
    private final NumberSetting compassWidth;
    private final ColorSetting markerColor;

    public DirectionModule() {
        super("direction", "Direction", Category.WORLD, "Shows which way you are facing.");
        style = add(new ModeSetting<Style>("style", "Style", Style.COMPASS));
        compassWidth = add(new NumberSetting("compassWidth", "Compass width", 140, 80, 260, 10)
                .visibleWhen(() -> style.is(Style.COMPASS)));
        markerColor = add(new ColorSetting("markerColor", "Marker color", 0xFF9B55D6));
        hud(new Element());
    }

    private static int index(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        return Math.round(normalized / 45f) % 8;
    }

    /** Short facing label for a Minecraft yaw, e.g. {@code "N (-Z)"}. */
    public static String facing(float yaw) {
        int i = index(yaw);
        return CARDINALS[i] + " (" + AXES[i] + ")";
    }

    private final class Element extends HudElement {

        Element() {
            super(DirectionModule.this, Anchor.TOP_CENTER, 0, 4);
        }

        @Override
        public boolean hasContent() {
            return Medirian.get().game().player() != null;
        }

        @Override
        public void render(Gfx g, boolean editor) {
            PlayerView player = Medirian.get().game().player();
            float yaw = player == null ? 180f : player.yaw();
            if (style.is(Style.TEXT)) {
                int i = index(yaw);
                String text = I18n.tr("hud.direction." + CARDINALS[i].toLowerCase(), NAMES[i]) + "  " + AXES[i];
                width = g.textWidth(text) + 10;
                height = 16;
                UiDraw.roundRect(g, 0, 0, width, height, 2.5f, HudStyle.DEFAULT_BACKGROUND);
                g.text(text, 5, 4, HudStyle.DEFAULT_TEXT, true);
                return;
            }
            drawCompass(g, yaw);
        }

        private void drawCompass(Gfx g, float yaw) {
            width = compassWidth.intValue();
            height = 18;
            UiDraw.roundRect(g, 0, 0, width, height, 2.5f, HudStyle.DEFAULT_BACKGROUND);
            // compass bearing: 0 = north, 90 = east
            float bearing = ((yaw + 180f) % 360f + 360f) % 360f;
            float span = 150f; // degrees visible across the strip
            float pxPerDeg = width / span;
            g.enableScissor(0, 0, width, height);
            for (int deg = 0; deg < 360; deg += 15) {
                float delta = deg - bearing;
                if (delta > 180) {
                    delta -= 360;
                } else if (delta < -180) {
                    delta += 360;
                }
                if (Math.abs(delta) > span / 2 + 10) {
                    continue;
                }
                float x = width / 2f + delta * pxPerDeg;
                float fade = 1f - Math.min(1f, Math.abs(delta) / (span / 2));
                if (deg % 45 == 0) {
                    String label = label(deg);
                    int color = Colors.fade(deg == 0 ? 0xFFE8833A : HudStyle.DEFAULT_TEXT, 0.35f + 0.65f * fade);
                    g.text(label, x - g.textWidth(label) / 2f, 5, color, true);
                } else {
                    g.fill(Math.round(x), 7, Math.round(x) + 1, 11, Colors.fade(0xFF9A94AB, 0.3f + 0.7f * fade));
                }
            }
            g.disableScissor();
            int center = width / 2;
            g.fill(center, height - 3, center + 1, height, markerColor.argb());
            g.fill(center, 0, center + 1, 2, markerColor.argb());
            String degrees = Format.decimals(bearing, 0) + "°";
            g.push();
            g.translate(width - g.textWidth(degrees) * 0.6f - 3, 1);
            g.scale(0.6f, 0.6f);
            g.text(degrees, 0, 0, 0xFF9A94AB, false);
            g.pop();
        }

        private String label(int degrees) {
            switch (degrees) {
                case 0: return "N";
                case 45: return "NE";
                case 90: return "E";
                case 135: return "SE";
                case 180: return "S";
                case 225: return "SW";
                case 270: return "W";
                default: return "NW";
            }
        }
    }
}
