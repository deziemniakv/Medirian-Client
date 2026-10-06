package dev.medirian.render;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeTest {

    private static boolean winter(int month, int day) {
        return Theme.isWinter(new GregorianCalendar(2026, month, day));
    }

    @Test
    void snowFallsFromDecemberToTheSixthOfJanuary() {
        assertFalse(winter(Calendar.OCTOBER, 31));
        assertFalse(winter(Calendar.NOVEMBER, 30));
        assertTrue(winter(Calendar.DECEMBER, 1));
        assertTrue(winter(Calendar.JANUARY, 6));
        assertFalse(winter(Calendar.JANUARY, 7));
    }
}
