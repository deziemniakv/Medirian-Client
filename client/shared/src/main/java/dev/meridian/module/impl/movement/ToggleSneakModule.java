package dev.meridian.module.impl.movement;

import dev.meridian.core.Meridian;
import dev.meridian.event.Events;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.platform.InputView;

/** Press the vanilla sneak key once to keep sneaking; press again to stand up. */
public final class ToggleSneakModule extends Module {

    private boolean toggled;
    private boolean wasDown;

    public ToggleSneakModule() {
        super("togglesneak", "Toggle Sneak", Category.MOVEMENT, "Tap the sneak key to keep sneaking.");
        requires(Capability.TOGGLE_SNEAK);
        on(Events.Tick.class, e -> {
            boolean down = Meridian.get().platform().input().isDown(InputView.GameKey.SNEAK);
            if (down && !wasDown && !Meridian.get().game().screenOpen()) {
                toggled = !toggled;
            }
            wasDown = down;
        });
        on(Events.WorldLeave.class, e -> toggled = false);
    }

    @Override
    protected void onDisable() {
        toggled = false;
    }

    public boolean forceSneak() {
        return isEnabled() && toggled;
    }
}
