package cn.mythicland.worldregion;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.PluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Minimal Bukkit entry point for the Lib-managed WorldRegion component graph.
 */
public final class WorldRegionPlugin extends JavaPlugin {

    private static final String COMPONENT_PACKAGE = "cn.mythicland.worldregion";

    private PluginBootstrap bootstrap;

    /**
     * Starts WorldRegion through Lib's annotation bootstrap.
     */
    @Override
    public void onEnable() {
        try {
            LibApi lib = LibApi.require(this);
            bootstrap = lib.createPluginBootstrap(this, COMPONENT_PACKAGE);
            bootstrap.enable();
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "WorldRegion failed to enable: " + LibApi.rootCauseMessage(exception),
                    exception
            );
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Closes WorldRegion's injected components.
     */
    @Override
    public void onDisable() {
        if (bootstrap != null) bootstrap.disable();
        bootstrap = null;
    }

    /**
     * Reloads WorldRegion configuration without reconstructing the plugin bootstrap.
     */
    public void reloadWorldRegion() {
        if (bootstrap == null) throw new IllegalStateException("WorldRegion bootstrap is unavailable");
        bootstrap.reload();
    }
}
