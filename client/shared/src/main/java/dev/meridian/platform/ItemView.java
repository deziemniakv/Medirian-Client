package dev.meridian.platform;

/** Read-only view of an item stack. */
public interface ItemView {

    boolean isEmpty();

    int count();

    /** Current damage; 0 for undamaged or non-damageable items. */
    int damage();

    /** Maximum damage, 0 when the item cannot be damaged. */
    int maxDamage();

    /** Display name without formatting codes. */
    String name();

    /** Native item stack, only for the adapter's own rendering code. */
    Object handle();
}
