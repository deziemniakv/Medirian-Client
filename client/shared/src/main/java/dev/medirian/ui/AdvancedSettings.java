package dev.medirian.ui;

import dev.medirian.config.GlobalSettings;
import dev.medirian.config.ProfileSettings;
import dev.medirian.core.Medirian;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Module;
import dev.medirian.module.impl.performance.DynamicFpsModule;
import dev.medirian.module.impl.performance.EntityCullingModule;
import dev.medirian.module.impl.performance.ParticleControlModule;
import dev.medirian.module.impl.render.BlockOverlayModule;
import dev.medirian.module.impl.render.FireOverlayModule;
import dev.medirian.module.impl.render.FullbrightModule;
import dev.medirian.module.impl.render.HurtCameraModule;
import dev.medirian.module.impl.render.ItemPhysicsModule;
import dev.medirian.module.impl.render.WeatherChangerModule;
import dev.medirian.platform.VanillaOptions;
import dev.medirian.platform.VanillaOptions.Option;
import dev.medirian.setting.KeySetting;
import dev.medirian.setting.Setting;
import dev.medirian.ui.widget.Button;
import dev.medirian.ui.widget.CycleSelector;
import dev.medirian.ui.widget.Slider;
import dev.medirian.ui.widget.Switch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Settings → Advanced: Minecraft's own options (through {@link VanillaOptions}, with the same
 * effects as its option screens), Medirian's visibility and rendering settings and the modules that
 * change rendering or performance, in five sections. Options a Minecraft version does not have are
 * left out for that version.
 */
final class AdvancedSettings {

    /** The five sections. */
    enum Section { GRAPHICS, RENDERING, VISIBILITY, PERFORMANCE, INTERFACE }

    /** English name and description of every vanilla option (translated with advanced.<key>). */
    private static final Map<Option, String[]> TEXT = new EnumMap<Option, String[]>(Option.class);

    static {
        text(Option.RENDER_DISTANCE, "Render distance", "How many chunks of the world are drawn around you. The biggest effect on FPS.");
        text(Option.SIMULATION_DISTANCE, "Simulation distance", "How far the world keeps moving in singleplayer: mobs, crops, redstone.");
        text(Option.ENTITY_DISTANCE, "Entity distance", "Minecraft's own scale for how far entities are drawn. Medirian's distances under Visibility go further.");
        text(Option.GRAPHICS, "Graphics", "How detailed leaves, transparency and other effects are. Fancier looks better and costs FPS.");
        text(Option.IMPROVED_TRANSPARENCY, "Improved transparency", "Correct layering of glass, water and particles. Costs GPU time.");
        text(Option.CUTOUT_LEAVES, "Detailed leaves", "Leaves with see-through gaps instead of solid blocks.");
        text(Option.SMOOTH_LIGHTING, "Smooth lighting", "Soft light and shadows across blocks instead of flat light.");
        text(Option.CLOUDS, "Clouds", "No clouds, flat clouds or three-dimensional clouds.");
        text(Option.CLOUD_RANGE, "Cloud distance", "How far clouds reach, in chunks.");
        text(Option.PARTICLES, "Particles", "How many particles Minecraft makes: all, fewer or the minimum.");
        text(Option.ENTITY_SHADOWS, "Entity shadows", "Round shadows under mobs, players and items.");
        text(Option.BIOME_BLEND, "Biome blend", "How softly grass, leaves and water colours change between biomes. Lower is faster.");
        text(Option.MIPMAP, "Mipmap levels", "Smoother textures in the distance. Off keeps far blocks sharp but noisy. Changing it reloads textures.");
        text(Option.VSYNC, "VSync", "Matches frames to the monitor: no tearing, but more input delay.");
        text(Option.FPS_LIMIT, "FPS limit", "The highest frame rate Minecraft draws.");
        text(Option.FULLSCREEN, "Fullscreen", "Fills the whole screen.");
        text(Option.BRIGHTNESS, "Brightness", "Brightness of dark places, from Moody to Bright.");
        text(Option.FOV, "Field of view", "How wide the camera sees.");
        text(Option.VIEW_BOBBING, "View bobbing", "The camera sways while walking.");
        text(Option.WEATHER_RADIUS, "Weather distance", "How far rain and snow are drawn around you.");
        text(Option.VIGNETTE, "Vignette", "Darker screen corners.");
        text(Option.CHUNK_FADE, "Chunk fade-in", "How long new chunks take to fade in. Off shows them at once.");
        text(Option.CHUNK_UPDATES, "Chunk builder", "When changed blocks are redrawn: in the background (smoothest FPS), semi-blocking or fully blocking.");
        text(Option.GUI_SCALE, "GUI scale", "Size of menus and Minecraft's own HUD (hotbar, chat). Medirian's HUD has its own scale.");
        text(Option.MENU_BLUR, "Menu background blur", "How much the world blurs behind open menus.");
        text(Option.CHAT_OPACITY, "Chat text opacity", "How solid chat text is.");
        text(Option.CHAT_SCALE, "Chat size", "Size of the chat text.");
        text(Option.CHAT_WIDTH, "Chat width", "How wide the chat is.");
        text(Option.TEXT_BACKGROUND, "Chat background", "Darkness of the background behind chat lines.");
        text(Option.ADVANCED_TOOLTIPS, "Advanced tooltips", "Item ids and durability in tooltips (F3 + H).");
        text(Option.HELD_ITEM_TOOLTIPS, "Held item name", "Shows the name of the item you switch to above the hotbar.");
        text(Option.ATTACK_INDICATOR, "Attack indicator", "Where the attack cooldown is shown.");
        text(Option.DAMAGE_TILT, "Damage tilt", "How much the camera shakes when you are hurt.");
        text(Option.SCREEN_EFFECTS, "Distortion effects", "Strength of nausea and portal warping.");
        text(Option.FOV_EFFECTS, "FOV effects", "How much speed and effects change the field of view.");
    }

    private static void text(Option option, String name, String description) {
        TEXT.put(option, new String[] {name, description});
    }

    private AdvancedSettings() {
    }

    static String sectionName(Section section) {
        switch (section) {
            case GRAPHICS: return I18n.tr("advanced.graphics", "Graphics");
            case RENDERING: return I18n.tr("advanced.rendering", "Rendering");
            case VISIBILITY: return I18n.tr("advanced.visibility", "Visibility");
            case PERFORMANCE: return I18n.tr("advanced.performance", "Performance");
            default: return I18n.tr("advanced.interface", "Interface");
        }
    }

    static void build(SettingsList list, Section section, MedirianScreen screen, final Medirian medirian) {
        VanillaOptions vanilla = medirian.platform().vanillaOptions();
        ProfileSettings profile = medirian.profileSettings();
        GlobalSettings global = medirian.settings();
        switch (section) {
            case GRAPHICS:
                list.add(new SettingsList.ControlRow(I18n.tr("settings.game.vanilla", "Minecraft settings"),
                        I18n.tr("settings.game.vanilla.desc", "Video, audio, controls and other vanilla options"),
                        new Button(I18n.tr("ui.open", "Open"), Button.Style.SECONDARY, () -> medirian.platform().actions().openVanillaSettings()), 72));
                vanillaRows(list, vanilla, Option.RENDER_DISTANCE, Option.SIMULATION_DISTANCE, Option.ENTITY_DISTANCE, Option.GRAPHICS,
                        Option.IMPROVED_TRANSPARENCY, Option.CUTOUT_LEAVES, Option.SMOOTH_LIGHTING, Option.CLOUDS, Option.CLOUD_RANGE,
                        Option.PARTICLES, Option.ENTITY_SHADOWS, Option.BIOME_BLEND, Option.MIPMAP, Option.VSYNC, Option.FPS_LIMIT,
                        Option.FULLSCREEN, Option.BRIGHTNESS, Option.FOV, Option.VIEW_BOBBING, Option.WEATHER_RADIUS, Option.VIGNETTE);
                break;
            case RENDERING:
                list.addSettings(settings(profile.fog));
                vanillaRows(list, vanilla, Option.CHUNK_UPDATES, Option.CHUNK_FADE, Option.DAMAGE_TILT, Option.SCREEN_EFFECTS, Option.FOV_EFFECTS);
                list.add(new SettingsList.HeaderRow(I18n.tr("advanced.renderingModules", "Rendering modules")));
                moduleRows(list, screen, medirian, WeatherChangerModule.class, FireOverlayModule.class, HurtCameraModule.class,
                        BlockOverlayModule.class, ItemPhysicsModule.class, FullbrightModule.class);
                break;
            case VISIBILITY:
                list.add(new SettingsList.InfoRow(I18n.tr("advanced.visibility.hint", "Things farther away than a distance are not drawn"),
                        () -> ""));
                list.addSettings(settings(profile.playerDistance, profile.entityDistance, profile.itemDistance, profile.blockEntityDistance,
                        profile.particleDistance, profile.nameTagDistance, profile.waypointDistance, profile.cosmeticDistance));
                break;
            case PERFORMANCE:
                list.addSettings(settings(profile.performanceProfile, profile.animatedTextures));
                list.add(new SettingsList.HeaderRow(I18n.tr("advanced.performanceModules", "Optimisations")));
                moduleRows(list, screen, medirian, EntityCullingModule.class, ParticleControlModule.class, DynamicFpsModule.class);
                break;
            default:
                vanillaRows(list, vanilla, Option.GUI_SCALE);
                list.addSettings(settings(profile.hudScale, global.animations, global.notifications));
                vanillaRows(list, vanilla, Option.MENU_BLUR, Option.CHAT_OPACITY, Option.CHAT_SCALE, Option.CHAT_WIDTH, Option.TEXT_BACKGROUND,
                        Option.ADVANCED_TOOLTIPS, Option.HELD_ITEM_TOOLTIPS, Option.ATTACK_INDICATOR);
                break;
        }
        if (vanilla == null && (section == Section.GRAPHICS || section == Section.INTERFACE)) {
            list.add(new SettingsList.InfoRow(I18n.tr("advanced.vanillaUnavailable", "Minecraft's options are not available here"), () -> ""));
        }
    }

    private static List<Setting<?>> settings(Setting<?>... settings) {
        return Arrays.<Setting<?>>asList(settings);
    }

    /** Rows for the options this Minecraft version has; the rest is left out. */
    private static void vanillaRows(SettingsList list, final VanillaOptions vanilla, Option... options) {
        if (vanilla == null) {
            return;
        }
        for (final Option option : options) {
            final VanillaOptions.Spec spec = vanilla.spec(option);
            if (spec == null) {
                continue;
            }
            String key = "advanced." + option.name().toLowerCase();
            String[] text = TEXT.get(option);
            String name = I18n.tr(key, text[0]);
            String description = I18n.tr(key + ".desc", text[1]);
            if (spec.toggle) {
                list.add(new SettingsList.ControlRow(name, description,
                        new Switch(() -> vanilla.get(option) != 0, on -> vanilla.set(option, on ? 1 : 0)), 22));
            } else if (spec.choices != null) {
                String[] labels = new String[spec.choices.length];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = I18n.tr("advanced.choice." + spec.choices[i].toLowerCase().replace(' ', '_'), spec.choices[i]);
                }
                list.add(new SettingsList.ControlRow(name, description,
                        new CycleSelector(labels, () -> vanilla.get(option), value -> vanilla.set(option, value)), 104));
            } else {
                list.add(new SettingsList.ControlRow(name, description, new Slider(rangeModel(vanilla, option, spec)), 150));
            }
        }
    }

    private static Slider.Model rangeModel(final VanillaOptions vanilla, final Option option, final VanillaOptions.Spec spec) {
        return new Slider.Model() {
            @Override
            public double progress() {
                return (vanilla.get(option) - spec.min) / (double) Math.max(1, spec.max - spec.min);
            }

            @Override
            public void setProgress(double progress) {
                int value = spec.min + (int) Math.round(progress * (spec.max - spec.min) / spec.step) * spec.step;
                value = Math.max(spec.min, Math.min(spec.max, value));
                if (value != vanilla.get(option)) {
                    vanilla.set(option, value);
                }
            }

            @Override
            public String label() {
                int value = vanilla.get(option);
                if (spec.maxLabel != null && value >= spec.max) {
                    return I18n.tr("advanced.value." + spec.maxLabel.toLowerCase(), spec.maxLabel);
                }
                if (spec.minLabel != null && value <= spec.min) {
                    return I18n.tr("advanced.value." + spec.minLabel.toLowerCase(), spec.minLabel);
                }
                return value + spec.unit;
            }

            @Override
            public void nudge(int steps) {
                int value = Math.max(spec.min, Math.min(spec.max, vanilla.get(option) + steps * spec.step));
                vanilla.set(option, value);
            }
        };
    }

    /**
     * A switch per module (with its description), followed by the module's own settings while it
     * is on (keybinds stay in the mod menu).
     */
    @SafeVarargs
    private static void moduleRows(SettingsList list, MedirianScreen screen, Medirian medirian, Class<? extends Module>... types) {
        for (Class<? extends Module> type : types) {
            final Module module = medirian.modules().get(type);
            if (module == null) {
                continue;
            }
            if (!module.requirements().isEmpty() && !supported(medirian, module)) {
                continue;
            }
            list.add(new SettingsList.ControlRow(module.displayName(), module.displayDescription(),
                    new Switch(module::isEnabled, module::setEnabled), 22));
            List<Setting<?>> own = new ArrayList<Setting<?>>();
            for (Setting<?> setting : module.settings()) {
                if (!(setting instanceof KeySetting)) {
                    own.add(setting);
                }
            }
            for (Setting<?> setting : own) {
                list.add(new SettingsList.GuardedRow(new SettingsList.SettingRow(screen, setting), module::isEnabled));
            }
        }
    }

    private static boolean supported(Medirian medirian, Module module) {
        for (dev.medirian.platform.Capability capability : module.requirements()) {
            if (!medirian.platform().supports(capability)) {
                return false;
            }
        }
        return true;
    }

    /** The sections in order. */
    static List<Section> sections() {
        return Collections.unmodifiableList(Arrays.asList(Section.values()));
    }
}
