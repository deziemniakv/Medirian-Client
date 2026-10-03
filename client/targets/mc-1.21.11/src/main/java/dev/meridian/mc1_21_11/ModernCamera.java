package dev.meridian.mc1_21_11;

import dev.meridian.platform.CameraView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/** The main camera of the last frame; the FOV is recorded by GameRendererMixin when the world is rendered. */
public final class ModernCamera implements CameraView {

    /** Vertical FOV of the last world render (zoom included). */
    public static volatile float fov = 70f;

    private final Minecraft minecraft;

    ModernCamera(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    private Camera camera() {
        return minecraft.gameRenderer.getMainCamera();
    }

    @Override
    public double x() {
        return camera().position().x;
    }

    @Override
    public double y() {
        return camera().position().y;
    }

    @Override
    public double z() {
        return camera().position().z;
    }

    @Override
    public float yaw() {
        return camera().yRot();
    }

    @Override
    public float pitch() {
        return camera().xRot();
    }

    @Override
    public float fov() {
        return fov;
    }
}
