package dev.medirian.render;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertSame;

class ThemeTest {

    private static Theme on(int month, int day) {
        return Theme.seasonal(new GregorianCalendar(2026, month, day));
    }

    @Test
    void seasonsFollowTheCalendar() {
        assertSame(Theme.HALLOWEEN, on(Calendar.OCTOBER, 31));
        assertSame(Theme.DEFAULT, on(Calendar.NOVEMBER, 30));
        assertSame(Theme.CHRISTMAS, on(Calendar.DECEMBER, 1));
        assertSame(Theme.CHRISTMAS, on(Calendar.JANUARY, 6));
        assertSame(Theme.DEFAULT, on(Calendar.JANUARY, 7));
        assertSame(Theme.CHRISTMAS, Theme.resolve(Theme.Mode.CHRISTMAS));
    }
}
