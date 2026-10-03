package dev.meridian.mc1_8_9;

import dev.meridian.platform.CameraView;
import dev.meridian.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;

/**
 * The camera of the last frame, rebuilt from the view entity: 1.8.9 has no camera object. The
 * position is the interpolated view entity plus {@link Camera#getPosition()} (eye height and third
 * person distance); FOV and partial ticks are recorded by GameRendererMixin during the world render.
 */
public final class LegacyCamera implements CameraView {

    /** Vertical FOV of the last world render (zoom included). */
    public static volatile float fov = 70f;
    public static volatile float tickDelta;

    private final MinecraftClient client;

    LegacyCamera(MinecraftClient client) {
        this.client = client;
    }

    private Entity entity() {
        Entity entity = client.getCameraEntity();
        return entity != null ? entity : client.player;
    }

    @Override
    public double x() {
        Entity e = entity();
        return e.prevX + (e.x - e.prevX) * tickDelta + Camera.getPosition().x;
    }

    @Override
    public double y() {
        Entity e = entity();
        return e.prevY + (e.y - e.prevY) * tickDelta + Camera.getPosition().y;
    }

    @Override
    public double z() {
        Entity e = entity();
        return e.prevZ + (e.z - e.prevZ) * tickDelta + Camera.getPosition().z;
    }

    @Override
    public float yaw() {
        Entity e = entity();
        float yaw = Hooks.freelookActive() ? Hooks.freelookYaw() : e.prevYaw + (e.yaw - e.prevYaw) * tickDelta;
        // front third person view looks back at the player
        return client.options.perspective == 2 ? yaw + 180f : yaw;
    }

    @Override
    public float pitch() {
        Entity e = entity();
        float pitch = Hooks.freelookActive() ? Hooks.freelookPitch() : e.prevPitch + (e.pitch - e.prevPitch) * tickDelta;
        return client.options.perspective == 2 ? -pitch : pitch;
    }

    @Override
    public float fov() {
        return fov;
    }
}
