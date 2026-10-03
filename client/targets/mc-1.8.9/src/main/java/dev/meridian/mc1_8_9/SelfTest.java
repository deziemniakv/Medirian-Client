package dev.meridian.mc1_8_9;

import dev.meridian.core.Log;
import dev.meridian.core.Meridian;
import dev.meridian.perf.CullState;
import dev.meridian.perf.OcclusionCuller;
import dev.meridian.platform.Occluders;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.ui.HudEditorScreen;
import dev.meridian.ui.ModMenuScreen;
import dev.meridian.ui.SettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer self-test (never active for players): {@code ./gradlew runClient -Pselftest}.
 * Walks through Meridian's screens and an in-game HUD, saving screenshots to
 * {@code run/screenshots/}, then quits.
 */
public final class SelfTest {

    private static final String WORLD = "meridian-selftest";
    private static final boolean ENABLED = Boolean.getBoolean("meridian.selftest");
    private static final String STACKED = "Meridian self-test: repeated line";

    /** Culling scene: a stone platform at this height above the player, so the result never depends on terrain. */
    private static final int SCENE_Y = 200;

    private static int step;
    private static int wait;
    private static int sceneX;
    private static int sceneZ;

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
                    meridian.modules().get("chat").setEnabled(true);
                    meridian.modules().get("entityculling").setEnabled(true);
                    client.player.sendChatMessage("/summon Pig ~1.5 ~ ~1.5");
                    for (int i = 0; i < 3; i++) {
                        client.inGameHud.getChatHud().addMessage(new LiteralText(STACKED));
                    }
                    client.inGameHud.getChatHud().addMessage(new LiteralText("Meridian self-test: chat line"));
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
                verifyChat(client);
                shot(client, "4-hud");
                meridian.platform().openScreen(new ModMenuScreen(null));
                next(30);
                return;
            case 8:
                shot(client, "5-modmenu-ingame");
                next(20);
                return;
            case 9:
                meridian.platform().openScreen(null);
                buildCullingScene(client.player);
                next(40);
                return;
            case 10:
                verifyCulling(client);
                shot(client, "6-culling");
                next(20);
                return;
            default:
                Log.info("Self-test finished; screenshots in {}", client.runDirectory);
                step = -1;
                client.scheduleStop();
        }
    }

    /** Three identical lines must have become one "(x3)" line with a timestamp. */
    @SuppressWarnings("unchecked")
    private static void verifyChat(MinecraftClient client) {
        try {
            Field field = ChatHud.class.getDeclaredField("messages");
            field.setAccessible(true);
            List<String> copies = new ArrayList<String>();
            for (ChatHudLine line : (List<ChatHudLine>) field.get(client.inGameHud.getChatHud())) {
                String text = line.getText().asUnformattedString();
                if (text.contains(STACKED)) {
                    copies.add(text);
                }
            }
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

    /** A platform high above the player facing east with a stone wall; one pig in front of it, one behind it. */
    private static void buildCullingScene(ClientPlayerEntity player) {
        sceneX = (int) Math.floor(player.x);
        sceneZ = (int) Math.floor(player.z);
        int x = sceneX;
        int z = sceneZ;
        int y = SCENE_Y;
        player.sendChatMessage("/kill @e[type=Pig]");
        player.sendChatMessage("/fill " + (x - 2) + " " + (y - 1) + " " + (z - 4) + " " + (x + 11) + " " + (y + 4) + " " + (z + 4) + " air");
        player.sendChatMessage("/fill " + (x - 2) + " " + (y - 1) + " " + (z - 4) + " " + (x + 11) + " " + (y - 1) + " " + (z + 4) + " stone");
        player.sendChatMessage("/fill " + (x + 6) + " " + y + " " + (z - 4) + " " + (x + 6) + " " + (y + 4) + " " + (z + 4) + " stone");
        player.sendChatMessage("/tp " + (x + 0.5) + " " + y + " " + (z + 0.5) + " -90 0");
        player.sendChatMessage("/summon Pig " + (x + 3.5) + " " + y + " " + (z + 0.5) + " {NoAI:1}");
        player.sendChatMessage("/summon Pig " + (x + 9.5) + " " + y + " " + (z + 0.5) + " {NoAI:1}");
        player.sendChatMessage("/setblock " + (x + 3) + " " + y + " " + (z + 2) + " chest");
        player.sendChatMessage("/setblock " + (x + 9) + " " + y + " " + (z + 2) + " chest");
    }

    /** The pig behind the wall must be hidden (and actually skipped while rendering), the other one visible. */
    private static void verifyCulling(MinecraftClient client) {
        Entity front = null;
        Entity behind = null;
        for (Entity entity : new ArrayList<Entity>(client.world.loadedEntities)) {
            if (!(entity instanceof PigEntity) || Math.abs(entity.y - SCENE_Y) > 1) {
                continue;
            }
            double dx = entity.x - (sceneX + 0.5);
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
        // the scene's eye position (the player may have been moved by a real keyboard in the meantime)
        Occluders blocks = Meridian.get().platform().game();
        double eyeX = sceneX + 0.5;
        double eyeY = SCENE_Y + 1.62;
        double eyeZ = sceneZ + 0.5;
        Box a = front.getBoundingBox();
        Box b = behind.getBoundingBox();
        boolean frontVisible = OcclusionCuller.isVisible(blocks, eyeX, eyeY, eyeZ, a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ);
        boolean behindVisible = OcclusionCuller.isVisible(blocks, eyeX, eyeY, eyeZ, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
        CullState state = (CullState) behind;
        boolean skipped = state.meridian$cullCheckedAt() != 0 && !state.meridian$cullVisible();
        if (frontVisible && !behindVisible && skipped) {
            Log.info("Self-test: occlusion culling OK");
        } else {
            Log.error("Self-test FAILED: occlusion culling (front visible {}, behind visible {}, skipped while rendering {})",
                    frontVisible, behindVisible, skipped);
        }
        Object frontChest = client.world.getBlockEntity(new BlockPos(sceneX + 3, SCENE_Y, sceneZ + 2));
        Object behindChest = client.world.getBlockEntity(new BlockPos(sceneX + 9, SCENE_Y, sceneZ + 2));
        if (!(frontChest instanceof CullState) || !(behindChest instanceof CullState)) {
            Log.error("Self-test FAILED: block entity culling scene incomplete (front {}, behind {})", frontChest, behindChest);
            return;
        }
        CullState frontState = (CullState) frontChest;
        CullState behindState = (CullState) behindChest;
        boolean frontShown = frontState.meridian$cullCheckedAt() != 0 && frontState.meridian$cullVisible();
        boolean behindSkipped = behindState.meridian$cullCheckedAt() != 0 && !behindState.meridian$cullVisible();
        if (frontShown && behindSkipped) {
            Log.info("Self-test: block entity culling OK");
        } else {
            Log.error("Self-test FAILED: block entity culling (front shown {}, behind skipped {})", frontShown, behindSkipped);
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
