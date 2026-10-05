package dev.medirian.module.impl.player;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.HudElement;
import dev.medirian.hud.HudStyle;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.platform.EffectView;
import dev.medirian.platform.PlayerView;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ColorSetting;
import dev.medirian.util.Format;

import java.util.Collections;
import java.util.List;

/** Active status effects with icon, level and remaining time. */
public final class PotionEffectsModule extends Module {

    private final BooleanSetting showIcons;
    private final BooleanSetting showLevel;
    private final BooleanSetting blink;
    private final BooleanSetting background;
    private final BooleanSetting hideVanilla;
    private final ColorSetting nameColor;
    private final ColorSetting timeColor;

    private List<EffectView> effects = Collections.emptyList();

    public PotionEffectsModule() {
        super("potioneffects", "Potion Effects", Category.PLAYER, "Lists active potion effects with their duration.");
        enableByDefault();
        showIcons = add(new BooleanSetting("showIcons", "Show icons", true));
        showLevel = add(new BooleanSetting("showLevel", "Show level", true));
        blink = add(new BooleanSetting("blink", "Blink when ending", true));
        background = add(new BooleanSetting("background", "Background", true));
        hideVanilla = add(new BooleanSetting("hideVanilla", "Hide vanilla effect icons", true)
                .visibleWhen(() -> Medirian.get().platform().supports(Capability.HIDE_VANILLA_EFFECTS)));
        nameColor = add(new ColorSetting("nameColor", "Name color", HudStyle.DEFAULT_TEXT));
        timeColor = add(new ColorSetting("timeColor", "Time color", 0xFFB7B0C8));
        hud(new Element());
    }

    /** True when the vanilla top-right effect icons should be hidden (modern versions). */
    public boolean hidesVanilla() {
        return isEnabled() && hideVanilla.on();
    }

    private final class Element extends HudElement {

        Element() {
            super(PotionEffectsModule.this, Anchor.BOTTOM_RIGHT, -4, -84);
        }

        @Override
        public void tick() {
            PlayerView player = Medirian.get().game().player();
            effects = player == null ? Collections.<EffectView>emptyList() : player.effects();
        }

        @Override
        public boolean hasContent() {
            return !effects.isEmpty();
        }

        @Override
        public void render(Gfx g, boolean editor) {
            boolean icons = showIcons.on() && Medirian.get().platform().supports(Capability.WORLD_ICONS);
            int rowHeight = icons ? 20 : 12;
            if (effects.isEmpty()) {
                String empty = I18n.tr("hud.potioneffects.empty", "No active effects");
                width = g.textWidth(empty) + 10;
                height = 16;
                UiDraw.roundRect(g, 0, 0, width, height, 2.5f, HudStyle.DEFAULT_BACKGROUND);
                g.text(empty, 5, 4, HudStyle.DEFAULT_TEXT, true);
                return;
            }
            int maxWidth = 0;
            for (EffectView effect : effects) {
                maxWidth = Math.max(maxWidth, Math.max(g.textWidth(name(effect)), g.textWidth(time(effect))));
            }
            int textX = icons ? 24 : 5;
            width = textX + maxWidth + 5;
            height = effects.size() * rowHeight + 4;
            if (background.on()) {
                UiDraw.roundRect(g, 0, 0, width, height, 2.5f, HudStyle.DEFAULT_BACKGROUND);
            }
            boolean blinkOff = blink.on() && (System.currentTimeMillis() / 400) % 2 == 0;
            int y = 2;
            for (EffectView effect : effects) {
                boolean ending = !effect.infinite() && effect.durationTicks() < 200;
                float alpha = ending && blinkOff ? 0.45f : 1f;
                if (icons) {
                    g.effectIcon(effect, 3, y + 1);
                    g.text(name(effect), textX, y + 1, Colors.fade(nameColor.argb(), alpha), true);
                    g.text(time(effect), textX, y + 10, Colors.fade(timeColor.argb(), alpha), true);
                } else {
                    String line = name(effect) + " " + time(effect);
                    g.text(line, textX, y + 2, Colors.fade(nameColor.argb(), alpha), true);
                }
                y += rowHeight;
            }
        }

        private String name(EffectView effect) {
            if (!showLevel.on() || effect.amplifier() <= 0) {
                return effect.name();
            }
            return effect.name() + " " + Format.roman(effect.amplifier() + 1);
        }

        private String time(EffectView effect) {
            return effect.infinite() ? "**:**" : Format.ticks(effect.durationTicks());
        }
    }
}
