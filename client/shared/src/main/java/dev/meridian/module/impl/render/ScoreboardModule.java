package dev.meridian.module.impl.render;

import dev.meridian.core.Meridian;
import dev.meridian.hud.Anchor;
import dev.meridian.hud.HudElement;
import dev.meridian.hud.HudStyle;
import dev.meridian.i18n.I18n;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.platform.SidebarView;
import dev.meridian.render.Gfx;
import dev.meridian.render.UiDraw;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.setting.ColorSetting;

/** Movable, restyled sidebar scoreboard (replaces the vanilla one while enabled). */
public final class ScoreboardModule extends Module {

    private final BooleanSetting hideNumbers;
    private final ColorSetting background;
    private final ColorSetting titleBackground;
    private final BooleanSetting shadow;

    public ScoreboardModule() {
        super("scoreboard", "Scoreboard", Category.RENDER, "Move, scale and restyle the sidebar scoreboard.");
        requires(Capability.SCOREBOARD);
        hideNumbers = add(new BooleanSetting("hideNumbers", "Hide red numbers", true));
        background = add(new ColorSetting("background", "Background", 0x66000000));
        titleBackground = add(new ColorSetting("titleBackground", "Title background", 0x80000000));
        shadow = add(new BooleanSetting("shadow", "Text shadow", false));
        hud(new Element());
    }

    private final class Element extends HudElement {

        Element() {
            super(ScoreboardModule.this, Anchor.MIDDLE_RIGHT, -2, 0);
        }

        @Override
        public boolean hasContent() {
            return Meridian.get().game().sidebar() != null;
        }

        @Override
        public void render(Gfx g, boolean editor) {
            SidebarView sidebar = Meridian.get().game().sidebar();
            int lineHeight = g.fontHeight();
            if (sidebar == null) {
                String text = I18n.tr("hud.scoreboard.none", "Scoreboard (no sidebar on this server)");
                width = g.textWidth(text) + 8;
                height = lineHeight + 6;
                UiDraw.roundRect(g, 0, 0, width, height, 2f, background.argb());
                g.text(text, 4, 3, HudStyle.DEFAULT_TEXT, false);
                return;
            }
            int lines = sidebar.lineCount();
            int maxWidth = g.richTextWidth(sidebar.title());
            for (int i = 0; i < lines; i++) {
                int lineWidth = g.richTextWidth(sidebar.line(i));
                Object score = sidebar.score(i);
                if (!hideNumbers.on() && score != null) {
                    lineWidth += g.richTextWidth(score) + 6;
                }
                maxWidth = Math.max(maxWidth, lineWidth);
            }
            width = maxWidth + 6;
            height = (lines + 1) * lineHeight + 2;
            g.fill(0, 0, width, lineHeight + 1, titleBackground.argb());
            g.fill(0, lineHeight + 1, width, height, background.argb());
            Object title = sidebar.title();
            g.richText(title, (width - g.richTextWidth(title)) / 2f, 1, 0xFFFFFFFF, shadow.on());
            for (int i = 0; i < lines; i++) {
                float y = (i + 1) * lineHeight + 1;
                g.richText(sidebar.line(i), 3, y, 0xFFFFFFFF, shadow.on());
                Object score = sidebar.score(i);
                if (!hideNumbers.on() && score != null) {
                    g.richText(score, width - 3 - g.richTextWidth(score), y, 0xFFFF5555, shadow.on());
                }
            }
        }
    }
}
