package dev.meridian.module.impl.hud;

import dev.meridian.hud.Anchor;
import dev.meridian.hud.TextHudElement;
import dev.meridian.i18n.I18n;
import dev.meridian.input.Key;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.setting.KeySetting;
import dev.meridian.util.Format;

/** A stopwatch controlled with two key bindings (start/pause and reset). */
public final class StopwatchModule extends Module {

    private long startedAtMs;
    private long accumulatedMs;
    private boolean running;

    public StopwatchModule() {
        super("stopwatch", "Stopwatch", Category.HUD, "A stopwatch you control with key bindings.");
        add(new KeySetting("startStop", "Start / pause key", Key.NONE).onPress(this::toggleRunning));
        add(new KeySetting("reset", "Reset key", Key.NONE).onPress(this::reset));
        hud(new TextHudElement(this, Anchor.TOP_CENTER, 0, 42) {
            @Override
            protected void collect(Lines out, boolean editor) {
                String state = running ? "" : " (" + I18n.tr("hud.stopwatch.paused", "paused") + ")";
                out.add(I18n.tr("hud.stopwatch", "Stopwatch"), Format.preciseDuration(elapsed()) + (elapsed() > 0 ? state : ""));
            }
        });
    }

    private void toggleRunning() {
        if (running) {
            accumulatedMs += System.currentTimeMillis() - startedAtMs;
            running = false;
        } else {
            startedAtMs = System.currentTimeMillis();
            running = true;
        }
    }

    private void reset() {
        running = false;
        accumulatedMs = 0;
    }

    private long elapsed() {
        return accumulatedMs + (running ? System.currentTimeMillis() - startedAtMs : 0);
    }
}
