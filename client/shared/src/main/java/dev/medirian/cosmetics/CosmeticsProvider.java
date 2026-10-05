package dev.medirian.cosmetics;

import java.util.List;
import java.util.UUID;

/**
 * Source of cosmetic definitions, ownership and other players' loadouts:
 * {@link BundledCosmetics} (what ships with the client) and {@link RemoteCosmetics}
 * (Medirian services).
 */
public interface CosmeticsProvider {

    /** Cosmetics this provider defines (may be empty). */
    List<Cosmetic> catalogue();

    /** Whether the local player may use the cosmetic according to this provider. */
    boolean owns(Cosmetic cosmetic);

    /** Equipped cosmetics of another player, or null when unknown (yet). Called every frame: must be cheap. */
    Loadout loadoutOf(UUID player);
}
