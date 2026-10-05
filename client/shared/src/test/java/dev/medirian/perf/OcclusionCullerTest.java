package dev.medirian.perf;

import dev.medirian.platform.Occluders;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionCullerTest {

    /** Opaque blocks at the listed positions, air everywhere else. */
    private static final class Blocks implements Occluders {
        final Set<Long> solid = new HashSet<Long>();
        int lookups;

        Blocks wall(int x, int fromY, int toY, int fromZ, int toZ) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    solid.add(key(x, y, z));
                }
            }
            return this;
        }

        @Override
        public boolean isOccluder(int x, int y, int z) {
            lookups++;
            return solid.contains(key(x, y, z));
        }

        private static long key(int x, int y, int z) {
            return ((long) x << 42) ^ ((long) (y & 0x1FFFFF) << 21) ^ (z & 0x1FFFFF);
        }
    }

    /** A pig-sized box standing at x, y, z. */
    private static boolean pigVisible(Blocks blocks, double camX, double camY, double camZ, double x, double y, double z) {
        return OcclusionCuller.isVisible(blocks, camX, camY, camZ, x - 0.45, y, z - 0.45, x + 0.45, y + 0.9, z + 0.45);
    }

    @Test
    void openSpaceIsVisibleWithOneRay() {
        Blocks blocks = new Blocks();
        assertTrue(pigVisible(blocks, 0.5, 65.6, 0.5, 10.5, 64, 0.5));
        // camera block + the centre ray's intermediate blocks: 10 along x and 1 down, minus the end block
        assertEquals(11, blocks.lookups);
    }

    @Test
    void solidWallHidesTheEntity() {
        Blocks blocks = new Blocks().wall(5, 60, 70, -5, 5);
        assertFalse(pigVisible(blocks, 0.5, 65.6, 0.5, 10.5, 64, 0.5));
    }

    @Test
    void entityPeekingAroundAWallIsVisible() {
        // the wall ends at z = 0; the pig stands at z = 1.3, so part of it is in view
        Blocks blocks = new Blocks().wall(5, 60, 70, -5, 0);
        assertTrue(pigVisible(blocks, 0.5, 65.6, 0.5, 10.5, 64, 1.3));
    }

    @Test
    void entityOverAWallIsVisible() {
        // a wall up to y = 64 does not hide the top of a pig standing on y = 64 from eye height 65.6
        Blocks blocks = new Blocks().wall(5, 60, 64, -5, 5);
        assertTrue(pigVisible(blocks, 0.5, 65.6, 0.5, 10.5, 65, 0.5));
    }

    @Test
    void cameraInsideABlockSeesEverything() {
        Blocks blocks = new Blocks().wall(0, 65, 65, 0, 0).wall(5, 60, 70, -5, 5);
        assertTrue(pigVisible(blocks, 0.5, 65.6, 0.5, 10.5, 64, 0.5));
    }

    @Test
    void endBlocksAreNeverOccluders() {
        // the ray's start and end blocks are solid but nothing in between
        Blocks blocks = new Blocks().wall(0, 65, 65, 0, 0).wall(3, 65, 65, 0, 0);
        assertTrue(OcclusionCuller.rayClear(blocks, 0.5, 65.5, 0.5, 3.5, 65.5, 0.5));
        assertFalse(OcclusionCuller.rayClear(new Blocks().wall(2, 65, 65, 0, 0), 0.5, 65.5, 0.5, 3.5, 65.5, 0.5));
    }

    @Test
    void diagonalRaysWalkEveryBlock() {
        // a single solid block on the diagonal path must be found, in every direction
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                Blocks blocks = new Blocks().wall(3 * sx, 65, 65, 3 * sz, 3 * sz);
                assertFalse(OcclusionCuller.rayClear(blocks, 0.5, 65.5, 0.5, 6.5 * sx + 0.5, 65.5, 6.5 * sz + 0.5),
                        "direction " + sx + "," + sz);
            }
        }
    }
}
