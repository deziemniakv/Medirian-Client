package dev.meridian.module.impl.movement;

import dev.meridian.core.Meridian;
import dev.meridian.event.Events;
import dev.meridian.hud.Anchor;
import dev.meridian.hud.TextHudElement;
import dev.meridian.i18n.I18n;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.platform.InputView;
import dev.meridian.platform.PlayerView;

/**
 * Press the vanilla sprint key once to keep sprinting. The adapter forces the sprint key down
 * while toggled ({@link dev.meridian.platform.Hooks#forceSprint()}); vanilla still decides
 * whether sprinting is possible (hunger, moving forward…), so behaviour stays legitimate.
 */
public final class ToggleSprintModule extends Module {

    private boolean toggled;
    private boolean wasDown;

    public ToggleSprintModule() {
        super("togglesprint", "Toggle Sprint", Category.MOVEMENT, "Tap the sprint key to keep sprinting.");
        requires(Capability.TOGGLE_SPRINT);
        on(Events.Tick.class, e -> {
            boolean down = Meridian.get().platform().input().isDown(InputView.GameKey.SPRINT);
            if (down && !wasDown && !Meridian.get().game().screenOpen()) {
                toggled = !toggled;
            }
            wasDown = down;
        });
        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 182) {
            @Override
            protected void collect(Lines out, boolean editor) {
                String state = state(editor);
                if (state != null) {
                    out.add(state);
                }
            }

            @Override
            public boolean hasContent() {
                return state(false) != null;
            }
        });
    }

    @Override
    protected void onDisable() {
        toggled = false;
    }

    public boolean forceSprint() {
        return isEnabled() && toggled;
    }

    private String state(boolean editor) {
        PlayerView player = Meridian.get().game().player();
        ToggleSneakModule sneak = Meridian.get().modules().get(ToggleSneakModule.class);
        if (sneak != null && sneak.forceSneak()) {
            return "[" + I18n.tr("hud.togglesprint.sneakToggled", "Sneaking (Toggled)") + "]";
        }
        if (player != null && player.sneaking()) {
            return "[" + I18n.tr("hud.togglesprint.sneakHeld", "Sneaking (Key Held)") + "]";
        }
        if (toggled) {
            return "[" + I18n.tr("hud.togglesprint.toggled", "Sprinting (Toggled)") + "]";
        }
        if (player != null && player.sprinting()) {
            return "[" + I18n.tr("hud.togglesprint.held", "Sprinting (Key Held)") + "]";
        }
        return editor ? "[" + I18n.tr("hud.togglesprint.toggled", "Sprinting (Toggled)") + "]" : null;
    }
}
