package dev.medirian.module.impl.combat;

import dev.medirian.event.Events;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.NumberSetting;
import dev.medirian.util.Format;

/** Distance from the player's eyes to the point where the last attack hit the target's hitbox. */
public final class ReachModule extends Module {

    private final NumberSetting decimals;
    private final NumberSetting hideAfter;
    private final BooleanSetting showUnit;

    private double lastReach = -1;
    private long lastHitMs;

    public ReachModule() {
        super("reach", "Reach Display", Category.COMBAT, "Shows the distance of your last hit.");
        requires(Capability.COMBAT_EVENTS);
        decimals = add(new NumberSetting("decimals", "Decimals", 2, 0, 3, 1));
        hideAfter = add(new NumberSetting("hideAfter", "Hide after", 3, 0, 30, 1).unit(" s")
                .description("0 keeps the last value visible."));
        showUnit = add(new BooleanSetting("showUnit", "Show unit", true));

        on(Events.AttackEntity.class, e -> {
            if (e.reach >= 0) {
                lastReach = e.reach;
                lastHitMs = System.currentTimeMillis();
            }
        });
        on(Events.WorldLeave.class, e -> lastReach = -1);

        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 55) {
            @Override
            protected void collect(Lines out, boolean editor) {
                String value = visible()
                        ? Format.decimals(lastReach, decimals.intValue()) + (showUnit.on() ? " " + I18n.tr("hud.reach.unit", "blocks") : "")
                        : "-";
                out.add(I18n.tr("hud.reach", "Reach"), value);
            }

            @Override
            public boolean hasContent() {
                return visible();
            }
        });
    }

    private boolean visible() {
        if (lastReach < 0) {
            return false;
        }
        int hide = hideAfter.intValue();
        return hide == 0 || System.currentTimeMillis() - lastHitMs < hide * 1000L;
    }
}
