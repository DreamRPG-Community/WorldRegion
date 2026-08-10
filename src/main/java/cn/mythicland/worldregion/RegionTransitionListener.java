package cn.mythicland.worldregion;

import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.text.LegacyActionBar;
import cn.mythicland.worldregion.api.RegionDefinition;
import cn.mythicland.worldregion.api.WorldRegionApi;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Shows Actionbar feedback when a player crosses a resolved region boundary.
 */
@ListenerComponent
final class RegionTransitionListener implements Listener {

    private static final long ACTIONBAR_MINIMUM_DISPLAY_TICKS = 20L;
    private static final long ACTIONBAR_MINIMUM_DISPLAY_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final long SERVER_TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50L);

    private final PluginTaskScope tasks;
    private final WorldRegionApi regions;
    private final RegionTransitionTracker transitions = new RegionTransitionTracker();
    private final Map<UUID, BukkitTask> pendingEntries = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingActionbars = new HashMap<>();
    private final Map<UUID, Long> actionbarAvailableAtNanos = new HashMap<>();

    RegionTransitionListener(PluginTaskScope tasks, WorldRegionApi regions) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.regions = Objects.requireNonNull(regions, "regions");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !hasBlockChanged(from, to)) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Optional<RegionTransitionTracker.Transition> transition = transitions.update(
                playerId,
                regions.findRegion(to)
        );
        if (transition.isEmpty()) return;

        cancelPendingEntry(playerId);
        RegionTransitionTracker.Transition change = transition.get();
        boolean announceLeave = shouldAnnounceLeave(change, from, to);
        boolean announceEnter = shouldAnnounceEnter(change, from, to);
        if (announceLeave) change.exited().ifPresent(region -> sendLeave(player, region));
        if (announceEnter) {
            change.entered().ifPresent(region -> {
                if (announceLeave) scheduleEntry(player, region, playerId);
                else sendEnter(player, region);
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        transitions.clear(playerId);
        cancelPendingEntry(playerId);
        cancelPendingActionbar(playerId);
        actionbarAvailableAtNanos.remove(playerId);
    }

    private void scheduleEntry(Player player, RegionDefinition region, UUID playerId) {
        if (!transitions.isCurrent(playerId, region)) return;
        BukkitTask task = tasks.runLater(ACTIONBAR_MINIMUM_DISPLAY_TICKS, () -> {
            pendingEntries.remove(playerId);
            if (!player.isOnline()) return;
            regions.findRegion(player.getLocation())
                    .filter(current -> sameRegion(current, region))
                    .ifPresent(current -> sendEnter(player, current));
        });
        pendingEntries.put(playerId, task);
    }

    private void cancelPendingEntry(UUID playerId) {
        BukkitTask task = pendingEntries.remove(playerId);
        tasks.cancel(task);
    }

    private void cancelPendingActionbar(UUID playerId) {
        BukkitTask task = pendingActionbars.remove(playerId);
        tasks.cancel(task);
    }

    private static boolean hasBlockChanged(Location from, Location to) {
        if (!Objects.equals(from.getWorld(), to.getWorld())) return true;
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    private void sendEnter(Player player, RegionDefinition region) {
        sendActionbar(player, "&f你来到了 " + region.displayName());
    }

    private void sendLeave(Player player, RegionDefinition region) {
        sendActionbar(player, "&f你离开了 " + region.displayName());
    }

    private void sendActionbar(Player player, String message) {
        UUID playerId = player.getUniqueId();
        cancelPendingActionbar(playerId);
        long now = System.nanoTime();
        long availableAt = actionbarAvailableAtNanos.getOrDefault(playerId, 0L);
        long remaining = availableAt - now;
        if (remaining <= 0L) {
            LegacyActionBar.send(player, message);
            actionbarAvailableAtNanos.put(
                    playerId,
                    now + ACTIONBAR_MINIMUM_DISPLAY_NANOS
            );
            return;
        }
        long delayTicks = Math.max(
                1L,
                (remaining + SERVER_TICK_NANOS - 1L) / SERVER_TICK_NANOS
        );
        BukkitTask task = tasks.runLater(delayTicks, () -> {
            pendingActionbars.remove(playerId);
            if (!player.isOnline()) {
                actionbarAvailableAtNanos.remove(playerId);
                return;
            }
            sendActionbar(player, message);
        });
        pendingActionbars.put(playerId, task);
    }

    static boolean shouldAnnounceLeave(
            RegionTransitionTracker.Transition transition,
            Location from,
            Location to
    ) {
        if (transition.exited().isEmpty()) return false;
        return transition.entered().isEmpty()
                || differentWorld(from, to)
                || !transition.exited().orElseThrow().bounds().contains(to);
    }

    static boolean shouldAnnounceEnter(
            RegionTransitionTracker.Transition transition,
            Location from,
            Location to
    ) {
        if (transition.entered().isEmpty()) return false;
        return transition.exited().isEmpty()
                || differentWorld(from, to)
                || !transition.entered().orElseThrow().bounds().contains(from);
    }

    private static boolean sameRegion(RegionDefinition first, RegionDefinition second) {
        if (first == null || second == null) return first == second;
        return first.id().equals(second.id());
    }

    private static boolean differentWorld(Location from, Location to) {
        return !Objects.equals(from.getWorld(), to.getWorld());
    }
}
