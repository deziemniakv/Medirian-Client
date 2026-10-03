package dev.meridian;

import dev.meridian.event.EventBus;
import dev.meridian.event.Listener;
import dev.meridian.hud.Anchor;
import dev.meridian.i18n.I18n;
import dev.meridian.input.ClickTracker;
import dev.meridian.input.Key;
import dev.meridian.render.Colors;
import dev.meridian.setting.ColorSetting;
import dev.meridian.setting.NumberSetting;
import dev.meridian.util.Format;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoreTest {

    @Test
    void clickTrackerCountsWithinWindow() {
        ClickTracker tracker = new ClickTracker();
        for (int i = 0; i < 10; i++) {
            tracker.click(1000 + i * 90L);
        }
        assertEquals(10f, tracker.cps(1900, 1000), 0.001);
        assertEquals(5f, tracker.cps(1900, 2000), 0.001, "2 s window halves the rate");
        assertEquals(0f, tracker.cps(5000, 1000), 0.001);
    }

    @Test
    void eventBusDispatchesByExactTypeAndSurvivesFailures() {
        EventBus bus = new EventBus();
        AtomicInteger hits = new AtomicInteger();
        Listener<String> failing = s -> {
            throw new IllegalStateException("boom");
        };
        bus.subscribe(String.class, failing);
        bus.subscribe(String.class, s -> hits.incrementAndGet());
        bus.post("event");
        bus.post(42);
        assertEquals(1, hits.get());
        bus.unsubscribe(String.class, failing);
        bus.post("again");
        assertEquals(2, hits.get());
    }

    @Test
    void anchorsResolveAndInvert() {
        float screenW = 960;
        float elementW = 50;
        for (Anchor anchor : Anchor.values()) {
            float x = anchor.resolveX(12, elementW, screenW);
            assertEquals(12, anchor.offsetX(x, elementW, screenW), 0.0001);
        }
        assertEquals(Anchor.BOTTOM_RIGHT, Anchor.fromPosition(900, 500, 50, 20, 960, 540));
        assertEquals(Anchor.TOP_LEFT, Anchor.fromPosition(4, 4, 50, 20, 960, 540));
        assertEquals(Anchor.CENTER, Anchor.fromPosition(455, 260, 50, 20, 960, 540));
        assertEquals(960 - 50 - 4, Anchor.TOP_RIGHT.resolveX(-4, 50, 960), 0.001);
    }

    @Test
    void numberSettingClampsAndSnaps() {
        NumberSetting setting = new NumberSetting("n", "N", 1, 0, 2, 0.1);
        setting.set(0.33333);
        assertEquals(0.3, setting.doubleValue(), 1e-9);
        setting.set(99.0);
        assertEquals(2.0, setting.doubleValue(), 1e-9);
        setting.setProgress(0.5);
        assertEquals(1.0, setting.doubleValue(), 1e-9);
    }

    @Test
    void colorHexParsing() {
        assertEquals(0xFF9B55D6, (int) ColorSetting.parseHex("#9B55D6"));
        assertEquals(0x809B55D6, (int) ColorSetting.parseHex("809b55d6"));
        assertNull(ColorSetting.parseHex("#12"));
        assertEquals("#FF9B55D6", ColorSetting.toHex(0xFF9B55D6));
    }

    @Test
    void hsvRoundTrip() {
        int color = 0xFF9B55D6;
        float[] hsv = Colors.toHsv(color);
        assertEquals(color, Colors.hsv(hsv[0], hsv[1], hsv[2], 255));
    }

    @Test
    void keysArePortableByName() {
        assertEquals(Key.RSHIFT, Key.byName("RSHIFT"));
        assertEquals(Key.NONE, Key.byName("does-not-exist"));
        assertEquals(Key.MOUSE_4, Key.fromMouseButton(3));
    }

    @Test
    void polishTranslationLoadsAndFallsBack() {
        try {
            I18n.setLanguage(I18n.Language.PL_PL);
            assertEquals("Gotowe", I18n.tr("ui.done", "Done"));
            assertEquals("Fallback", I18n.tr("missing.key", "Fallback"));
        } finally {
            I18n.setLanguage(I18n.Language.EN_US);
        }
        assertEquals("Done", I18n.tr("ui.done", "Done"));
    }

    @Test
    void formatting() {
        assertEquals("1:23", Format.duration(83_000));
        assertEquals("1:23:03", Format.duration(4_983_000));
        assertEquals("0:05", Format.ticks(100));
        assertEquals("IV", Format.roman(4));
        assertEquals("1.5 GB", Format.bytes(1536L * 1024 * 1024));
    }
}
