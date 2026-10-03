package dev.meridian.module.impl.movement;

import dev.meridian.core.Meridian;
import dev.meridian.input.Key;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.platform.PlayerView;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.setting.ModeSetting;

/**
 * Look around in third person without changing where the player faces. While active the adapter
 * routes mouse movement to {@link #turn} and renders the camera with {@link #yaw()}/{@link #pitch()}.
 *
 * <p>Some servers disallow freelook; respecting server policies is tracked in TODO.md.
 */
public final class FreelookModule extends Module {

    /** Whether the key must be held or toggles freelook. */
    public enum Mode { HOLD, TOGGLE }

    private final ModeSetting<Mode> mode;
    private final BooleanSetting thirdPerson;
    private final BooleanSetting invertPitch;

    private boolean active;
    private float yaw;
    private float pitch;
    private int previousPerspective;

    public FreelookModule() {
        super("freelook", "Freelook", Category.MOVEMENT, "Rotate the camera around your player while holding the key.");
        requires(Capability.FREELOOK);
        defaultKeybind(Key.LALT);
        mode = add(new ModeSetting<Mode>("mode", "Key mode", Mode.HOLD));
        thirdPerson = add(new BooleanSetting("thirdPerson", "Switch to third person", true));
        invertPitch = add(new BooleanSetting("invertPitch", "Invert vertical", false));
    }

    @Override
    public boolean keybindWorksWhenDisabled() {
        return false;
    }

    @Override
    protected void onKeybindPressed() {
        setActive(!mode.is(Mode.TOGGLE) || !active);
    }

    @Override
    protected void onKeybindReleased() {
        if (mode.is(Mode.HOLD)) {
            setActive(false);
        }
    }

    @Override
    protected void onDisable() {
        setActive(false);
    }

    private void setActive(boolean on) {
        if (on == active) {
            return;
        }
        PlayerView player = Meridian.get().game().player();
        if (on && player == null) {
            return;
        }
        if (on) {
            yaw = player.yaw();
            pitch = player.pitch();
            if (thirdPerson.on()) {
                previousPerspective = Meridian.get().platform().actions().perspective();
                Meridian.get().platform().actions().setPerspective(1);
            }
        } else if (thirdPerson.on()) {
            Meridian.get().platform().actions().setPerspective(previousPerspective);
        }
        active = on;
    }

    public boolean active() {
        return active;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** Raw mouse deltas as passed to vanilla {@code Entity#turn} (scaled by 0.15 like vanilla). */
    public void turn(double deltaYaw, double deltaPitch) {
        yaw += (float) (deltaYaw * 0.15);
        pitch += (float) ((invertPitch.on() ? -deltaPitch : deltaPitch) * 0.15);
        pitch = Math.max(-90f, Math.min(90f, pitch));
    }
}
