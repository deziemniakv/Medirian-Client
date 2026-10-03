package dev.meridian.module.impl.performance;

import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.perf.CullState;
import dev.meridian.perf.OcclusionCuller;
import dev.meridian.platform.Capability;
import dev.meridian.platform.Occluders;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.setting.NumberSetting;

/**
 * Entity culling: entities farther than the configured distance, and (optionally) entities and
 * block entities (chests, signs, heads, banners…) fully hidden behind solid blocks, are not
 * rendered. They still exist and tick normally. Big FPS gains in crowded lobbies, farms and
 * underground.
 *
 * <p>Occlusion results are cached on the entity: hidden entities are re-tested every
 * {@link #HIDDEN_RECHECK_NS} so they reappear without visible delay, visible ones less often
 * (rendering a hidden entity a little longer costs nothing but the frame time it already took).
 * Only objects that passed vanilla's frustum / distance checks are tested.
 */
public final class EntityCullingModule extends Module {

    private static final long HIDDEN_RECHECK_NS = 10_000_000L;
    private static final long VISIBLE_RECHECK_NS = 100_000_000L;
    /** Beyond this, occlusion tests get long and gain little; distance culling applies instead. */
    private static final double MAX_OCCLUSION_DISTANCE = 128;

    private final NumberSetting entityDistance;
    private final NumberSetting playerDistance;
    private final BooleanSetting occlusion;
    private final BooleanSetting occludePlayers;
    private final BooleanSetting occludeBlockEntities;

    public EntityCullingModule() {
        super("entityculling", "Entity Culling", Category.PERFORMANCE,
                "Skips rendering entities that are far away or hidden behind blocks.");
        requires(Capability.ENTITY_CULLING);
        entityDistance = add(new NumberSetting("entityDistance", "Entity distance", 80, 16, 256, 8).unit(" m"));
        playerDistance = add(new NumberSetting("playerDistance", "Player distance", 160, 16, 512, 8).unit(" m"));
        occlusion = add(new BooleanSetting("occlusion", "Hide entities behind blocks", true)
                .description("Entities completely hidden behind solid blocks are not rendered."));
        occludePlayers = add(new BooleanSetting("occludePlayers", "Also hide players", false)
                .description("Players behind walls lose their name tags too, unlike vanilla.")
                .visibleWhen(occlusion::on));
        occludeBlockEntities = add(new BooleanSetting("occludeBlockEntities", "Also hide block entities", true)
                .description("Chests, signs, heads, banners and similar blocks behind walls.")
                .visibleWhen(occlusion::on));
    }

    /**
     * Whether an entity that passed vanilla's checks should be rendered.
     *
     * @param state  the entity's cached occlusion result
     * @param camX   camera position, then the entity's bounding box
     */
    public boolean shouldRender(Occluders blocks, CullState state, boolean isPlayer, double camX, double camY, double camZ,
                                double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!isEnabled()) {
            return true;
        }
        double dx = (minX + maxX) * 0.5 - camX;
        double dy = (minY + maxY) * 0.5 - camY;
        double dz = (minZ + maxZ) * 0.5 - camZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double max = isPlayer ? playerDistance.doubleValue() : entityDistance.doubleValue();
        if (distanceSq > max * max) {
            return false;
        }
        if (!occlusion.on() || (isPlayer && !occludePlayers.on())
                || distanceSq > MAX_OCCLUSION_DISTANCE * MAX_OCCLUSION_DISTANCE) {
            return true;
        }
        return visible(blocks, state, camX, camY, camZ, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Whether a block entity at x/y/z that vanilla would render should be rendered. Its renderer
     * may draw outside its block (double chests, banners, beds, items on top), so the tested box
     * extends one block around it; renderers drawing much further (beacon beams) must not be passed in.
     */
    public boolean shouldRenderBlockEntity(Occluders blocks, CullState state, double camX, double camY, double camZ,
                                           int x, int y, int z) {
        if (!isEnabled() || !occlusion.on() || !occludeBlockEntities.on()) {
            return true;
        }
        double dx = x + 0.5 - camX;
        double dy = y + 0.5 - camY;
        double dz = z + 0.5 - camZ;
        if (dx * dx + dy * dy + dz * dz > MAX_OCCLUSION_DISTANCE * MAX_OCCLUSION_DISTANCE) {
            return true;
        }
        return visible(blocks, state, camX, camY, camZ, x - 1, y - 1, z - 1, x + 2, y + 2, z + 2);
    }

    /** Cached occlusion test (see the class comment for the re-test intervals). */
    private static boolean visible(Occluders blocks, CullState state, double camX, double camY, double camZ,
                                   double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        long now = System.nanoTime();
        long checkedAt = state.meridian$cullCheckedAt();
        boolean visible = state.meridian$cullVisible();
        if (checkedAt == 0 || now - checkedAt >= (visible ? VISIBLE_RECHECK_NS : HIDDEN_RECHECK_NS)) {
            visible = OcclusionCuller.isVisible(blocks, camX, camY, camZ, minX, minY, minZ, maxX, maxY, maxZ);
            state.meridian$setCull(visible, now == 0 ? 1 : now);
        }
        return visible;
    }
}
