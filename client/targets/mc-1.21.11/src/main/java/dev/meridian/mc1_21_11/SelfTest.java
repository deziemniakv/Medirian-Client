package dev.meridian.mc1_21_11;

import dev.meridian.core.Log;
import dev.meridian.core.Meridian;
import dev.meridian.perf.CullState;
import dev.meridian.perf.OcclusionCuller;
import dev.meridian.platform.Occluders;
import dev.meridian.ui.HudEditorScreen;
import dev.meridian.ui.ModMenuScreen;
import dev.meridian.ui.SettingsScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Developer self-test (never active for players): enabled with {@code -Dmeridian.selftest=true}
 * via {@code ./gradlew runClient -Pselftest}. Walks through Meridian's screens and an in-game HUD,
 * saving a screenshot of each step to {@code run/screenshots/}, then quits. Used to verify
 * rendering of every adapter after changes.
 */
final class SelfTest {

    private static final String WORLD = "meridian-selftest";
    private static final boolean ENABLED = Boolean.getBoolean("meridian.selftest");
    static final String STACKED = "Meridian self-test: repeated line";

    private static int step;
    private static int wait;
    /** Culling scene: a stone platform at this height above the player, so the result never depends on terrain. */
    private static final int SCENE_Y = 200;
    private static int sceneX;
    private static int sceneZ;

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
                    for (int i = 0; i < 3; i++) {
                        minecraft.gui.getChat().addMessage(Component.literal(STACKED));
                    }
                    minecraft.gui.getChat().addMessage(Component.literal("Meridian self-test: chat line"));
                    next(120);
                }
                return;
            case 6:
                // hurt the pig right before the screenshot to show the Hit Color flash
                minecraft.player.connection.sendCommand("damage @e[type=pig,limit=1,sort=nearest] 1");
                next(3);
                return;
            case 7:
                verifyChat(minecraft);
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
            case 9:
                meridian.platform().openScreen(null);
                buildCullingScene(minecraft.player);
                next(40);
                return;
            case 10:
                verifyCulling(minecraft);
                shot(minecraft, "6-culling");
                next(20);
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
        meridian.modules().get("chat").setEnabled(true);
        meridian.modules().get("entityculling").setEnabled(true);
    }

    /** A platform high above the player facing east with a stone wall; one pig in front of it, one behind it. */
    private static void buildCullingScene(LocalPlayer player) {
        sceneX = (int) Math.floor(player.getX());
        sceneZ = (int) Math.floor(player.getZ());
        int x = sceneX;
        int z = sceneZ;
        int y = SCENE_Y;
        player.connection.sendCommand("kill @e[type=pig]");
        player.connection.sendCommand("fill " + (x - 2) + " " + (y - 1) + " " + (z - 4) + " " + (x + 11) + " " + (y + 4) + " " + (z + 4) + " air");
        player.connection.sendCommand("fill " + (x - 2) + " " + (y - 1) + " " + (z - 4) + " " + (x + 11) + " " + (y - 1) + " " + (z + 4) + " stone");
        player.connection.sendCommand("fill " + (x + 6) + " " + y + " " + (z - 4) + " " + (x + 6) + " " + (y + 4) + " " + (z + 4) + " stone");
        player.connection.sendCommand("tp @s " + (x + 0.5) + " " + y + " " + (z + 0.5) + " -90 0");
        player.connection.sendCommand("summon pig " + (x + 3.5) + " " + y + " " + (z + 0.5) + " {NoAI:1b}");
        player.connection.sendCommand("summon pig " + (x + 9.5) + " " + y + " " + (z + 0.5) + " {NoAI:1b}");
    }

    /** The pig behind the wall must be hidden (and actually skipped while rendering), the other one visible. */
    private static void verifyCulling(Minecraft minecraft) {
        Entity front = null;
        Entity behind = null;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.getType() != EntityType.PIG || Math.abs(entity.getY() - SCENE_Y) > 1) {
                continue;
            }
            double dx = entity.getX() - (sceneX + 0.5);
            if (Math.abs(dx - 3) < 0.5) {
                front = entity;
            } else if (Math.abs(dx - 9) < 0.5) {
                behind = entity;
            }
        }
        if (front == null || behind == null) {
            Log.error("Self-test FAILED: culling scene incomplete (front {}, behind {})", front, behind);
            return;
        }
        Occluders blocks = Meridian.get().platform().game();
        // the scene's eye position (the player may have been moved by a real keyboard in the meantime)
        Vec3 eye = new Vec3(sceneX + 0.5, SCENE_Y + 1.62, sceneZ + 0.5);
        boolean frontVisible = visible(blocks, eye, front.getBoundingBox());
        boolean behindVisible = visible(blocks, eye, behind.getBoundingBox());
        CullState state = (CullState) behind;
        boolean skipped = state.meridian$cullCheckedAt() != 0 && !state.meridian$cullVisible();
        if (frontVisible && !behindVisible && skipped) {
            Log.info("Self-test: occlusion culling OK");
        } else {
            Log.error("Self-test FAILED: occlusion culling (front visible {}, behind visible {}, skipped while rendering {})",
                    frontVisible, behindVisible, skipped);
        }
    }

    private static boolean visible(Occluders blocks, Vec3 eye, AABB box) {
        return OcclusionCuller.isVisible(blocks, eye.x, eye.y, eye.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    /** Three identical lines must have become one "(x3)" line with a timestamp. */
    @SuppressWarnings("unchecked")
    private static void verifyChat(Minecraft minecraft) {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<GuiMessage> messages = (List<GuiMessage>) field.get(minecraft.gui.getChat());
            List<String> copies = messages.stream().map(m -> m.content().getString()).filter(t -> t.contains(STACKED)).toList();
            String stacked = copies.isEmpty() ? "" : copies.get(0);
            if (copies.size() == 1 && stacked.startsWith("[") && stacked.endsWith(STACKED + " (x3)")) {
                Log.info("Self-test: chat stacking OK ({})", stacked);
            } else {
                Log.error("Self-test FAILED: chat stacking ({} copies, line: {})", copies.size(), stacked);
            }
        } catch (ReflectiveOperationException e) {
            Log.error("Self-test could not inspect the chat", e);
        }
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
