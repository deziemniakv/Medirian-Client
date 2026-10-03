package dev.meridian.platform;

/** The camera of the last rendered frame: third person, freelook and zoom included. */
public interface CameraView {

    double x();

    double y();

    double z();

    /** Degrees, Minecraft convention: 0 looks south (+Z), 90 west (-X). */
    float yaw();

    /** Degrees, positive looks down. */
    float pitch();

    /** Vertical field of view in degrees as used for the last world render (zoom and effects included). */
    float fov();
}
