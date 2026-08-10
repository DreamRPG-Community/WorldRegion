package cn.mythicland.worldregion.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionBoundsTest {

    @Test
    void inclusiveBlockBoundsContainLocationsInsideSelectedBlocks() {
        RegionBounds bounds = new RegionBounds(0, 10, 0, 2, 12, 2);

        assertTrue(bounds.contains(0.0D, 10.0D, 0.0D));
        assertTrue(bounds.contains(2.999D, 12.999D, 2.999D));
        assertFalse(bounds.contains(3.0D, 12.0D, 2.0D));
    }

    @Test
    void overlappingBoundsAreDetectedByBlockVolume() {
        RegionBounds first = new RegionBounds(0, 0, 0, 2, 2, 2);
        RegionBounds second = new RegionBounds(2, 2, 2, 4, 4, 4);
        RegionBounds separate = new RegionBounds(3, 3, 3, 4, 4, 4);

        assertTrue(first.intersects(second));
        assertFalse(first.intersects(separate));
    }

    @Test
    void volumeCountsEverySelectedBlock() {
        RegionBounds bounds = new RegionBounds(-1, 4, 2, 1, 5, 4);

        assertEquals(18L, bounds.volume());
    }
}
