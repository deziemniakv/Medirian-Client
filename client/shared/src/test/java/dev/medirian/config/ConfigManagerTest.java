package dev.medirian.config;

import com.google.gson.JsonObject;
import dev.medirian.core.MedirianHome;
import dev.medirian.event.EventBus;
import dev.medirian.hud.Anchor;
import dev.medirian.module.BuiltinModules;
import dev.medirian.module.Module;
import dev.medirian.module.ModuleManager;
import dev.medirian.input.Key;
import dev.medirian.setting.NumberSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @TempDir
    File temp;

    private static final class Setup {
        final ModuleManager modules = new ModuleManager(new EventBus(), capability -> true);
        final GlobalSettings global = new GlobalSettings();
        final ProfileSettings profile = new ProfileSettings();
        final ConfigManager config;

        Setup(File root) {
            BuiltinModules.registerAll(modules);
            config = new ConfigManager(MedirianHome.at(root), modules, global, profile);
        }
    }

    @Test
    void firstRunCreatesPresetProfiles() {
        Setup setup = new Setup(temp);
        setup.config.init(null);
        setup.config.shutdown();

        assertTrue(new File(temp, "config/profiles/default.json").isFile());
        assertTrue(new File(temp, "config/profiles/pvp.json").isFile());
        assertTrue(new File(temp, "config/profiles/performance.json").isFile());
        assertEquals("Default", setup.config.activeProfile());
    }

    @Test
    void presetsDifferFromDefault() {
        Setup setup = new Setup(temp);
        setup.config.init("PvP");
        assertTrue(setup.modules.get("keystrokes").isEnabled(), "PvP enables keystrokes");
        assertFalse(setup.modules.get("coordinates").isEnabled(), "PvP disables coordinates");
        setup.config.loadProfile("Default", false);
        assertFalse(setup.modules.get("keystrokes").isEnabled());
        assertTrue(setup.modules.get("coordinates").isEnabled());
        setup.config.shutdown();
    }

    @Test
    void settingsKeybindsAndLayoutRoundTrip() {
        Setup first = new Setup(temp);
        first.config.init(null);
        Module cps = first.modules.get("cps");
        cps.setEnabled(true);
        ((NumberSetting) cps.setting("window")).set(1500);
        cps.keybind().set(Key.K);
        cps.hud().setLayout(Anchor.BOTTOM_RIGHT, -10, -20);
        cps.hud().setScale(1.5f);
        first.config.saveNow();
        first.config.shutdown();

        Setup second = new Setup(temp);
        second.config.init(null);
        Module loaded = second.modules.get("cps");
        assertTrue(loaded.isEnabled());
        assertEquals(1500, ((NumberSetting) loaded.setting("window")).intValue());
        assertEquals(Key.K, loaded.keybind().key());
        assertEquals(Anchor.BOTTOM_RIGHT, loaded.hud().anchor());
        assertEquals(-10f, loaded.hud().offsetX(), 0.01);
        assertEquals(1.5f, loaded.hud().scale(), 0.01);
        second.config.shutdown();
    }

    @Test
    void unknownModulesArePreserved() throws IOException {
        Setup first = new Setup(temp);
        first.config.init(null);
        first.config.shutdown();
        File file = new File(temp, "config/profiles/default.json");
        JsonObject json = JsonFiles.read(file);
        JsonObject future = new JsonObject();
        future.addProperty("enabled", true);
        json.getAsJsonObject("modules").add("futuremodule", future);
        JsonFiles.write(file, json);

        Setup second = new Setup(temp);
        second.config.init(null);
        second.config.saveNow();
        second.config.shutdown();

        JsonObject saved = JsonFiles.read(file);
        assertNotNull(saved.getAsJsonObject("modules").get("futuremodule"), "module of another version must survive a save");
    }

    @Test
    void customProfilesCanBeCreatedAndDeleted() {
        Setup setup = new Setup(temp);
        setup.config.init(null);
        assertNull(setup.config.validateName("My Bedwars"));
        assertEquals("profile.error.chars", setup.config.validateName("bad/name"));
        assertTrue(setup.config.createProfile("My Bedwars"));
        assertEquals("profile.error.exists", setup.config.validateName("My Bedwars"));
        assertTrue(setup.config.profiles().contains("My Bedwars"));
        assertTrue(setup.config.loadProfile("My Bedwars", true));
        assertEquals("My Bedwars", setup.config.activeProfile());
        assertTrue(setup.config.deleteProfile("My Bedwars"));
        assertEquals("Default", setup.config.activeProfile());
        assertFalse(setup.config.deleteProfile("Default"), "Default cannot be deleted");
        setup.config.shutdown();
    }

    @Test
    void slugIsFileSystemSafe() {
        assertEquals("my-bedwars", ConfigManager.slug("  My Bedwars "));
        assertEquals("pvp", ConfigManager.slug("PvP"));
        assertEquals("profile", ConfigManager.slug("___"));
    }
}
