package dev.medirian.render;

import dev.medirian.platform.CameraView;

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
     * Camera-space coordinates of a world position: {@code out[0]} to the right, {@code out[1]} up
     * and {@code out[2]} along the view direction (negative behind the camera), in blocks.
     */
    public static void cameraSpace(CameraView camera, double x, double y, double z, double[] out) {
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
        out[0] = dx * rx + dz * rz;
        out[1] = dx * ux + dy * uy + dz * uz;
        out[2] = dx * fx + dy * fy + dz * fz;
    }

    /**
     * Projects a world position into a {@code width} × {@code height} viewport (GUI units).
     *
     * @param out receives screen x, screen y and the depth along the view direction (blocks)
     * @return false when the point is behind the camera
     */
    public static boolean project(CameraView camera, double x, double y, double z, float width, float height, float[] out) {
        double[] space = new double[3];
        cameraSpace(camera, x, y, z, space);
        double depth = space[2];
        if (depth < NEAR) {
            return false;
        }
        double halfHeight = Math.tan(Math.toRadians(camera.fov()) * 0.5) * depth;
        double halfWidth = halfHeight * width / height;
        out[0] = (float) ((space[0] / halfWidth + 1) * 0.5 * width);
        out[1] = (float) ((1 - space[1] / halfHeight) * 0.5 * height);
        out[2] = (float) depth;
        return true;
    }

    /**
     * Where to point at an off-screen position: the intersection of the direction from the screen
     * centre towards it with a rectangle inset by {@code margin}. Also works for positions behind
     * the camera (the direction to turn to).
     *
     * @param out receives screen x and y
     */
    public static void edge(CameraView camera, double x, double y, double z, float width, float height, float margin, float[] out) {
        double[] space = new double[3];
        cameraSpace(camera, x, y, z, space);
        double dirX = space[0];
        double dirY = -space[1];
        if (Math.abs(dirX) < 1e-6 && Math.abs(dirY) < 1e-6) {
            dirY = 1; // straight behind: point down
        }
        double halfW = width / 2.0 - margin;
        double halfH = height / 2.0 - margin;
        double t = Math.min(Math.abs(dirX) > 1e-9 ? halfW / Math.abs(dirX) : Double.MAX_VALUE,
                Math.abs(dirY) > 1e-9 ? halfH / Math.abs(dirY) : Double.MAX_VALUE);
        out[0] = (float) (width / 2.0 + dirX * t);
        out[1] = (float) (height / 2.0 + dirY * t);
    }
}
