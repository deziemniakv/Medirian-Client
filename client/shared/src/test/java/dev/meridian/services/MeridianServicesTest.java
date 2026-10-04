package dev.meridian.services;

import dev.meridian.account.MeridianAccountService.State;
import dev.meridian.account.PlayerIdentity;
import dev.meridian.net.Http;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeridianServicesTest {

    static final PlayerIdentity ALEX = new PlayerIdentity("Alex", "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0", true);
    static final String TOKEN = "header.payload.signature";

    private FakeServices fake;
    private MeridianServices services;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeServices();
    }

    @AfterEach
    void tearDown() {
        if (services != null) {
            services.shutdown();
        }
        fake.close();
    }

    /** Waits for everything queued on the services thread. */
    private void drain() throws Exception {
        services.submit(() -> { }).get();
    }

    @Test
    void signsInThroughTheSessionServerWithoutSendingTheTokenToMeridian() throws Exception {
        services = new MeridianServices(fake.config(), ALEX, () -> TOKEN);
        final int[] listeners = {0};
        services.onSignedIn(() -> listeners[0]++);
        assertEquals(State.SIGNING_IN, services.state());
        services.start();
        drain();
        assertEquals(State.SIGNED_IN, services.state());
        assertEquals("Alex", services.displayName());
        assertEquals(1, listeners[0]);

        FakeServices.Request join = fake.to("/session/minecraft/join").get(0);
        assertTrue(join.body.contains(TOKEN));
        assertTrue(join.body.contains("0f1e2d3c4b5a69788796a5b4c3d2e1f0"), "selectedProfile without dashes");
        // the access token only ever goes to the session server
        synchronized (fake.requests) {
            for (FakeServices.Request request : fake.requests) {
                if (!request.path.startsWith("/session/")) {
                    assertFalse(request.body.contains(TOKEN), request.path);
                }
            }
        }
    }

    @Test
    void offlineAccountsDoNotTryMojang() throws Exception {
        PlayerIdentity offline = new PlayerIdentity("Dev", "11112222333344445555666677778888", false);
        services = new MeridianServices(new ServicesConfig(fake.url(), null), offline, () -> "FabricMC");
        services.start();
        drain();
        assertEquals(State.OFFLINE_ACCOUNT, services.state());
        assertTrue(fake.requests.isEmpty());
    }

    @Test
    void withoutAUrlServicesAreUnavailable() throws Exception {
        services = new MeridianServices(new ServicesConfig("  ", null), ALEX, () -> TOKEN);
        services.start();
        drain();
        assertEquals(State.UNAVAILABLE, services.state());
        assertFalse(services.enabled());
        assertEquals(401, services.call("GET", "/v1/me", null).status);
    }

    @Test
    void aRefusedJoinFailsAndCanBeRetried() throws Exception {
        fake.joinStatus = 403;
        services = new MeridianServices(fake.config(), ALEX, () -> TOKEN);
        services.start();
        drain();
        assertEquals(State.FAILED, services.state());
        assertNotNull(services.error());
        assertTrue(services.error().contains("403"), services.error());
        fake.joinStatus = 204;
        services.retry();
        drain();
        assertEquals(State.SIGNED_IN, services.state());
    }

    @Test
    void anExpiredSessionIsRenewedOnce() throws Exception {
        services = new MeridianServices(fake.config(), ALEX, () -> TOKEN);
        services.start();
        drain();
        fake.validToken = "expired-on-the-server";
        final Http.Response[] response = new Http.Response[1];
        services.submit(() -> {
            try {
                response[0] = services.call("GET", "/v1/cosmetics/owned", null);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }).get();
        assertEquals(200, response[0].status);
        assertEquals(2, fake.to("/v1/auth/session").size());
    }

    @Test
    void unreachableServicesFailWithAMessage() throws Exception {
        String url = fake.url();
        fake.close();
        services = new MeridianServices(new ServicesConfig(url, url + "/session/minecraft"), ALEX, () -> TOKEN);
        services.start();
        drain();
        assertEquals(State.FAILED, services.state());
        assertTrue(services.error().contains("unreachable"), services.error());
    }

    @Test
    void sessionUuids() {
        assertEquals("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0", PlayerIdentity.sessionUuid("0F1E2D3C4B5A69788796A5B4C3D2E1F0", "Alex"));
        // the offline UUID Minecraft gives a player without one
        assertEquals(java.util.UUID.nameUUIDFromBytes("OfflinePlayer:Player93".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                PlayerIdentity.sessionUuid("Player93", "Player93"));
    }

    @Test
    void configuration() {
        ServicesConfig config = new ServicesConfig("https://api.example.com//", "");
        assertEquals("https://api.example.com", config.apiUrl());
        assertEquals(ServicesConfig.MOJANG_SESSION_SERVER, config.sessionServer());
        assertFalse(config.customSessionServer());
        assertTrue(config.enabled());
        assertTrue(new ServicesConfig("x", "http://127.0.0.1:1/session/minecraft/").customSessionServer());
        assertEquals("a%20b%2Fc", CloudProfiles.encode("a b/c"));
    }
}
