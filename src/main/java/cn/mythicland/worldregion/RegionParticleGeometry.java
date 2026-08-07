package cn.mythicland.worldregion;

import cn.mythicland.worldregion.api.RegionBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the twelve edges of a cuboid and samples them at a visible spacing.
 */
final class RegionParticleGeometry {

    private RegionParticleGeometry() {
    }

    static List<Edge> outline(RegionBounds bounds) {
        RegionBounds regionBounds = Objects.requireNonNull(bounds, "bounds");
        double minX = regionBounds.minX();
        double minY = regionBounds.minY();
        double minZ = regionBounds.minZ();
        double maxX = regionBounds.maxX() + 1.0D;
        double maxY = regionBounds.maxY() + 1.0D;
        double maxZ = regionBounds.maxZ() + 1.0D;
        return List.of(
                edge(minX, minY, minZ, maxX, minY, minZ),
                edge(minX, minY, maxZ, maxX, minY, maxZ),
                edge(minX, maxY, minZ, maxX, maxY, minZ),
                edge(minX, maxY, maxZ, maxX, maxY, maxZ),
                edge(minX, minY, minZ, minX, maxY, minZ),
                edge(maxX, minY, minZ, maxX, maxY, minZ),
                edge(minX, minY, maxZ, minX, maxY, maxZ),
                edge(maxX, minY, maxZ, maxX, maxY, maxZ),
                edge(minX, minY, minZ, minX, minY, maxZ),
                edge(maxX, minY, minZ, maxX, minY, maxZ),
                edge(minX, maxY, minZ, minX, maxY, maxZ),
                edge(maxX, maxY, minZ, maxX, maxY, maxZ)
        );
    }

    static List<Point> sample(Edge edge, double spacing, int maxSteps) {
        Edge selectedEdge = Objects.requireNonNull(edge, "edge");
        if (!Double.isFinite(spacing) || spacing <= 0.0D) {
            throw new IllegalArgumentException("spacing must be finite and positive");
        }
        if (maxSteps < 1) throw new IllegalArgumentException("maxSteps must be positive");
        double deltaX = selectedEdge.end().x() - selectedEdge.start().x();
        double deltaY = selectedEdge.end().y() - selectedEdge.start().y();
        double deltaZ = selectedEdge.end().z() - selectedEdge.start().z();
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        int steps = Math.max(1, Math.min(maxSteps, (int) Math.ceil(length / spacing)));
        List<Point> points = new ArrayList<>(steps + 1);
        for (int index = 0; index <= steps; index++) {
            double progress = (double) index / steps;
            points.add(new Point(
                    selectedEdge.start().x() + deltaX * progress,
                    selectedEdge.start().y() + deltaY * progress,
                    selectedEdge.start().z() + deltaZ * progress
            ));
        }
        return List.copyOf(points);
    }

    private static Edge edge(
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ
    ) {
        return new Edge(
                new Point(startX, startY, startZ),
                new Point(endX, endY, endZ)
        );
    }

    record Edge(Point start, Point end) {

        Edge {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
        }
    }

    record Point(double x, double y, double z) {
    }
}
