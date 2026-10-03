package dev.meridian.mc1_8_9;

import dev.meridian.core.Log;
import dev.meridian.core.Meridian;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.ui.HudEditorScreen;
import dev.meridian.ui.ModMenuScreen;
import dev.meridian.ui.SettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;

/**
 * Developer self-test (never active for players): {@code ./gradlew runClient -Pselftest}.
 * Walks through Meridian's screens and an in-game HUD, saving screenshots to
 * {@code run/screenshots/}, then quits.
 */
public final class SelfTest {

    private static final String WORLD = "meridian-selftest";
    private static final boolean ENABLED = Boolean.getBoolean("meridian.selftest");

    private static int step;
    private static int wait;

    private SelfTest() {
    }

    public static void tick(MinecraftClient client) {
        if (!ENABLED || step < 0) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Meridian meridian = Meridian.get();
        switch (step) {
            case 0:
                if (client.currentScreen instanceof TitleScreen) {
                    next(40);
                }
                return;
            case 1:
                meridian.platform().openScreen(new ModMenuScreen(null));
                next(30);
                return;
            case 2:
                shot(client, "1-modmenu");
                meridian.platform().openScreen(new HudEditorScreen(null));
                next(30);
                return;
            case 3:
                shot(client, "2-hudeditor");
                meridian.platform().openScreen(new SettingsScreen(null));
                next(30);
                return;
            case 4:
                shot(client, "3-settings");
                meridian.platform().openScreen(null);
                meridian.config().loadProfile("PvP", false);
                client.startIntegratedServer(WORLD, WORLD,
                        new LevelInfo(4242L, LevelInfo.GameMode.CREATIVE, true, false, LevelGeneratorType.DEFAULT).enableCommands());
                next(20);
                return;
            case 5:
                if (client.player != null && client.world != null && client.currentScreen == null) {
                    meridian.modules().get("blockoverlay").setEnabled(true);
                    ((BooleanSetting) meridian.modules().get("blockoverlay").setting("fill")).set(true);
                    meridian.modules().get("hitcolor").setEnabled(true);
                    client.player.sendChatMessage("/summon Pig ~1.5 ~ ~1.5");
                    next(100);
                }
                return;
            case 6:
                // hurt the pig (instant damage) right before the screenshot to show the Hit Color flash
                client.player.sendChatMessage("/effect @e[type=Pig,c=1] 7 1 0");
                // look at the ground so a block is selected (set late: joining resets the rotation)
                client.player.pitch = 55f;
                client.player.prevPitch = 55f;
                next(3);
                return;
            case 7:
                shot(client, "4-hud");
                meridian.platform().openScreen(new ModMenuScreen(null));
                next(30);
                return;
            case 8:
                shot(client, "5-modmenu-ingame");
                next(20);
                return;
            default:
                Log.info("Self-test finished; screenshots in {}", client.runDirectory);
                step = -1;
                client.scheduleStop();
        }
    }

    private static void next(int ticks) {
        step++;
        wait = ticks;
    }

    private static void shot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, "selftest-" + name + ".png", client.width, client.height,
                client.getFramebuffer());
        Log.info("Self-test screenshot: {}", name);
    }
}
