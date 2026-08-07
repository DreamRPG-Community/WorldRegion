package cn.mythicland.worldregion;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.worldregion.api.LandmarkDefinition;
import cn.mythicland.worldregion.api.RegionDefinition;
import cn.mythicland.worldregion.api.WorldRegionApi;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Implements the public WorldRegion service contract.
 */
@ServiceComponent(WorldRegionApi.class)
final class WorldRegionService implements WorldRegionApi {

    private final LibApi lib;
    private final WorldRegionDataStore dataStore;
    private final WorldManagerIntegration worldManager;

    WorldRegionService(
            LibApi lib,
            WorldRegionDataStore dataStore,
            WorldManagerIntegration worldManager
    ) {
        this.lib = Objects.requireNonNull(lib, "lib");
        this.dataStore = Objects.requireNonNull(dataStore, "dataStore");
        this.worldManager = Objects.requireNonNull(worldManager, "worldManager");
    }

    @Override
    public Collection<RegionDefinition> regions() {
        return dataStore.regions();
    }

    @Override
    public Optional<RegionDefinition> findRegion(Location location) {
        return dataStore.findRegion(Objects.requireNonNull(location, "location"));
    }

    @Override
    public Collection<LandmarkDefinition> landmarks() {
        return dataStore.landmarks();
    }

    @Override
    public Optional<LandmarkDefinition> findLandmark(String id) {
        return dataStore.findLandmark(id);
    }

    @Override
    public CompletableFuture<Boolean> teleport(Player player, String id) {
        Player targetPlayer = Objects.requireNonNull(player, "player");
        LandmarkDefinition landmark = findLandmark(id)
                .orElseThrow(() -> new IllegalArgumentException("Landmark does not exist: " + id));
        return lib.supplyOnMain(() -> worldManager.resolveWorld(landmark.worldName()))
                .thenCompose(future -> future)
                .thenCompose(world -> lib.supplyOnMain(() -> teleport(targetPlayer, landmark, world)));
    }

    private static boolean teleport(Player player, LandmarkDefinition landmark, World world) {
        if (!player.isOnline()) throw new IllegalStateException("Player is no longer online");
        Location target = new Location(
                world,
                landmark.x(),
                landmark.y(),
                landmark.z(),
                landmark.yaw(),
                landmark.pitch()
        );
        return player.teleport(target);
    }
}
