package dev.meridian.module;

import dev.meridian.config.GlobalSettings;
import dev.meridian.event.EventBus;
import dev.meridian.input.Key;
import dev.meridian.input.KeybindManager;
import dev.meridian.platform.Capability;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleManagerTest {

    static final class Probe {
    }

    static final class ProbeModule extends Module {
        final AtomicInteger received = new AtomicInteger();

        ProbeModule() {
            super("probe", "Probe", Category.MISC, "test module");
            on(Probe.class, e -> received.incrementAndGet());
        }
    }

    @Test
    void unsupportedModulesAreNotRegistered() {
        ModuleManager modules = new ModuleManager(new EventBus(), capability -> capability != Capability.ZOOM);
        BuiltinModules.registerAll(modules);
        assertNull(modules.get("zoom"), "zoom requires the ZOOM capability");
        assertNotNull(modules.get("fps"));
        assertTrue(modules.unsupported().stream().anyMatch(m -> m.id().equals("zoom")));
    }

    @Test
    void listenersOnlyRunWhileEnabled() {
        EventBus bus = new EventBus();
        ModuleManager modules = new ModuleManager(bus, capability -> true);
        ProbeModule probe = new ProbeModule();
        modules.register(probe);
        bus.post(new Probe());
        assertEquals(0, probe.received.get());
        probe.setEnabled(true);
        bus.post(new Probe());
        assertEquals(1, probe.received.get());
        probe.setEnabled(false);
        bus.post(new Probe());
        assertEquals(1, probe.received.get());
    }

    @Test
    void searchMatchesNamesAndDescriptions() {
        ModuleManager modules = new ModuleManager(new EventBus(), capability -> true);
        BuiltinModules.registerAll(modules);
        assertTrue(modules.search("keystr", null).stream().anyMatch(m -> m.id().equals("keystrokes")));
        assertTrue(modules.search("latency", null).stream().anyMatch(m -> m.id().equals("ping")));
        assertTrue(modules.search("", Category.PERFORMANCE).stream().allMatch(m -> m.category() == Category.PERFORMANCE));
    }

    @Test
    void keybindTogglesModuleAndHoldModulesNeedEnabling() {
        ModuleManager modules = new ModuleManager(new EventBus(), capability -> true);
        BuiltinModules.registerAll(modules);
        AtomicInteger menus = new AtomicInteger();
        GlobalSettings global = new GlobalSettings();
        KeybindManager keybinds = new KeybindManager(modules, global, new KeybindManager.GlobalActions() {
            @Override
            public void openModMenu() {
                menus.incrementAndGet();
            }

            @Override
            public void openHudEditor() {
            }
        });
        Module fullbright = modules.get("fullbright");
        fullbright.keybind().set(Key.G);
        keybinds.onKey(Key.G, true);
        keybinds.onKey(Key.G, false);
        assertTrue(fullbright.isEnabled());

        keybinds.onKey(Key.RSHIFT, true);
        assertEquals(1, menus.get());

        Module zoom = modules.get("zoom");
        assertEquals(Key.C, zoom.keybind().key(), "zoom defaults to C");
        zoom.setEnabled(false);
        keybinds.onKey(Key.C, true);
        assertFalse(zoom.keybind().isDown(), "hold keys of disabled modules are ignored");
    }
}
