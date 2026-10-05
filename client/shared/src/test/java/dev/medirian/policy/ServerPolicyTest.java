package dev.medirian.policy;

import dev.medirian.event.EventBus;
import dev.medirian.module.BuiltinModules;
import dev.medirian.module.Module;
import dev.medirian.module.ModuleManager;
import dev.medirian.notify.NotificationManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPolicyTest {

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsing() {
        ServerPolicy policy = ServerPolicy.parse(json("{\"version\":1,\"disable\":[\"Freelook\",\"zoom\",3],\"message\":\"No freelook\"}"));
        assertTrue(policy.disabledModules.contains("freelook"));
        assertTrue(policy.disabledModules.contains("zoom"));
        assertEquals("No freelook", policy.message);
        assertNull(ServerPolicy.parse(json("not json")));
        assertNull(ServerPolicy.parse(json("[1,2]")));
        assertNull(ServerPolicy.parse(new byte[0]));
    }

    @Test
    void modulesAreLockedUntilTheServerIsLeft() {
        ModuleManager modules = new ModuleManager(new EventBus(), capability -> true);
        BuiltinModules.registerAll(modules);
        Module freelook = modules.get("freelook");
        Module zoom = modules.get("zoom");
        freelook.setEnabled(true);
        zoom.setEnabled(false);
        ServerPolicyManager policies = new ServerPolicyManager(modules, new NotificationManager());

        policies.receive(json("{\"disable\":[\"freelook\",\"zoom\",\"unknown\"]}"));
        assertFalse(freelook.isEnabled());
        assertTrue(freelook.isLocked());
        freelook.setEnabled(true);
        assertFalse(freelook.isEnabled(), "locked modules cannot be switched on");

        // a new policy without zoom lifts its lock, zoom stays off as before
        policies.receive(json("{\"disable\":[\"freelook\"]}"));
        assertFalse(zoom.isLocked());
        assertFalse(zoom.isEnabled());

        policies.clear();
        assertFalse(freelook.isLocked());
        assertTrue(freelook.isEnabled(), "restored after leaving");
    }
}
