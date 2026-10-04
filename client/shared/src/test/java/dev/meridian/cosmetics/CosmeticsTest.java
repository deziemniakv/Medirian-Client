package dev.meridian.cosmetics;

import com.google.gson.JsonObject;
import dev.meridian.account.PlayerIdentity;
import dev.meridian.core.MeridianHome;
import dev.meridian.services.FakeServices;
import dev.meridian.services.MeridianServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticsTest {

    static final PlayerIdentity ALEX = new PlayerIdentity("Alex", "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0", true);
    static final UUID STEVE = UUID.fromString("11112222-3333-4444-5555-666677778888");
    static final UUID HEROBRINE = UUID.fromString("99998888-7777-6666-5555-444433332222");

    @TempDir
    File home;
    private FakeServices fake;
    private MeridianServices services;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeServices();
        services = new MeridianServices(fake.config(), ALEX, () -> "a.b.c");
    }

    @AfterEach
    void tearDown() {
        services.shutdown();
        fake.close();
    }

    private void drain() throws Exception {
        services.submit(() -> { }).get();
    }

    private CosmeticsManager manager() {
        CosmeticsManager manager = new CosmeticsManager(MeridianHome.at(home));
        manager.registerProvider(new BundledCosmetics());
        return manager;
    }

    static CosmeticRenderer renderer(final CosmeticType type) {
        return new CosmeticRenderer() {
            @Override
            public CosmeticType type() {
                return type;
            }

            @Override
            public void onEquipped(Cosmetic cosmetic) {
            }
        };
    }

    @Test
    void theBundledCatalogueHasTexturesForEverything() {
        List<Cosmetic> all = new BundledCosmetics().catalogue();
        assertTrue(all.size() >= 15);
        for (Cosmetic cosmetic : all) {
            if (cosmetic.type() == CosmeticType.CAPE) {
                assertEquals("cosmetics/capes/" + cosmetic.id() + ".png", cosmetic.asset());
            }
            if (cosmetic.asset() != null) {
                assertNotNull(getClass().getResource("/assets/meridian/textures/" + cosmetic.asset()), cosmetic.asset());
            }
            if (cosmetic.type() != CosmeticType.CAPE) {
                // previews for the cosmetics screen
                assertNotNull(getClass().getResource("/assets/meridian/textures/cosmetics/icons/" + cosmetic.id() + ".png"), cosmetic.id());
            }
        }
    }

    @Test
    void freeCapesWorkLocallyWithoutServices() {
        CosmeticsManager manager = manager();
        assertFalse(manager.available(), "no renderer registered yet");
        manager.registerRenderer(new CosmeticRenderer() {
            @Override
            public CosmeticType type() {
                return CosmeticType.CAPE;
            }

            @Override
            public void onEquipped(Cosmetic cosmetic) {
            }
        });
        assertTrue(manager.available());
        Cosmetic moonlit = manager.byId("cape_moonlit");
        Cosmetic founder = manager.byId("cape_founder");
        assertTrue(manager.equip(moonlit));
        assertFalse(manager.equip(founder), "granted capes need the services");
        assertEquals("cosmetics/capes/cape_moonlit.png", manager.capeTexture(STEVE, true));
        assertNull(manager.capeTexture(STEVE, false), "other players need the services");

        // persisted
        CosmeticsManager reloaded = manager();
        reloaded.load();
        assertEquals(moonlit.id(), reloaded.equipped(CosmeticType.CAPE).id());
        reloaded.unequip(CosmeticType.CAPE);
        assertNull(reloaded.capeTexture(STEVE, true));
    }

    @Test
    void otherPlayersCapesAreFetchedInOneBatch() throws Exception {
        JsonObject loadout = new JsonObject();
        loadout.addProperty("CAPE", "cape_frost");
        fake.loadouts.put(STEVE.toString().replace("-", ""), loadout);
        CosmeticsManager manager = manager();
        manager.registerRenderer(renderer(CosmeticType.CAPE));
        manager.connect(services);
        // unknown at first: the lookups only queue the players
        assertNull(manager.capeTexture(STEVE, false));
        assertNull(manager.capeTexture(HEROBRINE, false));
        RemoteCosmetics remote = remote(manager);
        remote.flush();
        assertEquals(1, fake.to("/v1/cosmetics/loadouts").size());
        assertEquals("cosmetics/capes/cape_frost.png", manager.capeTexture(STEVE, false));
        assertNull(manager.capeTexture(HEROBRINE, false));
        // cached: no new request
        remote.flush();
        Thread.sleep(RemoteCosmetics.BATCH_DELAY_MS + 200);
        drain();
        assertEquals(1, fake.to("/v1/cosmetics/loadouts").size());
    }

    @Test
    void signingInLoadsOwnershipAndUploadsTheLoadout() throws Exception {
        CosmeticsManager manager = manager();
        manager.equip(manager.byId("cape_aurora"));
        manager.connect(services);
        services.start();
        drain();
        assertTrue(manager.owns(manager.byId("cape_founder")), "granted by the services");
        FakeServices.Request upload = fake.to("/v1/cosmetics/loadout").get(0);
        assertEquals("PUT", upload.method);
        assertTrue(upload.body.contains("cape_aurora"), upload.body);
        // a change is uploaded right away
        assertTrue(manager.equip(manager.byId("cape_founder")));
        drain();
        assertEquals(2, fake.to("/v1/cosmetics/loadout").size());
        assertTrue(fake.to("/v1/cosmetics/loadout").get(1).body.contains("cape_founder"));
    }

    private static RemoteCosmetics remote(CosmeticsManager manager) throws Exception {
        java.lang.reflect.Field field = CosmeticsManager.class.getDeclaredField("remote");
        field.setAccessible(true);
        return (RemoteCosmetics) field.get(manager);
    }
}
