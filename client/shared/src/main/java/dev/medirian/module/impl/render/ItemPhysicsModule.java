package dev.medirian.module.impl.render;

import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;

/**
 * Dropped items rest on the ground instead of floating and spinning: flat items (most items) lie
 * down, blocks stand on the ground, each turned by its own fixed angle.
 */
public final class ItemPhysicsModule extends Module {

    public ItemPhysicsModule() {
        super("itemphysics", "Item Physics", Category.RENDER, "Dropped items lie on the ground instead of floating and spinning.");
        requires(Capability.ITEM_PHYSICS);
    }

    public boolean active() {
        return isEnabled();
    }
}
