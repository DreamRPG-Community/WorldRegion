package cn.mythicland.worldregion;

import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.worldregion.api.LandmarkDefinition;
import cn.mythicland.worldregion.api.RegionBounds;
import cn.mythicland.worldregion.api.RegionDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Owns YAML persistence for WorldRegion's public definitions.
 */
@InjectComponent
final class WorldRegionDataStore {

    private final JavaPlugin plugin;
    private final WorldManagerIntegration worldManager;
    private final Path regionsFile;
    private final Path landmarksFile;
    private Map<String, RegionDefinition> regions;
    private Map<String, LandmarkDefinition> landmarks;

    WorldRegionDataStore(
            JavaPlugin plugin,
            WorldManagerIntegration worldManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.worldManager = Objects.requireNonNull(worldManager, "worldManager");
        Path dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        this.regionsFile = dataDirectory.resolve("regions.yml").normalize();
        this.landmarksFile = dataDirectory.resolve("landmarks.yml").normalize();
        ensureResource("regions.yml", regionsFile);
        ensureResource("landmarks.yml", landmarksFile);
        reload();
    }

    synchronized void reload() {
        regions = loadRegions();
        landmarks = loadLandmarks();
    }

    synchronized Collection<RegionDefinition> regions() {
        return List.copyOf(regions.values());
    }

    synchronized Collection<LandmarkDefinition> landmarks() {
        return List.copyOf(landmarks.values());
    }

    synchronized Optional<RegionDefinition> findRegion(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        String worldName = worldManager.logicalNameOrBukkitName(world);
        return selectRegion(regions.values(), worldName, location);
    }

    static Optional<RegionDefinition> selectRegion(
            Collection<RegionDefinition> candidates,
            String worldName,
            Location location
    ) {
        Objects.requireNonNull(candidates, "candidates");
        String selectedWorldName = Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(location, "location");
        return candidates.stream()
                .filter(region -> region.worldName().equals(selectedWorldName))
                .filter(region -> region.bounds().contains(location))
                .sorted(regionComparator())
                .findFirst();
    }

    synchronized Optional<LandmarkDefinition> findLandmark(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(landmarks.get(id.trim()));
    }

    synchronized List<RegionDefinition> visibleRegions(
            Location viewerLocation,
            int renderDistance,
            int maxRegions
    ) {
        Objects.requireNonNull(viewerLocation, "viewerLocation");
        World world = Objects.requireNonNull(viewerLocation.getWorld(), "viewerLocation.world");
        String worldName = worldManager.logicalNameOrBukkitName(world);
        double maxDistanceSquared = (double) renderDistance * renderDistance;
        return regions.values().stream()
                .filter(region -> region.worldName().equals(worldName))
                .filter(region -> distanceSquaredToBounds(viewerLocation, region.bounds()) <= maxDistanceSquared)
                .sorted(regionComparator())
                .limit(maxRegions)
                .toList();
    }

    synchronized void createRegion(RegionDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (regions.containsKey(definition.id())) {
            throw new IllegalArgumentException("Region already exists: " + definition.id());
        }
        Map<String, RegionDefinition> updated = new LinkedHashMap<>(regions);
        updated.put(definition.id(), definition);
        saveRegions(updated);
        regions = immutableMap(updated);
    }

    synchronized void deleteRegion(String id) {
        String normalizedId = requireId(id);
        if (!regions.containsKey(normalizedId)) {
            throw new IllegalArgumentException("Region does not exist: " + normalizedId);
        }
        Map<String, RegionDefinition> updated = new LinkedHashMap<>(regions);
        updated.remove(normalizedId);
        saveRegions(updated);
        regions = immutableMap(updated);
    }

    synchronized void createLandmark(LandmarkDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (landmarks.containsKey(definition.id())) {
            throw new IllegalArgumentException("Landmark already exists: " + definition.id());
        }
        Map<String, LandmarkDefinition> updated = new LinkedHashMap<>(landmarks);
        updated.put(definition.id(), definition);
        saveLandmarks(updated);
        landmarks = immutableMap(updated);
    }

    synchronized void deleteLandmark(String id) {
        String normalizedId = requireId(id);
        if (!landmarks.containsKey(normalizedId)) {
            throw new IllegalArgumentException("Landmark does not exist: " + normalizedId);
        }
        Map<String, LandmarkDefinition> updated = new LinkedHashMap<>(landmarks);
        updated.remove(normalizedId);
        saveLandmarks(updated);
        landmarks = immutableMap(updated);
    }

    private Map<String, RegionDefinition> loadRegions() {
        YamlConfiguration configuration = loadFile(regionsFile);
        ConfigurationSection section = requireRootSection(configuration, "regions.yml", "regions");

        Map<String, RegionDefinition> loaded = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection region = requireSection(section, id);
            RegionDefinition definition = new RegionDefinition(
                    id,
                    requiredString(region, "display-name"),
                    requiredInt(region, "priority"),
                    requiredString(region, "world"),
                    new RegionBounds(
                            requiredInt(region, "bounds.min-x"),
                            requiredInt(region, "bounds.min-y"),
                            requiredInt(region, "bounds.min-z"),
                            requiredInt(region, "bounds.max-x"),
                            requiredInt(region, "bounds.max-y"),
                            requiredInt(region, "bounds.max-z")
                    )
            );
            if (loaded.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException("Duplicate region identifier: " + definition.id());
            }
        }
        return immutableMap(loaded);
    }

    private Map<String, LandmarkDefinition> loadLandmarks() {
        YamlConfiguration configuration = loadFile(landmarksFile);
        ConfigurationSection section = requireRootSection(configuration, "landmarks.yml", "landmarks");

        Map<String, LandmarkDefinition> loaded = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection landmark = requireSection(section, id);
            LandmarkDefinition definition = new LandmarkDefinition(
                    id,
                    requiredString(landmark, "display-name"),
                    requiredString(landmark, "world"),
                    requiredDouble(landmark, "x"),
                    requiredDouble(landmark, "y"),
                    requiredDouble(landmark, "z"),
                    (float) requiredDouble(landmark, "yaw"),
                    (float) requiredDouble(landmark, "pitch")
            );
            if (loaded.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException("Duplicate landmark identifier: " + definition.id());
            }
        }
        return immutableMap(loaded);
    }

    private void saveRegions(Map<String, RegionDefinition> definitions) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.createSection("regions");
        for (RegionDefinition definition : definitions.values()) {
            String path = "regions." + definition.id();
            configuration.set(path + ".display-name", definition.displayName());
            configuration.set(path + ".priority", definition.priority());
            configuration.set(path + ".world", definition.worldName());
            configuration.set(path + ".bounds.min-x", definition.bounds().minX());
            configuration.set(path + ".bounds.min-y", definition.bounds().minY());
            configuration.set(path + ".bounds.min-z", definition.bounds().minZ());
            configuration.set(path + ".bounds.max-x", definition.bounds().maxX());
            configuration.set(path + ".bounds.max-y", definition.bounds().maxY());
            configuration.set(path + ".bounds.max-z", definition.bounds().maxZ());
        }
        saveFile(regionsFile, configuration);
    }

    private void saveLandmarks(Map<String, LandmarkDefinition> definitions) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.createSection("landmarks");
        for (LandmarkDefinition definition : definitions.values()) {
            String path = "landmarks." + definition.id();
            configuration.set(path + ".display-name", definition.displayName());
            configuration.set(path + ".world", definition.worldName());
            configuration.set(path + ".x", definition.x());
            configuration.set(path + ".y", definition.y());
            configuration.set(path + ".z", definition.z());
            configuration.set(path + ".yaw", definition.yaw());
            configuration.set(path + ".pitch", definition.pitch());
        }
        saveFile(landmarksFile, configuration);
    }

    private YamlConfiguration loadFile(Path file) {
        validateFile(file);
        return YamlConfiguration.loadConfiguration(file.toFile());
    }

    private void saveFile(Path file, YamlConfiguration configuration) {
        validateFile(file);
        try {
            configuration.save(file.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save WorldRegion data file: " + file, exception);
        }
    }

    private void ensureResource(String resourceName, Path target) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            validateFile(target);
            return;
        }
        try {
            Files.createDirectories(Objects.requireNonNull(target.getParent(), "target.parent"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create WorldRegion data directory", exception);
        }
        plugin.saveResource(resourceName, false);
        validateFile(target);
    }

    private static void validateFile(Path file) {
        if (Files.isSymbolicLink(file)) {
            throw new IllegalStateException("WorldRegion data file is a symbolic link: " + file);
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("WorldRegion data file is not a regular file: " + file);
        }
    }

    private static ConfigurationSection requireSection(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) throw new IllegalStateException("Missing WorldRegion section: " + path);
        return section;
    }

    static ConfigurationSection requireRootSection(
            YamlConfiguration configuration,
            String fileName,
            String path
    ) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(path, "path");
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section != null) return section;
        Set<String> rootKeys = configuration.getKeys(false);
        if (rootKeys.isEmpty()) return configuration.createSection(path);
        if (rootKeys.size() == 1 && rootKeys.contains(path) && configuration.get(path) == null) {
            return configuration.createSection(path);
        }
        throw new IllegalStateException(fileName + " requires a " + path + " section");
    }

    private static String requiredString(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Configuration requires a non-empty string: " + path);
        }
        return text.trim();
    }

    private static int requiredInt(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Configuration requires a number: " + path);
        }
        return number.intValue();
    }

    private static double requiredDouble(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Configuration requires a number: " + path);
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result)) {
            throw new IllegalStateException("Configuration requires a finite number: " + path);
        }
        return result;
    }

    private static String requireId(String id) {
        String value = Objects.requireNonNull(id, "id").trim();
        if (value.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        return value;
    }

    private static Comparator<RegionDefinition> regionComparator() {
        return Comparator.comparingInt(RegionDefinition::priority)
                .reversed()
                .thenComparingLong(region -> region.bounds().volume())
                .thenComparing(RegionDefinition::id);
    }

    private static double distanceSquaredToBounds(Location location, RegionBounds bounds) {
        double x = clamp(location.getX(), bounds.minX(), bounds.maxX() + 1.0D);
        double y = clamp(location.getY(), bounds.minY(), bounds.maxY() + 1.0D);
        double z = clamp(location.getZ(), bounds.minZ(), bounds.maxZ() + 1.0D);
        double xDelta = location.getX() - x;
        double yDelta = location.getY() - y;
        double zDelta = location.getZ() - z;
        return xDelta * xDelta + yDelta * yDelta + zDelta * zDelta;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
