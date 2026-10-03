package dev.meridian.platform;

import dev.meridian.core.Log;
import dev.meridian.core.Meridian;
import dev.meridian.event.Events;
import dev.meridian.input.Key;
import dev.meridian.module.impl.combat.HitColorModule;
import dev.meridian.module.impl.misc.ChatModule;
import dev.meridian.module.impl.misc.ScreenshotModule;
import dev.meridian.module.impl.movement.FreelookModule;
import dev.meridian.module.impl.movement.ToggleSneakModule;
import dev.meridian.module.impl.movement.ToggleSprintModule;
import dev.meridian.module.impl.movement.ZoomModule;
import dev.meridian.module.impl.performance.DynamicFpsModule;
import dev.meridian.module.impl.performance.EntityCullingModule;
import dev.meridian.module.impl.performance.ParticleControlModule;
import dev.meridian.module.impl.player.PotionEffectsModule;
import dev.meridian.module.impl.render.BlockOverlayModule;
import dev.meridian.module.impl.render.CrosshairModule;
import dev.meridian.module.impl.render.FullbrightModule;
import dev.meridian.module.impl.render.ScoreboardModule;
import dev.meridian.module.impl.render.TimeChangerModule;
import dev.meridian.module.impl.render.WeatherChangerModule;
import dev.meridian.perf.CullState;
import dev.meridian.render.Gfx;

import java.io.File;

/**
 * The adapter → shared contract. Mixins and adapter code call these static methods; nothing else
 * in shared is called from Minecraft code.
 *
 * <p>Every method is safe to call before {@link Meridian#boot} (it returns the vanilla value) and
 * must be cheap: they run inside render loops. Module references are resolved once at boot.
 */
public final class Hooks {

    private static Meridian meridian;
    private static ZoomModule zoom;
    private static FreelookModule freelook;
    private static FullbrightModule fullbright;
    private static TimeChangerModule timeChanger;
    private static WeatherChangerModule weather;
    private static ParticleControlModule particles;
    private static EntityCullingModule culling;
    private static DynamicFpsModule dynamicFps;
    private static CrosshairModule crosshair;
    private static ScoreboardModule scoreboard;
    private static PotionEffectsModule potions;
    private static ToggleSprintModule toggleSprint;
    private static ToggleSneakModule toggleSneak;
    private static BlockOverlayModule blockOverlay;
    private static HitColorModule hitColor;
    private static ChatModule chatModule;

    private Hooks() {
    }

    /** Called by {@link Meridian} during boot. */
    public static void bind(Meridian instance) {
        meridian = instance;
        zoom = instance.modules().get(ZoomModule.class);
        freelook = instance.modules().get(FreelookModule.class);
        fullbright = instance.modules().get(FullbrightModule.class);
        timeChanger = instance.modules().get(TimeChangerModule.class);
        weather = instance.modules().get(WeatherChangerModule.class);
        particles = instance.modules().get(ParticleControlModule.class);
        culling = instance.modules().get(EntityCullingModule.class);
        dynamicFps = instance.modules().get(DynamicFpsModule.class);
        crosshair = instance.modules().get(CrosshairModule.class);
        scoreboard = instance.modules().get(ScoreboardModule.class);
        potions = instance.modules().get(PotionEffectsModule.class);
        toggleSprint = instance.modules().get(ToggleSprintModule.class);
        toggleSneak = instance.modules().get(ToggleSneakModule.class);
        blockOverlay = instance.modules().get(BlockOverlayModule.class);
        hitColor = instance.modules().get(HitColorModule.class);
        chatModule = instance.modules().get(ChatModule.class);
    }

    // ------------------------------------------------------------------ lifecycle

    /** End of every client tick. */
    public static void clientTick() {
        if (meridian != null) {
            meridian.tick();
        }
    }

    /** Start of every rendered frame. */
    public static void frameStart() {
        if (meridian != null) {
            meridian.performance().onFrame();
        }
    }

    /** In-game HUD pass, {@code g} in GUI space. */
    public static void renderHud(Gfx g, float partialTicks) {
        if (meridian != null) {
            meridian.renderHud(g, partialTicks);
        }
    }

    public static void worldRenderStart() {
        if (meridian != null) {
            meridian.performance().worldRenderStart();
        }
    }

    public static void worldRenderEnd() {
        if (meridian != null) {
            meridian.performance().worldRenderEnd();
        }
    }

    public static void shutdown() {
        if (meridian != null) {
            try {
                meridian.shutdown();
            } catch (Throwable t) {
                Log.error("Error during shutdown", t);
            }
        }
    }

    // ------------------------------------------------------------------ input

    /** Keyboard key changed state while no screen is open. */
    public static void keyEvent(Key key, boolean pressed) {
        if (meridian != null) {
            meridian.keybinds().onKey(key, pressed);
            meridian.events().post(new Events.KeyInput(key, pressed));
        }
    }

    /** Mouse button changed state while no screen is open. */
    public static void mouseButton(int button, boolean pressed) {
        if (meridian == null) {
            return;
        }
        if (pressed) {
            meridian.inputStats().click(button, System.currentTimeMillis());
        }
        meridian.keybinds().onKey(Key.fromMouseButton(button), pressed);
        meridian.events().post(new Events.MouseButton(button, pressed));
    }

    /** Mouse wheel while playing. Returns true when Meridian consumed it (vanilla must ignore it). */
    public static boolean mouseScroll(double amount) {
        return zoom != null && zoom.onScroll(amount);
    }

    /** A screen was opened (or closed with {@code open = false}). */
    public static void screenChanged(boolean open) {
        if (meridian != null && open) {
            meridian.keybinds().releaseAll();
        }
    }

    // ------------------------------------------------------------------ game events

    public static void attack(EntityView target, double reach) {
        if (meridian != null) {
            meridian.events().post(new Events.AttackEntity(target, reach));
        }
    }

    /** Chat message received; {@code plainText} without formatting. */
    public static void chat(String plainText) {
        if (meridian != null) {
            meridian.events().post(new Events.ChatReceived(plainText));
        }
    }

    /** Timestamp prefix ("[14:05] ") for a new chat line, or null for none. */
    public static String chatTimestamp(String plainText) {
        return chatModule == null ? null : chatModule.timestamp(plainText);
    }

    /**
     * How many times in a row this chat line has arrived (1 = new line). When greater than 1 the
     * adapter removes the newest chat line and adds this one with a "(xN)" counter.
     *
     * @param previousShown the line Meridian saw last is still the newest line in the chat
     */
    public static int chatStack(String plainText, boolean previousShown) {
        return chatModule == null ? 1 : chatModule.stack(plainText, previousShown);
    }

    /** Number of chat messages / lines to keep. */
    public static int chatHistory(int vanilla) {
        return chatModule == null ? vanilla : chatModule.history(vanilla);
    }

    /** A screenshot was saved. May be called from any thread. */
    public static void screenshot(final File file) {
        final Meridian m = meridian;
        if (m != null) {
            m.runOnClientThread(() -> m.events().post(new Events.ScreenshotTaken(file)));
        }
    }

    /** True when the Screenshot module replaces the vanilla "Saved screenshot as…" chat line. */
    public static boolean suppressScreenshotChat() {
        ScreenshotModule module = meridian == null ? null : meridian.modules().get(ScreenshotModule.class);
        return module != null && module.isEnabled() && module.replacesChatMessage();
    }

    // ------------------------------------------------------------------ overrides

    /** Multiplier applied to the final camera FOV (zoom). */
    public static double fovMultiplier() {
        return zoom == null ? 1.0 : zoom.fovMultiplier();
    }

    /** True while zooming; adapters may use it to disable view bobbing / hand rendering. */
    public static boolean zooming() {
        return zoom != null && zoom.zooming();
    }

    public static boolean freelookActive() {
        return freelook != null && freelook.active();
    }

    public static float freelookYaw() {
        return freelook.yaw();
    }

    public static float freelookPitch() {
        return freelook.pitch();
    }

    /** Applies raw mouse deltas (vanilla units before the 0.15 factor) to the freelook camera. */
    public static void freelookTurn(double deltaYaw, double deltaPitch) {
        freelook.turn(deltaYaw, deltaPitch);
    }

    public static double gamma(double vanilla) {
        return fullbright == null ? vanilla : fullbright.gamma(vanilla);
    }

    public static long timeOfDay(long vanilla) {
        return timeChanger == null ? vanilla : timeChanger.time(vanilla);
    }

    public static float rainLevel(float vanilla) {
        return weather == null ? vanilla : weather.rain(vanilla);
    }

    public static float thunderLevel(float vanilla) {
        return weather == null ? vanilla : weather.thunder(vanilla);
    }

    /** Whether a new particle may be spawned given {@code currentCount} live particles. */
    public static boolean allowParticle(int currentCount) {
        return particles == null || particles.allow(currentCount);
    }

    /**
     * Whether an entity that passed vanilla's render checks (frustum) should be rendered: distance
     * and occlusion culling. {@code state} is the entity itself (adapters mix {@link CullState} into it).
     */
    public static boolean shouldRenderEntity(CullState state, boolean isPlayer, double camX, double camY, double camZ,
                                             double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return culling == null || culling.shouldRender(meridian.platform().game(), state, isPlayer, camX, camY, camZ,
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Frame limit to apply this frame. */
    public static int framerateLimit(int vanilla, boolean focused, boolean minimized) {
        return dynamicFps == null ? vanilla : dynamicFps.limit(vanilla, focused, minimized);
    }

    public static boolean renderVanillaCrosshair() {
        return crosshair == null || !crosshair.isEnabled();
    }

    public static boolean renderVanillaScoreboard() {
        return scoreboard == null || !scoreboard.isEnabled();
    }

    public static boolean renderVanillaEffects() {
        return potions == null || !potions.hidesVanilla();
    }

    /** True when Toggle Sprint holds the sprint key down. */
    public static boolean forceSprint() {
        return toggleSprint != null && toggleSprint.forceSprint();
    }

    /** Colour of the block outline (ARGB); {@code vanilla} when Block Overlay is off. */
    public static int blockOutlineColor(int vanilla) {
        return blockOverlay == null ? vanilla : blockOverlay.outlineColor(vanilla);
    }

    /** Line width of the block outline. */
    public static float blockOutlineWidth(float vanilla) {
        return blockOverlay == null ? vanilla : blockOverlay.outlineWidth(vanilla);
    }

    /** Fill colour of the selected block (ARGB), or 0 for no fill. */
    public static int blockOutlineFill() {
        return blockOverlay == null ? 0 : blockOverlay.fillColor();
    }

    /** Hurt overlay colour as opaque RGB ({@code 0xFFRRGGBB}), or 0 for the vanilla red. */
    public static int hitColor() {
        return hitColor == null ? 0 : hitColor.color();
    }

    /** Hurt overlay strength relative to vanilla (1 = vanilla). */
    public static float hitColorIntensity() {
        return hitColor == null ? 1f : hitColor.intensity();
    }

    /** True when Toggle Sneak holds the sneak key down. */
    public static boolean forceSneak() {
        return toggleSneak != null && toggleSneak.forceSneak();
    }
}
