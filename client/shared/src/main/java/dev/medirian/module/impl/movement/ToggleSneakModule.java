package dev.medirian.module.impl.movement;

import dev.medirian.core.Medirian;
import dev.medirian.event.Events;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.platform.InputView;

/** Press the vanilla sneak key once to keep sneaking; press again to stand up. */
public final class ToggleSneakModule extends Module {

    private boolean toggled;
    private boolean wasDown;

    public ToggleSneakModule() {
        super("togglesneak", "Toggle Sneak", Category.MOVEMENT, "Tap the sneak key to keep sneaking.");
        requires(Capability.TOGGLE_SNEAK);
        on(Events.Tick.class, e -> {
            boolean down = Medirian.get().platform().input().isDown(InputView.GameKey.SNEAK);
            if (down && !wasDown && !Medirian.get().game().screenOpen()) {
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
