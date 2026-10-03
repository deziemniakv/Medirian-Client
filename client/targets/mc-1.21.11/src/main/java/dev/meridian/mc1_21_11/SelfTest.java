package dev.meridian.mc1_21_11;

import dev.meridian.core.Log;
import dev.meridian.core.Meridian;
import dev.meridian.ui.HudEditorScreen;
import dev.meridian.ui.ModMenuScreen;
import dev.meridian.ui.SettingsScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

/**
 * Developer self-test (never active for players): enabled with {@code -Dmeridian.selftest=true}
 * via {@code ./gradlew runClient -Pselftest}. Walks through Meridian's screens and an in-game HUD,
 * saving a screenshot of each step to {@code run/screenshots/}, then quits. Used to verify
 * rendering of every adapter after changes.
 */
final class SelfTest {

    private static final String WORLD = "meridian-selftest";
    private static final boolean ENABLED = Boolean.getBoolean("meridian.selftest");

    private static int step;
    private static int wait;

    private SelfTest() {
    }

    static void tick(Minecraft minecraft) {
        if (!ENABLED) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Meridian meridian = Meridian.get();
        switch (step) {
            case 0:
                if (minecraft.screen instanceof TitleScreen) {
                    next(60);
                }
                return;
            case 1:
                meridian.platform().openScreen(new ModMenuScreen(null));
                next(30);
                return;
            case 2:
                shot(minecraft, "1-modmenu");
                meridian.platform().openScreen(new HudEditorScreen(null));
                next(30);
                return;
            case 3:
                shot(minecraft, "2-hudeditor");
                meridian.platform().openScreen(new SettingsScreen(null));
                next(30);
                return;
            case 4:
                shot(minecraft, "3-settings");
                meridian.platform().openScreen(null);
                meridian.config().loadProfile("PvP", false);
                enterWorld(minecraft);
                next(20);
                return;
            case 5:
                if (minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
                    prepareScene(meridian);
                    minecraft.player.setXRot(55f); // look at the ground so a block is selected
                    minecraft.player.connection.sendCommand("summon pig ^ ^ ^3");
                    next(120);
                }
                return;
            case 6:
                // hurt the pig right before the screenshot to show the Hit Color flash
                minecraft.player.connection.sendCommand("damage @e[type=pig,limit=1,sort=nearest] 1");
                next(3);
                return;
            case 7:
                shot(minecraft, "4-hud");
                // press the mod menu key through the game's real keyboard handler
                pressKey(minecraft, GLFW.GLFW_KEY_RIGHT_SHIFT);
                next(30);
                return;
            case 8:
                if (minecraft.screen instanceof ScreenBridge bridge && bridge.meridian() instanceof ModMenuScreen) {
                    Log.info("Self-test: mod menu key OK");
                } else {
                    Log.error("Self-test FAILED: mod menu key did not open the mod menu (screen: {})", minecraft.screen);
                }
                shot(minecraft, "5-modmenu-ingame");
                next(40);
                return;
            default:
                Log.info("Self-test finished; screenshots in {}", minecraft.gameDirectory);
                step = -1;
                minecraft.stop();
        }
    }

    /** Turns on the render modules whose effect is only visible in a world (shared by both adapters). */
    static void prepareScene(Meridian meridian) {
        meridian.modules().get("blockoverlay").setEnabled(true);
        ((dev.meridian.setting.BooleanSetting) meridian.modules().get("blockoverlay").setting("fill")).set(true);
        meridian.modules().get("hitcolor").setEnabled(true);
    }

    private static void next(int ticks) {
        step++;
        wait = ticks;
    }

    /** Sends a key press + release through KeyboardHandler#keyPress (named method: dev environment only). */
    private static void pressKey(Minecraft minecraft, int glfwKey) {
        try {
            Method keyPress = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
            keyPress.setAccessible(true);
            long window = minecraft.getWindow().handle();
            keyPress.invoke(minecraft.keyboardHandler, window, GLFW.GLFW_PRESS, new KeyEvent(glfwKey, 0, 0));
            keyPress.invoke(minecraft.keyboardHandler, window, GLFW.GLFW_RELEASE, new KeyEvent(glfwKey, 0, 0));
        } catch (ReflectiveOperationException e) {
            Log.error("Self-test could not simulate key {}", glfwKey, e);
        }
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, "selftest-" + name + ".png", minecraft.getMainRenderTarget(), 1,
                message -> Log.info("Self-test screenshot: {}", message.getString()));
    }

    private static void enterWorld(Minecraft minecraft) {
        if (minecraft.getLevelSource().levelExists(WORLD)) {
            minecraft.createWorldOpenFlows().openWorld(WORLD, () -> minecraft.setScreen(new TitleScreen()));
            return;
        }
        GameRules rules = new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures());
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, WorldOptions.defaultWithRandomSeed(),
                WorldPresets::createNormalWorldDimensions, new TitleScreen());
    }
}
