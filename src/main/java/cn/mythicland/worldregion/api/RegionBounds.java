package cn.mythicland.worldregion.api;

import org.bukkit.Location;

import java.util.Objects;

/**
 * Immutable, inclusive block-aligned bounds for one three-dimensional region.
 *
 * @param minX minimum block X coordinate
 * @param minY minimum block Y coordinate
 * @param minZ minimum block Z coordinate
 * @param maxX maximum block X coordinate
 * @param maxY maximum block Y coordinate
 * @param maxZ maximum block Z coordinate
 */
public record RegionBounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    /**
     * Validates ordered bounds.
     */
    public RegionBounds {
        if (minX > maxX) throw new IllegalArgumentException("minX cannot exceed maxX");
        if (minY > maxY) throw new IllegalArgumentException("minY cannot exceed maxY");
        if (minZ > maxZ) throw new IllegalArgumentException("minZ cannot exceed maxZ");
    }

    /**
     * Creates block-aligned bounds from two locations in the same world.
     *
     * @param first  first selected location
     * @param second second selected location
     * @return normalized inclusive block bounds
     */
    public static RegionBounds between(Location first, Location second) {
        Location firstLocation = Objects.requireNonNull(first, "first");
        Location secondLocation = Objects.requireNonNull(second, "second");
        if (firstLocation.getWorld() == null || secondLocation.getWorld() == null) {
            throw new IllegalArgumentException("Region corners must belong to worlds");
        }
        if (!firstLocation.getWorld().equals(secondLocation.getWorld())) {
            throw new IllegalArgumentException("Region corners must belong to the same world");
        }
        return new RegionBounds(
                Math.min(firstLocation.getBlockX(), secondLocation.getBlockX()),
                Math.min(firstLocation.getBlockY(), secondLocation.getBlockY()),
                Math.min(firstLocation.getBlockZ(), secondLocation.getBlockZ()),
                Math.max(firstLocation.getBlockX(), secondLocation.getBlockX()),
                Math.max(firstLocation.getBlockY(), secondLocation.getBlockY()),
                Math.max(firstLocation.getBlockZ(), secondLocation.getBlockZ())
        );
    }

    /**
     * Checks whether a precise player location lies within the selected blocks.
     *
     * @param location location to test
     * @return true when the location lies inside this cuboid
     */
    public boolean contains(Location location) {
        Objects.requireNonNull(location, "location");
        return this.contains(location.getX(), location.getY(), location.getZ());
    }

    /**
     * Checks a precise coordinate against the inclusive block volume.
     *
     * @param x coordinate
     * @param y coordinate
     * @param z coordinate
     * @return true when the coordinate lies inside this cuboid
     */
    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1.0D
                && y >= minY && y < maxY + 1.0D
                && z >= minZ && z < maxZ + 1.0D;
    }

    /**
     * Checks whether two bounds share any block.
     *
     * @param other other bounds
     * @return true when the two cuboids overlap
     */
    public boolean intersects(RegionBounds other) {
        Objects.requireNonNull(other, "other");
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /**
     * Returns the number of blocks in this cuboid.
     *
     * @return cuboid volume
     */
    public long volume() {
        return ((long) maxX - minX + 1L)
                * ((long) maxY - minY + 1L)
                * ((long) maxZ - minZ + 1L);
    }
}
