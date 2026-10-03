package dev.meridian.platform;

/** Read-only view of a living entity. Reads live data from the wrapped entity. */
public interface EntityView {

    int entityId();

    /** Display name without formatting codes. */
    String name();

    float health();

    float maxHealth();

    /** Absorption hearts (golden apple), 0 when none. */
    float absorption();

    /** Remaining hurt animation ticks; > 0 right after taking damage. */
    int hurtTime();

    boolean isPlayer();

    boolean isAlive();

    /** Total armor points (0–20). */
    int armorValue();

    /** Distance from the local player's eyes in blocks. */
    double distanceToPlayer();

    /** Native entity object, only for the adapter's own rendering code. */
    Object handle();
}
