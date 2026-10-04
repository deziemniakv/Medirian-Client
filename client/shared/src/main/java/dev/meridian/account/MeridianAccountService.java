package dev.meridian.account;

/**
 * Meridian account (separate from the Minecraft account): cosmetics seen by other players and
 * cloud sync of profiles. Signing in proves the Minecraft account through Mojang's session server
 * (see {@link dev.meridian.services.MeridianServices}); there is no separate password.
 */
public interface MeridianAccountService {

    enum State {
        /** No Meridian services URL is configured. */
        UNAVAILABLE,
        /** The game runs with an offline/development account, which Mojang cannot confirm. */
        OFFLINE_ACCOUNT,
        SIGNING_IN,
        SIGNED_IN,
        /** Signing in failed; see {@link #error()}. */
        FAILED
    }

    State state();

    /** Name of the signed-in player, or null. */
    String displayName();

    /** Why signing in failed, or null. */
    String error();

    /** Starts signing in again (after a failure). */
    void retry();

    /** Implementation used when Meridian services are not configured. */
    MeridianAccountService UNAVAILABLE = new MeridianAccountService() {
        @Override
        public State state() {
            return State.UNAVAILABLE;
        }

        @Override
        public String displayName() {
            return null;
        }

        @Override
        public String error() {
            return null;
        }

        @Override
        public void retry() {
        }
    };
}
