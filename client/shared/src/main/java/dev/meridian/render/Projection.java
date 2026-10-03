package dev.meridian.render;

import dev.meridian.platform.CameraView;

/**
 * Projects world positions onto the screen with the camera's position, rotation and vertical FOV
 * (the same perspective Minecraft uses; view bobbing is not taken into account).
 */
public final class Projection {

    /** Points closer to the camera plane than this are treated as behind the camera. */
    private static final double NEAR = 0.05;

    private Projection() {
    }

    /**
     * Projects a world position into a {@code width} × {@code height} viewport (GUI units).
     *
     * @param out receives screen x, screen y and the depth along the view direction (blocks)
     * @return false when the point is behind the camera
     */
    public static boolean project(CameraView camera, double x, double y, double z, float width, float height, float[] out) {
        double dx = x - camera.x();
        double dy = y - camera.y();
        double dz = z - camera.z();
        double yaw = Math.toRadians(camera.yaw());
        double pitch = Math.toRadians(camera.pitch());
        double cosPitch = Math.cos(pitch);
        // forward, right (horizontal) and up = right × forward
        double fx = -Math.sin(yaw) * cosPitch;
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * cosPitch;
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < NEAR) {
            return false;
        }
        double right = dx * rx + dz * rz;
        double up = dx * ux + dy * uy + dz * uz;
        double halfHeight = Math.tan(Math.toRadians(camera.fov()) * 0.5) * depth;
        double halfWidth = halfHeight * width / height;
        out[0] = (float) ((right / halfWidth + 1) * 0.5 * width);
        out[1] = (float) ((1 - up / halfHeight) * 0.5 * height);
        out[2] = (float) depth;
        return true;
    }
}
