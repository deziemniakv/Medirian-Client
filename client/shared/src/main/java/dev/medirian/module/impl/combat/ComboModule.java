package dev.medirian.module.impl.combat;

import dev.medirian.event.Events;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.platform.EntityView;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.NumberSetting;

/**
 * Counts consecutive hits that landed (the target entered its hurt animation) without the player
 * being hit back. Resets when the player takes damage or after a configurable timeout.
 */
public final class ComboModule extends Module {

    private final NumberSetting timeout;
    private final BooleanSetting hideWhenZero;

    private int combo;
    private long lastHitMs;
    private EntityView pending;
    private long pendingSinceMs;

    public ComboModule() {
        super("combo", "Combo Counter", Category.COMBAT, "Counts consecutive hits without taking damage.");
        requires(Capability.COMBAT_EVENTS);
        timeout = add(new NumberSetting("timeout", "Reset after", 3, 1, 10, 0.5).unit(" s"));
        hideWhenZero = add(new BooleanSetting("hideWhenZero", "Hide when no combo", false));

        on(Events.AttackEntity.class, e -> {
            // only count hits on targets that can currently take damage
            if (e.target.hurtTime() == 0) {
                pending = e.target;
                pendingSinceMs = System.currentTimeMillis();
            }
        });
        on(Events.PlayerHurt.class, e -> combo = 0);
        on(Events.WorldLeave.class, e -> {
            combo = 0;
            pending = null;
        });
        on(Events.Tick.class, e -> tick());

        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 72) {
            @Override
            protected void collect(Lines out, boolean editor) {
                out.add(I18n.tr("hud.combo", "Combo"), String.valueOf(combo));
            }

            @Override
            public boolean hasContent() {
                return !hideWhenZero.on() || combo > 0;
            }
        });
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (pending != null) {
            if (pending.hurtTime() > 0) {
                combo++;
                lastHitMs = now;
                pending = null;
            } else if (now - pendingSinceMs > 500) {
                pending = null; // the hit did not land
            }
        }
        if (combo > 0 && now - lastHitMs > timeout.doubleValue() * 1000) {
            combo = 0;
        }
    }

    public int combo() {
        return combo;
    }
}
