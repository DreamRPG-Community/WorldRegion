package cn.mythicland.worldregion;

import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Class-loader-safe optional adapter for WorldManager's public service.
 */
@InjectComponent
final class WorldManagerIntegration {

    private static final String PLUGIN_NAME = "WorldManager";
    private static final String API_CLASS_NAME = "cn.mythicland.worldmanager.api.WorldManagerApi";

    private final JavaPlugin plugin;
    private Object provider;
    private Method findLogicalName;
    private Method findWorld;
    private Method loadWorld;
    private boolean lookupFailed;
    private boolean failureLogged;

    WorldManagerIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    String logicalNameOrBukkitName(World world) {
        return logicalName(world).orElse(world.getName());
    }

    CompletableFuture<World> resolveWorld(String logicalName) {
        World loaded = Bukkit.getWorld(logicalName);
        if (loaded != null) return CompletableFuture.completedFuture(loaded);
        if (providerUnavailable()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("World is not loaded and WorldManager is unavailable: " + logicalName)
            );
        }
        try {
            Object result = findWorld.invoke(provider, logicalName);
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                Object world = optional.orElseThrow();
                if (world instanceof World resolved) return CompletableFuture.completedFuture(resolved);
            }
            Object future = loadWorld.invoke(provider, logicalName);
            if (!(future instanceof CompletableFuture<?> loading)) {
                throw new IllegalStateException("WorldManager load did not return CompletableFuture");
            }
            return loading.thenApply(value -> {
                if (!(value instanceof World world)) {
                    throw new IllegalStateException("WorldManager loaded an invalid world value");
                }
                return world;
            });
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("WorldManager could not resolve world: " + logicalName, exception)
            );
        }
    }

    private Optional<String> logicalName(World world) {
        if (world == null || providerUnavailable()) return Optional.empty();
        try {
            Object result = findLogicalName.invoke(provider, world);
            if (!(result instanceof Optional<?> optional)) return Optional.empty();
            return optional.filter(String.class::isInstance).map(String.class::cast);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            logLookupFailure(exception);
            return Optional.empty();
        }
    }

    private boolean providerUnavailable() {
        if (provider != null && findLogicalName != null && findWorld != null && loadWorld != null) return false;
        if (lookupFailed) return true;

        Plugin worldManager = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (worldManager == null || !worldManager.isEnabled()) return true;

        try {
            Class<?> apiClass = Class.forName(
                    API_CLASS_NAME,
                    true,
                    worldManager.getClass().getClassLoader()
            );
            RegisteredServiceProvider<?> registration = getRegistration(apiClass);
            if (registration == null || registration.getProvider() == null) return true;
            Object service = registration.getProvider();
            provider = service;
            findLogicalName = service.getClass().getMethod("findLogicalName", World.class);
            findWorld = service.getClass().getMethod("find", String.class);
            loadWorld = service.getClass().getMethod("load", String.class);
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            lookupFailed = true;
            logLookupFailure(exception);
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private RegisteredServiceProvider<?> getRegistration(Class<?> apiClass) {
        return plugin.getServer().getServicesManager().getRegistration((Class<Object>) apiClass);
    }

    private void logLookupFailure(Throwable exception) {
        if (failureLogged) return;
        failureLogged = true;
        plugin.getLogger().log(
                Level.WARNING,
                "WorldManager is enabled but its public world API could not be used.",
                exception
        );
    }
}
