package cn.mythicland.worldregion;

import cn.mythicland.worldregion.api.RegionBounds;
import cn.mythicland.worldregion.api.RegionDefinition;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies deterministic active-region selection for overlapping cuboids.
 */
class WorldRegionDataStoreTest {

    @Test
    void higherPriorityRegionWinsBeforeVolumeAndIdentifierTieBreakers() {
        Location location = new Location(null, 5.5D, 5.5D, 5.5D);
        RegionDefinition broadHighPriority = region(
                "broad",
                20,
                new RegionBounds(0, 0, 0, 20, 20, 20)
        );
        RegionDefinition compactLowPriority = region(
                "compact",
                10,
                new RegionBounds(5, 5, 5, 6, 6, 6)
        );

        assertEquals(
                "broad",
                WorldRegionDataStore.selectRegion(
                        List.of(compactLowPriority, broadHighPriority),
                        "world",
                        location
                ).orElseThrow().id()
        );
    }

    @Test
    void equalPriorityRegionsChooseSmallerVolumeThenStableIdentifier() {
        Location location = new Location(null, 5.5D, 5.5D, 5.5D);
        RegionDefinition larger = region("zeta", 10, new RegionBounds(0, 0, 0, 10, 10, 10));
        RegionDefinition smaller = region("alpha", 10, new RegionBounds(5, 5, 5, 6, 6, 6));
        RegionDefinition sameVolumeLaterId = region("beta", 10, new RegionBounds(5, 5, 5, 6, 6, 6));

        assertEquals(
                "alpha",
                WorldRegionDataStore.selectRegion(
                        List.of(larger, sameVolumeLaterId, smaller),
                        "world",
                        location
                ).orElseThrow().id()
        );
    }

    @Test
    void selectionExcludesOtherWorldsAndLocationsOutsideEveryCuboid() {
        RegionDefinition netherRegion = region(
                "nether",
                "nether",
                10,
                new RegionBounds(0, 0, 0, 10, 10, 10)
        );

        assertTrue(
                WorldRegionDataStore.selectRegion(
                        List.of(netherRegion),
                        "world",
                        new Location(null, 5.5D, 5.5D, 5.5D)
                ).isEmpty()
        );
        assertTrue(
                WorldRegionDataStore.selectRegion(
                        List.of(region("world", 10, new RegionBounds(0, 0, 0, 10, 10, 10))),
                        "world",
                        new Location(null, 20.5D, 20.5D, 20.5D)
                ).isEmpty()
        );
    }

    @Test
    void emptyRegionDataFileIsInitializedAsAnEmptyRootSection() {
        ConfigurationSection section = WorldRegionDataStore.requireRootSection(
                new YamlConfiguration(),
                "regions.yml",
                "regions"
        );

        assertTrue(section.getKeys(false).isEmpty());
    }

    @Test
    void blankRegionsRootIsInitializedAsAnEmptyRootSection() throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString("regions:\n");

        ConfigurationSection section = WorldRegionDataStore.requireRootSection(
                configuration,
                "regions.yml",
                "regions"
        );

        assertTrue(section.getKeys(false).isEmpty());
    }

    private static RegionDefinition region(String id, int priority, RegionBounds bounds) {
        return region(id, "world", priority, bounds);
    }

    private static RegionDefinition region(
            String id,
            String worldName,
            int priority,
            RegionBounds bounds
    ) {
        return new RegionDefinition(id, "&a" + id, priority, worldName, bounds);
    }
}
