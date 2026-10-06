package dev.medirian.mc1_21_8;

import dev.medirian.core.Log;
import dev.medirian.core.Medirian;
import dev.medirian.module.impl.world.WaypointsModule;
import dev.medirian.ui.WaypointsScreen;
import dev.medirian.waypoint.Waypoint;
import dev.medirian.waypoint.WaypointStore;
import dev.medirian.perf.CullState;
import dev.medirian.perf.OcclusionCuller;
import dev.medirian.platform.Occluders;
import dev.medirian.ui.HudEditorScreen;
import dev.medirian.ui.ModMenuScreen;
import dev.medirian.ui.SettingsScreen;
import dev.medirian.ui.CosmeticsScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Developer self-test (never active for players): enabled with {@code -Dmedirian.selftest=true}
 * via {@code ./gradlew runClient -Pselftest}. Walks through Medirian's screens and an in-game HUD,
 * saving a screenshot of each step to {@code run/screenshots/}, then quits. Used to verify
 * rendering of every adapter after changes.
 */
final class SelfTest {

    private static final String WORLD = "medirian-selftest";
    private static final boolean ENABLED = Boolean.getBoolean("medirian.selftest");
    static final String STACKED = "Medirian self-test: repeated line";

    private static final String CAPE = "cape_moonlit";
    /** Medirian services end to end, when a services URL is configured. */
    private static dev.medirian.services.ServicesSelfTest servicesCheck;
    private static int step;
    private static int wait;
    /** Culling scene: a stone platform at this height above the player, so the result never depends on terrain. */
    private static final int SCENE_Y = 200;
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

    static void tick(Minecraft minecraft) {
        if (!ENABLED) {
            return;
        }
        Medirian medirian = Medirian.get();
        if (servicesCheck != null) {
            servicesCheck.poll();
        }
        if (sampling) {
            sampleSum += medirian.performance().worldRenderMs();
            sampleCount++;
            // a real keyboard / mouse used the window: the measurement no longer compares like with like
            LocalPlayer player = minecraft.player;
            if (!disturbed && (minecraft.screen != null || Math.abs(player.getYRot() + 90f) > 1f || Math.abs(player.getXRot()) > 1f)) {
                disturbed = true;
                Log.warn("Self-test benchmark disturbed (screen {}, yaw {}, pitch {})", minecraft.screen, player.getYRot(), player.getXRot());
            }
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (step) {
            case 0:
                if (minecraft.screen instanceof ScreenBridge && ((ScreenBridge) minecraft.screen).medirian() instanceof dev.medirian.ui.TitleMenuScreen) {
                    // the menu screenshots are compared with baselines (scripts/visual-test.mjs):
                    // no animations, toasts or winter snow (runtime only, the settings are untouched)
                    dev.medirian.render.Anim.setEnabled(false);
                    dev.medirian.render.Theme.setWinterSnow(false);
                    medirian.notifications().setEnabled(false);
                    next(60);
                }
                return;
            case 1:
                // Medirian's main menu replaced Minecraft's title screen
                shot(minecraft, "0-title");
                medirian.platform().openScreen(new ModMenuScreen(null));
                next(30);
                return;
            case 2:
                shot(minecraft, "1-modmenu");
                medirian.platform().openScreen(new HudEditorScreen(null));
                next(30);
                return;
            case 3:
                shot(minecraft, "2-hudeditor");
                medirian.platform().openScreen(new SettingsScreen(null));
                next(30);
                return;
            case 4:
                shot(minecraft, "3-settings");
                // a cape for the cosmetics screen and the third-person shot
                medirian.cosmetics().equip(medirian.cosmetics().byId(CAPE));
                medirian.cosmetics().equip(medirian.cosmetics().byId("hat_tophat"));
                medirian.cosmetics().equip(medirian.cosmetics().byId("wings_medirian"));
                medirian.cosmetics().equip(medirian.cosmetics().byId("trail_sparkles"));
                medirian.cosmetics().equip(medirian.cosmetics().byId("emote_wave"));
                medirian.platform().openScreen(new CosmeticsScreen(null));
                next(30);
                return;
            case 5:
                shot(minecraft, "3b-cosmetics");
                servicesCheck = new dev.medirian.services.ServicesSelfTest(medirian);
                medirian.platform().openScreen(new CosmeticsScreen(null).showTab(dev.medirian.cosmetics.CosmeticType.HAT));
                next(20);
                return;
            case 6:
                shot(minecraft, "3c-cosmetics-hats");
                medirian.platform().openScreen(null);
                medirian.config().loadProfile("PvP", false);
                enterWorld(minecraft);
                next(20);
                return;
            case 7:
                if (minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
                    prepareScene(medirian);
                    minecraft.player.setXRot(30f); // the tagged pig's name and a selected block both in view
                    minecraft.player.connection.sendCommand("summon pig ^ ^ ^3 {CustomName:\"Tagged\",CustomNameVisible:1b}");
                    // live data for Armor Status and Potion Effects
                    minecraft.player.connection.sendCommand("item replace entity @s armor.head with diamond_helmet");
                    minecraft.player.connection.sendCommand("item replace entity @s armor.chest with iron_chestplate");
                    minecraft.player.connection.sendCommand("item replace entity @s weapon.mainhand with diamond_sword");
                    minecraft.player.connection.sendCommand("effect give @s speed 120 1");
                    minecraft.player.connection.sendCommand("effect give @s poison 120 0");
                    for (int i = 0; i < 3; i++) {
                        minecraft.gui.getChat().addMessage(Component.literal(STACKED));
                    }
                    minecraft.gui.getChat().addMessage(Component.literal("Medirian self-test: chat line"));
                    next(120);
                }
                return;
            case 8:
                // hurt the pig right before the screenshot to show the Hit Color flash
                minecraft.player.connection.sendCommand("damage @e[type=pig,limit=1,sort=nearest] 1");
                minecraft.player.setXRot(30f); // the tagged pig's name and a selected block both in view
                // really burning (creative players do not catch fire), only for the screenshot: Fire Overlay
                minecraft.player.connection.sendCommand("gamemode survival");
                minecraft.player.connection.sendCommand("setblock ~ ~ ~ fire");
                next(3);
                return;
            case 9:
                verifyChat(minecraft);
                shot(minecraft, "4-hud");
                minecraft.player.connection.sendCommand("gamemode creative");
                minecraft.player.connection.sendCommand("setblock ~ ~ ~ air");
                // third person from behind, in daylight: the Medirian cape
                minecraft.player.connection.sendCommand("time set 1000");
                minecraft.player.connection.sendCommand("weather clear");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                medirian.emotes().playEquipped();
                next(20);
                return;
            case 10:
                if (hatTour(minecraft)) {
                    return;
                }
                verifyCape(minecraft);
                if (dev.medirian.mc1_21_8.ModernCosmetics.drawn > 0) {
                    Log.info("Self-test: hat and wings OK ({} drawn)", dev.medirian.mc1_21_8.ModernCosmetics.drawn);
                } else {
                    Log.error("Self-test FAILED: hat and wings were not drawn");
                }
                if (dev.medirian.platform.Hooks.emotePose(minecraft.player.getUUID(), true, new dev.medirian.cosmetics.emote.EmotePose())) {
                    Log.info("Self-test: emote OK (waving)");
                } else {
                    Log.error("Self-test FAILED: the emote is not playing");
                }
                shot(minecraft, "4b-cape");
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                // press the mod menu key through the game's real keyboard handler
                pressKey(minecraft, GLFW.GLFW_KEY_RIGHT_SHIFT);
                next(30);
                return;
            case 11:
                if (minecraft.screen instanceof ScreenBridge bridge && bridge.medirian() instanceof ModMenuScreen) {
                    Log.info("Self-test: mod menu key OK");
                } else {
                    Log.error("Self-test FAILED: mod menu key did not open the mod menu (screen: {})", minecraft.screen);
                }
                shot(minecraft, "5-modmenu-ingame");
                // in a world the Target HUD previews the local player's head
                medirian.platform().openScreen(new HudEditorScreen(null));
                next(30);
                return;
            case 12:
                shot(minecraft, "5b-hudeditor-ingame");
                medirian.platform().openScreen(null);
                verifyChatCopy(minecraft);
                sendPolicy(minecraft, "{\"disable\":[\"freelook\"],\"message\":\"Self-test policy\"}");
                buildCullingScene(minecraft.player);
                next(40);
                return;
            case 13:
                verifyPolicy(medirian);
                verifyCulling(minecraft);
                shot(minecraft, "6-culling");
                medirian.platform().openScreen(new WaypointsScreen(null));
                next(30);
                return;
            case 14:
                shot(minecraft, "6b-waypoints");
                medirian.platform().openScreen(null);
                spawnCrowd(minecraft.player);
                // measure rendering, not the frame limiter or a pause menu
                medirian.modules().get("dynamicfps").setEnabled(false);
                minecraft.options.pauseOnLostFocus = false;
                minecraft.options.enableVsync().set(false);
                minecraft.options.framerateLimit().set(260);
                next(80);
                return;
            case 15:
                aimAtWall(minecraft.player);
                startSampling();
                next(BENCH_TICKS);
                return;
            case 16:
                shot(minecraft, "7-bench-occlusion");
                Log.info("Self-test benchmark: {} pigs hidden by occlusion", hiddenPigs(minecraft));
                onWorldMs = stopSampling();
                onFrameMs = medirian.performance().frames().averageFrameMs();
                occlusion(medirian, false);
                next(40);
                return;
            case 17:
                aimAtWall(minecraft.player);
                startSampling();
                next(BENCH_TICKS);
                return;
            case 18:
                shot(minecraft, "8-bench-no-occlusion");
                float offWorldMs = stopSampling();
                float offFrameMs = medirian.performance().frames().averageFrameMs();
                occlusion(medirian, true);
                // the saved test world would otherwise keep the crowd for the next run
                minecraft.player.connection.sendCommand("kill @e[type=pig]");
                if (disturbed) {
                    Log.warn("Self-test benchmark skipped: the game window was used while measuring");
                } else {
                    Log.info("Self-test benchmark ({} pigs behind a wall): world render {} ms with occlusion vs {} ms without; frame {} ms vs {} ms",
                            BENCH_PIGS, String.format("%.2f", onWorldMs), String.format("%.2f", offWorldMs),
                            String.format("%.2f", onFrameMs), String.format("%.2f", offFrameMs));
                }
                next(20);
                return;
            case 19:
                // winter snow over the menu (forced, whatever the date)
                dev.medirian.render.Theme.previewSnow(true);
                // and a translation other than English/Polish (umlauts in the font)
                dev.medirian.i18n.I18n.setLanguage(dev.medirian.i18n.I18n.Language.DE_DE);
                medirian.platform().openScreen(new ModMenuScreen(null));
                next(30);
                return;
            case 20:
                shot(minecraft, "9-winter");
                dev.medirian.render.Theme.previewSnow(false);
                medirian.platform().openScreen(null);
                next(10);
                return;
            case 21:
                // Medirian's smooth font (the setting lives in the throwaway self-test home)
                medirian.settings().font.set(dev.medirian.render.font.UiFont.MEDIRIAN);
                medirian.platform().openScreen(new ModMenuScreen(null));
                next(20);
                return;
            case 22:
                shot(minecraft, "9c-font");
                medirian.settings().font.set(dev.medirian.render.font.UiFont.MINECRAFT);
                medirian.platform().openScreen(null);
                next(10);
                return;
            default:
                if (servicesCheck != null && !servicesCheck.poll()) {
                    return; // still waiting for Medirian services (the check times out by itself)
                }
                Log.info("Self-test finished; screenshots in {}", minecraft.gameDirectory);
                step = -1;
                minecraft.stop();
        }
    }

    /** Turns on the render modules whose effect is only visible in a world (shared by both adapters). */
    static void prepareScene(Medirian medirian) {
        medirian.modules().get("blockoverlay").setEnabled(true);
        ((dev.medirian.setting.BooleanSetting) medirian.modules().get("blockoverlay").setting("fill")).set(true);
        medirian.modules().get("hitcolor").setEnabled(true);
        medirian.modules().get("chat").setEnabled(true);
        medirian.modules().get("entityculling").setEnabled(true);
        medirian.modules().get("healthtags").setEnabled(true);
        ((dev.medirian.setting.BooleanSetting) medirian.modules().get("healthtags").setting("mobs")).set(true);
        medirian.modules().get("fireoverlay").setEnabled(true);
        medirian.modules().get("hurtcam").setEnabled(true);
        medirian.modules().get("speed").setEnabled(true);
    }

    /** A platform high above the player facing east with a stone wall; one pig in front of it, one behind it. */
    private static void buildCullingScene(LocalPlayer player) {
        sceneX = (int) Math.floor(player.getX());
        sceneZ = (int) Math.floor(player.getZ());
        int x = sceneX;
        int z = sceneZ;
        int y = SCENE_Y;
        player.connection.sendCommand("kill @e[type=pig]");
        player.connection.sendCommand("kill @e[type=item]");
        player.connection.sendCommand("fill " + (x - 2) + " " + (y - 1) + " " + (z - 4) + " " + (x + 11) + " " + (y + 4) + " " + (z + 4) + " air");
        player.connection.sendCommand("fill " + (x - 2) + " " + (y - 1) + " " + (z - 4) + " " + (x + 11) + " " + (y - 1) + " " + (z + 4) + " stone");
        player.connection.sendCommand("fill " + (x + 6) + " " + y + " " + (z - 4) + " " + (x + 6) + " " + (y + 4) + " " + (z + 4) + " stone");
        player.connection.sendCommand("tp @s " + (x + 0.5) + " " + y + " " + (z + 0.5) + " -90 0");
        player.connection.sendCommand("summon pig " + (x + 3.5) + " " + y + " " + (z + 0.5) + " {NoAI:1b}");
        player.connection.sendCommand("summon pig " + (x + 9.5) + " " + y + " " + (z + 0.5) + " {NoAI:1b}");
        player.connection.sendCommand("setblock " + (x + 3) + " " + y + " " + (z + 2) + " chest");
        player.connection.sendCommand("setblock " + (x + 9) + " " + y + " " + (z + 2) + " chest");
        // a waypoint on top of a gold block: its marker must sit on the block's top face
        player.connection.sendCommand("setblock " + (x + 5) + " " + y + " " + (z - 2) + " gold_block");
        // dropped items for Item Physics: one flat item, one block
        Medirian.get().modules().get("itemphysics").setEnabled(true);
        player.connection.sendCommand("summon item " + (x + 4.5) + " " + y + " " + (z - 0.5) + " {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767}");
        player.connection.sendCommand("summon item " + (x + 4.5) + " " + y + " " + (z + 1.5) + " {Item:{id:\"minecraft:stone\",count:1},PickupDelay:32767}");
        addSceneWaypoint(x + 5, y, z - 2);
    }

    /** A crowd of pigs behind the wall (several per spot: only the rendering cost matters). */
    private static void spawnCrowd(LocalPlayer player) {
        for (int i = 0; i < BENCH_PIGS; i++) {
            double x = sceneX + 7.5 + (i % 4) * 0.9;
            double z = sceneZ - 3.5 + (i / 4) % 8;
            player.connection.sendCommand("summon pig " + x + " " + SCENE_Y + " " + z + " {NoAI:1b}");
        }
    }

    /** Pigs whose last occlusion test (within the last second) found them hidden. */
    private static int hiddenPigs(Minecraft minecraft) {
        int hidden = 0;
        long now = System.nanoTime();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            CullState state = (CullState) entity;
            if (entity.getType() == EntityType.PIG && state.medirian$cullCheckedAt() != 0
                    && now - state.medirian$cullCheckedAt() < 1_000_000_000L && !state.medirian$cullVisible()) {
                hidden++;
            }
        }
        return hidden;
    }

    /** Looks east at the wall from the scene position. */
    private static void aimAtWall(LocalPlayer player) {
        player.setYRot(-90f);
        player.setYHeadRot(-90f);
        player.setXRot(0f);
    }

    private static void occlusion(Medirian medirian, boolean on) {
        ((dev.medirian.setting.BooleanSetting) medirian.modules().get("entityculling").setting("occlusion")).set(on);
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
        Occluders blocks = Medirian.get().platform().game();
        // the scene's eye position (the player may have been moved by a real keyboard in the meantime)
        Vec3 eye = new Vec3(sceneX + 0.5, SCENE_Y + 1.62, sceneZ + 0.5);
        boolean frontVisible = visible(blocks, eye, front.getBoundingBox());
        boolean behindVisible = visible(blocks, eye, behind.getBoundingBox());
        CullState state = (CullState) behind;
        boolean skipped = state.medirian$cullCheckedAt() != 0 && !state.medirian$cullVisible();
        if (frontVisible && !behindVisible && skipped) {
            Log.info("Self-test: occlusion culling OK");
        } else {
            Log.error("Self-test FAILED: occlusion culling (front visible {}, behind visible {}, skipped while rendering {})",
                    frontVisible, behindVisible, skipped);
        }
        Object frontChest = minecraft.level.getBlockEntity(new BlockPos(sceneX + 3, SCENE_Y, sceneZ + 2));
        Object behindChest = minecraft.level.getBlockEntity(new BlockPos(sceneX + 9, SCENE_Y, sceneZ + 2));
        verifyBlockEntityCulling(frontChest, behindChest);
    }

    /** Both chests must have been tested while rendering: the front one visible, the one behind the wall hidden. */
    static void verifyBlockEntityCulling(Object frontChest, Object behindChest) {
        if (!(frontChest instanceof CullState front) || !(behindChest instanceof CullState behind)) {
            Log.error("Self-test FAILED: block entity culling scene incomplete (front {}, behind {})", frontChest, behindChest);
            return;
        }
        boolean frontShown = front.medirian$cullCheckedAt() != 0 && front.medirian$cullVisible();
        boolean behindSkipped = behind.medirian$cullCheckedAt() != 0 && !behind.medirian$cullVisible();
        if (frontShown && behindSkipped) {
            Log.info("Self-test: block entity culling OK");
        } else {
            Log.error("Self-test FAILED: block entity culling (front shown {}, behind skipped {})", frontShown, behindSkipped);
        }
    }

    private static boolean visible(Occluders blocks, Vec3 eye, AABB box) {
        return OcclusionCuller.isVisible(blocks, eye.x, eye.y, eye.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    /**
     * Delivers a server policy the way the network does: encoded and decoded with vanilla's gameplay
     * codec (so the payload type must be registered), then handled by the packet listener.
     */
    private static void sendPolicy(Minecraft minecraft, String json) {
        Medirian.get().modules().get("freelook").setEnabled(true);
        var connection = minecraft.getConnection();
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), connection.registryAccess());
        net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.encode(buf,
                new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
                        new MedirianPayload(MedirianPayload.POLICY, json.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        var decoded = net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.decode(buf);
        connection.handleCustomPayload(decoded);
    }

    /** The policy locked Freelook; lifting it restores the module as it was. */
    private static void verifyPolicy(Medirian medirian) {
        dev.medirian.module.Module freelook = medirian.modules().get("freelook");
        boolean locked = freelook.isLocked() && !freelook.isEnabled();
        medirian.policies().clear();
        boolean restored = !freelook.isLocked() && freelook.isEnabled();
        if (locked && restored) {
            Log.info("Self-test: server policy OK (Freelook locked, then restored)");
        } else {
            Log.error("Self-test FAILED: server policy (locked {}, restored {})", locked, restored);
        }
        freelook.setEnabled(false);
        // the client side of the protocol: hello must survive the serverbound codec, and sending must not fail
        byte[] hello = dev.medirian.policy.ServerPolicy.hello("test", "1.21.8");
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket.STREAM_CODEC.encode(buf,
                new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(new MedirianPayload(MedirianPayload.HELLO, hello)));
        var decoded = net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket.STREAM_CODEC.decode(buf);
        boolean roundTrip = decoded.payload() instanceof MedirianPayload p && java.util.Arrays.equals(p.data(), hello);
        medirian.platform().actions().registerPluginChannels(java.util.Arrays.asList("medirian:policy", "medirian:hello"));
        medirian.platform().actions().sendPluginMessage("medirian:hello", hello);
        if (roundTrip) {
            Log.info("Self-test: plugin channel hello OK");
        } else {
            Log.error("Self-test FAILED: hello payload did not survive the codec ({})", decoded.payload());
        }
    }

    /** Every line of a wrapped message must resolve to the whole message (right-click copy). */
    private static void verifyChatCopy(Minecraft minecraft) {
        String message = "Medirian self-test: a long chat message that wraps onto several lines of the chat, so copying has to find the whole message from any of its lines. The end.";
        minecraft.gui.getChat().addMessage(Component.literal(message));
        minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
        ChatLookup chat = (ChatLookup) minecraft.gui.getChat();
        double scale = minecraft.options.chatScale().get();
        int base = net.minecraft.util.Mth.floor((minecraft.getWindow().getGuiScaledHeight() - 40) / (float) scale);
        int lineHeight = (int) (9 * (minecraft.options.chatLineSpacing().get() + 1.0));
        String bottom = chat.medirian$messageAt(20, (base - lineHeight / 2.0) * scale);
        String above = chat.medirian$messageAt(20, (base - lineHeight * 1.5) * scale);
        minecraft.setScreen(null);
        String expected = dev.medirian.module.impl.misc.ChatModule.stripDecorations(bottom == null ? "" : bottom);
        if (message.equals(expected) && bottom.equals(above)) {
            Log.info("Self-test: chat copy OK (both lines of the wrapped message)");
        } else {
            Log.error("Self-test FAILED: chat copy (bottom line: {}, line above: {})", bottom, above);
        }
    }

    /** Three identical lines must have become one "(x3)" line with a timestamp. */
    @SuppressWarnings("unchecked")
    /** The local player's skin must carry the Medirian cape. */
    private static void verifyCape(Minecraft minecraft) {
        net.minecraft.client.resources.PlayerSkin skin = minecraft.player.getSkin();
        if (skin.capeTexture() != null && skin.capeTexture().getPath().contains(CAPE)) {
            Log.info("Self-test: cape OK ({})", skin.capeTexture());
        } else {
            Log.error("Self-test FAILED: cape (skin {})", skin);
        }
    }

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

    /** Replaces the test world's waypoints with one at the given block. */
    private static void addSceneWaypoint(int x, int y, int z) {
        Medirian medirian = Medirian.get();
        WaypointsModule module = medirian.modules().get(WaypointsModule.class);
        module.setEnabled(true);
        String world = medirian.game().worldKey();
        WaypointStore store = module.store();
        for (Waypoint old : new java.util.ArrayList<Waypoint>(store.of(world))) {
            store.remove(world, old);
        }
        store.add(world, new Waypoint("Gold", x, y, z, medirian.game().player().dimensionId(), 0xFFE8C547));
        // behind the camera (it looks east): shown as a marker at the screen edge
        store.add(world, new Waypoint("Behind", x - 30, y, z + 4, medirian.game().player().dimensionId(), 0xFF4C8DFF));
    }

    private static final String[] HATS = {"hat_tophat", "hat_crown", "hat_witch", "hat_santa"};
    private static int hat = -1;
    private static float tourYaw;
    private static float tourPitch;
    private static int tourFov;

    /**
     * Every hat on the player seen from the front (third person, close up), one screenshot each:
     * how the hats sit on the head. Returns true while the tour is still running.
     */
    private static boolean hatTour(Minecraft minecraft) {
        if (hat >= HATS.length) {
            return false;
        }
        Medirian medirian = Medirian.get();
        LocalPlayer player = minecraft.player;
        if (hat < 0) {
            // turn away from the test scene so the camera in front of the face has a free view
            tourYaw = player.getYRot();
            tourPitch = player.getXRot();
            tourFov = minecraft.options.fov().get();
            face(player, tourYaw + 180f, 0f);
            // hats hide under a helmet: the tour is about the bare head
            player.connection.sendCommand("item replace entity @s armor.head with air");
            minecraft.options.hideGui = true;
            minecraft.options.fov().set(30);
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        } else {
            shot(minecraft, "4c-" + HATS[hat]);
        }
        hat++;
        if (hat < HATS.length) {
            medirian.cosmetics().equip(medirian.cosmetics().byId(HATS[hat]));
        } else {
            // back to the cape screenshot's view, hat and wave
            face(player, tourYaw, tourPitch);
            player.connection.sendCommand("item replace entity @s armor.head with diamond_helmet");
            minecraft.options.hideGui = false;
            minecraft.options.fov().set(tourFov);
            medirian.cosmetics().equip(medirian.cosmetics().byId("hat_tophat"));
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            medirian.emotes().playEquipped();
        }
        // the first shot waits for the wave started before the cape screenshot to end (2.4 s)
        wait = hat == 0 ? 30 : hat < HATS.length ? 8 : 20;
        return true;
    }

    private static void face(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
    }

    private static void next(int ticks) {
        step++;
        wait = ticks;
    }

    /** Sends a key press + release through KeyboardHandler#keyPress (named method: dev environment only). */
    private static void pressKey(Minecraft minecraft, int glfwKey) {
        try {
            Method keyPress = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, int.class, int.class, int.class);
            keyPress.setAccessible(true);
            long window = minecraft.getWindow().getWindow();
            keyPress.invoke(minecraft.keyboardHandler, window, glfwKey, 0, GLFW.GLFW_PRESS, 0);
            keyPress.invoke(minecraft.keyboardHandler, window, glfwKey, 0, GLFW.GLFW_RELEASE, 0);
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
