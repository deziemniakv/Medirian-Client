package dev.medirian.cosmetics.emote;

import dev.medirian.account.PlayerIdentity;
import dev.medirian.core.MedirianHome;
import dev.medirian.cosmetics.BundledCosmetics;
import dev.medirian.cosmetics.Cosmetic;
import dev.medirian.cosmetics.CosmeticRenderer;
import dev.medirian.cosmetics.CosmeticType;
import dev.medirian.cosmetics.CosmeticsManager;
import dev.medirian.platform.ClientActions;
import dev.medirian.platform.GameView;
import dev.medirian.services.FakeServices;
import dev.medirian.services.MedirianServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmotesTest {

    @TempDir
    File home;

    @Test
    void everyEmoteFadesInPlaysAndEnds() {
        EmotePose pose = new EmotePose();
        for (String id : new String[] {"emote_wave", "emote_cheer", "emote_dance"}) {
            double length = Emotes.duration(id);
            assertTrue(length > 1, id);
            assertTrue(Emotes.pose(id, 0.05, pose), id);
            assertTrue(pose.weight > 0 && pose.weight < 1, id + " fades in");
            assertTrue(Emotes.pose(id, length / 2, pose), id);
            assertEquals(1f, pose.weight, 1e-6, id + " fully applied mid-way");
            assertTrue(pose.has(EmotePose.RIGHT_ARM), id + " moves the right arm");
            assertFalse(Emotes.pose(id, length, pose), id + " ends");
        }
        assertFalse(Emotes.pose("emote_nope", 0.5, pose));
        // blending: half weight lands half way between vanilla and the emote
        Emotes.pose("emote_cheer", 0.1, pose);
        float target = pose.rotations[EmotePose.RIGHT_ARM * 3];
        assertEquals(target * pose.weight, pose.blend(EmotePose.RIGHT_ARM, 0, 0f), 1e-6);
        assertEquals(0.7f, pose.blend(EmotePose.RIGHT_LEG, 0, 0.7f), 0f, "untouched parts keep the game's pose");
    }

    @Test
    void theLocalEmoteTurnsTheCameraAndOthersComeFromTheServices() throws Exception {
        final long[] now = {1_000_000};
        final int[] perspective = {0};
        final UUID me = UUID.randomUUID();
        final UUID steve = UUID.fromString("11112222-3333-4444-5555-666677778888");
        try (FakeServices fake = new FakeServices()) {
            fake.emotes.put("11112222333344445555666677778888", "emote_dance");
            MedirianServices services = new MedirianServices(fake.config(),
                    new PlayerIdentity("Alex", me.toString(), true), () -> "a.b.c");
            try {
                CosmeticsManager cosmetics = new CosmeticsManager(MedirianHome.at(home));
                cosmetics.registerProvider(new BundledCosmetics());
                cosmetics.registerRenderer(new CosmeticRenderer() {
                    @Override
                    public CosmeticType type() {
                        return CosmeticType.EMOTE;
                    }

                    @Override
                    public void onEquipped(Cosmetic cosmetic) {
                    }
                });
                EmoteManager emotes = new EmoteManager(cosmetics, services, () -> now[0]);
                GameView game = (GameView) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {GameView.class}, (proxy, method, args) -> {
                    if (method.getName().equals("inWorld")) {
                        return true;
                    }
                    if (method.getName().equals("forEachPlayer")) {
                        GameView.PlayerVisitor visitor = (GameView.PlayerVisitor) args[0];
                        visitor.visit(me, true, 0, 64, 0);
                        visitor.visit(steve, false, 3, 64, 0);
                    }
                    return null;
                });
                ClientActions actions = (ClientActions) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ClientActions.class}, (proxy, method, args) -> {
                    if (method.getName().equals("perspective")) {
                        return perspective[0];
                    }
                    if (method.getName().equals("setPerspective")) {
                        perspective[0] = (Integer) args[0];
                    }
                    return null;
                });

                assertFalse(emotes.playEquipped(), "nothing equipped yet");
                cosmetics.equip(cosmetics.byId("emote_wave"));
                assertTrue(emotes.playEquipped());
                emotes.tick(game, actions);
                assertEquals(2, perspective[0], "front view while the emote plays");
                EmotePose pose = new EmotePose();
                now[0] += 1000;
                assertTrue(emotes.pose(me, true, pose));
                now[0] += 2000;
                emotes.tick(game, actions);
                assertEquals(0, perspective[0], "back to first person afterwards");
                assertFalse(emotes.pose(me, true, pose));

                // another player's emote: polled from the services
                services.submit(() -> { }).get();
                services.submit(() -> { }).get();
                assertTrue(emotes.pose(steve, false, pose), "the services said Steve dances");
                assertEquals(1, fake.to("/v1/emotes/active").size());
            } finally {
                services.shutdown();
            }
        }
    }
}
