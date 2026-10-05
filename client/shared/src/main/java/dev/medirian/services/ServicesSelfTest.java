package dev.medirian.services;

import dev.medirian.account.MedirianAccountService;
import dev.medirian.core.Log;
import dev.medirian.core.Medirian;

import java.util.UUID;

/**
 * End-to-end check of Medirian services for the in-game self-test (only when a services URL is
 * configured): sign-in, a cloud profile round trip and the player's own cape as other players
 * would see it. Driven by {@link #poll()} from the client thread every tick.
 */
public final class ServicesSelfTest {

    private static final String PROFILE = "Performance";
    private static final int TIMEOUT_TICKS = 400;

    private final Medirian medirian;
    private int ticks;
    private boolean uploaded;
    private boolean downloaded;
    private String failure;
    private boolean done;
    private boolean started;

    public ServicesSelfTest(Medirian medirian) {
        this.medirian = medirian;
    }

    public boolean enabled() {
        return medirian.services().enabled();
    }

    /** Advances the check; returns true once it has finished (successfully or not). */
    public boolean poll() {
        if (done || !enabled()) {
            return true;
        }
        if (++ticks > TIMEOUT_TICKS) {
            return finish("timed out (account " + medirian.account().state() + ", uploaded " + uploaded + ", downloaded " + downloaded + ")");
        }
        MedirianAccountService.State state = medirian.account().state();
        if (state == MedirianAccountService.State.FAILED || state == MedirianAccountService.State.OFFLINE_ACCOUNT) {
            return finish("account " + state + ": " + medirian.account().error());
        }
        if (state != MedirianAccountService.State.SIGNED_IN) {
            return false;
        }
        if (!started) {
            started = true;
            Log.info("Self-test: signed in to Medirian services as {}", medirian.account().displayName());
            medirian.cloudProfiles().upload(PROFILE, (updatedAt, error) -> {
                if (error != null) {
                    finish("upload: " + error);
                    return;
                }
                uploaded = true;
                medirian.cloudProfiles().download(PROFILE, (ok, downloadError) -> {
                    if (downloadError != null) {
                        finish("download: " + downloadError);
                    } else {
                        downloaded = true;
                    }
                });
            });
        }
        if (failure != null || !downloaded) {
            return done;
        }
        // the cape as other clients see it: looked up through the loadout batch endpoint
        String cape = medirian.cosmetics().capeTexture(uuid(medirian.platform().identity().uuid()), false);
        if (cape != null) {
            Log.info("Self-test: services OK (cloud profile round trip, own cape visible to others: {})", cape);
            done = true;
        }
        return done;
    }

    private boolean finish(String reason) {
        if (!done) {
            failure = reason;
            done = true;
            Log.error("Self-test FAILED: Medirian services ({})", reason);
        }
        return true;
    }

    /** A UUID with or without dashes. */
    static UUID uuid(String text) {
        String hex = text.replace("-", "");
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20));
    }
}
