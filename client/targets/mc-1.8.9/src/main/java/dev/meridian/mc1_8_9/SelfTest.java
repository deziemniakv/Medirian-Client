package dev.meridian.mc1_8_9;

import dev.meridian.core.Log;
import dev.meridian.core.Meridian;
import dev.meridian.module.impl.world.WaypointsModule;
import dev.meridian.ui.WaypointsScreen;
import dev.meridian.waypoint.Waypoint;
import dev.meridian.waypoint.WaypointStore;
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
import org.lwjgl.opengl.Display;

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
    /** Occlusion benchmark: pigs behind the wall, world render time measured with occlusion on and off. */
    private static final int BENCH_PIGS = 160;
    private static final int BENCH_TICKS = 100;
    private static boolean sampling;
    private static double sampleSum;
    private static int sampleCount;
    private static float onWorldMs;
    private static float onFrameMs;
    private static boolean disturbed;

    private SelfTest() {
    }

    public static void tick(MinecraftClient client) {
        if (!ENABLED || step < 0) {
            return;
        }
        Meridian meridian = Meridian.get();
        if (sampling) {
            sampleSum += meridian.performance().worldRenderMs();
            sampleCount++;
            // a real keyboard / mouse used the window: the measurement no longer compares like with like
            if (!disturbed && (client.currentScreen != null || Math.abs(client.player.yaw + 90f) > 1f || Math.abs(client.player.pitch) > 1f)) {
                disturbed = true;
                Log.warn("Self-test benchmark disturbed (screen {}, yaw {}, pitch {})", client.currentScreen, client.player.yaw, client.player.pitch);
            }
        }
        if (wait > 0) {
            wait--;
            return;
        }
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
                    meridian.modules().get("healthtags").setEnabled(true);
                    ((BooleanSetting) meridian.modules().get("healthtags").setting("mobs")).set(true);
                    meridian.modules().get("fireoverlay").setEnabled(true);
                    meridian.modules().get("hurtcam").setEnabled(true);
                    meridian.modules().get("speed").setEnabled(true);
                    client.player.sendChatMessage("/summon Pig ~1.5 ~ ~1.5");
                    // a named pig five blocks in front of the player (farther than the Hit Color pig, so the damage effect skips it)
                    double yaw = Math.toRadians(client.player.yaw);
                    client.player.sendChatMessage(String.format(java.util.Locale.ROOT, "/summon Pig %.2f %.2f %.2f {CustomName:\"Tagged\",CustomNameVisible:1,NoAI:1}",
                            client.player.x - Math.sin(yaw) * 5, client.player.y, client.player.z + Math.cos(yaw) * 5));
                    // live data for Armor Status and Potion Effects
                    client.player.sendChatMessage("/replaceitem entity @p slot.armor.head diamond_helmet");
                    client.player.sendChatMessage("/replaceitem entity @p slot.armor.chest iron_chestplate");
                    client.player.sendChatMessage("/replaceitem entity @p slot.hotbar.0 diamond_sword");
                    client.player.inventory.selectedSlot = 0;
                    client.player.sendChatMessage("/effect @p speed 120 1");
                    client.player.sendChatMessage("/effect @p poison 120 0");
                    for (int i = 0; i < 3; i++) {
                        client.inGameHud.getChatHud().addMessage(new LiteralText(STACKED));
                    }
                    client.inGameHud.getChatHud().addMessage(new LiteralText("Meridian self-test: chat line"));
                    next(100);
                }
                return;
            case 6:
                // hurt the pig (instant damage) right before the screenshot to show the Hit Color flash
                client.player.sendChatMessage("/effect @e[type=Pig,name=!Tagged,c=1] 7 1 0");
                // look at the ground so a block is selected (set late: joining resets the rotation)
                client.player.pitch = 30f;
                client.player.prevPitch = 30f;
                // really burning (creative players do not catch fire), only for the screenshot: Fire Overlay
                client.player.sendChatMessage("/gamemode 0");
                client.player.sendChatMessage("/setblock ~ ~ ~ fire");
                next(3);
                return;
            case 7:
                verifyHealthTag(client);
                verifyChat(client);
                shot(client, "4-hud");
                client.player.sendChatMessage("/gamemode 1");
                client.player.sendChatMessage("/setblock ~ ~ ~ air");
                meridian.platform().openScreen(new ModMenuScreen(null));
                next(30);
                return;
            case 8:
                shot(client, "5-modmenu-ingame");
                // in a world the Target HUD previews the local player's head
                meridian.platform().openScreen(new HudEditorScreen(null));
                next(30);
                return;
            case 9:
                shot(client, "5b-hudeditor-ingame");
                meridian.platform().openScreen(null);
                verifyChatCopy(client);
                sendPolicy(client, "{\"disable\":[\"freelook\"],\"message\":\"Self-test policy\"}");
                buildCullingScene(client.player);
                next(40);
                return;
            case 10:
                verifyPolicy(meridian);
                verifyCulling(client);
                shot(client, "6-culling");
                meridian.platform().openScreen(new WaypointsScreen(null));
                next(30);
                return;
            case 11:
                shot(client, "6b-waypoints");
                meridian.platform().openScreen(null);
                spawnCrowd(client.player);
                // measure rendering, not the frame limiter or a pause menu
                meridian.modules().get("dynamicfps").setEnabled(false);
                client.options.pauseOnLostFocus = false;
                client.options.vsync = false;
                Display.setVSyncEnabled(false);
                client.options.maxFramerate = 260;
                next(80);
                return;
            case 12:
                aimAtWall(client.player);
                startSampling();
                next(BENCH_TICKS);
                return;
            case 13:
                shot(client, "7-bench-occlusion");
                onWorldMs = stopSampling();
                onFrameMs = meridian.performance().frames().averageFrameMs();
                occlusion(meridian, false);
                next(40);
                return;
            case 14:
                aimAtWall(client.player);
                startSampling();
                next(BENCH_TICKS);
                return;
            case 15:
                shot(client, "8-bench-no-occlusion");
                float offWorldMs = stopSampling();
                float offFrameMs = meridian.performance().frames().averageFrameMs();
                occlusion(meridian, true);
                // the saved test world would otherwise keep the crowd for the next run
                client.player.sendChatMessage("/kill @e[type=Pig]");
                if (disturbed) {
                    Log.warn("Self-test benchmark skipped: the game window was used while measuring");
                } else {
                    Log.info("Self-test benchmark ({} pigs behind a wall): world render {} ms with occlusion vs {} ms without; frame {} ms vs {} ms",
                            BENCH_PIGS, String.format("%.2f", onWorldMs), String.format("%.2f", offWorldMs),
                            String.format("%.2f", onFrameMs), String.format("%.2f", offFrameMs));
                }
                next(20);
                return;
            default:
                Log.info("Self-test finished; screenshots in {}", client.runDirectory);
                step = -1;
                client.scheduleStop();
        }
    }

    /** The named pig's label must end with its health in hearts (10 HP pig: "§a5❤" or less). */
    private static void verifyHealthTag(MinecraftClient client) {
        for (Entity entity : new ArrayList<Entity>(client.world.loadedEntities)) {
            if (entity instanceof PigEntity && "Tagged".equals(((PigEntity) entity).getCustomName())) {
                String label = NameTags.label("Tagged", (PigEntity) entity);
                if (label.matches("Tagged §[ace][0-9.]+❤")) {
                    Log.info("Self-test: health tag OK ({})", label);
                } else {
                    Log.error("Self-test FAILED: health tag ({})", label);
                }
                return;
            }
        }
        Log.error("Self-test FAILED: health tag pig not found");
    }

    /** Delivers a server policy to the network handler like a received plugin message. */
    private static void sendPolicy(MinecraftClient client, String json) {
        Meridian.get().modules().get("freelook").setEnabled(true);
        client.getNetworkHandler().onCustomPayload(new net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket(
                "meridian:policy", new net.minecraft.util.PacketByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
    }

    /** The policy locked Freelook; lifting it restores the module as it was. */
    private static void verifyPolicy(Meridian meridian) {
        dev.meridian.module.Module freelook = meridian.modules().get("freelook");
        boolean locked = freelook.isLocked() && !freelook.isEnabled();
        meridian.policies().clear();
        boolean restored = !freelook.isLocked() && freelook.isEnabled();
        if (locked && restored) {
            Log.info("Self-test: server policy OK (Freelook locked, then restored)");
        } else {
            Log.error("Self-test FAILED: server policy (locked {}, restored {})", locked, restored);
        }
        freelook.setEnabled(false);
        // the client side of the protocol must not fail (singleplayer accepts any plugin message)
        meridian.platform().actions().registerPluginChannels(java.util.Arrays.asList("meridian:policy", "meridian:hello"));
        meridian.platform().actions().sendPluginMessage("meridian:hello", dev.meridian.policy.ServerPolicy.hello("test", "1.8.9"));
        Log.info("Self-test: plugin channel hello sent");
    }

    /** Every line of a wrapped message must resolve to the whole message (right-click copy). */
    private static void verifyChatCopy(MinecraftClient client) {
        String message = "Meridian self-test: a long chat message that wraps onto several lines of the chat, so copying has to find the whole message from any of its lines. The end.";
        client.inGameHud.getChatHud().addMessage(new LiteralText(message));
        client.setScreen(new net.minecraft.client.gui.screen.ChatScreen());
        ChatLookup chat = (ChatLookup) client.inGameHud.getChatHud();
        int scaleFactor = new net.minecraft.client.util.Window(client).getScaleFactor();
        float chatScale = client.inGameHud.getChatHud().getChatScale();
        int fontHeight = client.textRenderer.fontHeight;
        // raw window coordinates (origin bottom-left), the middle of the bottom line and of the one above
        int x = 20 * scaleFactor;
        String bottom = chat.meridian$messageAt(x, (int) ((27 + fontHeight * 0.5f * chatScale) * scaleFactor));
        String above = chat.meridian$messageAt(x, (int) ((27 + fontHeight * 1.5f * chatScale) * scaleFactor));
        client.setScreen(null);
        String expected = dev.meridian.module.impl.misc.ChatModule.stripDecorations(bottom == null ? "" : bottom);
        if (message.equals(expected) && bottom.equals(above)) {
            Log.info("Self-test: chat copy OK (both lines of the wrapped message)");
        } else {
            Log.error("Self-test FAILED: chat copy (bottom line: {}, line above: {})", bottom, above);
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
        player.sendChatMessage("/kill @e[type=Item]");
        player.sendChatMessage("/fill " + (x - 2) + " " + (y - 1) + " " + (z - 4) + " " + (x + 11) + " " + (y + 4) + " " + (z + 4) + " air");
        player.sendChatMessage("/fill " + (x - 2) + " " + (y - 1) + " " + (z - 4) + " " + (x + 11) + " " + (y - 1) + " " + (z + 4) + " stone");
        player.sendChatMessage("/fill " + (x + 6) + " " + y + " " + (z - 4) + " " + (x + 6) + " " + (y + 4) + " " + (z + 4) + " stone");
        player.sendChatMessage("/tp " + (x + 0.5) + " " + y + " " + (z + 0.5) + " -90 0");
        player.sendChatMessage("/summon Pig " + (x + 3.5) + " " + y + " " + (z + 0.5) + " {NoAI:1}");
        player.sendChatMessage("/summon Pig " + (x + 9.5) + " " + y + " " + (z + 0.5) + " {NoAI:1}");
        player.sendChatMessage("/setblock " + (x + 3) + " " + y + " " + (z + 2) + " chest");
        player.sendChatMessage("/setblock " + (x + 9) + " " + y + " " + (z + 2) + " chest");
        // a waypoint on top of a gold block: its marker must sit on the block's top face
        player.sendChatMessage("/setblock " + (x + 5) + " " + y + " " + (z - 2) + " gold_block");
        // dropped items for Item Physics: one flat item, one block
        Meridian.get().modules().get("itemphysics").setEnabled(true);
        player.sendChatMessage("/summon Item " + (x + 4.5) + " " + y + " " + (z - 0.5) + " {Item:{id:\"minecraft:diamond\",Count:1},PickupDelay:32767}");
        player.sendChatMessage("/summon Item " + (x + 4.5) + " " + y + " " + (z + 1.5) + " {Item:{id:\"minecraft:stone\",Count:1},PickupDelay:32767}");
        addSceneWaypoint(x + 5, y, z - 2);
    }

    /** A crowd of pigs behind the wall (several per spot: only the rendering cost matters). */
    private static void spawnCrowd(ClientPlayerEntity player) {
        for (int i = 0; i < BENCH_PIGS; i++) {
            double x = sceneX + 7.5 + (i % 4) * 0.9;
            double z = sceneZ - 3.5 + (i / 4) % 8;
            player.sendChatMessage("/summon Pig " + x + " " + SCENE_Y + " " + z + " {NoAI:1}");
        }
    }

    /** Looks east at the wall from the scene position. */
    private static void aimAtWall(ClientPlayerEntity player) {
        player.yaw = -90f;
        player.prevYaw = -90f;
        player.pitch = 0f;
        player.prevPitch = 0f;
    }

    private static void occlusion(Meridian meridian, boolean on) {
        ((BooleanSetting) meridian.modules().get("entityculling").setting("occlusion")).set(on);
    }

    private static void startSampling() {
        sampleSum = 0;
        sampleCount = 0;
        sampling = true;
    }

    private static float stopSampling() {
        sampling = false;
        return sampleCount == 0 ? 0 : (float) (sampleSum / sampleCount);
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

    /** Replaces the test world's waypoints with one at the given block. */
    private static void addSceneWaypoint(int x, int y, int z) {
        Meridian meridian = Meridian.get();
        WaypointsModule module = meridian.modules().get(WaypointsModule.class);
        module.setEnabled(true);
        String world = meridian.game().worldKey();
        WaypointStore store = module.store();
        for (Waypoint old : new java.util.ArrayList<Waypoint>(store.of(world))) {
            store.remove(world, old);
        }
        store.add(world, new Waypoint("Gold", x, y, z, meridian.game().player().dimensionId(), 0xFFE8C547));
        // behind the camera (it looks east): shown as a marker at the screen edge
        store.add(world, new Waypoint("Behind", x - 30, y, z + 4, meridian.game().player().dimensionId(), 0xFF4C8DFF));
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
