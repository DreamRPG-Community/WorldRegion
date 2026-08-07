package cn.mythicland.worldregion;

import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.config.ConfigSupport;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

/**
 * Mutable configuration holder for WorldRegion's selection and particle runtime.
 */
@InjectComponent
public final class WorldRegionSettings {

    private final JavaPlugin plugin;
    private volatile SettingsSnapshot snapshot;

    /**
     * Loads the initial WorldRegion configuration.
     *
     * @param plugin owning plugin
     */
    public WorldRegionSettings(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.snapshot = read(ConfigSupport.loadDefault(plugin));
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reload() {
        snapshot = read(ConfigSupport.loadDefault(plugin));
    }

    /**
     * Returns the immutable current settings.
     *
     * @return current settings snapshot
     */
    public SettingsSnapshot snapshot() {
        return snapshot;
    }

    private SettingsSnapshot read(FileConfiguration configuration) {
        String wandName = requiredString(configuration, "selection.wand").toUpperCase(Locale.ROOT);
        if (!wandName.equals("WOOD_SPADE")) {
            throw new IllegalStateException("WorldRegion selection.wand must be WOOD_SPADE: " + wandName);
        }
        boolean particlesEnabled = requiredBoolean(configuration, "particle.enabled");
        long intervalTicks = requiredPositiveLong(configuration, "particle.interval-ticks");
        int renderDistance = requiredPositiveInt(configuration, "particle.render-distance");
        int maxRegionsPerPlayer = requiredPositiveInt(configuration, "particle.max-regions-per-player");
        return new SettingsSnapshot(
                particlesEnabled,
                intervalTicks,
                renderDistance,
                maxRegionsPerPlayer
        );
    }

    private static String requiredString(FileConfiguration configuration, String path) {
        Object value = configuration.get(path);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Configuration requires a non-empty string: " + path);
        }
        return text.trim();
    }

    private static boolean requiredBoolean(FileConfiguration configuration, String path) {
        Object value = configuration.get(path);
        if (!(value instanceof Boolean result)) {
            throw new IllegalStateException("Configuration requires a boolean: " + path);
        }
        return result;
    }

    private static long requiredPositiveLong(FileConfiguration configuration, String path) {
        Object value = configuration.get(path);
        if (!(value instanceof Number number) || number.longValue() < 1L) {
            throw new IllegalStateException("Configuration requires a positive number: " + path);
        }
        return number.longValue();
    }

    private static int requiredPositiveInt(FileConfiguration configuration, String path) {
        Object value = configuration.get(path);
        if (!(value instanceof Number number) || number.intValue() < 1) {
            throw new IllegalStateException("Configuration requires a positive integer: " + path);
        }
        return number.intValue();
    }

    /**
     * Immutable particle and selection settings.
     *
     * @param particlesEnabled      whether boundary particles are enabled
     * @param intervalTicks         redraw interval
     * @param renderDistance        maximum distance from a viewer
     * @param maxRegionsPerPlayer   maximum rendered regions per viewer
     */
    public record SettingsSnapshot(
            boolean particlesEnabled,
            long intervalTicks,
            int renderDistance,
            int maxRegionsPerPlayer
    ) {
    }
}
