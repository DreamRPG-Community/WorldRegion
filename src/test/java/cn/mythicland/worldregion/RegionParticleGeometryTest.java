package cn.mythicland.worldregion;

import cn.mythicland.worldregion.api.RegionBounds;
import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that region boundaries produce a complete, dense particle outline.
 */
class RegionParticleGeometryTest {

    @Test
    void cuboidBoundaryContainsAllTwelveEdges() {
        List<RegionParticleGeometry.Edge> edges = RegionParticleGeometry.outline(
                new RegionBounds(0, 10, 20, 4, 12, 24)
        );

        assertEquals(12, edges.size());
    }

    @Test
    void boundarySamplingKeepsConfiguredParticleGapsAtMost() {
        RegionParticleGeometry.Edge edge = new RegionParticleGeometry.Edge(
                new RegionParticleGeometry.Point(0.0D, 0.0D, 0.0D),
                new RegionParticleGeometry.Point(2.0D, 0.0D, 0.0D)
        );

        List<RegionParticleGeometry.Point> points = RegionParticleGeometry.sample(edge, 0.5D, 48);

        assertEquals(5, points.size());
        assertEquals(0.5D, points.get(1).x());
        assertEquals(0.5D, points.get(2).x() - points.get(1).x());
        assertEquals(2.0D, points.getLast().x());
    }

    @Test
    void boundarySamplingRejectsNonPositiveSpacing() {
        RegionParticleGeometry.Edge edge = new RegionParticleGeometry.Edge(
                new RegionParticleGeometry.Point(0.0D, 0.0D, 0.0D),
                new RegionParticleGeometry.Point(1.0D, 0.0D, 0.0D)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> RegionParticleGeometry.sample(edge, 0.0D, 48)
        );
    }

    @Test
    void longBoundaryUsesTheReferencePointCapWithoutDroppingItsEndpoints() {
        RegionParticleGeometry.Edge edge = new RegionParticleGeometry.Edge(
                new RegionParticleGeometry.Point(0.0D, 0.0D, 0.0D),
                new RegionParticleGeometry.Point(100.0D, 0.0D, 0.0D)
        );

        List<RegionParticleGeometry.Point> points = RegionParticleGeometry.sample(edge, 0.5D, 48);

        assertEquals(49, points.size());
        assertEquals(0.0D, points.getFirst().x());
        assertEquals(100.0D, points.getLast().x());
    }

    @Test
    void yellowLegacyDisplayPrefixProducesYellowParticleColor() {
        assertEquals(Color.fromRGB(255, 255, 85), RegionParticleRenderer.colorFor("&e遗落梦境"));
    }
}
