package dev.medirian.module.impl.combat;

import dev.medirian.core.Medirian;
import dev.medirian.event.Events;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.HudElement;
import dev.medirian.hud.HudStyle;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.platform.EntityView;
import dev.medirian.render.Anim;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.UiDraw;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ColorSetting;
import dev.medirian.setting.NumberSetting;
import dev.medirian.util.Format;

/** Information about the entity you are fighting: head, name, animated health bar, distance. */
public final class TargetHudModule extends Module {

    private final NumberSetting duration;
    private final BooleanSetting showHead;
    private final BooleanSetting showDistance;
    private final ColorSetting background;
    private final ColorSetting barColor;

    private EntityView target;
    private long lastAttackMs;
    private final Anim healthAnim = new Anim(1f, 8f);

    public TargetHudModule() {
        super("targethud", "Target HUD", Category.COMBAT, "Shows health and info of the entity you are fighting.");
        requires(Capability.COMBAT_EVENTS);
        duration = add(new NumberSetting("duration", "Show for", 3, 1, 15, 0.5).unit(" s"));
        showHead = add(new BooleanSetting("showHead", "Show head", true));
        showDistance = add(new BooleanSetting("showDistance", "Show distance", true));
        background = add(new ColorSetting("background", "Background", 0xC00B0A10));
        barColor = add(new ColorSetting("barColor", "Health bar", 0xFF9B55D6));

        on(Events.AttackEntity.class, e -> {
            if (target == null || target.entityId() != e.target.entityId()) {
                healthAnim.snap(ratio(e.target));
            }
            target = e.target;
            lastAttackMs = System.currentTimeMillis();
        });
        on(Events.WorldLeave.class, e -> target = null);
        hud(new Element());
    }

    private static float ratio(EntityView entity) {
        float max = entity.maxHealth();
        return max <= 0 ? 0 : Math.max(0f, Math.min(1f, entity.health() / max));
    }

    private boolean showing() {
        return target != null && System.currentTimeMillis() - lastAttackMs < duration.doubleValue() * 1000;
    }

    private final class Element extends HudElement {

        Element() {
            super(TargetHudModule.this, Anchor.CENTER, 70, 34);
            width = 132;
            height = 40;
        }

        @Override
        public boolean hasContent() {
            return showing();
        }

        @Override
        public void render(Gfx g, boolean editor) {
            EntityView entity = showing() ? target : null;
            boolean preview = false;
            if (entity == null && editor) {
                entity = Medirian.get().game().player(); // preview with the local player
                preview = true;
            }
            int head = showHead.on() ? 30 : 0;
            width = 132;
            height = 40;
            UiDraw.roundRect(g, 0, 0, width, height, 4, background.argb());
            if (entity == null) {
                g.text(I18n.tr("hud.targethud.none", "No target"), 6, 16, HudStyle.DEFAULT_TEXT, true);
                return;
            }
            int textX = 6;
            if (head > 0) {
                g.entityHead(entity, 5, 5, head);
                textX = 5 + head + 6;
            }
            int textWidth = width - textX - 6;
            String name = preview ? entity.name() + " (" + I18n.tr("hud.preview", "preview") + ")" : entity.name();
            g.text(UiDraw.ellipsize(g, name, textWidth), textX, 6, HudStyle.DEFAULT_TEXT, true);

            float health = entity.health() + entity.absorption();
            String hp = Format.decimals(health, 1) + " HP";
            if (showDistance.on() && !preview) {
                hp += "  " + Format.decimals(entity.distanceToPlayer(), 1) + "m";
            }
            g.text(hp, textX, 17, 0xFFB7B0C8, true);

            float ratio = healthAnim.target(ratio(entity)).get();
            int barY = 29;
            UiDraw.roundRect(g, textX, barY, textWidth, 5, 2, 0x50FFFFFF);
            if (ratio > 0) {
                UiDraw.roundRect(g, textX, barY, Math.max(2, textWidth * ratio), 5, 2, healthColor(ratio));
            }
        }

        private int healthColor(float ratio) {
            if (ratio > 0.5f) {
                return barColor.argb();
            }
            return Colors.lerp(0xFFE5566A, barColor.argb(), ratio * 2f);
        }
    }
}
