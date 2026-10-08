package dev.medirian.module.impl.performance;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.perf.SystemStats;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.util.Format;

/** JVM memory usage (and optionally process CPU load). */
public final class MemoryModule extends Module {

    /** Memory display format. */
    public enum Display { PERCENT, USED_OF_MAX, BOTH }

    private final ModeSetting<Display> display;
    private final BooleanSetting showCpu;

    public MemoryModule() {
        super("memory", "Memory Monitor", Category.PERFORMANCE, "Shows Java memory usage and CPU load.");
        display = add(new ModeSetting<Display>("display", "Display", Display.BOTH));
        showCpu = add(new BooleanSetting("showCpu", "Show CPU load", false));
        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 106) {
            @Override
            protected void collect(Lines out, boolean editor) {
                SystemStats stats = Medirian.get().performance().system();
                String percent = Math.round(stats.memoryPercent()) + "%";
                String usage = Format.bytes(stats.usedBytes()) + " / " + Format.bytes(stats.maxBytes());
                String value;
                switch (display.get()) {
                    case PERCENT:
                        value = percent;
                        break;
                    case USED_OF_MAX:
                        value = usage;
                        break;
                    default:
                        value = percent + "  " + usage;
                        break;
                }
                out.add(I18n.tr("hud.memory", "Memory"), value);
                if (showCpu.on()) {
                    double cpu = stats.cpuLoad();
                    out.add("CPU", cpu < 0 ? "-" : Math.round(cpu * 100) + "%");
                }
            }

            @Override
            protected long refreshIntervalMs() {
                return 500;
            }
        });
    }
}
