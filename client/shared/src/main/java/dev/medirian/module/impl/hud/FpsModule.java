package dev.medirian.module.impl.hud;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.perf.FrameStats;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.util.Format;

/** Frames per second measured by Medirian's own frame timer (more precise than the vanilla 1 s counter). */
public final class FpsModule extends Module {

    private final BooleanSetting showFrameTime;
    private final BooleanSetting showLow;

    public FpsModule() {
        super("fps", "FPS", Category.HUD, "Shows frames per second, optionally frame time and 1% low.");
        enableByDefault();
        showFrameTime = add(new BooleanSetting("frameTime", "Show frame time", false));
        showLow = add(new BooleanSetting("onePercentLow", "Show 1% low", false));
        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 4) {
            @Override
            protected void collect(Lines out, boolean editor) {
                FrameStats frames = Medirian.get().performance().frames();
                out.add("FPS", String.valueOf(Math.round(frames.averageFps())));
                if (showFrameTime.on()) {
                    out.add("Frame", Format.decimals(frames.averageFrameMs(), 1) + " ms");
                }
                if (showLow.on()) {
                    out.add("1% low", String.valueOf(Math.round(frames.onePercentLowFps())));
                }
            }

            @Override
            protected long refreshIntervalMs() {
                return 250;
            }
        });
    }
}
