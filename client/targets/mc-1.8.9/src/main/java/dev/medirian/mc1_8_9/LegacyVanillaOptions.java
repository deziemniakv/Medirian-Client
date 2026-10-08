package dev.medirian.mc1_8_9;

import dev.medirian.platform.VanillaOptions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.Window;

/**
 * Minecraft 1.8.9's options for Settings → Advanced. Numbers go through
 * {@link GameOptions#setValue} and choices through {@link GameOptions#getBooleanValue} (Minecraft's
 * own "next value" step), so they have exactly the side effects of Minecraft's option screens:
 * chunks reload, chat re-wraps, VSync and fullscreen switch, textures reload for mipmaps.
 */
final class LegacyVanillaOptions implements VanillaOptions {

    private final MinecraftClient client;
    private final Runnable changed;

    LegacyVanillaOptions(MinecraftClient client, Runnable changed) {
        this.client = client;
        this.changed = changed;
    }

    @Override
    public Spec spec(Option option) {
        switch (option) {
            case RENDER_DISTANCE:
                return Spec.range(2, (int) GameOptions.Option.RENDER_DISTANCE.getMaxValue(), 1, " ch");
            case GRAPHICS: return Spec.choices("Fast", "Fancy");
            case SMOOTH_LIGHTING: return Spec.choices("Off", "Minimum", "Maximum");
            case CLOUDS: return Spec.choices("Off", "Flat", "3D");
            case PARTICLES: return Spec.choices("All", "Decreased", "Minimal");
            case ENTITY_SHADOWS: return Spec.toggle();
            // choices, not a slider: every change reloads all resources
            case MIPMAP: return Spec.choices("Off", "1", "2", "3", "4");
            case VSYNC: return Spec.toggle();
            case FPS_LIMIT: return Spec.range(10, 260, 10, "").withMaxLabel("Unlimited");
            case FULLSCREEN: return Spec.toggle();
            case BRIGHTNESS: return Spec.range(0, 100, 5, "%").withMinLabel("Moody").withMaxLabel("Bright");
            case FOV: return Spec.range(30, 110, 1, "°");
            case VIEW_BOBBING: return Spec.toggle();
            case GUI_SCALE: return Spec.choices("Auto", "Small", "Normal", "Large");
            case CHAT_OPACITY: return Spec.range(10, 100, 5, "%");
            case CHAT_SCALE: return Spec.range(0, 100, 5, "%");
            case CHAT_WIDTH: return Spec.range(0, 100, 5, "%");
            case ADVANCED_TOOLTIPS: return Spec.toggle();
            case HELD_ITEM_TOOLTIPS: return Spec.toggle();
            default: return null; // options of newer versions
        }
    }

    private static int bool(boolean value) {
        return value ? 1 : 0;
    }

    private static int percent(float value) {
        return Math.round(value * 100);
    }

    @Override
    public int get(Option option) {
        GameOptions o = client.options;
        switch (option) {
            case RENDER_DISTANCE: return o.viewDistance;
            case GRAPHICS: return bool(o.fancyGraphics);
            case SMOOTH_LIGHTING: return o.ao;
            case CLOUDS: return o.cloudMode;
            case PARTICLES: return o.particle;
            case ENTITY_SHADOWS: return bool(o.entityShadows);
            case MIPMAP: return o.mipmapLevels;
            case VSYNC: return bool(o.vsync);
            case FPS_LIMIT: return o.maxFramerate;
            case FULLSCREEN: return bool(client.isFullscreen());
            case BRIGHTNESS: return percent(o.gamma);
            case FOV: return Math.round(o.fov);
            case VIEW_BOBBING: return bool(o.bobView);
            case GUI_SCALE: return o.guiScale;
            // Minecraft shows chat opacity as 10%..100% for 0..1
            case CHAT_OPACITY: return Math.round(o.chatOpacity * 90 + 10);
            case CHAT_SCALE: return percent(o.chatScale);
            case CHAT_WIDTH: return percent(o.chatWidth);
            case ADVANCED_TOOLTIPS: return bool(o.advancedItemTooltips);
            case HELD_ITEM_TOOLTIPS: return bool(o.heldItemTooltips);
            default: return 0;
        }
    }

    /** Steps a cycling option forward until it has {@code value}, the way its button does. */
    private void cycle(GameOptions.Option option, int current, int value, int count) {
        int steps = ((value - current) % count + count) % count;
        if (steps != 0) {
            client.options.getBooleanValue(option, steps);
        }
    }

    @Override
    public void set(Option option, int value) {
        GameOptions o = client.options;
        switch (option) {
            case RENDER_DISTANCE: o.setValue(GameOptions.Option.RENDER_DISTANCE, value); break;
            case GRAPHICS: cycle(GameOptions.Option.GRAPHICS, bool(o.fancyGraphics), value, 2); break;
            case SMOOTH_LIGHTING: cycle(GameOptions.Option.AMBIENT_OCCLUSION, o.ao, value, 3); break;
            case CLOUDS: cycle(GameOptions.Option.SHOW_CLOUDS, o.cloudMode, value, 3); break;
            case PARTICLES: cycle(GameOptions.Option.PARTICLES, o.particle, value, 3); break;
            case ENTITY_SHADOWS: cycle(GameOptions.Option.ENTITY_SHADOWS, bool(o.entityShadows), value, 2); break;
            case MIPMAP:
                if (value != o.mipmapLevels) {
                    o.setValue(GameOptions.Option.MIPMAP_LEVELS, value);
                }
                break;
            case VSYNC: cycle(GameOptions.Option.ENABLE_VSYNC, bool(o.vsync), value, 2); break;
            case FPS_LIMIT: o.setValue(GameOptions.Option.MAX_FPS, value); break;
            case FULLSCREEN: cycle(GameOptions.Option.USE_FULLSCREEN, bool(client.isFullscreen()), value, 2); break;
            case BRIGHTNESS: o.setValue(GameOptions.Option.BRIGHTNESS, value / 100f); break;
            case FOV: o.setValue(GameOptions.Option.FIELD_OF_VIEW, value); break;
            case VIEW_BOBBING: cycle(GameOptions.Option.VIEW_BOBBING, bool(o.bobView), value, 2); break;
            case GUI_SCALE:
                cycle(GameOptions.Option.GUI_SCALE, o.guiScale, value, 4);
                // what the options screen does after the scale changes: lay the open screen out again
                if (client.currentScreen != null) {
                    Window window = new Window(client);
                    client.currentScreen.init(client, window.getWidth(), window.getHeight());
                }
                break;
            case CHAT_OPACITY: o.setValue(GameOptions.Option.CHAT_OPACITY, (value - 10) / 90f); break;
            case CHAT_SCALE: o.setValue(GameOptions.Option.CHAT_SCALE, value / 100f); break;
            case CHAT_WIDTH: o.setValue(GameOptions.Option.CHAT_WIDTH, value / 100f); break;
            case ADVANCED_TOOLTIPS: o.advancedItemTooltips = value != 0; break;
            case HELD_ITEM_TOOLTIPS: o.heldItemTooltips = value != 0; break;
            default: return;
        }
        changed.run();
    }
}
