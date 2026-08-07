package cn.mythicland.worldregion;

import cn.mythicland.worldregion.api.RegionDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks each player's last resolved region and emits only meaningful transitions.
 */
final class RegionTransitionTracker {

    private final Map<UUID, RegionDefinition> currentRegions = new HashMap<>();
    private final Set<UUID> initializedPlayers = new HashSet<>();

    Optional<Transition> update(UUID playerId, Optional<RegionDefinition> currentRegion) {
        UUID targetPlayerId = Objects.requireNonNull(playerId, "playerId");
        Optional<RegionDefinition> targetRegion = Objects.requireNonNull(currentRegion, "currentRegion");
        RegionDefinition previousRegion = currentRegions.get(targetPlayerId);
        if (targetRegion.isPresent()) currentRegions.put(targetPlayerId, targetRegion.get());
        else currentRegions.remove(targetPlayerId);
        if (initializedPlayers.add(targetPlayerId)) {
            if (targetRegion.isEmpty()) return Optional.empty();
            return Optional.of(new Transition(Optional.empty(), targetRegion));
        }

        if (sameRegion(previousRegion, targetRegion.orElse(null))) return Optional.empty();
        return Optional.of(new Transition(Optional.ofNullable(previousRegion), targetRegion));
    }

    boolean isCurrent(UUID playerId, RegionDefinition region) {
        RegionDefinition currentRegion = currentRegions.get(Objects.requireNonNull(playerId, "playerId"));
        RegionDefinition expectedRegion = Objects.requireNonNull(region, "region");
        return currentRegion != null && currentRegion.id().equals(expectedRegion.id());
    }

    void clear(UUID playerId) {
        UUID targetPlayerId = Objects.requireNonNull(playerId, "playerId");
        currentRegions.remove(targetPlayerId);
        initializedPlayers.remove(targetPlayerId);
    }

    void clearAll() {
        currentRegions.clear();
        initializedPlayers.clear();
    }

    private static boolean sameRegion(
            RegionDefinition first,
            RegionDefinition second
    ) {
        if (first == null || second == null) return first == second;
        return first.id().equals(second.id());
    }

    /**
     * Immutable description of a region transition.
     *
     * @param exited  region left by the player, if any
     * @param entered region entered by the player, if any
     */
    record Transition(Optional<RegionDefinition> exited, Optional<RegionDefinition> entered) {

        Transition {
            Objects.requireNonNull(exited, "exited");
            Objects.requireNonNull(entered, "entered");
        }
    }
}
