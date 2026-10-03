package dev.meridian.cosmetics;

import java.util.List;

/**
 * Source of cosmetic definitions and ownership.
 *
 * <p>TODO(cosmetics-service): a network provider backed by the Meridian cosmetics API
 * (catalogue, ownership per account, other players' loadouts). Until it exists only local
 * providers are used and no cosmetics are shown to other players.
 */
public interface CosmeticsProvider {

    /** Every cosmetic this provider knows about. */
    List<Cosmetic> catalogue();

    /** Whether the given player (UUID string) owns the cosmetic. */
    boolean owns(String playerUuid, Cosmetic cosmetic);

    /** Equipped cosmetics of another player, or null when unknown. */
    Loadout loadoutOf(String playerUuid);
}
