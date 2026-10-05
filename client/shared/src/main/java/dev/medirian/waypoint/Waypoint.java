package dev.medirian.waypoint;

/** A named position in one dimension of one world. Mutable; changes go through {@link WaypointStore#changed()}. */
public final class Waypoint {

    public String name;
    public int x;
    public int y;
    public int z;
    /** Namespaced dimension id, e.g. {@code minecraft:overworld}. */
    public String dimension;
    /** Opaque RGB as {@code 0xFFRRGGBB}. */
    public int color;
    public boolean visible = true;
    /** Created automatically where the player died; only the latest one per world is kept. */
    public boolean death;

    public Waypoint(String name, int x, int y, int z, String dimension, int color) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.color = color | 0xFF000000;
    }

    /** Squared distance from the centre of the waypoint's block. */
    public double distanceSq(double px, double py, double pz) {
        double dx = x + 0.5 - px;
        double dy = y + 0.5 - py;
        double dz = z + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
