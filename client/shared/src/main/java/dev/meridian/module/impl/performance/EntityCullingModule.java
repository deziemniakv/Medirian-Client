package dev.meridian.module.impl.performance;

import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;
import dev.meridian.setting.NumberSetting;

/**
 * Distance-based entity culling: entities farther than the configured distance are not rendered
 * (they still exist and tick normally). Big FPS gains in crowded lobbies and farms.
 *
 * <p>Occlusion culling (skipping entities hidden behind blocks) is tracked in TODO.md.
 */
public final class EntityCullingModule extends Module {

    private final NumberSetting entityDistance;
    private final NumberSetting playerDistance;

    public EntityCullingModule() {
        super("entityculling", "Entity Culling", Category.PERFORMANCE,
                "Skips rendering entities beyond a configurable distance.");
        requires(Capability.ENTITY_CULLING);
        entityDistance = add(new NumberSetting("entityDistance", "Entity distance", 80, 16, 256, 8).unit(" m"));
        playerDistance = add(new NumberSetting("playerDistance", "Player distance", 160, 16, 512, 8).unit(" m"));
    }

    public boolean shouldRender(double distanceSq, boolean isPlayer) {
        if (!isEnabled()) {
            return true;
        }
        double max = isPlayer ? playerDistance.doubleValue() : entityDistance.doubleValue();
        return distanceSq <= max * max;
    }
}
