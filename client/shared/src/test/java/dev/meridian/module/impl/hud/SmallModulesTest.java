package dev.meridian.module.impl.hud;

import dev.meridian.module.impl.combat.HealthTagsModule;
import dev.meridian.module.impl.render.FireOverlayModule;
import dev.meridian.module.impl.render.HurtCameraModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallModulesTest {

    @Test
    void healthTagsFormatAndColour() {
        HealthTagsModule tags = new HealthTagsModule();
        assertFalse(tags.appliesTo(true), "off until enabled");
        tags.setEnabled(true);
        assertTrue(tags.appliesTo(true));
        assertFalse(tags.appliesTo(false), "mobs only when enabled in the settings");
        assertEquals("10❤", tags.text(20f));
        assertEquals("9.5❤", tags.text(19f));
        assertEquals("8.7❤", tags.text(17.333f));
        assertEquals("+2", tags.absorptionText(4f));
        assertNull(tags.absorptionText(0f));
        assertEquals(HealthTagsModule.HIGH, HealthTagsModule.color(20, 20));
        assertEquals(HealthTagsModule.MEDIUM, HealthTagsModule.color(10, 20));
        assertEquals(HealthTagsModule.LOW, HealthTagsModule.color(4, 20));
    }

    @Test
    void speedAveragesTheLastHalfSecond() {
        SpeedModule speed = new SpeedModule();
        assertEquals(0, speed.blocksPerSecond(), 1e-9);
        // sprinting: 0.2806 blocks per tick ≈ 5.61 m/s
        for (int i = 0; i < 10; i++) {
            speed.record(0.2806);
        }
        assertEquals(5.612, speed.blocksPerSecond(), 1e-3);
        // standing still for half a second brings it back to 0
        for (int i = 0; i < 10; i++) {
            speed.record(0);
        }
        assertEquals(0, speed.blocksPerSecond(), 1e-9);
    }

    @Test
    void disabledVisualModulesKeepVanilla() {
        HurtCameraModule hurt = new HurtCameraModule();
        FireOverlayModule fire = new FireOverlayModule();
        assertEquals(1f, hurt.strength(), 0f);
        assertEquals(0f, fire.offsetY(), 0f);
        assertEquals(0.9f, fire.alpha(0.9f), 0f);
        hurt.setEnabled(true);
        fire.setEnabled(true);
        assertEquals(0f, hurt.strength(), 0f);
        assertTrue(fire.offsetY() < 0f);
        assertTrue(fire.alpha(0.9f) < 0.9f);
    }
}
