package cn.mythicland.worldregion.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only region and landmark service exposed through Bukkit's ServicesManager.
 */
public interface WorldRegionApi {

    /**
     * Returns all configured regions in stable configuration order.
     *
     * @return immutable region definitions
     */
    Collection<RegionDefinition> regions();

    /**
     * Resolves the highest-priority region containing a location.
     *
     * @param location location to inspect
     * @return matching region, or empty when no region contains the location
     */
    Optional<RegionDefinition> findRegion(Location location);

    /**
     * Returns all configured landmarks in stable configuration order.
     *
     * @return immutable landmark definitions
     */
    Collection<LandmarkDefinition> landmarks();

    /**
     * Finds one landmark by its stable identifier.
     *
     * @param id landmark identifier
     * @return matching landmark, or empty when absent
     */
    Optional<LandmarkDefinition> findLandmark(String id);

    /**
     * Loads the landmark world when supported and teleports a player to the landmark.
     *
     * @param player player to teleport
     * @param id     landmark identifier
     * @return future completed with Bukkit's teleport result
     */
    CompletableFuture<Boolean> teleport(Player player, String id);
}
