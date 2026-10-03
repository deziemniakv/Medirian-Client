package dev.meridian.platform;

import java.util.List;

/** The local player. */
public interface PlayerView extends EntityView {

    enum Dimension { OVERWORLD, NETHER, END, OTHER }

    double x();

    double y();

    double z();

    /** Yaw in degrees, not normalised (Minecraft convention: 0 = south, 90 = west). */
    float yaw();

    float pitch();

    Dimension dimension();

    /** Armor slot item, {@code slot} 0 = helmet … 3 = boots. Never null; may be empty. */
    ItemView armor(int slot);

    ItemView mainHand();

    /** Off-hand item, or null in versions without an off hand. */
    ItemView offHand();

    /** Active status effects; the list is a snapshot refreshed at most once per tick. */
    List<EffectView> effects();

    boolean sprinting();

    boolean sneaking();
}
