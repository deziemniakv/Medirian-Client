package dev.medirian.cosmetics;

import dev.medirian.platform.ClientActions;
import dev.medirian.platform.GameView;
import dev.medirian.platform.TrailParticle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Trail cosmetics: every client tick, players who wear a trail and are moving leave particles at
 * their feet. Particles are spawned locally by every client for every player it sees, so a trail
 * is visible to others exactly like a cape (through the loadouts from Medirian services).
 */
public final class Trails {

    /** Horizontal movement per tick (blocks) above which a player counts as moving. */
    static final double MOVING = 0.04;

    private final Map<UUID, double[]> lastPositions = new HashMap<UUID, double[]>();
    private final Random random = new Random();
    private int tick;

    /** The particle of a trail cosmetic. */
    static TrailParticle particleOf(Cosmetic trail) {
        switch (trail.id()) {
            case "trail_hearts":
                return TrailParticle.HEART;
            case "trail_flames":
                return TrailParticle.FLAME;
            case "trail_snow":
                return TrailParticle.SNOW;
            default:
                return TrailParticle.SPARK;
        }
    }

    public void tick(final GameView game, final ClientActions actions, final CosmeticsManager cosmetics) {
        if (!game.inWorld() || !cosmetics.canRender(CosmeticType.TRAIL)) {
            lastPositions.clear();
            return;
        }
        tick++;
        final Map<UUID, double[]> seen = new HashMap<UUID, double[]>();
        game.forEachPlayer((uuid, local, x, y, z) -> {
            double[] last = lastPositions.get(uuid);
            seen.put(uuid, new double[] {x, y, z});
            if (last == null) {
                return;
            }
            double dx = x - last[0];
            double dz = z - last[2];
            if (dx * dx + dz * dz < MOVING * MOVING) {
                return;
            }
            Cosmetic trail = cosmetics.worn(uuid, local, CosmeticType.TRAIL);
            if (trail == null) {
                return;
            }
            TrailParticle particle = particleOf(trail);
            // hearts are big: fewer of them
            if (particle == TrailParticle.HEART && tick % 4 != 0) {
                return;
            }
            double ox = (random.nextDouble() - 0.5) * 0.4;
            double oz = (random.nextDouble() - 0.5) * 0.4;
            double rise = particle == TrailParticle.SNOW ? -0.02 : 0.02;
            actions.spawnParticle(particle, x + ox, y + 0.1, z + oz, 0, rise, 0);
        });
        // forget players that left
        for (Iterator<UUID> it = lastPositions.keySet().iterator(); it.hasNext(); ) {
            if (!seen.containsKey(it.next())) {
                it.remove();
            }
        }
        lastPositions.putAll(seen);
    }
}
