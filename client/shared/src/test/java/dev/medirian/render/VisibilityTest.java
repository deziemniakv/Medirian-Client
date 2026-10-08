package dev.medirian.render;

import dev.medirian.config.ProfileSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityTest {

    @AfterEach
    void unbind() {
        Visibility.bind(null);
    }

    @Test
    void everythingIsDrawnByDefault() {
        ProfileSettings settings = new ProfileSettings();
        Visibility.bind(settings);
        // the defaults are "No limit": nothing changes until the player picks a distance
        assertEquals("No limit", settings.playerDistance.formatted());
        assertTrue(Visibility.entity(Visibility.PLAYER, 1e9));
        assertTrue(Visibility.entity(Visibility.ITEM, 1e9));
        assertTrue(Visibility.entity(Visibility.OTHER, 1e9));
        assertTrue(Visibility.particle(1e9));
        assertTrue(Visibility.nameTag(1e9));
        assertTrue(Visibility.cosmetics(1e9));
    }

    @Test
    void eachKindHasItsOwnDistance() {
        ProfileSettings settings = new ProfileSettings();
        Visibility.bind(settings);
        settings.itemDistance.set(16.0);
        settings.nameTagDistance.set(32.0);
        assertTrue(Visibility.entity(Visibility.ITEM, 15 * 15));
        assertFalse(Visibility.entity(Visibility.ITEM, 17 * 17), "dropped items beyond 16 blocks are hidden");
        assertTrue(Visibility.entity(Visibility.PLAYER, 17 * 17), "players keep their own (unlimited) distance");
        assertTrue(Visibility.entity(Visibility.OTHER, 17 * 17));
        assertTrue(Visibility.nameTag(31 * 31));
        assertFalse(Visibility.nameTag(33 * 33));
        assertEquals("16 m", settings.itemDistance.formatted());
    }

    @Test
    void withoutSettingsNothingIsHidden() {
        Visibility.bind(null);
        assertTrue(Visibility.entity(Visibility.OTHER, 1e12));
        assertTrue(Visibility.blockEntity(1e12));
        assertTrue(Visibility.waypoint(1e12));
    }
}
