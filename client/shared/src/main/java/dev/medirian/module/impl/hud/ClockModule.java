package dev.medirian.module.impl.hud;

import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.setting.BooleanSetting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Real-world time. */
public final class ClockModule extends Module {

    private final BooleanSetting twelveHour;
    private final BooleanSetting seconds;
    private final BooleanSetting date;
    private SimpleDateFormat format;
    private String pattern = "";

    public ClockModule() {
        super("clock", "Clock", Category.HUD, "Shows the current time.");
        twelveHour = add(new BooleanSetting("twelveHour", "12-hour format", false));
        seconds = add(new BooleanSetting("seconds", "Show seconds", false));
        date = add(new BooleanSetting("date", "Show date", false));
        hud(new TextHudElement(this, Anchor.TOP_CENTER, 0, 24) {
            @Override
            protected void collect(Lines out, boolean editor) {
                out.add(I18n.tr("hud.clock", "Time"), formatter().format(new Date()));
            }

            @Override
            protected long refreshIntervalMs() {
                return 500;
            }
        });
    }

    private SimpleDateFormat formatter() {
        String wanted = (date.on() ? "dd.MM " : "")
                + (twelveHour.on() ? "h:mm" : "HH:mm")
                + (seconds.on() ? ":ss" : "")
                + (twelveHour.on() ? " a" : "");
        if (!wanted.equals(pattern)) {
            pattern = wanted;
            format = new SimpleDateFormat(wanted, Locale.ENGLISH);
        }
        return format;
    }
}
