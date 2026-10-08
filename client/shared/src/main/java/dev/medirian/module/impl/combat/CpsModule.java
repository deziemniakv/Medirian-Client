package dev.medirian.module.impl.combat;

import dev.medirian.core.Medirian;
import dev.medirian.event.Events;
import dev.medirian.hud.HudSurface;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.input.InputStats;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ColorSetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;

/** Clicks per second for left and right mouse buttons, with history graph and click flash. */
public final class CpsModule extends Module {

    /** How left/right CPS are laid out. */
    public enum Layout { COMBINED, SEPARATE, LEFT_ONLY }

    private static final int HISTORY = 50; // 5 s at one sample per 100 ms

    private final NumberSetting window;
    private final ModeSetting<Layout> layout;
    private final BooleanSetting clickFlash;
    private final BooleanSetting history;
    private final ColorSetting graphColor;

    private final float[] leftHistory = new float[HISTORY];
    private int historyHead;
    private int tickCounter;

    public CpsModule() {
        super("cps", "CPS", Category.COMBAT, "Clicks per second for both mouse buttons.");
        window = add(new NumberSetting("window", "Click window", 1000, 250, 3000, 50).unit(" ms")
                .description("Time window used to count clicks."));
        layout = add(new ModeSetting<Layout>("layout", "Layout", Layout.COMBINED));
        clickFlash = add(new BooleanSetting("clickFlash", "Click animation", true));
        history = add(new BooleanSetting("history", "CPS history graph", false));
        graphColor = add(new ColorSetting("graphColor", "Graph color", 0xFF9B55D6).visibleWhen(history::on));

        on(Events.Tick.class, e -> sampleHistory());

        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 38) {
            @Override
            protected void collect(Lines out, boolean editor) {
                InputStats stats = Medirian.get().inputStats();
                long now = System.currentTimeMillis();
                long ms = window.intValue();
                int left = Math.round(stats.left().cps(now, ms));
                int right = Math.round(stats.right().cps(now, ms));
                switch (layout.get()) {
                    case SEPARATE:
                        out.add("LMB", left + " CPS");
                        out.add("RMB", right + " CPS");
                        break;
                    case LEFT_ONLY:
                        out.add("CPS", String.valueOf(left));
                        break;
                    default:
                        out.add("CPS", left + " | " + right);
                        break;
                }
            }

            @Override
            protected long refreshIntervalMs() {
                return 50;
            }

            @Override
            public void render(Gfx g, boolean editor) {
                super.render(g, editor);
                if (clickFlash.on()) {
                    long since = System.currentTimeMillis() - Medirian.get().inputStats().left().lastClickMs();
                    if (since < 180) {
                        UiDraw.roundRect(g, 0, 0, width, textHeight(), HudSurface.radius(), Colors.fade(0x559B55D6, 1f - since / 180f));
                    }
                }
            }

            @Override
            protected int extraHeight() {
                return history.on() ? 18 : 0;
            }

            @Override
            protected void renderExtra(Gfx g, int y, boolean editor) {
                drawHistory(g, y, width);
            }
        });
    }

    private void sampleHistory() {
        if (++tickCounter < 2) {
            return;
        }
        tickCounter = 0;
        long now = System.currentTimeMillis();
        leftHistory[historyHead] = Medirian.get().inputStats().left().cps(now, window.intValue());
        historyHead = (historyHead + 1) % HISTORY;
    }

    private void drawHistory(Gfx g, int y, int width) {
        int graphHeight = 18;
        HudSurface.panel(g, 0, y, width, graphHeight, 0x8C0B0A10);
        float max = 10;
        for (float v : leftHistory) {
            max = Math.max(max, v);
        }
        float barWidth = (width - 4) / (float) HISTORY;
        for (int i = 0; i < HISTORY; i++) {
            float value = leftHistory[(historyHead + i) % HISTORY];
            int barHeight = Math.round((graphHeight - 4) * value / max);
            if (barHeight <= 0) {
                continue;
            }
            int x1 = Math.round(2 + i * barWidth);
            int x2 = Math.max(x1 + 1, Math.round(2 + (i + 1) * barWidth) - 1);
            g.fill(x1, y + graphHeight - 2 - barHeight, x2, y + graphHeight - 2, graphColor.argb());
        }
    }
}
