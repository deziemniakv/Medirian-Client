package dev.medirian.mc1_21_8;

import dev.medirian.platform.CameraView;
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
        return camera().getPosition().x;
    }

    @Override
    public double y() {
        return camera().getPosition().y;
    }

    @Override
    public double z() {
        return camera().getPosition().z;
    }

    @Override
    public float yaw() {
        return camera().getYRot();
    }

    @Override
    public float pitch() {
        return camera().getXRot();
    }

    @Override
    public float fov() {
        return fov;
    }
}
