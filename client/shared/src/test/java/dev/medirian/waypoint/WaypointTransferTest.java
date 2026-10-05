package dev.medirian.waypoint;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointTransferTest {

    @Test
    void exportImportRoundTrip() {
        Waypoint base = new Waypoint("Base", 120, 64, -38, "minecraft:overworld", 0xFF2BB5A0);
        Waypoint death = new Waypoint("Death", 1, 2, 3, "minecraft:overworld", 0xFFE5484D);
        death.death = true;
        String text = WaypointTransfer.export(Arrays.asList(base, death));
        List<Waypoint> parsed = WaypointTransfer.parse(text, "minecraft:the_end");
        assertEquals(1, parsed.size(), "death points are not exported");
        assertEquals("Base", parsed.get(0).name);
        assertEquals(-38, parsed.get(0).z);
        assertEquals("minecraft:overworld", parsed.get(0).dimension);
        assertEquals(0xFF2BB5A0, parsed.get(0).color);
    }

    @Test
    void plainLines() {
        List<Waypoint> parsed = WaypointTransfer.parse("Nether portal 10 70 -4\nfarm, 1, 2, 3\nnot a waypoint\nEnd city -100 60 2000 minecraft:the_end",
                "minecraft:the_nether");
        assertEquals(3, parsed.size());
        assertEquals("Nether portal", parsed.get(0).name);
        assertEquals("minecraft:the_nether", parsed.get(0).dimension);
        assertEquals("farm", parsed.get(1).name);
        assertEquals("minecraft:the_end", parsed.get(2).dimension);
        assertEquals(-100, parsed.get(2).x);
    }

    @Test
    void garbageGivesNothing() {
        assertTrue(WaypointTransfer.parse("[{\"name\": 3}]", "minecraft:overworld").isEmpty());
        assertTrue(WaypointTransfer.parse("hello", "minecraft:overworld").isEmpty());
        assertTrue(WaypointTransfer.parse(null, "minecraft:overworld").isEmpty());
    }
}
