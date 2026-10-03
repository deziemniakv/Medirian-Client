package dev.meridian.account;

/**
 * Meridian account (separate from the Minecraft account): cosmetics ownership, cloud sync of
 * profiles, friends. The service does not exist yet, so the only implementation reports
 * {@link State#UNAVAILABLE} and the UI says so honestly.
 *
 * <p>TODO(account-service): implement against the Meridian backend once it exists; authenticate
 * with the Minecraft session via Mojang's session server join/hasJoined handshake.
 */
public interface MeridianAccountService {

    enum State { UNAVAILABLE, SIGNED_OUT, SIGNED_IN }

    State state();

    /** Display name of the Meridian account, or null when not signed in. */
    String displayName();

    /** Implementation used until the Meridian backend exists. */
    MeridianAccountService UNAVAILABLE = new MeridianAccountService() {
        @Override
        public State state() {
            return State.UNAVAILABLE;
        }

        @Override
        public String displayName() {
            return null;
        }
    };
}
