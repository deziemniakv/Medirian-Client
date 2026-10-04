package dev.meridian.cosmetics;

import dev.meridian.core.MeridianHome;
import dev.meridian.cosmetics.model.CosmeticMesh;
import dev.meridian.cosmetics.model.CosmeticModels;
import dev.meridian.cosmetics.model.MeshBuilder;
import dev.meridian.platform.ClientActions;
import dev.meridian.platform.GameView;
import dev.meridian.platform.TrailParticle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WornCosmeticsTest {

    @TempDir
    File home;

    @Test
    void aBoxHasSixFacesWithTheVanillaTextureLayout() {
        CosmeticMesh mesh = new MeshBuilder(64, 32).box(-4, -8, -4, 8, 8, 8, 0, 0).build();
        assertEquals(6, mesh.quads());
        float[] d = mesh.data();
        // the front (4th face, -z) uses (8, 8)-(16, 16) of a 64×32 texture
        int front = 3 * CosmeticMesh.STRIDE;
        assertEquals(8 / 64f, d[front + 3], 1e-6);
        assertEquals(8 / 32f, d[front + 4], 1e-6);
        assertEquals(16 / 64f, d[front + 5 + 3], 1e-6);
        assertEquals(-1f, d[front + 22], 0f);
        assertEquals(-4f, d[front + 2], 0f);
    }

    @Test
    void everyHatModelFitsItsTextureAndSitsOnTheHead() {
        for (String id : new String[] {"hat_tophat", "hat_crown", "hat_witch", "hat_santa"}) {
            CosmeticMesh mesh = CosmeticModels.model(id);
            assertTrue(mesh.quads() >= 6, id);
            float[] d = mesh.data();
            for (int q = 0; q < mesh.quads(); q++) {
                for (int v = 0; v < 4; v++) {
                    int i = q * CosmeticMesh.STRIDE + v * 5;
                    assertTrue(d[i + 3] >= 0 && d[i + 3] <= 1 && d[i + 4] >= 0 && d[i + 4] <= 1, id + " uv in range");
                    // above the neck (y < 0) and not lower than the top of the head (y = -8) by more than the brim
                    assertTrue(d[i + 1] <= -7.9f, id + " sits on the head: y=" + d[i + 1]);
                }
            }
            assertNotNull(getClass().getResource("/assets/meridian/textures/cosmetics/hats/" + id + ".png"), id + " texture");
            assertNotNull(getClass().getResource("/assets/meridian/textures/cosmetics/icons/" + id + ".png"), id + " icon");
        }
    }

    @Test
    void wingsSpreadBackwardsSymmetrically() {
        CosmeticMesh flat = CosmeticModels.wings(0);
        CosmeticMesh spread = CosmeticModels.wings(40);
        assertEquals(2, spread.quads());
        float[] f = flat.data();
        float[] s = spread.data();
        // vertex 1 is the wing tip: flat wings stay on the back plane, spread ones reach behind it
        assertEquals(f[5 + 2], f[CosmeticMesh.STRIDE + 5 + 2], 1e-5);
        assertTrue(s[5 + 2] > f[5 + 2]);
        // the two wings mirror each other across x = 0
        assertEquals(-s[5], s[CosmeticMesh.STRIDE + 5], 1e-5);
        float slow = CosmeticModels.wingSpread(1.0, false, 7);
        float fast = CosmeticModels.wingSpread(1.0, true, 7);
        assertTrue(slow >= 24 && slow <= 36 && fast >= 24 && fast <= 52);
    }

    @Test
    void trailsFollowMovingPlayersOnly() {
        CosmeticsManager manager = new CosmeticsManager(MeridianHome.at(home));
        manager.registerProvider(new BundledCosmetics());
        manager.registerRenderer(CosmeticsTest.renderer(CosmeticType.TRAIL));
        manager.equip(manager.byId("trail_hearts"));
        final UUID me = UUID.randomUUID();
        final double[] position = {0, 64, 0};
        GameView game = (GameView) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {GameView.class}, (proxy, method, args) -> {
            if (method.getName().equals("inWorld")) {
                return true;
            }
            if (method.getName().equals("forEachPlayer")) {
                ((GameView.PlayerVisitor) args[0]).visit(me, true, position[0], position[1], position[2]);
            }
            return null;
        });
        final List<TrailParticle> spawned = new ArrayList<TrailParticle>();
        ClientActions actions = (ClientActions) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ClientActions.class}, (proxy, method, args) -> {
            if (method.getName().equals("spawnParticle")) {
                spawned.add((TrailParticle) args[0]);
            }
            return null;
        });
        Trails trails = new Trails();
        for (int tick = 0; tick < 8; tick++) {
            trails.tick(game, actions, manager);
        }
        assertTrue(spawned.isEmpty(), "standing still leaves no trail");
        for (int tick = 0; tick < 8; tick++) {
            position[0] += 0.2;
            trails.tick(game, actions, manager);
        }
        assertEquals(2, spawned.size(), "hearts every fourth tick while moving");
        assertEquals(TrailParticle.HEART, spawned.get(0));
        manager.unequip(CosmeticType.TRAIL);
        spawned.clear();
        position[0] += 0.2;
        trails.tick(game, actions, manager);
        assertTrue(spawned.isEmpty());
        assertNull(manager.worn(me, true, CosmeticType.TRAIL));
    }
}
