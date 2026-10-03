package dev.meridian.module.impl.performance;

import dev.meridian.core.Meridian;
import dev.meridian.hud.Anchor;
import dev.meridian.hud.HudElement;
import dev.meridian.hud.HudStyle;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.perf.FrameStats;
import dev.meridian.perf.PerformanceManager;
import dev.meridian.render.Gfx;
import dev.meridian.render.UiDraw;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.setting.NumberSetting;
import dev.meridian.util.Format;

/** Frame time graph of the most recent frames with average and 1% low. */
public final class FpsGraphModule extends Module {

    private final NumberSetting frames;
    private final NumberSetting graphHeight;
    private final BooleanSetting showStats;

    public FpsGraphModule() {
        super("fpsgraph", "FPS Graph", Category.PERFORMANCE, "Frame time graph to spot stutters.");
        frames = add(new NumberSetting("frames", "Frames shown", 120, 60, FrameStats.CAPACITY, 10));
        graphHeight = add(new NumberSetting("height", "Graph height", 30, 16, 80, 2));
        showStats = add(new BooleanSetting("showStats", "Show statistics", true));
        hud(new Element());
    }

    private final class Element extends HudElement {

        Element() {
            super(FpsGraphModule.this, Anchor.TOP_RIGHT, -4, 110);
        }

        @Override
        public void render(Gfx g, boolean editor) {
            PerformanceManager perf = Meridian.get().performance();
            FrameStats stats = perf.frames();
            int count = frames.intValue();
            int graph = graphHeight.intValue();
            int statsHeight = showStats.on() ? 11 : 0;
            width = count + 6;
            height = graph + 6 + statsHeight;
            UiDraw.roundRect(g, 0, 0, width, height, 2.5f, HudStyle.DEFAULT_BACKGROUND);

            // scale: 2× the average frame time fills the graph, at least 33 ms
            float scaleMs = Math.max(33.3f, stats.averageFrameMs() * 2.5f);
            int bottom = 3 + graph;
            int targetY = bottom - Math.round(graph * (16.7f / scaleMs));
            if (targetY > 3) {
                g.fill(3, targetY, width - 3, targetY + 1, 0x30FFFFFF);
            }
            int available = Math.min(count, stats.size());
            for (int i = 0; i < available; i++) {
                float ms = stats.frameMs(i);
                int barHeight = Math.max(1, Math.min(graph, Math.round(graph * ms / scaleMs)));
                int x = width - 3 - i - 1;
                g.fill(x, bottom - barHeight, x + 1, bottom, color(ms, stats.averageFrameMs()));
            }
            if (showStats.on()) {
                String text = Math.round(stats.averageFps()) + " FPS  "
                        + Format.decimals(stats.averageFrameMs(), 1) + " ms  1%: " + Math.round(stats.onePercentLowFps());
                g.push();
                g.translate(3, bottom + 3);
                g.scale(0.75f, 0.75f);
                g.text(text, 0, 0, 0xFFB7B0C8, false);
                g.pop();
            }
        }

        private int color(float ms, float average) {
            if (ms > average * 2.5f && ms > 25) {
                return 0xFFE5566A; // stutter
            }
            if (ms > average * 1.5f) {
                return 0xFFE5B454;
            }
            return 0xCC9B55D6;
        }
    }
}
