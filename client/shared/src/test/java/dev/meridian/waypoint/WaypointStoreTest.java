package dev.meridian.waypoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointStoreTest {

    @TempDir
    File dir;

    @Test
    void waypointsSurviveARestart() {
        File file = new File(dir, "waypoints.json");
        WaypointStore store = new WaypointStore(file);
        Waypoint base = store.add("server:mc.example.net", new Waypoint("Base", 10, 64, -3, "minecraft:overworld", 0xFF9B55D6));
        base.visible = false;
        store.changed();
        store.add("local:world", new Waypoint("Portal", 1, 70, 2, "minecraft:the_nether", 0xFFF08A24));

        WaypointStore reloaded = new WaypointStore(file);
        List<Waypoint> list = reloaded.of("server:mc.example.net");
        assertEquals(1, list.size());
        Waypoint loaded = list.get(0);
        assertEquals("Base", loaded.name);
        assertEquals(-3, loaded.z);
        assertEquals("minecraft:overworld", loaded.dimension);
        assertEquals(0xFF9B55D6, loaded.color);
        assertFalse(loaded.visible);
        assertEquals("minecraft:the_nether", reloaded.of("local:world").get(0).dimension);
        assertTrue(reloaded.of("server:other").isEmpty());
    }

    @Test
    void onlyTheLatestDeathPointIsKept() {
        WaypointStore store = new WaypointStore(new File(dir, "waypoints.json"));
        store.add("w", new Waypoint("Home", 0, 64, 0, "minecraft:overworld", 0xFF000000));
        Waypoint first = new Waypoint("Death", 5, 64, 5, "minecraft:overworld", 0xFFE5484D);
        first.death = true;
        store.add("w", first);
        Waypoint second = new Waypoint("Death", 9, 64, 9, "minecraft:overworld", 0xFFE5484D);
        second.death = true;
        store.add("w", second);
        assertEquals(2, store.of("w").size());
        assertEquals(9, store.of("w").get(1).x);
    }

    @Test
    void namesAreNumberedAndRemovalWorks() {
        WaypointStore store = new WaypointStore(new File(dir, "waypoints.json"));
        assertEquals("Waypoint 1", store.nextName("w", "Waypoint"));
        Waypoint one = store.add("w", new Waypoint("Waypoint 1", 0, 0, 0, "minecraft:overworld", 0));
        assertEquals("Waypoint 2", store.nextName("w", "Waypoint"));
        store.remove("w", one);
        assertTrue(store.of("w").isEmpty());
        assertEquals("Waypoint 1", store.nextName("w", "Waypoint"));
    }
}
