package dev.medirian.mc1_21_8;

import dev.medirian.platform.VanillaOptions;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.server.level.ParticleStatus;

/**
 * Minecraft 1.21.8's options for Settings → Advanced. Values go through {@link OptionInstance#set},
 * so they have the same effects as Minecraft's option screens; the few changes those screens make
 * themselves (GUI scale, mipmaps) are repeated here. options.txt is saved by the platform shortly
 * after a change.
 */
final class ModernVanillaOptions implements VanillaOptions {

    private final Minecraft minecraft;
    private final Runnable changed;

    ModernVanillaOptions(Minecraft minecraft, Runnable changed) {
        this.minecraft = minecraft;
        this.changed = changed;
    }

    private int maxDistance() {
        return Runtime.getRuntime().maxMemory() >= 1_000_000_000L ? 32 : 16;
    }

    @Override
    public Spec spec(Option option) {
        switch (option) {
            case RENDER_DISTANCE: return Spec.range(2, maxDistance(), 1, " ch");
            case SIMULATION_DISTANCE: return Spec.range(5, maxDistance(), 1, " ch");
            case ENTITY_DISTANCE: return Spec.range(50, 500, 25, "%");
            case GRAPHICS: return Spec.choices("Fast", "Fancy", "Fabulous");
            case SMOOTH_LIGHTING: return Spec.toggle();
            case CLOUDS: return Spec.choices("Off", "Flat", "3D");
            case CLOUD_RANGE: return Spec.range(2, 128, 2, " ch");
            case PARTICLES: return Spec.choices("All", "Decreased", "Minimal");
            case ENTITY_SHADOWS: return Spec.toggle();
            case BIOME_BLEND: return Spec.range(0, 7, 1, "").withMinLabel("Off");
            // choices, not a slider: every change reloads textures
            case MIPMAP: return Spec.choices("Off", "1", "2", "3", "4");
            case VSYNC: return Spec.toggle();
            case FPS_LIMIT: return Spec.range(10, 260, 10, "").withMaxLabel("Unlimited");
            case FULLSCREEN: return Spec.toggle();
            case BRIGHTNESS: return Spec.range(0, 100, 5, "%").withMinLabel("Moody").withMaxLabel("Bright");
            case FOV: return Spec.range(30, 110, 1, "°");
            case VIEW_BOBBING: return Spec.toggle();
            case CHUNK_UPDATES: return Spec.choices("Threaded", "Semi blocking", "Fully blocking");
            // choices, not a slider: every change resizes the whole interface
            case GUI_SCALE: return Spec.choices(guiScaleLabels());
            case MENU_BLUR: return Spec.range(0, 10, 1, "").withMinLabel("Off");
            case CHAT_OPACITY: return Spec.range(10, 100, 5, "%");
            case CHAT_SCALE: return Spec.range(0, 100, 5, "%");
            case CHAT_WIDTH: return Spec.range(0, 100, 5, "%");
            case TEXT_BACKGROUND: return Spec.range(0, 100, 5, "%");
            case ADVANCED_TOOLTIPS: return Spec.toggle();
            case ATTACK_INDICATOR: return Spec.choices("Off", "Crosshair", "Hotbar");
            case DAMAGE_TILT: return Spec.range(0, 100, 5, "%");
            case SCREEN_EFFECTS: return Spec.range(0, 100, 5, "%");
            case FOV_EFFECTS: return Spec.range(0, 100, 5, "%");
            default: return null; // newer or older options (improved transparency, held item tooltips…)
        }
    }

    private String[] guiScaleLabels() {
        int max = minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode());
        String[] labels = new String[max + 1];
        labels[0] = "Auto";
        for (int i = 1; i <= max; i++) {
            labels[i] = i + "x";
        }
        return labels;
    }

    private static int bool(OptionInstance<Boolean> option) {
        return option.get() ? 1 : 0;
    }

    private static int percent(OptionInstance<Double> option) {
        return (int) Math.round(option.get() * 100);
    }

    @Override
    public int get(Option option) {
        Options o = minecraft.options;
        switch (option) {
            case RENDER_DISTANCE: return o.renderDistance().get();
            case SIMULATION_DISTANCE: return o.simulationDistance().get();
            case ENTITY_DISTANCE: return percent(o.entityDistanceScaling());
            case GRAPHICS: return o.graphicsMode().get().ordinal();
            case SMOOTH_LIGHTING: return bool(o.ambientOcclusion());
            case CLOUDS: return o.cloudStatus().get().ordinal();
            case CLOUD_RANGE: return o.cloudRange().get();
            case PARTICLES: return o.particles().get().ordinal();
            case ENTITY_SHADOWS: return bool(o.entityShadows());
            case BIOME_BLEND: return o.biomeBlendRadius().get();
            case MIPMAP: return o.mipmapLevels().get();
            case VSYNC: return bool(o.enableVsync());
            case FPS_LIMIT: return o.framerateLimit().get();
            case FULLSCREEN: return minecraft.getWindow().isFullscreen() ? 1 : 0;
            case BRIGHTNESS: return percent(o.gamma());
            case FOV: return o.fov().get();
            case VIEW_BOBBING: return bool(o.bobView());
            case CHUNK_UPDATES: return o.prioritizeChunkUpdates().get().ordinal();
            case GUI_SCALE: return o.guiScale().get();
            case MENU_BLUR: return o.menuBackgroundBlurriness().get();
            case CHAT_OPACITY: return percent(o.chatOpacity());
            case CHAT_SCALE: return percent(o.chatScale());
            case CHAT_WIDTH: return percent(o.chatWidth());
            case TEXT_BACKGROUND: return percent(o.textBackgroundOpacity());
            case ADVANCED_TOOLTIPS: return o.advancedItemTooltips ? 1 : 0;
            case ATTACK_INDICATOR: return o.attackIndicator().get().ordinal();
            case DAMAGE_TILT: return percent(o.damageTiltStrength());
            case SCREEN_EFFECTS: return percent(o.screenEffectScale());
            case FOV_EFFECTS: return percent(o.fovEffectScale());
            default: return 0;
        }
    }

    @Override
    public void set(Option option, int value) {
        Options o = minecraft.options;
        switch (option) {
            case RENDER_DISTANCE: o.renderDistance().set(value); break;
            case SIMULATION_DISTANCE: o.simulationDistance().set(value); break;
            case ENTITY_DISTANCE: o.entityDistanceScaling().set(value / 100.0); break;
            case GRAPHICS: o.graphicsMode().set(GraphicsStatus.values()[value]); break;
            case SMOOTH_LIGHTING: o.ambientOcclusion().set(value != 0); break;
            case CLOUDS: o.cloudStatus().set(CloudStatus.values()[value]); break;
            case CLOUD_RANGE: o.cloudRange().set(value); break;
            case PARTICLES: o.particles().set(ParticleStatus.values()[value]); break;
            case ENTITY_SHADOWS: o.entityShadows().set(value != 0); break;
            case BIOME_BLEND: o.biomeBlendRadius().set(value); break;
            case MIPMAP:
                o.mipmapLevels().set(value);
                // what the video settings screen does when it closes
                minecraft.updateMaxMipLevel(value);
                minecraft.delayTextureReload();
                break;
            case VSYNC: o.enableVsync().set(value != 0); break;
            case FPS_LIMIT: o.framerateLimit().set(value); break;
            case FULLSCREEN:
                if (minecraft.getWindow().isFullscreen() != (value != 0)) {
                    minecraft.getWindow().toggleFullScreen();
                    o.fullscreen().set(minecraft.getWindow().isFullscreen());
                }
                break;
            case BRIGHTNESS: o.gamma().set(value / 100.0); break;
            case FOV: o.fov().set(value); break;
            case VIEW_BOBBING: o.bobView().set(value != 0); break;
            case CHUNK_UPDATES: o.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.values()[value]); break;
            case GUI_SCALE:
                o.guiScale().set(value);
                minecraft.resizeDisplay();
                break;
            case MENU_BLUR: o.menuBackgroundBlurriness().set(value); break;
            case CHAT_OPACITY: o.chatOpacity().set(value / 100.0); break;
            case CHAT_SCALE: o.chatScale().set(value / 100.0); break;
            case CHAT_WIDTH: o.chatWidth().set(value / 100.0); break;
            case TEXT_BACKGROUND: o.textBackgroundOpacity().set(value / 100.0); break;
            case ADVANCED_TOOLTIPS: o.advancedItemTooltips = value != 0; break;
            case ATTACK_INDICATOR: o.attackIndicator().set(AttackIndicatorStatus.values()[value]); break;
            case DAMAGE_TILT: o.damageTiltStrength().set(value / 100.0); break;
            case SCREEN_EFFECTS: o.screenEffectScale().set(value / 100.0); break;
            case FOV_EFFECTS: o.fovEffectScale().set(value / 100.0); break;
            default: return;
        }
        changed.run();
    }
}
