package dev.meridian.perf;

import dev.meridian.platform.Occluders;

/**
 * Tests whether any part of a box can be seen from the camera or whether full opaque blocks hide
 * all of it. Rays are cast from the camera to the centre and the eight (slightly inset) corners of
 * the box through the block grid; the box is visible as soon as one ray passes. Visible boxes
 * usually cost a single ray, hidden ones nine rays that each stop at the first opaque block.
 *
 * <p>The test is conservative: blocks containing the camera or a ray's end point are never treated
 * as occluders, so an entity is only hidden when it is fully behind solid blocks.
 */
public final class OcclusionCuller {

    /** Corner inset, so corners lying exactly on block faces are tested inside the box. */
    private static final double INSET = 0.05;

    private OcclusionCuller() {
    }

    public static boolean isVisible(Occluders blocks, double camX, double camY, double camZ,
                                    double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (camX >= minX && camX <= maxX && camY >= minY && camY <= maxY && camZ >= minZ && camZ <= maxZ) {
            return true;
        }
        // a camera inside a solid block (spectator mode, clipping) would see everything as hidden
        if (blocks.isOccluder(floor(camX), floor(camY), floor(camZ))) {
            return true;
        }
        if (rayClear(blocks, camX, camY, camZ, (minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5)) {
            return true;
        }
        double insetX = Math.min(INSET, (maxX - minX) * 0.25);
        double insetY = Math.min(INSET, (maxY - minY) * 0.25);
        double insetZ = Math.min(INSET, (maxZ - minZ) * 0.25);
        double x0 = minX + insetX;
        double x1 = maxX - insetX;
        double y0 = minY + insetY;
        double y1 = maxY - insetY;
        double z0 = minZ + insetZ;
        double z1 = maxZ - insetZ;
        return rayClear(blocks, camX, camY, camZ, x0, y0, z0)
                || rayClear(blocks, camX, camY, camZ, x1, y0, z0)
                || rayClear(blocks, camX, camY, camZ, x0, y1, z0)
                || rayClear(blocks, camX, camY, camZ, x1, y1, z0)
                || rayClear(blocks, camX, camY, camZ, x0, y0, z1)
                || rayClear(blocks, camX, camY, camZ, x1, y0, z1)
                || rayClear(blocks, camX, camY, camZ, x0, y1, z1)
                || rayClear(blocks, camX, camY, camZ, x1, y1, z1);
    }

    /**
     * Walks the blocks a segment passes through (Amanatides &amp; Woo) and reports whether none of
     * the blocks between its start and end block is an occluder.
     */
    static boolean rayClear(Occluders blocks, double x0, double y0, double z0, double x1, double y1, double z1) {
        int x = floor(x0);
        int y = floor(y0);
        int z = floor(z0);
        int steps = Math.abs(floor(x1) - x) + Math.abs(floor(y1) - y) + Math.abs(floor(z1) - z);
        if (steps <= 1) {
            return true;
        }
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double maxX = stepX > 0 ? (x + 1 - x0) * deltaX : stepX < 0 ? (x0 - x) * deltaX : Double.POSITIVE_INFINITY;
        double maxY = stepY > 0 ? (y + 1 - y0) * deltaY : stepY < 0 ? (y0 - y) * deltaY : Double.POSITIVE_INFINITY;
        double maxZ = stepZ > 0 ? (z + 1 - z0) * deltaZ : stepZ < 0 ? (z0 - z) * deltaZ : Double.POSITIVE_INFINITY;
        // the last step enters the end block, which is not tested
        for (int i = 1; i < steps; i++) {
            if (maxX < maxY) {
                if (maxX < maxZ) {
                    x += stepX;
                    maxX += deltaX;
                } else {
                    z += stepZ;
                    maxZ += deltaZ;
                }
            } else if (maxY < maxZ) {
                y += stepY;
                maxY += deltaY;
            } else {
                z += stepZ;
                maxZ += deltaZ;
            }
            if (blocks.isOccluder(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
