package dev.medirian.module.impl.hud;

import dev.medirian.core.Medirian;
import dev.medirian.event.Events;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.PlayerView;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.util.Format;

/** Movement speed averaged over the last half second (10 ticks). */
public final class SpeedModule extends Module {

    public enum Unit implements ModeSetting.Labeled {
        BLOCKS_PER_SECOND("m/s"),
        KILOMETERS_PER_HOUR("km/h");

        private final String label;

        Unit(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    private static final int WINDOW = 10;
    /** Larger jumps between two ticks are teleports, not movement. */
    private static final double TELEPORT = 10;

    private final ModeSetting<Unit> unit;
    private final BooleanSetting vertical;
    private final double[] distances = new double[WINDOW];
    private int index;
    private int samples;
    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;

    public SpeedModule() {
        super("speed", "Speed", Category.HUD, "Shows how fast you move.");
        unit = add(new ModeSetting<Unit>("unit", "Unit", Unit.BLOCKS_PER_SECOND));
        vertical = add(new BooleanSetting("vertical", "Include vertical movement", false));
        on(Events.Tick.class, e -> sample());
        on(Events.WorldJoin.class, e -> reset());
        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 89) {
            @Override
            protected void collect(Lines out, boolean editor) {
                double perSecond = blocksPerSecond();
                String value = unit.is(Unit.KILOMETERS_PER_HOUR)
                        ? Format.decimals(perSecond * 3.6, 1) + " km/h"
                        : Format.decimals(perSecond, 2) + " m/s";
                out.add(I18n.tr("hud.speed", "Speed"), value);
            }

            @Override
            protected long refreshIntervalMs() {
                return 100;
            }
        });
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        hasLast = false;
        samples = 0;
        index = 0;
    }

    private void sample() {
        PlayerView player = Medirian.get().game().player();
        if (player == null) {
            reset();
            return;
        }
        double x = player.x();
        double y = player.y();
        double z = player.z();
        if (hasLast) {
            double dx = x - lastX;
            double dy = vertical.on() ? y - lastY : 0;
            double dz = z - lastZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            record(distance > TELEPORT ? 0 : distance);
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLast = true;
    }

    /** Adds one tick's travelled distance (package-private for tests). */
    void record(double blocksThisTick) {
        distances[index] = blocksThisTick;
        index = (index + 1) % WINDOW;
        samples = Math.min(WINDOW, samples + 1);
    }

    /** Average over the recorded ticks (20 ticks per second). */
    public double blocksPerSecond() {
        if (samples == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += distances[i];
        }
        return sum / samples * 20;
    }
}
