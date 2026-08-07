package cn.mythicland.worldregion;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * Owns WorldRegion's scheduled particle lifecycle and reload boundary.
 */
@InjectComponent
final class WorldRegionLifecycle implements LibPluginLifecycle {

    private final JavaPlugin plugin;
    private final LibApi lib;
    private final WorldRegionSettings settings;
    private final WorldRegionDataStore dataStore;
    private final RegionParticleRenderer particles;
    private final RegionSelectionService selections;
    private BukkitTask particleTask;

    WorldRegionLifecycle(
            JavaPlugin plugin,
            LibApi lib,
            WorldRegionSettings settings,
            WorldRegionDataStore dataStore,
            RegionParticleRenderer particles,
            RegionSelectionService selections
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.dataStore = Objects.requireNonNull(dataStore, "dataStore");
        this.particles = Objects.requireNonNull(particles, "particles");
        this.selections = Objects.requireNonNull(selections, "selections");
    }

    @Override
    public void enable() {
        restartParticleTask();
        plugin.getLogger().info("WorldRegion enabled.");
    }

    @Override
    public void reload() {
        settings.reload();
        dataStore.reload();
        restartParticleTask();
        plugin.getLogger().info("WorldRegion configuration reloaded.");
    }

    @Override
    public void disable() {
        if (particleTask != null) particleTask.cancel();
        particleTask = null;
        selections.clearAll();
    }

    private void restartParticleTask() {
        if (particleTask != null) particleTask.cancel();
        particleTask = null;
        WorldRegionSettings.SettingsSnapshot current = settings.snapshot();
        if (!current.particlesEnabled()) return;
        particleTask = lib.runTimer(
                1L,
                current.intervalTicks(),
                particles::renderAll
        );
    }
}
